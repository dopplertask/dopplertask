package com.dopplertask.dopplertask.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/** The address a browser reached the server on, also behind a proxy that ends HTTPS and says so in X-Forwarded-Proto. */
final class ServerAddress {

    private ServerAddress() {
    }

    static String of(HttpServletRequest request) {
        return (isHttps(request) ? "https" : "http") + "://" + request.getHeader(HttpHeaders.HOST);
    }

    static boolean isHttps(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
}
