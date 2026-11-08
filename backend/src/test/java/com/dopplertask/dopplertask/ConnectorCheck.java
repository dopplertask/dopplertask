package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.RunFiles;

import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorAction;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorTrigger;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorWebhooks;
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
import java.util.Base64;
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
    private String memoryDifference;

    public static void main(String[] arguments) throws IOException {
        List<String> differences = new ConnectorCheck().differences(arguments.length > 0 ? arguments[0] : "");
        System.out.println(differences.isEmpty() ? "Every action and trigger sends what the original sent." : String.join("\n", differences));
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
            String name = connector + " " + (fixture.has("trigger") ? "trigger " + fixture.path("trigger").asText() : fixture.path("action").asText());
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

    /** What our connector gave, and why it failed if it did. */
    private record Outcome(JsonNode output, String error) {
    }

    private Outcome run(String connector, JsonNode fixture) throws IOException {
        sent.clear();
        List<JsonNode> replies = new ArrayList<>();
        fixture.path("answers").forEach(replies::add);
        answers = replies.iterator();
        Map<String, String> credential = MAPPER.convertValue(fixture.path("credential"), Map.class);
        JsonNode settings = fixture.has("settings") ? fixture.path("settings") : fixture.path("inputs");
        if (fixture.has("trigger")) {
            return check(connector, fixture, settings, credential);
        }
        ConnectorAction step = ConnectorAction.of(connector);
        step.setOperation(fixture.path("action").asText());
        settings.properties().forEach(setting -> step.setSetting(setting.getKey(), setting.getValue().isString() ? setting.getValue().asString() : setting.getValue()));
        step.useCredentialValues(credential);
        // The fixture's files are files of the run, chosen by name, as Read file would keep them.
        TaskExecution run = new TaskExecution();
        fixture.path("files").properties().forEach(file -> {
            String name = file.getValue().path("name").asText();
            RunFiles.INSTANCE.keep(run, name, Base64.getDecoder().decode(file.getValue().path("base64").asText()));
            step.setSetting(file.getKey(), name);
        });
        ActionResult result = step.perform(run, variables);
        return new Outcome(parsed(result.getOutput()), result.getErrorMsg());
    }

    /** One check of a trigger from the place the fixture gives, with the new items it found. */
    private Outcome check(String connector, JsonNode fixture, JsonNode settings, Map<String, String> credential) {
        ConnectorTrigger trigger = ConnectorTrigger.of(connector);
        trigger.setOperation(fixture.path("trigger").asText());
        trigger.setMemory(memoryOf(fixture.path("store")).toString());
        memoryDifference = null;
        settings.properties().forEach(setting -> trigger.setSetting(setting.getKey(), setting.getValue().isString() ? setting.getValue().asString() : setting.getValue()));
        trigger.useCredentialValues(credential);
        if (fixture.path("webhook").asBoolean()) {
            // The same address the recorder gave the original: https://tasks.example.com/hooks/test-key.
            ConnectorWebhooks.INSTANCE.setPublicUrl("https://tasks.example.com");
            trigger.setHookKey("test-key");
            var webhook = Connectors.INSTANCE.find(connector).getTriggers().get(fixture.path("trigger").asText()).getWebhook();
            // A webhook the person sets up by pasting its address has nothing to subscribe.
            if (webhook.getSubscribe() != null) {
                trigger.subscribe(webhook);
            }
            trigger.takeItemsOf(ConnectorWebhooks.INSTANCE.eventOf("application/json", fixture.path("event").toString(),
                    fixture.has("eventHeaders") ? MAPPER.convertValue(fixture.path("eventHeaders"), Map.class) : Map.of(), Map.of()));
        } else {
            JsonNode place = fixture.path("store");
            trigger.setPollState(place.has("lastPoll") ? place.path("lastPoll").asText()
                    : place.has("seen") ? place.path("seen").toString() : place.path("lastItem").asText(null));
            trigger.lookForNewItems();
        }
        if (fixture.has("storeAfter")) {
            memoryDifference = java.util.Objects.equals(memoryOf(fixture.path("storeAfter")), MAPPER.readTree(trigger.getMemory()))
                    ? null : "memory differs. Original: " + memoryOf(fixture.path("storeAfter")) + ". Ours: " + trigger.getMemory() + ".";
        }
        List<JsonNode> items = new ArrayList<>();
        for (String item = trigger.nextWaiting(); item != null; item = trigger.nextWaiting()) {
            items.add(parsed(item));
        }
        return new Outcome(sorted(items), "");
    }

    /** The keys of the original's store that are the trigger's own memory: not its place, which the trigger keeps itself. */
    private static JsonNode memoryOf(JsonNode store) {
        Map<String, JsonNode> memory = new LinkedHashMap<>();
        store.properties().forEach(entry -> memory.put(entry.getKey(), entry.getValue()));
        List.of("lastPoll", "lastItem", "seen").forEach(memory::remove);
        return MAPPER.valueToTree(memory);
    }

    /** New items in one order, since the original lists them newest first and ours start the task oldest first. */
    private static JsonNode sorted(Iterable<JsonNode> items) {
        List<JsonNode> list = new ArrayList<>();
        items.forEach(list::add);
        list.sort(java.util.Comparator.comparing(JsonNode::toString));
        return MAPPER.valueToTree(list);
    }

    private List<String> compare(String name, JsonNode fixture, Outcome result) {
        List<String> differences = new ArrayList<>();
        JsonNode expected = fixture.path("exchanges");
        if (expected.size() != sent.size()) {
            differences.add(name + ": sent " + sent.size() + " request(s), the original sent " + expected.size() + ". " + result.error());
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
                    check(differences, where + "header " + header.getKey(), withoutBoundary(header.getValue().asText()), withoutBoundary(ours.path("headers").path(header.getKey()).asText(null))));
            check(differences, where + "body", withoutBoundary(withoutRandomMailParts(original.path("body"))), withoutBoundary(withoutRandomMailParts(ours.path("body"))));
        }
        if (memoryDifference != null) {
            differences.add(name + ": " + memoryDifference);
        }
        if (fixture.has("output") && !fixture.path("output").has("error")) {
            check(differences, name + ": output", withoutRandomMailParts(fixture.has("trigger") ? sorted(fixture.path("output")) : fixture.path("output")), withoutRandomMailParts(result.output()));
        }
        return differences;
    }

    private static void check(List<String> differences, String what, Object original, Object ours) {
        if (!java.util.Objects.equals(original, ours)) {
            differences.add(what + " differs. Original: " + original + ". Ours: " + ours + ".");
        }
    }

    /** A multipart body with its random boundary taken out, since each sender picks its own. */
    private static Object withoutBoundary(Object value) {
        String text = value instanceof JsonNode node ? (node.isTextual() ? node.asText() : null) : (String) value;
        if (text == null) {
            return value;
        }
        if (text.startsWith("multipart/form-data")) {
            return "multipart/form-data";
        }
        String boundary = text.startsWith("--") ? text.substring(2, text.indexOf("\r\n") > 0 ? text.indexOf("\r\n") : text.length()) : null;
        return boundary == null ? value : text.replace(boundary, "BOUNDARY");
    }

    /**
     * A body with the parts of an email that differ every time taken out: its Message-ID, its Date and the boundaries
     * between its parts. An email is often sent base64url-encoded in a JSON field, as Gmail takes it, so such a field is
     * compared as the email it holds.
     */
    private static JsonNode withoutRandomMailParts(JsonNode body) {
        if (body.isObject()) {
            Map<String, JsonNode> fields = new LinkedHashMap<>();
            body.properties().forEach(field -> fields.put(field.getKey(), withoutRandomMailParts(field.getValue())));
            return MAPPER.valueToTree(fields);
        }
        if (body.isArray()) {
            List<JsonNode> items = new ArrayList<>();
            body.forEach(item -> items.add(withoutRandomMailParts(item)));
            return MAPPER.valueToTree(items);
        }
        if (!body.isTextual()) {
            return body;
        }
        String text = body.asText();
        // A body written out as JSON text, as an original may give it back in its output, is compared as that JSON.
        if (text.startsWith("{") || text.startsWith("[")) {
            JsonNode json = parsed(text);
            if (!json.isTextual()) {
                return MAPPER.getNodeFactory().textNode(withoutRandomMailParts(json).toString());
            }
        }
        String email = text.contains("MIME-Version:") ? text : decodedEmail(text);
        return email == null ? body : MAPPER.getNodeFactory().textNode(sameEveryTime(email));
    }

    private static String decodedEmail(String text) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(text.replace('+', '-').replace('/', '_').replace("=", "")), StandardCharsets.UTF_8);
            return decoded.contains("MIME-Version:") ? decoded : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String sameEveryTime(String email) {
        String text = email.replaceAll("(?im)^(Message-ID|Date): .*$", "$1: SAME");
        java.util.regex.Matcher boundaries = java.util.regex.Pattern.compile("boundary=\"?([^\";\r\n]+)").matcher(email);
        while (boundaries.find()) {
            text = text.replace(boundaries.group(1), "BOUNDARY");
        }
        return text;
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
