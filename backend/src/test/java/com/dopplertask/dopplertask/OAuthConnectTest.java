package com.dopplertask.dopplertask;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
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
import java.util.concurrent.CopyOnWriteArrayList;

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

    @Before
    public void startService() throws IOException {
        service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.createContext("/token", exchange -> {
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            tokenRequests.add(form);
            String answer;
            if (form.contains("grant_type=authorization_code")) {
                answer = form.contains("code_verifier=") && form.contains("code=good-code") ? "{\"access_token\":\"first\",\"refresh_token\":\"refresh-1\",\"expires_in\":3600}" : "{\"error\":\"invalid_grant\"}";
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

    private void createCredential(String name, String values) throws Exception {
        this.mockMvc.perform(post("/credentials").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"type\":\"HttpAction\",\"values\":{" + values + "}}")).andExpect(status().isOk());
    }

    private String runHttpStep(String taskName, String credential) throws Exception {
        String task = "{\"name\":\"" + taskName + "\",\"active\":false,\"parameters\":[],\"actions\":["
                + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"s\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"HttpAction\",\"method\":\"GET\",\"url\":\"" + address + "/api\",\"credential\":\"" + credential + "\",\"ports\":[{\"externalId\":\"h\",\"portType\":\"INPUT\"}]}],"
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
