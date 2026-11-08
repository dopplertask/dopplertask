package com.dopplertask.dopplertask;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import com.dopplertask.dopplertask.domain.action.connection.HttpAction;
import com.dopplertask.dopplertask.domain.action.integration.OAuthSignIn;
import com.dopplertask.dopplertask.service.CredentialService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A small stand-in for a service: it signs people in, gives tokens, and answers API calls with the token it saw.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OAuthConnectTest {

    @Autowired
    private MockMvc mockMvc;

    private HttpServer service;
    private String address;
    private final List<String> tokenRequests = new CopyOnWriteArrayList<>();
    private final AtomicInteger rotations = new AtomicInteger(1);

    @Autowired
    private CredentialService credentials;

    @Before
    public void startService() throws IOException {
        service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.createContext("/token", exchange -> {
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            tokenRequests.add(form);
            String answer;
            if (form.contains("grant_type=authorization_code")) {
                answer = form.contains("code_verifier=") && form.contains("code=good-code") ? "{\"access_token\":\"first\",\"refresh_token\":\"refresh-1\",\"expires_in\":3600}" : "{\"error\":\"invalid_grant\"}";
            } else if (form.contains("refresh_token=rotate-")) {
                // A service that replaces the refresh token on every use and refuses the old one, with tokens too short to keep.
                int used = Integer.parseInt(form.replaceAll(".*refresh_token=rotate-(\\d+).*", "$1"));
                answer = used == rotations.get() ? "{\"access_token\":\"rotated-" + used + "\",\"refresh_token\":\"rotate-" + rotations.incrementAndGet() + "\",\"expires_in\":1}" : "{\"error\":\"invalid_grant\"}";
            } else if (form.contains("grant_type=refresh_token")) {
                answer = form.contains("refresh_token=refresh-1") ? "{\"access_token\":\"fresh\",\"expires_in\":3600}" : "{\"error\":\"invalid_grant\"}";
            } else {
                answer = form.contains("client_secret=machine-secret") ? "{\"access_token\":\"machine\",\"expires_in\":3600}" : "{\"error\":\"invalid_client\"}";
            }
            reply(exchange, answer);
        });
        service.createContext("/api", exchange -> reply(exchange, "{\"token\":\"" + exchange.getRequestHeaders().getFirst("Authorization") + "\"}"));
        service.start();
        address = "http://127.0.0.1:" + service.getAddress().getPort();
    }

    @After
    public void stopService() {
        service.stop(0);
    }

    @Test
    public void testConnectAccountThenStepsSignInWithIt() throws Exception {
        createCredential("account-api", "\"oauthProvider\":\"CUSTOM\",\"oauthClientId\":\"app\",\"oauthClientSecret\":\"app-secret\",\"oauthScope\":\"read write\","
                + "\"oauthAuthUrl\":\"" + address + "/authorize\",\"oauthTokenUrl\":\"" + address + "/token\",\"oauthAuthParameters\":\"audience=api\"");

        String signInUrl = JsonPath.read(this.mockMvc.perform(post("/credentials/account-api/connect").header("Host", "dopplertask.example"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.url");
        Map<String, List<String>> query = UriComponentsBuilder.fromUriString(signInUrl).build().getQueryParams();
        Assert.assertTrue(signInUrl.startsWith(address + "/authorize?"));
        Assert.assertEquals("app", query.get("client_id").get(0));
        Assert.assertEquals("http%3A%2F%2Fdopplertask.example%2Foauth%2Fcallback", query.get("redirect_uri").get(0));
        Assert.assertEquals("S256", query.get("code_challenge_method").get(0));
        Assert.assertEquals("api", query.get("audience").get(0));
        Assert.assertFalse("The secret stays on the server", signInUrl.contains("app-secret"));
        String state = query.get("state").get(0);

        this.mockMvc.perform(get("/oauth/callback").param("state", state).param("code", "good-code"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("The account is connected")));
        this.mockMvc.perform(get("/credentials")).andExpect(jsonPath("$[?(@.name == 'account-api')].fieldsSet[*]", hasItem("oauthRefreshToken")));

        // A sign-in link works once.
        this.mockMvc.perform(get("/oauth/callback").param("state", state).param("code", "good-code"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("already used")));

        Assert.assertTrue(runHttpStep("oauth_account", "account-api").contains("Bearer fresh"));
    }

    @Test
    public void testClientSignsInAsItselfWithoutConnecting() throws Exception {
        createCredential("machine-api", "\"oauthProvider\":\"CUSTOM\",\"oauthGrant\":\"clientCredentials\",\"oauthClientId\":\"machine\",\"oauthClientSecret\":\"machine-secret\","
                + "\"oauthTokenUrl\":\"" + address + "/token\"");
        Assert.assertTrue(runHttpStep("oauth_machine", "machine-api").contains("Bearer machine"));
        this.mockMvc.perform(post("/credentials/machine-api/connect")).andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("no account to connect")));
    }

    @Test
    public void testServiceRefusingTheCodeExplainsWhyFail() throws Exception {
        createCredential("refused-api", "\"oauthProvider\":\"CUSTOM\",\"oauthClientId\":\"app\",\"oauthAuthUrl\":\"" + address + "/authorize\",\"oauthTokenUrl\":\"" + address + "/token\"");
        String signInUrl = JsonPath.read(this.mockMvc.perform(post("/credentials/refused-api/connect")).andReturn().getResponse().getContentAsString(), "$.url");
        String state = UriComponentsBuilder.fromUriString(signInUrl).build().getQueryParams().get("state").get(0);
        this.mockMvc.perform(get("/oauth/callback").param("state", state).param("error", "access_denied"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("did not connect the account: access_denied")));
    }

    @Test
    public void testCredentialWithoutConnectedAccountSendsNoTokenSuccess() throws Exception {
        createCredential("unconnected-api", "\"oauthProvider\":\"CUSTOM\",\"oauthClientId\":\"app\",\"oauthTokenUrl\":\"" + address + "/token\",\"oauthAuthParameters\":\"x=1\"");
        // Without a token the step signs in as before, so nothing is sent with an account.
        String run = runHttpStep("oauth_unconnected", "unconnected-api");
        Assert.assertTrue(run, run.contains("FINISHED") && !run.contains("Bearer"));
    }

    @Test
    public void testNewRefreshTokenIsKeptWhenTheRunFailsSuccess() throws Exception {
        createCredential("rotating-api", "\"oauthProvider\":\"CUSTOM\",\"oauthClientId\":\"app\",\"oauthTokenUrl\":\"" + address + "/token\",\"oauthRefreshToken\":\"rotate-1\"");
        String run = runHttpStep("oauth_rotating", "rotating-api", "\"failOn\":\"stop\",");
        Assert.assertTrue(run, run.contains("FAILED"));
        Assert.assertEquals("rotate-2", credentials.valuesNamed("rotating-api").get("oauthRefreshToken"));
    }

    @Test
    public void testARunHoldingAReplacedRefreshTokenGetsTheNewOneSuccess() throws Exception {
        createCredential("rotating-twice", "\"oauthProvider\":\"CUSTOM\",\"oauthClientId\":\"app\",\"oauthTokenUrl\":\"" + address + "/token\"");
        HttpAction startedEarly = stepHolding("rotating-twice", "rotate-1");
        // Both runs hold rotate-1, which the first one uses up.
        Assert.assertEquals("Bearer rotated-1", OAuthSignIn.INSTANCE.headersFor(startedEarly, "The step").getHeaders().get("Authorization"));
        Assert.assertEquals("Bearer rotated-2", OAuthSignIn.INSTANCE.headersFor(startedEarly, "The step").getHeaders().get("Authorization"));
    }

    @Test
    public void testAReconnectedAccountKeepsItsTokenSuccess() throws Exception {
        createCredential("reconnected-api", "\"oauthProvider\":\"CUSTOM\",\"oauthClientId\":\"app\",\"oauthTokenUrl\":\"" + address + "/token\",\"oauthRefreshToken\":\"connected-again\"");
        // A run that started before the person connected the account again still holds the old token.
        Assert.assertEquals("Bearer rotated-1", OAuthSignIn.INSTANCE.headersFor(stepHolding("reconnected-api", "rotate-1"), "The step").getHeaders().get("Authorization"));
        Assert.assertEquals("connected-again", credentials.valuesNamed("reconnected-api").get("oauthRefreshToken"));
    }

    @Test
    public void testRunsRefreshingAtTheSameMomentEachSignInSuccess() throws Exception {
        createCredential("busy-api", "\"oauthProvider\":\"CUSTOM\",\"oauthClientId\":\"app\",\"oauthTokenUrl\":\"" + address + "/token\",\"oauthRefreshToken\":\"rotate-1\"");
        List<CompletableFuture<String>> runs = IntStream.range(0, 4).mapToObj(run -> CompletableFuture.supplyAsync(() ->
                OAuthSignIn.INSTANCE.headersFor(stepHolding("busy-api", "rotate-1"), "The step").getHeaders().get("Authorization"))).toList();
        Assert.assertEquals(List.of("Bearer rotated-1", "Bearer rotated-2", "Bearer rotated-3", "Bearer rotated-4"), runs.stream().map(CompletableFuture::join).sorted().toList());
        Assert.assertEquals("rotate-5", credentials.valuesNamed("busy-api").get("oauthRefreshToken"));
    }

    private HttpAction stepHolding(String credential, String refreshToken) {
        HttpAction step = new HttpAction();
        step.setCredential(credential);
        step.setOauthProvider("CUSTOM");
        step.setOauthClientId("app");
        step.setOauthTokenUrl(address + "/token");
        step.setOauthRefreshToken(refreshToken);
        return step;
    }

    private void createCredential(String name, String values) throws Exception {
        this.mockMvc.perform(post("/credentials").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"type\":\"HttpAction\",\"values\":{" + values + "}}")).andExpect(status().isOk());
    }

    private String runHttpStep(String taskName, String credential) throws Exception {
        return runHttpStep(taskName, credential, "");
    }

    private String runHttpStep(String taskName, String credential, String stepFields) throws Exception {
        String task = "{\"name\":\"" + taskName + "\",\"active\":false,\"parameters\":[],\"actions\":["
                + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"s\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"HttpAction\"," + stepFields + "\"method\":\"GET\",\"url\":\"" + address + "/api\",\"credential\":\"" + credential + "\",\"ports\":[{\"externalId\":\"h\",\"portType\":\"INPUT\"}]}],"
                + "\"connections\":[{\"source\":{\"externalId\":\"s\"},\"target\":{\"externalId\":\"h\"}}]}";
        this.mockMvc.perform(post("/task").content(task)).andExpect(status().isOk());
        return this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + taskName + "\",\"parameters\":{}}"))
                .andReturn().getResponse().getContentAsString();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
