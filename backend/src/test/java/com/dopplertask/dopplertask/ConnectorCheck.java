package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.ActionResult;

import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorAction;
import com.dopplertask.dopplertask.domain.action.connector.Connectors;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Runs every connector action with the inputs of its fixture and compares what it sends with what the original
 * Activepieces action sent, which tools/connector-harness recorded into the fixture. Nothing leaves the machine: the
 * requests go to a server here, which answers with the fixture's answers.
 *
 * Runs as a test, and without Gradle from the source folders, so that a new connector file needs no build:
 * java -cp "$(cat build/connector-check-classpath.txt)" com.dopplertask.dopplertask.ConnectorCheck resend
 */
public class ConnectorCheck {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final VariableExtractorUtil variables = new VariableExtractorUtil(Application.lockedTemplateEngine(-1));
    private final List<JsonNode> sent = new CopyOnWriteArrayList<>();
    private Iterator<JsonNode> answers;

    public static void main(String[] arguments) throws IOException {
        List<String> differences = new ConnectorCheck().differences(arguments.length > 0 ? arguments[0] : "");
        System.out.println(differences.isEmpty() ? "Every action sends what the original sent." : String.join("\n", differences));
        System.exit(differences.isEmpty() ? 0 : 1);
    }

    @Test
    public void testEveryConnectorActionSendsWhatTheOriginalSentSuccess() throws IOException {
        List<String> differences = differences("");
        Assert.assertTrue(String.join("\n", differences), differences.isEmpty());
    }

    /** What differs from the originals, for one connector or, when empty, for all of them. */
    public List<String> differences(String only) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        Connectors.INSTANCE.setLocalServerForTests("http://127.0.0.1:" + server.getAddress().getPort());
        try {
            return check(only);
        } finally {
            Connectors.INSTANCE.setLocalServerForTests(null);
            server.stop(0);
        }
    }

    private List<String> check(String only) throws IOException {
        List<String> differences = new ArrayList<>();
        int checked = 0;
        for (Resource file : new PathMatchingResourcePatternResolver().getResources("classpath*:connector-fixtures/" + (only.isEmpty() ? "*" : only) + "/*.json")) {
            JsonNode fixture = MAPPER.readTree(file.getInputStream());
            String connector = URI.create(file.getURI().toString()).getPath().replaceAll(".*/connector-fixtures/([^/]+)/.*", "$1");
            String name = connector + " " + fixture.path("action").asText();
            try {
                differences.addAll(compare(name, fixture, run(connector, fixture)));
            } catch (Exception e) {
                differences.add(name + ": " + e);
            }
            checked++;
        }
        if (checked == 0) {
            differences.add("There are no fixtures for " + (only.isEmpty() ? "any connector" : only) + ".");
        }
        return differences;
    }

    private ActionResult run(String connector, JsonNode fixture) throws IOException {
        sent.clear();
        List<JsonNode> replies = new ArrayList<>();
        fixture.path("answers").forEach(replies::add);
        answers = replies.iterator();
        ConnectorAction step = new ConnectorAction(connector);
        step.setOperation(fixture.path("action").asText());
        JsonNode settings = fixture.has("settings") ? fixture.path("settings") : fixture.path("inputs");
        settings.properties().forEach(setting -> step.setSetting(setting.getKey(), setting.getValue().isString() ? setting.getValue().asString() : setting.getValue()));
        step.useCredentialValues(MAPPER.convertValue(fixture.path("credential"), Map.class));
        return step.perform(new TaskExecution(), variables);
    }

    private List<String> compare(String name, JsonNode fixture, ActionResult result) {
        List<String> differences = new ArrayList<>();
        JsonNode expected = fixture.path("exchanges");
        if (expected.size() != sent.size()) {
            differences.add(name + ": sent " + sent.size() + " request(s), the original sent " + expected.size() + ". " + result.getErrorMsg());
            return differences;
        }
        for (int index = 0; index < expected.size(); index++) {
            JsonNode original = expected.get(index).path("request");
            JsonNode ours = sent.get(index);
            URI address = URI.create(original.path("url").asText());
            String where = name + " request " + (index + 1) + ": ";
            check(differences, where + "method", original.path("method").asText(), ours.path("method").asText());
            check(differences, where + "path", address.getRawPath(), ours.path("path").asText());
            check(differences, where + "query", sortedQuery(address.getRawQuery()), sortedQuery(ours.path("query").asText(null)));
            original.path("headers").properties().forEach(header ->
                    check(differences, where + "header " + header.getKey(), header.getValue().asText(), ours.path("headers").path(header.getKey()).asText(null)));
            check(differences, where + "body", original.path("body"), ours.path("body"));
        }
        if (fixture.has("output") && !fixture.path("output").has("error")) {
            check(differences, name + ": output", fixture.path("output"), parsed(result.getOutput()));
        }
        return differences;
    }

    private static void check(List<String> differences, String what, Object original, Object ours) {
        if (!java.util.Objects.equals(original, ours)) {
            differences.add(what + " differs. Original: " + original + ". Ours: " + ours + ".");
        }
    }

    private static String sortedQuery(String query) {
        return query == null || query.isEmpty() ? "" : Arrays.stream(query.split("&")).sorted().collect(Collectors.joining("&"));
    }

    private void answer(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((key, values) -> headers.put(key.toLowerCase(), values.get(0)));
        sent.add(MAPPER.valueToTree(Map.of("method", exchange.getRequestMethod(), "path", exchange.getRequestURI().getRawPath(),
                "query", exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery(), "headers", headers, "body", parsed(body))));
        JsonNode reply = answers.hasNext() ? answers.next() : MAPPER.readTree("{\"status\":599,\"body\":{\"error\":\"The fixture has no answer for this request.\"}}");
        byte[] bytes = (reply.path("body").isString() ? reply.path("body").asString() : reply.path("body").toString()).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.path("status").asInt(200), bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static JsonNode parsed(String text) {
        if (text == null || text.isEmpty()) {
            return MAPPER.nullNode();
        }
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            return MAPPER.getNodeFactory().textNode(text);
        }
    }
}
