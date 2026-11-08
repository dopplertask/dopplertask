package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.action.connector.Connectors;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A connector's input offers the choices its service has, read on the server with the step's credential. */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ConnectorChoicesTest {

    @Autowired
    private MockMvc mockMvc;

    private HttpServer service;
    private final AtomicReference<String> signedInWith = new AtomicReference<>();

    @Before
    public void startService() throws IOException {
        service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.createContext("/columns", exchange -> {
            byte[] body = "{\"columns\": [{\"id\": \"c1\", \"title\": \"Name\", \"kind\": \"text\"}, {\"id\": \"c2\", \"title\": \"Age\", \"kind\": \"num\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        service.createContext("/lists", exchange -> {
            signedInWith.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"data\": [{\"id\": \"l1\", \"title\": \"Customers\"}, {\"id\": \"l2\", \"title\": \"Partners\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        service.start();
        Connectors.INSTANCE.setLocalServerForTests("http://127.0.0.1:" + service.getAddress().getPort());
    }

    @After
    public void stopService() {
        Connectors.INSTANCE.setLocalServerForTests(null);
        service.stop(0);
    }

    @Test
    public void testChoicesComeFromTheServiceSuccess() throws Exception {
        credential("choices-key", "choices-test", "{\"key\": \"secret-key\"}");
        choices("choices-key").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("l1")).andExpect(jsonPath("$[0].label").value("Customers"))
                .andExpect(jsonPath("$[1].label").value("Partners"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("secret-key"))));
        Assert.assertEquals("Bearer secret-key", signedInWith.get());
    }

    @Test
    public void testAFormLoadsItsFieldsFromTheServiceSuccess() throws Exception {
        credential("fields-key", "choices-test", "{\"key\": \"secret-key\"}");
        mockMvc.perform(post("/connectors/choices-test/fields").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\": \"send\", \"input\": \"row\", \"credential\": \"fields-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("c1")).andExpect(jsonPath("$[0].label").value("Name")).andExpect(jsonPath("$[0].type").value("string"))
                .andExpect(jsonPath("$[1].label").value("Age")).andExpect(jsonPath("$[1].type").value("number"));
    }

    @Test
    public void testACredentialOfAnotherKindIsRefusedFail() throws Exception {
        credential("http-key", "HttpAction", "{\"authHeaderName\": \"X-Key\", \"authHeaderValue\": \"other\"}");
        choices("http-key").andExpect(status().isBadRequest()).andExpect(content().string(containsString("this step needs choices-test")));
    }

    private void credential(String name, String type, String values) throws Exception {
        mockMvc.perform(post("/credentials").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\",\"type\":\"" + type + "\",\"values\":" + values + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions choices(String credential) throws Exception {
        return mockMvc.perform(post("/connectors/choices-test/choices").contentType(MediaType.APPLICATION_JSON)
                .content("{\"operation\": \"send\", \"input\": \"list_id\", \"credential\": \"" + credential + "\"}"));
    }
}
