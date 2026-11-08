package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.action.connector.ConnectorTrigger;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorWebhooks;
import com.dopplertask.dopplertask.domain.action.connector.Connectors;
import com.dopplertask.dopplertask.domain.action.trigger.TriggerResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** One address takes the events of a service's whole app, and each trigger gets the signed ones for its own account. */
public class ConnectorAppEventsTest {

    private static final String KEY = ConnectorWebhooks.INSTANCE.appKey("app-test");
    private final AtomicReference<String> team = new AtomicReference<>();
    private HttpServer service;

    @Before
    public void startService() throws IOException {
        service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.createContext("/", exchange -> {
            byte[] body = ("{\"team_id\": \"" + team.get() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        service.start();
        Connectors.INSTANCE.setLocalServerForTests("http://127.0.0.1:" + service.getAddress().getPort());
        ConnectorWebhooks.INSTANCE.setPublicUrl("https://tasks.example.com");
    }

    @After
    public void stopService() {
        ConnectorWebhooks.INSTANCE.setPublicUrl("");
        Connectors.INSTANCE.setLocalServerForTests(null);
        service.stop(0);
    }

    @Test
    public void testEachTriggerTakesTheSignedEventsOfItsOwnAccountSuccess() throws Exception {
        ConnectorTrigger first = trigger("T1");
        CompletableFuture<TriggerResult> firstStarted = started(first, "T1");
        ConnectorTrigger second = trigger("T2");
        CompletableFuture<TriggerResult> secondStarted = started(second, "T2");

        Assert.assertEquals("c1", ConnectorWebhooks.INSTANCE.deliver(KEY, event("{\"challenge\": \"c1\"}", "secret")).asText());
        ConnectorWebhooks.INSTANCE.deliver(KEY, event("{\"team_id\": \"T1\", \"event\": {\"type\": \"message\", \"text\": \"forged\"}}", "wrong"));
        ConnectorWebhooks.INSTANCE.deliver(KEY, event("{\"team_id\": \"T1\", \"event\": {\"type\": \"reaction\", \"text\": \"other kind\"}}", "secret"));
        ConnectorWebhooks.INSTANCE.deliver(KEY, event("{\"team_id\": \"T1\", \"event\": {\"type\": \"message\", \"text\": \"hello\"}}", "secret"));

        Assert.assertEquals("{\"type\":\"message\",\"text\":\"hello\"}", firstStarted.get(5, TimeUnit.SECONDS).getResultMap().get("item"));
        Thread.sleep(200);
        Assert.assertFalse(secondStarted.isDone());
        first.stop();
        second.stop();
    }

    private static ConnectorTrigger trigger(String account) {
        ConnectorTrigger trigger = ConnectorTrigger.of("app-test");
        trigger.setOperation("new_message");
        trigger.useCredentialValues(Map.of("token", "t-" + account, "secret", "secret"));
        return trigger;
    }

    /** Starts a trigger and waits until it has found its account. */
    private CompletableFuture<TriggerResult> started(ConnectorTrigger trigger, String account) throws InterruptedException {
        team.set(account);
        CompletableFuture<TriggerResult> started = CompletableFuture.supplyAsync(trigger::trigger);
        while (!account.equals(trigger.getPollState())) {
            Thread.sleep(20);
        }
        return started;
    }

    private static ConnectorWebhooks.Event event(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "v0=" + HexFormat.of().formatHex(mac.doFinal(("v0:1700000000:" + body).getBytes(StandardCharsets.UTF_8)));
        return ConnectorWebhooks.INSTANCE.eventOf("application/json", body, Map.of("x-timestamp", "1700000000", "x-signature", signature), null);
    }
}
