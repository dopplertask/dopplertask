package com.dopplertask.dopplertask.filter;

import com.dopplertask.dopplertask.service.SsoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Requires a token once one is configured. Without tokens everything stays open, as it always has been.
 */
@Component
public class AccessFilter extends OncePerRequestFilter {

    private static final Pattern OPEN_PATHS = Pattern.compile("^(/|/public/.*|/webhook/.*|/sso/.*|/health|/oauth/callback)$");
    private static final String BEARER = "Bearer ";
    private static final String AGENT_REFUSED = "An agent cannot do this. A person has to.";
    private static final String AGENT_ENDPOINT = "/mcp";
    public static final String SESSION_COOKIE = "dopplertask_session";

    // Everything an agent may do. Anything else, such as deleting or running a single action, is for a person.
    private static final List<Pattern> AGENT_REQUESTS = List.of(
            Pattern.compile("^GET /(task|task/actions|task/grouped|executions|credentials)$"),
            Pattern.compile("^GET /task/[^/]+/(taskname|checksum)$"),
            Pattern.compile("^GET /execution/\\d+$"),
            Pattern.compile("^POST /(task|schedule/task|schedule/directtask|mcp)$"));

    private final String personToken;
    private final String agentToken;
    private final SsoService sso;

    public AccessFilter(@Value("${dopplertask.personToken:}") String personToken, @Value("${dopplertask.agentToken:}") String agentToken, SsoService sso) {
        if (personToken.isEmpty() && !agentToken.isEmpty() && !sso.isEnabled()) {
            throw new IllegalStateException("An agent token needs a person token or SSO too, or nobody could approve what the agent proposes. Set DOPPLERTASK_PERSON_TOKEN.");
        }
        this.sso = sso;
        this.personToken = personToken;
        this.agentToken = agentToken;
    }

    public static String sessionIdOf(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, SESSION_COOKIE);
        return cookie == null ? null : cookie.getValue();
    }

    /**
     * Who made this request. Without tokens it is a person, except on the agent endpoint.
     */
    public static Caller callerOf(HttpServletRequest request) {
        return request.getAttribute(Caller.REQUEST_ATTRIBUTE) == Caller.AGENT ? Caller.AGENT : Caller.PERSON;
    }

    /** The person making this request, when the sign-in names one. */
    public static Optional<String> personOf(HttpServletRequest request) {
        return Optional.ofNullable((String) request.getAttribute(Caller.PERSON_ATTRIBUTE));
    }

    /**
     * For a filter in front of this one that signs callers in itself. An agent named here gets the same limits as one
     * that signed in with the agent token, and a person's name is kept with what that person approves.
     */
    public static void signIn(HttpServletRequest request, Caller caller, String name) {
        request.setAttribute(Caller.REQUEST_ATTRIBUTE, caller);
        request.setAttribute(Caller.PERSON_ATTRIBUTE, caller == Caller.PERSON ? name : null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (callerOf(request) == Caller.AGENT && !agentMay(request)) {
            refuse(response, HttpStatus.FORBIDDEN, AGENT_REFUSED);
            return;
        }
        boolean protectedAccess = !personToken.isEmpty() || sso.isEnabled();
        if (!protectedAccess || OPEN_PATHS.matcher(request.getRequestURI()).matches()) {
            // Trying an agent should need no setup, and what it does is still only a proposal.
            if (AGENT_ENDPOINT.equals(request.getRequestURI())) {
                request.setAttribute(Caller.REQUEST_ATTRIBUTE, Caller.AGENT);
            }
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()) : "";
        Optional<SsoService.Person> person = sso.personOf(sessionIdOf(request));
        if (matches(token, personToken) || person.isPresent()) {
            person.ifPresent(signedIn -> signIn(request, Caller.PERSON, signedIn.email()));
            chain.doFilter(request, response);
        } else if (matches(token, agentToken)) {
            if (agentMay(request)) {
                signIn(request, Caller.AGENT, null);
                chain.doFilter(request, response);
            } else {
                refuse(response, HttpStatus.FORBIDDEN, AGENT_REFUSED);
            }
        } else {
            refuse(response, HttpStatus.UNAUTHORIZED, "A valid access token is required.");
        }
    }

    private static boolean agentMay(HttpServletRequest request) {
        String requested = request.getMethod() + " " + request.getRequestURI();
        return AGENT_REQUESTS.stream().anyMatch(allowed -> allowed.matcher(requested).matches());
    }

    // Compares the whole token every time, so that the answer's timing does not reveal how much of it was right.
    private boolean matches(String given, String expected) {
        return !expected.isEmpty() && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private void refuse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
