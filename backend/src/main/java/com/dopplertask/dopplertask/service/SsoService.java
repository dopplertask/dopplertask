package com.dopplertask.dopplertask.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Sign-in through the company's identity provider with OpenID Connect, using the authorization code flow with PKCE.
 * It knows who a person is, so that what they approve can be recorded. Off unless an issuer is configured.
 */
@Service
public class SsoService {

    private static final Duration SIGN_IN_TIME = Duration.ofMinutes(10);
    private static final Duration SESSION_TIME = Duration.ofHours(12);

    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final Set<String> allowedDomains;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SignIn> signIns = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private JsonNode provider;

    public SsoService(@Value("${dopplertask.sso.issuer:}") String issuer,
                      @Value("${dopplertask.sso.clientId:}") String clientId,
                      @Value("${dopplertask.sso.clientSecret:}") String clientSecret,
                      @Value("${dopplertask.sso.allowedDomains:}") String allowedDomains) {
        this.issuer = issuer.replaceAll("/$", "");
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.allowedDomains = Arrays.stream(allowedDomains.split(",")).map(String::trim).map(String::toLowerCase).filter(domain -> !domain.isEmpty()).collect(Collectors.toSet());
        if (!issuer.isEmpty() && clientId.isEmpty()) {
            throw new IllegalStateException("SSO needs a client id. Set DOPPLERTASK_SSO_CLIENT_ID.");
        }
    }

    public boolean isEnabled() {
        return !issuer.isEmpty();
    }

    /**
     * @return the address at the identity provider to send the person to.
     */
    public String begin(String redirectUri) throws IOException, InterruptedException {
        Instant now = Instant.now();
        signIns.values().removeIf(signIn -> signIn.expires.isBefore(now));
        String state = randomText();
        String nonce = randomText();
        String verifier = randomText();
        signIns.put(state, new SignIn(nonce, verifier, redirectUri, now.plus(SIGN_IN_TIME)));
        return provider().path("authorization_endpoint").asText() + "?" + form(Map.of(
                "response_type", "code",
                "client_id", clientId,
                "redirect_uri", redirectUri,
                "scope", "openid email profile",
                "state", state,
                "nonce", nonce,
                "code_challenge", challengeOf(verifier),
                "code_challenge_method", "S256"));
    }

    /**
     * Finishes a sign-in the identity provider sent back.
     *
     * @return the id of the new session.
     */
    public String complete(String code, String state) throws SsoException {
        SignIn signIn = state == null ? null : signIns.remove(state);
        if (signIn == null || signIn.expires.isBefore(Instant.now())) {
            throw new SsoException("That sign-in has expired. Please try again.");
        }
        try {
            String idToken = exchange(code, signIn);
            JWTClaimsSet claims = verified(idToken);
            if (!signIn.nonce.equals(claims.getStringClaim("nonce"))) {
                throw new SsoException("The identity provider's answer did not belong to this sign-in.");
            }
            String email = claims.getStringClaim("email");
            requireAllowed(email, claims.getBooleanClaim("email_verified"));
            String name = claims.getStringClaim("name");
            String sessionId = randomText();
            sessions.put(sessionId, new Session(new Person(email == null ? claims.getSubject() : email, name), Instant.now().plus(SESSION_TIME)));
            return sessionId;
        } catch (SsoException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SsoException("Sign-in was interrupted. Please try again.");
        } catch (Exception e) {
            throw new SsoException("The identity provider's answer could not be verified.", e);
        }
    }

    public Optional<Person> personOf(String sessionId) {
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expires.isBefore(Instant.now())) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(session.person);
    }

    public void signOut(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    private void requireAllowed(String email, Boolean verified) throws SsoException {
        if (allowedDomains.isEmpty()) {
            return;
        }
        String domain = email == null ? "" : email.substring(email.lastIndexOf('@') + 1).toLowerCase();
        // An address the provider has not verified says nothing about which company someone belongs to.
        if (!allowedDomains.contains(domain) || Boolean.FALSE.equals(verified)) {
            throw new SsoException("This account is not allowed here. Ask your administrator for access.");
        }
    }

    private String exchange(String code, SignIn signIn) throws IOException, InterruptedException, SsoException {
        Map<String, String> fields = new java.util.HashMap<>(Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", signIn.redirectUri,
                "client_id", clientId,
                "code_verifier", signIn.verifier));
        if (!clientSecret.isEmpty()) {
            fields.put("client_secret", clientSecret);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(provider().path("token_endpoint").asText()))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(fields)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = response.statusCode() == 200 ? mapper.readTree(response.body()) : null;
        if (body == null || !body.hasNonNull("id_token")) {
            throw new SsoException("The identity provider refused the sign-in.");
        }
        return body.get("id_token").asText();
    }

    private JWTClaimsSet verified(String idToken) throws Exception {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(Set.of(JWSAlgorithm.RS256, JWSAlgorithm.ES256),
                new RemoteJWKSet<>(new URL(provider().path("jwks_uri").asText()))));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(clientId,
                new JWTClaimsSet.Builder().issuer(provider().path("issuer").asText()).build(), Set.of("sub", "exp", "iat")));
        return processor.process(idToken, null);
    }

    private synchronized JsonNode provider() throws IOException, InterruptedException {
        if (provider == null) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(issuer + "/.well-known/openid-configuration")).timeout(Duration.ofSeconds(30)).build();
            JsonNode discovered = mapper.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
            if (!issuer.equals(discovered.path("issuer").asText().replaceAll("/$", ""))) {
                throw new IOException("The identity provider says it is " + discovered.path("issuer").asText() + ", not " + issuer + ".");
            }
            provider = discovered;
        }
        return provider;
    }

    private String randomText() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String challengeOf(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(field -> field.getKey() + "=" + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public record Person(String email, String name) {
    }

    private record SignIn(String nonce, String verifier, String redirectUri, Instant expires) {
    }

    private record Session(Person person, Instant expires) {
    }

    /**
     * A sign-in that did not succeed. The message can be shown to the person.
     */
    public static class SsoException extends Exception {
        public SsoException(String message) {
            super(message);
        }

        public SsoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
