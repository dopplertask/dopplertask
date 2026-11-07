package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.filter.AccessFilter;
import com.dopplertask.dopplertask.service.SsoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
public class SsoController {

    private static final String EDITOR = "/public/index.html";

    private final SsoService sso;
    private final String personToken;
    private final String redirectUri;

    public SsoController(SsoService sso, @Value("${dopplertask.personToken:}") String personToken, @Value("${dopplertask.sso.redirectUri:}") String redirectUri) {
        this.sso = sso;
        this.personToken = personToken;
        this.redirectUri = redirectUri;
    }

    /**
     * What the sign-in page offers, and who is signed in.
     */
    @GetMapping("/sso/status")
    public Map<String, Object> status(HttpServletRequest request) {
        Map<String, Object> status = new HashMap<>();
        status.put("sso", sso.isEnabled());
        status.put("tokens", !personToken.isEmpty());
        sso.personOf(AccessFilter.sessionIdOf(request)).ifPresent(person -> status.put("person", person));
        return status;
    }

    @GetMapping("/sso/login")
    public ResponseEntity<Void> login(HttpServletRequest request) {
        if (!sso.isEnabled()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        try {
            return redirect(sso.begin(callbackOf(request)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return backToSignIn("Sign-in was interrupted. Please try again.");
        } catch (IOException | JacksonException | IllegalArgumentException e) {
            return backToSignIn("The identity provider could not be reached.");
        }
    }

    @GetMapping("/sso/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state, HttpServletRequest request) {
        if (code == null) {
            return backToSignIn("The identity provider did not sign you in.");
        }
        try {
            String session = sso.complete(code, state);
            ResponseCookie cookie = ResponseCookie.from(AccessFilter.SESSION_COOKIE, session)
                    .httpOnly(true).sameSite("Lax").path("/").secure(isHttps(request)).build();
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(EDITOR)).header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
        } catch (SsoService.SsoException e) {
            return backToSignIn(e.getMessage());
        }
    }

    @PostMapping("/sso/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        sso.signOut(AccessFilter.sessionIdOf(request));
        ResponseCookie expired = ResponseCookie.from(AccessFilter.SESSION_COOKIE, "").httpOnly(true).sameSite("Lax").path("/").maxAge(0).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expired.toString()).build();
    }

    private String callbackOf(HttpServletRequest request) {
        if (!redirectUri.isEmpty()) {
            return redirectUri;
        }
        return (isHttps(request) ? "https" : "http") + "://" + request.getHeader(HttpHeaders.HOST) + "/sso/callback";
    }

    private boolean isHttps(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    private ResponseEntity<Void> backToSignIn(String message) {
        return redirect(EDITOR + "?signInError=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
