package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.action.connector.Connector;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorRequests;
import com.dopplertask.dopplertask.domain.action.connector.Connectors;
import com.dopplertask.dopplertask.domain.action.integration.JsonApi;
import org.junit.Assert;
import org.junit.Test;

import java.util.Base64;
import java.util.Map;

/** Each word of the connector format, and the rules that keep a credential with its own service. */
public class ConnectorFormatTest {

    private static final String SERVICE = """
            {"name": "sample", "label": "Sample", "hosts": ["{{credential.subdomain}}.sample.com"], "baseUrl": "https://{{credential.subdomain}}.sample.com/api",
             "credential": {"fields": {"subdomain": {"label": "Subdomain"}, "user": {"label": "User"}, "key": {"label": "Key", "secret": true}},
                            "basic": {"username": "{{credential.user}}", "password": "{{credential.key}}"}},
             "actions": {"create": {"label": "Create",
                 "inputs": {"name": {"label": "Name"}, "size": {"label": "Size", "type": "number"}, "note": {"label": "Note"}},
                 "request": {"method": "POST", "url": "https://{{credential.subdomain}}.sample.com/api/items/{{name}}", "body": {"size": "{{size}}", "note": "{{note}}"}},
                 "beforeRequest": "request.headers['X-Size'] = String(request.body.size * 2); return request;",
                 "afterResponse": "return {id: response.data.id};"}}}
            """;

    private static final Map<String, String> CREDENTIAL = Map.of("subdomain", "acme", "user", "ada", "key", "secret-key");

    @Test
    public void testRequestIsFilledScriptedAndSignedSuccess() {
        Connector connector = connector(SERVICE);
        ConnectorRequests.Built request = ConnectorRequests.INSTANCE.build(connector, connector.getActions().get("create"), Map.of("name", "a/b c", "size", "3", "note", ""), CREDENTIAL);
        Assert.assertEquals("https://acme.sample.com/api/items/a%2Fb%20c", request.getUrl());
        Assert.assertEquals("{\"size\":3}", request.getBody().toString());
        Assert.assertEquals("6", request.getHeaders().get("X-Size"));
        Assert.assertEquals("Basic " + Base64.getEncoder().encodeToString("ada:secret-key".getBytes()), request.getHeaders().get("Authorization"));
        Assert.assertTrue(Connectors.INSTANCE.allows(connector, "acme.sample.com", CREDENTIAL));
    }

    @Test
    public void testACredentialHostThatIsNotAHostIsRefusedFail() {
        Connector connector = connector(SERVICE);
        try {
            ConnectorRequests.INSTANCE.build(connector, connector.getActions().get("create"), Map.of("name", "x"), Map.of("subdomain", "evil.com/steal?", "user", "u", "key", "k"));
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("should be a host name"));
        }
        Assert.assertFalse(Connectors.INSTANCE.allows(connector, "evil.com", CREDENTIAL));
    }

    @Test
    public void testCustomCallStaysOnTheServiceSuccess() {
        Connector connector = connector(SERVICE);
        ConnectorRequests.Built request = ConnectorRequests.INSTANCE.customCall(connector, Map.of("method", "PATCH", "path", "/items/7", "query", "{\"limit\": 2}", "body", "{\"a\": 1}"), CREDENTIAL);
        Assert.assertEquals("PATCH", request.getMethod());
        Assert.assertEquals("https://acme.sample.com/api/items/7?limit=2", request.getUrl());
        try {
            ConnectorRequests.INSTANCE.customCall(connector, Map.of("path", "https://evil.com/x"), CREDENTIAL);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("should start after the address"));
        }
    }

    @Test
    public void testAScriptAsksForEveryPageSuccess() {
        java.util.List<String> asked = new java.util.ArrayList<>();
        String script = "let items = response.items, cursor = response.next;\n"
                + "while (cursor) { const page = call({method: 'GET', url: '/items', query: {cursor}}); items = items.concat(page.items); cursor = page.next; }\n"
                + "return items;";
        var result = com.dopplertask.dopplertask.domain.action.connector.ConnectorScripts.INSTANCE.run(script, Map.of(), null,
                JsonApi.INSTANCE.getMapper().readTree("{\"items\": [1], \"next\": \"b\"}"), spec -> {
                    asked.add(spec.path("query").path("cursor").asText());
                    return JsonApi.INSTANCE.getMapper().readTree(spec.path("query").path("cursor").asText().equals("b") ? "{\"items\": [2], \"next\": \"c\"}" : "{\"items\": [3]}");
                });
        Assert.assertEquals("[1,2,3]", result.toString());
        Assert.assertEquals(java.util.List.of("b", "c"), asked);
    }

    @Test
    public void testAScriptCannotReachJavaFail() {
        try {
            com.dopplertask.dopplertask.domain.action.connector.ConnectorScripts.INSTANCE.run("return Java.type('java.lang.System').getenv();", Map.of(), null, null,
                    spec -> { throw new AssertionError("no request expected"); });
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("The connector's script failed"));
        }
    }

    @Test
    public void testHeaderWithALineBreakIsRefusedFail() {
        Connector connector = connector("""
                {"name": "lines", "label": "Lines", "hosts": ["api.lines.com"],
                 "actions": {"go": {"label": "Go", "inputs": {"tag": {"label": "Tag"}}, "request": {"url": "https://api.lines.com/x", "headers": {"X-Tag": "{{tag}}"}}}}}
                """);
        try {
            ConnectorRequests.INSTANCE.build(connector, connector.getActions().get("go"), Map.of("tag", "a\r\nX-Injected: 1"), Map.of());
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("line break"));
        }
    }

    @Test
    public void testFilesThatCouldSendACredentialElsewhereAreRefusedFail() {
        Assert.assertEquals(java.util.List.of(), Connectors.INSTANCE.problemsOf(connector(SERVICE)));
        String unsafe = """
                {"name": "unsafe", "label": "Unsafe", "hosts": ["api.unsafe.com"],
                 "credential": {"fields": {"key": {"label": "Key", "secret": true}}},
                 "actions": {
                   "plain": {"label": "Plain", "request": {"url": "http://api.unsafe.com/x"}},
                   "steered": {"label": "Steered", "inputs": {"host": {"label": "Host"}}, "request": {"url": "https://{{host}}/x"}},
                   "elsewhere": {"label": "Elsewhere", "request": {"url": "https://other.com/x"}},
                   "leaky": {"label": "Leaky", "request": {"url": "https://api.unsafe.com/x", "body": {"k": "{{credential.key}}"}}}}}
                """;
        String problems = String.join(" ", Connectors.INSTANCE.problemsOf(connector(unsafe)));
        Assert.assertTrue(problems, problems.contains("plain should use https"));
        Assert.assertTrue(problems, problems.contains("steered has an input in its host"));
        Assert.assertTrue(problems, problems.contains("other.com, which is not in the connector's hosts"));
        Assert.assertTrue(problems, problems.contains("Credential values belong in the credential's headers or query"));
    }

    @Test
    public void testEveryConnectorFileIsValidAndItsLogoExistsSuccess() throws java.io.IOException {
        String logos = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/static/js/flow/brand-icons.js"));
        Connectors.INSTANCE.all().forEach(connector -> {
            Assert.assertEquals(connector.getName(), java.util.List.of(), Connectors.INSTANCE.problemsOf(connector));
            Assert.assertTrue(connector.getName() + " names the logo " + connector.getIcon() + ", which the editor does not have",
                    connector.getIcon().isEmpty() || logos.contains("\"" + connector.getIcon() + "\": {"));
        });
    }

    private static Connector connector(String json) {
        return JsonApi.INSTANCE.getMapper().readValue(json, Connector.class);
    }
}
