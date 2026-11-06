package com.dopplertask.dopplertask.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Requires a token once one is configured. Without tokens everything stays open, as it always has been.
 */
@Component
public class AccessFilter extends OncePerRequestFilter {

    private static final Pattern OPEN_PATHS = Pattern.compile("^(/|/public/.*|/webhook/.*)$");
    private static final String BEARER = "Bearer ";

    // Everything an agent may do. Anything else, such as deleting or running a single action, is for a person.
    private static final List<Pattern> AGENT_REQUESTS = List.of(
            Pattern.compile("^GET /(task|task/actions|task/grouped|executions)$"),
            Pattern.compile("^GET /task/[^/]+/(taskname|checksum)$"),
            Pattern.compile("^GET /execution/\\d+$"),
            Pattern.compile("^POST /(task|schedule/task|schedule/directtask|mcp)$"));

    private final String personToken;
    private final String agentToken;

    public AccessFilter(@Value("${dopplertask.personToken:}") String personToken, @Value("${dopplertask.agentToken:}") String agentToken) {
        if (personToken.isEmpty() && !agentToken.isEmpty()) {
            throw new IllegalStateException("An agent token needs a person token too, or nobody could approve what the agent proposes. Set DOPPLERTASK_PERSON_TOKEN.");
        }
        this.personToken = personToken;
        this.agentToken = agentToken;
    }

    /**
     * Who made this request. Without tokens it is always a person.
     */
    public static Caller callerOf(HttpServletRequest request) {
        return request.getAttribute(Caller.REQUEST_ATTRIBUTE) == Caller.AGENT ? Caller.AGENT : Caller.PERSON;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (personToken.isEmpty() || OPEN_PATHS.matcher(request.getRequestURI()).matches()) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()) : "";
        if (matches(token, personToken)) {
            chain.doFilter(request, response);
        } else if (matches(token, agentToken)) {
            String requested = request.getMethod() + " " + request.getRequestURI();
            if (AGENT_REQUESTS.stream().anyMatch(allowed -> allowed.matcher(requested).matches())) {
                request.setAttribute(Caller.REQUEST_ATTRIBUTE, Caller.AGENT);
                chain.doFilter(request, response);
            } else {
                refuse(response, HttpStatus.FORBIDDEN, "An agent cannot do this. A person has to.");
            }
        } else {
            refuse(response, HttpStatus.UNAUTHORIZED, "A valid access token is required.");
        }
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
