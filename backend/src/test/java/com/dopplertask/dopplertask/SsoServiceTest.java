package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.service.SsoService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Runs the whole sign-in against a small identity provider that signs real tokens, and checks what must be refused.
 */
public class SsoServiceTest {

    private static final String CALLBACK = "http://localhost/sso/callback";

    private HttpServer provider;
    private String issuer;
    private RSAKey key;
    private RSAKey signingKey;
    private Consumer<JWTClaimsSet.Builder> tokenChange = claims -> {
    };
    private String nonceFromRequest;
    private String challengeFromRequest;
    private String verifierReceived;

    @Before
    public void startProvider() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("real").generate();
        signingKey = key;
        provider = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        issuer = "http://localhost:" + provider.getAddress().getPort();
        provider.createContext("/.well-known/openid-configuration", exchange -> answer(exchange, 200, "{\"issuer\":\"" + issuer + "\",\"authorization_endpoint\":\"" + issuer + "/authorize\",\"token_endpoint\":\"" + issuer + "/token\",\"jwks_uri\":\"" + issuer + "/keys\"}"));
        provider.createContext("/keys", exchange -> answer(exchange, 200, new JWKSet(key.toPublicJWK()).toString()));
        provider.createContext("/token", this::token);
        provider.start();
    }

    @After
    public void stopProvider() {
        provider.stop(0);
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> fields = parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        verifierReceived = fields.get("code_verifier");
        if (!"good-code".equals(fields.get("code"))) {
            answer(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer(issuer).audience("client").subject("user-1")
                    .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 60000))
                    .claim("nonce", nonceFromRequest).claim("email", "ada@example.com").claim("email_verified", true).claim("name", "Ada");
            tokenChange.accept(claims);
            SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("real").build(), claims.build());
            token.sign(new RSASSASigner(signingKey));
            answer(exchange, 200, "{\"id_token\":\"" + token.serialize() + "\"}");
        } catch (Exception e) {
            answer(exchange, 500, "{}");
        }
    }

    private void answer(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private Map<String, String> parse(String query) {
        return Arrays.stream(query.split("&")).map(part -> part.split("=", 2))
                .collect(Collectors.toMap(part -> part[0], part -> URLDecoder.decode(part[1], StandardCharsets.UTF_8)));
    }

    private SsoService service(String allowedDomains) {
        return new SsoService(issuer, "client", "secret", allowedDomains);
    }

    private String begin(SsoService sso) throws Exception {
        Map<String, String> sent = parse(URI.create(sso.begin(CALLBACK)).getRawQuery());
        nonceFromRequest = sent.get("nonce");
        challengeFromRequest = sent.get("code_challenge");
        Assert.assertEquals("S256", sent.get("code_challenge_method"));
        return sent.get("state");
    }

    private void assertRefused(SsoService sso, String state, String code) {
        Assert.assertThrows(SsoService.SsoException.class, () -> sso.complete(code, state));
    }

    @Test
    public void testASignInGivesASessionWithThePersonSuccess() throws Exception {
        SsoService sso = service("");
        String session = sso.complete("good-code", begin(sso));
        Assert.assertEquals("ada@example.com", sso.personOf(session).orElseThrow().email());
        Assert.assertEquals("Ada", sso.personOf(session).orElseThrow().name());
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifierReceived.getBytes(StandardCharsets.US_ASCII)));
        Assert.assertEquals("The token request must prove it comes from the same sign-in", expected, challengeFromRequest);

        sso.signOut(session);
        Assert.assertTrue(sso.personOf(session).isEmpty());
    }

    @Test
    public void testATokenSignedWithAnotherKeyIsRefusedFail() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("real").generate();
        SsoService sso = service("");
        assertRefused(sso, begin(sso), "good-code");
    }

    @Test
    public void testATokenForAnotherApplicationIsRefusedFail() throws Exception {
        tokenChange = claims -> claims.audience("someone-else");
        SsoService sso = service("");
        assertRefused(sso, begin(sso), "good-code");
    }

    @Test
    public void testATokenFromAnotherIssuerIsRefusedFail() throws Exception {
        tokenChange = claims -> claims.issuer("http://evil.example");
        SsoService sso = service("");
        assertRefused(sso, begin(sso), "good-code");
    }

    @Test
    public void testAnExpiredTokenIsRefusedFail() throws Exception {
        tokenChange = claims -> claims.expirationTime(new Date(System.currentTimeMillis() - 600000));
        SsoService sso = service("");
        assertRefused(sso, begin(sso), "good-code");
    }

    @Test
    public void testATokenFromAnotherSignInIsRefusedFail() throws Exception {
        tokenChange = claims -> claims.claim("nonce", "from-another-sign-in");
        SsoService sso = service("");
        assertRefused(sso, begin(sso), "good-code");
    }

    @Test
    public void testAStateCanOnlyBeUsedOnceFail() throws Exception {
        SsoService sso = service("");
        String state = begin(sso);
        sso.complete("good-code", state);
        assertRefused(sso, state, "good-code");
        assertRefused(sso, "made-up", "good-code");
        assertRefused(sso, null, "good-code");
    }

    @Test
    public void testACodeTheProviderRefusesGivesNoSessionFail() throws Exception {
        SsoService sso = service("");
        assertRefused(sso, begin(sso), "bad-code");
    }

    @Test
    public void testOnlyAllowedDomainsCanSignInSuccess() throws Exception {
        SsoService allowed = service("other.org, Example.com");
        Assert.assertTrue(allowed.personOf(allowed.complete("good-code", begin(allowed))).isPresent());

        SsoService refused = service("other.org");
        assertRefused(refused, begin(refused), "good-code");
    }

    @Test
    public void testAnUnverifiedAddressDoesNotPassADomainRuleFail() throws Exception {
        tokenChange = claims -> claims.claim("email_verified", false);
        SsoService sso = service("example.com");
        assertRefused(sso, begin(sso), "good-code");
    }

    @Test
    public void testAnIssuerThatGivesAnotherNameIsRefusedFail() throws Exception {
        SsoService sso = new SsoService(issuer + "/other", "client", "", "");
        Assert.assertThrows(tools.jackson.core.JacksonException.class, () -> sso.begin(CALLBACK));
    }

    @Test(expected = IllegalStateException.class)
    public void testAnIssuerWithoutAClientIdIsRefusedAtStartFail() {
        new SsoService("http://localhost", "", "", "");
    }
}
