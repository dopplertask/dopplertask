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
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Stops a web page on another site from changing or running anything here. A task can run commands, and a browser
 * sends such a request without asking when its content type looks like plain text. Browsers always name the site in
 * the Origin header. Other clients do not send one and are not affected, and webhooks exist to be called from outside.
 */
@Component
public class CrossSiteRequestFilter extends OncePerRequestFilter {

    private static final List<String> READ_ONLY_METHODS = List.of("GET", "HEAD", "OPTIONS");
    private static final String WEBHOOK_PATH = "/webhook/";

    private final List<String> allowedOrigins;

    public CrossSiteRequestFilter(@Value("${dopplertask.allowedOrigins:}") String allowedOrigins) {
        this.allowedOrigins = Arrays.asList(allowedOrigins.split(","));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || READ_ONLY_METHODS.contains(request.getMethod()) || request.getRequestURI().startsWith(WEBHOOK_PATH)
                || allowedOrigins.contains(origin) || isSameSite(origin, request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"Requests from another site are not allowed. Add the site to dopplertask.allowedOrigins if it should have access.\"}");
    }

    private boolean isSameSite(String origin, HttpServletRequest request) {
        String authority = authorityOf(origin);
        return authority != null && (authority.equalsIgnoreCase(request.getHeader("Host")) || authority.equalsIgnoreCase(request.getHeader("X-Forwarded-Host")));
    }

    // A sandboxed page sends the origin "null", which names no site.
    private String authorityOf(String origin) {
        try {
            return URI.create(origin).getAuthority();
        } catch (IllegalArgumentException notAnOrigin) {
            return null;
        }
    }
}
