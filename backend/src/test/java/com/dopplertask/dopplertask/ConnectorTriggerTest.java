package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorAction;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorTrigger;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorWebhooks;
import com.dopplertask.dopplertask.domain.action.connector.Connectors;
import com.dopplertask.dopplertask.domain.action.integration.JsonApi;
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
    private final List<String> asked = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<String> queries = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Before
    public void startService() throws IOException {
        service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.createContext("/", exchange -> {
            signedInWith.set(exchange.getRequestHeaders().getFirst("Authorization"));
            asked.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " " + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            queries.add(exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery());
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

    @Test
    public void testASecretTypedIntoATriggerMovesToACredentialSuccess() {
        ConnectorTrigger trigger = ConnectorTrigger.of("poll-test");
        trigger.setSetting("key", "typed-secret");
        Assert.assertEquals(Map.of("key", "typed-secret"), trigger.inlineCredentialValues(java.util.Set.of("key")));
        trigger.forgetCredentialValues(java.util.Set.of("key"));
        Assert.assertEquals(Map.of(), trigger.settingValues());
    }

    @Test
    public void testAWebhookTriggerSubscribesTakesEventsAndUnsubscribesSuccess() throws Exception {
        ConnectorWebhooks.INSTANCE.setPublicUrl("https://tasks.example.com");
        try {
            ConnectorTrigger trigger = trigger("new_event");
            trigger.setSetting("list_id", "l1");
            Assert.assertNull(trigger.problem());
            answer.set("{\"hook\": {\"id\": \"h1\"}}");
            var started = java.util.concurrent.CompletableFuture.supplyAsync(trigger::trigger);
            while (trigger.getPollState() == null) {
                Thread.sleep(20);
            }
            Assert.assertEquals("h1", trigger.getPollState());
            Assert.assertTrue(asked.get(0), asked.get(0).startsWith("POST /hooks {\"url\":\"https://tasks.example.com/hooks/" + trigger.getHookKey() + "\",\"list\":\"l1\"}"));
            ConnectorWebhooks.INSTANCE.deliver(trigger.getHookKey(), event("{\"type\": \"deleted\", \"data\": {\"id\": \"x\"}}"));
            ConnectorWebhooks.INSTANCE.deliver(trigger.getHookKey(), event("{\"type\": \"created\", \"data\": {\"id\": \"e1\"}}"));
            Assert.assertEquals("{\"id\":\"e1\"}", started.get(5, java.util.concurrent.TimeUnit.SECONDS).getResultMap().get("item"));
            trigger.stop();
            Assert.assertTrue(asked.toString(), asked.contains("DELETE /hooks/h1 "));
            Assert.assertNull(trigger.getPollState());
            Assert.assertNull(ConnectorWebhooks.INSTANCE.deliver(trigger.getHookKey(), event("{}")));
        } finally {
            ConnectorWebhooks.INSTANCE.setPublicUrl("");
        }
    }

    @Test
    public void testACheckByTimeAsksOnlyForWhatChangedSinceTheLastSuccess() {
        ConnectorTrigger trigger = trigger("changed_order");
        answer.set("{\"orders\": []}");
        newItems(trigger);
        String last = trigger.getPollState();
        newItems(trigger);
        Assert.assertEquals(List.of("GET /orders ", "GET /orders "), asked);
        Assert.assertEquals(List.of("", "since=" + last), queries);
    }

    @Test
    public void testAWebhookEventCanAskTheServiceForTheRestSuccess() throws Exception {
        ConnectorWebhooks.INSTANCE.setPublicUrl("https://tasks.example.com");
        try {
            ConnectorTrigger trigger = trigger("new_task");
            trigger.setHookKey("k1");
            answer.set("{\"hook\": {\"id\": \"h2\"}}");
            trigger.subscribe(Connectors.INSTANCE.find("poll-test").getTriggers().get("new_task").getWebhook());
            answer.set("{\"id\": \"t9\", \"name\": \"Call Ada\"}");
            trigger.takeItemsOf(event("{\"task_id\": \"t9\"}"));
            Assert.assertEquals("{\"id\":\"t9\",\"name\":\"Call Ada\"}", nextItem(trigger));
            Assert.assertEquals("GET /tasks/t9 ", asked.get(1));
            Assert.assertEquals("Bearer poll-key", signedInWith.get());
        } finally {
            ConnectorWebhooks.INSTANCE.setPublicUrl("");
        }
    }

    @Test
    public void testADeleteSendsItsBodySuccess() {
        ConnectorAction step = ConnectorAction.of("poll-test");
        step.setOperation("remove_ban");
        step.setSetting("reason", "spam");
        step.useCredentialValues(Map.of("key", "poll-key"));
        step.perform(new TaskExecution(), new VariableExtractorUtil(Application.lockedTemplateEngine(-1)));
        Assert.assertEquals(List.of("DELETE /bans/u1 {\"reason\":\"spam\"}"), asked);
    }

    @Test
    public void testAnAnswerScriptSeesWhatWasSentButNotTheCredentialSuccess() {
        ConnectorAction step = ConnectorAction.of("poll-test");
        step.setOperation("echo");
        step.setSetting("note", "hello");
        step.useCredentialValues(Map.of("key", "poll-key"));
        String output = step.perform(new TaskExecution(), new VariableExtractorUtil(Application.lockedTemplateEngine(-1))).getOutput();
        Assert.assertTrue(output, output.contains("\"sent\":{\"note\":\"hello\"}"));
        Assert.assertFalse(output, output.contains("poll-key"));
        Assert.assertEquals("Bearer poll-key", signedInWith.get());
    }

    @Test
    public void testAWebhookThePersonSetsUpListensAtItsOwnAddressAndAnswersChecksSuccess() throws Exception {
        ConnectorWebhooks.INSTANCE.setPublicUrl("https://tasks.example.com");
        try {
            ConnectorTrigger trigger = trigger("pasted");
            trigger.setTriggerSuffix("pasted-key");
            Assert.assertTrue(trigger.getActionInfo().stream().anyMatch(property -> property.getType() == Action.PropertyInformation.PropertyInformationType.ADDRESS));
            var started = java.util.concurrent.CompletableFuture.supplyAsync(trigger::trigger);
            while (ConnectorWebhooks.INSTANCE.check("pasted-key", event("{}")) == null) {
                Thread.sleep(20);
            }
            Assert.assertEquals("{\"challenge\":\"c1\"}", ConnectorWebhooks.INSTANCE.deliver("pasted-key", event("{\"challenge\": \"c1\"}")).toString());
            Assert.assertTrue(ConnectorWebhooks.INSTANCE.deliver("pasted-key", event("{\"id\": \"m1\"}")).isNull());
            Assert.assertEquals("{\"id\":\"m1\"}", started.get(5, java.util.concurrent.TimeUnit.SECONDS).getResultMap().get("item"));
            Assert.assertEquals(List.of(), asked);
        } finally {
            ConnectorWebhooks.INSTANCE.setPublicUrl("");
        }
    }

    @Test
    public void testNewByUnseenStartsForKeysNotSeenBeforeSuccess() {
        ConnectorTrigger trigger = trigger("new_label");
        answer.set("{\"labels\": [{\"id\": \"b\"}, {\"id\": \"a\"}]}");
        Assert.assertEquals(List.of(), newItems(trigger));
        answer.set("{\"labels\": [{\"id\": \"a\"}, {\"id\": \"c\"}, {\"id\": \"b\"}]}");
        Assert.assertEquals(List.of("c"), newItems(trigger));
        Assert.assertEquals(List.of(), newItems(trigger));
    }

    @Test
    public void testATriggerRemembersWhatItsScriptsKeepSuccess() {
        ConnectorTrigger trigger = trigger("new_change");
        answer.set("{\"next\": \"c2\", \"changes\": [{\"id\": \"x1\"}]}");
        newItems(trigger);
        newItems(trigger);
        Assert.assertEquals(List.of("", "cursor=c2"), queries);
        Assert.assertEquals("{\"cursor\":\"c2\"}", trigger.getMemory());
    }

    @Test
    public void testFilesATriggerKeepsGoWithTheItemIntoTheRunSuccess() {
        ConnectorTrigger trigger = trigger("with_file");
        trigger.setPollState("x0");
        answer.set("{\"data\": [{\"id\": \"x1\"}, {\"id\": \"x0\"}]}");
        trigger.lookForNewItems();
        var started = trigger.trigger();
        Assert.assertEquals("{\"id\":\"x1\",\"file\":\"note.txt\"}", started.getResultMap().get("item"));
        Assert.assertEquals("hi", new String(started.getFiles().get("note.txt"), StandardCharsets.UTF_8));
    }

    @Test
    public void testATimeIsReadInTheFormatsServicesWriteSuccess() {
        long noon = Instant.parse("2025-10-10T12:00:00Z").toEpochMilli();
        for (String time : List.of("2025-10-10T12:00:00Z", "2025-10-10T14:00:00+02:00", "2025-10-10T12:00:00.000+0000", "Fri, 10 Oct 2025 12:00:00 +0000",
                "2025-10-10T12:00:00", "2025-10-10 12:00:00", "/Date(" + noon + "+0000)/")) {
            Assert.assertEquals(time, Long.valueOf(noon), ConnectorTrigger.Companion.timeOf(JsonApi.INSTANCE.getMapper().getNodeFactory().textNode(time)));
        }
        Assert.assertEquals(Long.valueOf(Instant.parse("2026-01-05T00:00:00Z").toEpochMilli()),
                ConnectorTrigger.Companion.timeOf(JsonApi.INSTANCE.getMapper().getNodeFactory().textNode("2026-01-05")));
        Assert.assertNull(ConnectorTrigger.Companion.timeOf(JsonApi.INSTANCE.getMapper().getNodeFactory().textNode("soon")));
    }

    @Test
    public void testATriggerSavedBeforeItHadMemoryStillChecksSuccess() {
        ConnectorTrigger trigger = trigger("new_change");
        trigger.setMemory(null);
        answer.set("{\"next\": \"c1\", \"changes\": []}");
        Assert.assertEquals(List.of(), newItems(trigger));
        Assert.assertEquals("{\"cursor\":\"c1\"}", trigger.getMemory());
    }

    @Test
    public void testAnEntryGivesItsItemAndTheNextCheckAsksAfterTheLastSuccess() {
        ConnectorTrigger trigger = trigger("new_entry");
        trigger.setPollState("a");
        answer.set("{\"entries\": [{\"id\": \"b\", \"data\": {\"name\": \"B\"}}, {\"id\": \"a\", \"data\": {\"name\": \"A\"}}]}");
        trigger.lookForNewItems();
        Assert.assertEquals("{\"name\":\"B\"}", nextItem(trigger));
        Assert.assertEquals(List.of("after=a"), queries);
    }

    @Test
    public void testAFileNeverTakesANameAlreadyUsedSuccess() {
        Assert.assertEquals("scan-2.pdf", com.dopplertask.dopplertask.domain.RunFiles.INSTANCE.freeName("scan.pdf", java.util.Set.of("scan.pdf")::contains));
    }

    @Test
    public void testASubscriptionThatEndsIsRenewedAfterARestartSuccess() throws Exception {
        ConnectorWebhooks.INSTANCE.setPublicUrl("https://tasks.example.com");
        try {
            ConnectorTrigger trigger = trigger("renewed");
            trigger.setHookKey("renew-key");
            trigger.setPollState("old-channel");
            answer.set("{\"id\": \"new-channel\"}");
            var started = java.util.concurrent.CompletableFuture.supplyAsync(trigger::trigger);
            while (!"new-channel".equals(trigger.getPollState())) {
                Thread.sleep(20);
            }
            Assert.assertEquals(List.of("DELETE /channels/old-channel ", "POST /channels {\"address\":\"https://tasks.example.com/hooks/renew-key\"}"), asked);
            ConnectorWebhooks.INSTANCE.deliver("renew-key", event("{\"id\": \"e1\"}"));
            Assert.assertEquals("{\"id\":\"e1\"}", started.get(5, java.util.concurrent.TimeUnit.SECONDS).getResultMap().get("item"));
        } finally {
            ConnectorWebhooks.INSTANCE.setPublicUrl("");
        }
    }

    private static ConnectorWebhooks.Event event(String json) {
        return ConnectorWebhooks.INSTANCE.eventOf("application/json", json, Map.of(), null);
    }

    @Test
    public void testAWebhookTriggerNeedsThePublicAddressFail() {
        Assert.assertTrue(trigger("new_event").problem().contains("DOPPLERTASK_PUBLIC_URL"));
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
            String item = nextItem(trigger);
            if (item == null) {
                return ids;
            }
            ids.add(JsonApi.INSTANCE.getMapper().readTree(item).path("id").asText());
        }
    }

    private static String nextItem(ConnectorTrigger trigger) {
        ConnectorTrigger.Waiting waiting = trigger.takeWaiting();
        return waiting == null ? null : waiting.getItem().toString();
    }
}
