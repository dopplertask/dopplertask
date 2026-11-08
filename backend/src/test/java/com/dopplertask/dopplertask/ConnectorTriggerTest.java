package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorTrigger;
import com.dopplertask.dopplertask.domain.action.connector.Connectors;
import com.dopplertask.dopplertask.domain.action.integration.JsonApi;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** A connector trigger starts the task once for each new item, oldest first, and never for what existed before. */
public class ConnectorTriggerTest {

    private HttpServer service;
    private final AtomicReference<String> answer = new AtomicReference<>("{}");
    private final AtomicReference<String> signedInWith = new AtomicReference<>();

    @Before
    public void startService() throws IOException {
        service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.createContext("/", exchange -> {
            signedInWith.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = answer.get().getBytes(StandardCharsets.UTF_8);
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
    public void testNewByTimeSkipsWhatExistedAndStartsOldestFirstSuccess() {
        ConnectorTrigger trigger = trigger("new_order");
        answer.set("{\"orders\": [{\"id\": 1, \"created\": \"2020-01-01T00:00:00Z\"}]}");
        Assert.assertEquals(List.of(), newItems(trigger));
        String later = Instant.now().plusSeconds(60).toString();
        String latest = Instant.now().plusSeconds(120).toString();
        answer.set("{\"orders\": [{\"id\": 3, \"created\": \"" + latest + "\"}, {\"id\": 2, \"created\": \"" + later + "\"}, {\"id\": 1, \"created\": \"2020-01-01T00:00:00Z\"}]}");
        Assert.assertEquals(List.of("2", "3"), newItems(trigger));
        Assert.assertEquals("Bearer poll-key", signedInWith.get());
    }

    @Test
    public void testNewByLastItemStartsForWhatCameAfterSuccess() {
        ConnectorTrigger trigger = trigger("new_contact");
        answer.set("{\"data\": [{\"id\": \"c2\"}, {\"id\": \"c1\"}]}");
        Assert.assertEquals(List.of(), newItems(trigger));
        answer.set("{\"data\": [{\"id\": \"c4\"}, {\"id\": \"c3\"}, {\"id\": \"c2\"}, {\"id\": \"c1\"}]}");
        Assert.assertEquals(List.of("c3", "c4"), newItems(trigger));
        Assert.assertEquals("c4", trigger.getPollState());
        Assert.assertEquals(List.of(), newItems(trigger));
    }

    @Test
    public void testATriggerIsSavedByItsConnectorSuccess() {
        Action read = JsonApi.INSTANCE.getMapper().readValue("{\"@type\": \"poll-test.trigger\", \"operation\": \"new_order\", \"every\": \"10\"}", Action.class);
        Assert.assertTrue(read instanceof ConnectorTrigger);
        Assert.assertEquals("poll-test", ((ConnectorTrigger) read).getConnector());
        Assert.assertTrue(JsonApi.INSTANCE.getMapper().writeValueAsString(read).contains("\"@type\":\"poll-test.trigger\""));
        Assert.assertNull(((ConnectorTrigger) read).problem());
        ((ConnectorTrigger) read).setEvery("0");
        Assert.assertTrue(((ConnectorTrigger) read).problem().contains("from 1"));
    }

    private static ConnectorTrigger trigger(String operation) {
        ConnectorTrigger trigger = ConnectorTrigger.of("poll-test");
        trigger.setOperation(operation);
        trigger.useCredentialValues(Map.of("key", "poll-key"));
        return trigger;
    }

    /** The ids of what one check found, in the order the task would start for them. */
    private static List<String> newItems(ConnectorTrigger trigger) {
        trigger.lookForNewItems();
        List<String> ids = new ArrayList<>();
        while (true) {
            String item = trigger.nextWaiting();
            if (item == null) {
                return ids;
            }
            ids.add(JsonApi.INSTANCE.getMapper().readTree(item).path("id").asText());
        }
    }
}
