package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorAction;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorTrigger;
import com.dopplertask.dopplertask.domain.action.connector.Connectors;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** A credential's session signs in first, and the step's requests carry its token instead of the credential. */
public class ConnectorSessionTest {

    private final VariableExtractorUtil variables = new VariableExtractorUtil(Application.lockedTemplateEngine(-1));
    private final List<String> asked = new CopyOnWriteArrayList<>();
    private HttpServer service;

    @Before
    public void startService() throws IOException {
        service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.createContext("/", exchange -> {
            String signedWith = String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")) + "|" + exchange.getRequestHeaders().getFirst("X-Key");
            String query = exchange.getRequestURI().getRawQuery();
            asked.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + (query == null ? "" : "?" + query) + " " + signedWith + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String answer = switch (exchange.getRequestURI().getPath()) {
                case "/logon" -> "{\"token\": \"s-1\"}";
                case "/p1" -> "{\"access_token\": \"page-token\"}";
                case "/items" -> "{\"data\": [{\"id\": \"i2\"}, {\"id\": \"i1\"}]}";
                default -> "{\"ok\": true}";
            };
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
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
    public void testAStepSignsInAndSendsOnlyTheSessionTokenSuccess() {
        String output = action("session-test", "items", Map.of(), Map.of("key", "k", "user", "ada", "password", "secret")).perform(new TaskExecution(), variables).getOutput();
        Assert.assertEquals(List.of(
                "POST /logon null|k {\"username\":\"ada\",\"password\":\"secret\"}",
                "GET /items Session s-1|null ",
                "GET /more Session s-1|null "), asked);
        Assert.assertFalse(output, output.contains("s-1") || output.contains("secret"));
    }

    @Test
    public void testWithoutTheValuesASessionNeedsTheCredentialSignsAsUsualSuccess() {
        action("session-test", "items", Map.of(), Map.of("key", "k")).perform(new TaskExecution(), variables);
        Assert.assertEquals(List.of("GET /items null|k ", "GET /more null|k "), asked);
    }

    @Test
    public void testATriggerSignsInForEachCheckSuccess() {
        ConnectorTrigger trigger = ConnectorTrigger.of("session-test");
        trigger.setOperation("new_item");
        trigger.useCredentialValues(Map.of("key", "k", "user", "ada", "password", "secret"));
        trigger.lookForNewItems();
        Assert.assertEquals("GET /items Session s-1|null ", asked.get(1));
    }

    @Test
    public void testAPagesOwnTokenIsAskedForWithTheStepsPageSuccess() {
        action("page-test", "post", Map.of("page", "p1", "message", "Hello"), Map.of("token", "user-token")).perform(new TaskExecution(), variables);
        Assert.assertEquals(List.of(
                "GET /p1?fields=access_token Bearer user-token|null ",
                "POST /p1/feed?access_token=page-token null|null {\"message\":\"Hello\"}"), asked);
    }

    private static ConnectorAction action(String connector, String operation, Map<String, String> settings, Map<String, String> credential) {
        ConnectorAction step = ConnectorAction.of(connector);
        step.setOperation(operation);
        settings.forEach(step::setSetting);
        step.useCredentialValues(credential);
        return step;
    }
}
