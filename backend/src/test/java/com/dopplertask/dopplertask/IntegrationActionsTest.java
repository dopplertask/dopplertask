package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.StatusCode;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.integration.chat.SlackAction;
import com.dopplertask.dopplertask.domain.action.integration.chat.TelegramAction;
import com.dopplertask.dopplertask.service.TaskServiceImpl;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.velocity.app.VelocityEngine;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Each outside service is replaced by a local server that checks what a real one would require, so these tests
 * show what is sent and how answers are read. They do not show that the real service agrees.
 */
public class IntegrationActionsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer service;
    private String baseUrl;
    private String path;
    private String method;
    private String authorization;
    private JsonNode received;
    private int answerStatus = 200;
    private String answerBody = "{}";

    @Before
    public void startService() throws IOException {
        service = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        service.createContext("/", this::answer);
        service.start();
        baseUrl = "http://localhost:" + service.getAddress().getPort();
    }

    @After
    public void stopService() {
        service.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        path = exchange.getRequestURI().getPath();
        method = exchange.getRequestMethod();
        authorization = exchange.getRequestHeaders().getFirst("Authorization");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received = body.isEmpty() ? null : mapper.readTree(body);
        byte[] bytes = answerBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answerStatus, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private ActionResult run(Action action) throws IOException {
        return action.run(new TaskServiceImpl(), new TaskExecution(), new VariableExtractorUtil(new VelocityEngine()), null);
    }

    private SlackAction slack() {
        SlackAction slack = new SlackAction();
        slack.setBaseUrl(baseUrl);
        slack.setToken("xoxb-test");
        slack.setChannel("#deploys");
        slack.setMessage("Deployed");
        return slack;
    }

    @Test
    public void testSlackSendsTheMessageAndGivesItsTimeStampSuccess() throws IOException {
        answerBody = "{\"ok\":true,\"ts\":\"1700.5\"}";
        SlackAction slack = slack();
        slack.setThreadTs("1600.1");
        ActionResult result = run(slack);

        Assert.assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        Assert.assertEquals("/chat.postMessage", path);
        Assert.assertEquals("POST", method);
        Assert.assertEquals("Bearer xoxb-test", authorization);
        Assert.assertEquals("#deploys", received.path("channel").asText());
        Assert.assertEquals("Deployed", received.path("text").asText());
        Assert.assertEquals("1600.1", received.path("thread_ts").asText());
        Assert.assertEquals("1700.5", result.getOutputVariables().get("ts"));
    }

    @Test
    public void testSlackReportsARefusalThatComesWithStatus200Fail() throws IOException {
        answerBody = "{\"ok\":false,\"error\":\"channel_not_found\"}";
        ActionResult result = run(slack());
        Assert.assertEquals(StatusCode.FAILURE, result.getStatusCode());
        Assert.assertEquals("Slack refused the message: channel_not_found.", result.getErrorMsg());
    }

    @Test
    public void testAServiceErrorNamesTheServiceAndItsReasonFail() throws IOException {
        answerStatus = 429;
        answerBody = "{\"error\":\"ratelimited\"}";
        ActionResult result = run(slack());
        Assert.assertEquals(StatusCode.FAILURE, result.getStatusCode());
        Assert.assertEquals("Slack answered with status 429: ratelimited.", result.getErrorMsg());
    }

    @Test
    public void testSlackSaysWhatIsMissingFail() throws IOException {
        SlackAction slack = slack();
        slack.setToken("");
        Assert.assertTrue(run(slack).getErrorMsg().contains("bot token"));
        slack = slack();
        slack.setChannel("");
        Assert.assertTrue(run(slack).getErrorMsg().contains("channel"));
        slack = slack();
        slack.setMessage("");
        Assert.assertTrue(run(slack).getErrorMsg().contains("message"));
    }

    @Test
    public void testTextFromAnEarlierStepIsNotEvaluatedAgainSuccess() throws IOException {
        answerBody = "{\"ok\":true}";
        TaskExecution execution = new TaskExecution();
        com.dopplertask.dopplertask.domain.ExecutionParameter parameter = new com.dopplertask.dopplertask.domain.ExecutionParameter("text", "#set($x = 1)$x \"quoted\"".getBytes(StandardCharsets.UTF_8), false);
        execution.getParameters().put("text", parameter);
        SlackAction slack = slack();
        slack.setMessage("$parameters.get('text')");
        slack.run(new TaskServiceImpl(), execution, new VariableExtractorUtil(new VelocityEngine()), null);
        Assert.assertEquals("#set($x = 1)$x \"quoted\"", received.path("text").asText());
    }

    private TelegramAction telegram() {
        TelegramAction telegram = new TelegramAction();
        telegram.setBaseUrl(baseUrl);
        telegram.setToken("123:abc");
        telegram.setChatId("@alerts");
        telegram.setMessage("Disk is full");
        return telegram;
    }

    @Test
    public void testTelegramSendsTheMessageAndGivesItsIdSuccess() throws IOException {
        answerBody = "{\"ok\":true,\"result\":{\"message_id\":42}}";
        ActionResult result = run(telegram());
        Assert.assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        Assert.assertEquals("/bot123:abc/sendMessage", path);
        Assert.assertEquals("@alerts", received.path("chat_id").asText());
        Assert.assertEquals("Disk is full", received.path("text").asText());
        Assert.assertEquals(42L, result.getOutputVariables().get("messageId"));
    }

    @Test
    public void testTelegramGivesItsOwnReasonWhenItRefusesFail() throws IOException {
        answerStatus = 400;
        answerBody = "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}";
        Assert.assertEquals("Telegram answered with status 400: Bad Request: chat not found.", run(telegram()).getErrorMsg());
    }

    @Test
    public void testTelegramRefusesTooLongAMessageBeforeSendingFail() throws IOException {
        TelegramAction telegram = telegram();
        telegram.setMessage("x".repeat(4097));
        Assert.assertTrue(run(telegram).getErrorMsg().contains("4096"));
        Assert.assertNull(path);
    }

    @Test
    public void testTelegramKeepsTheTokenOutOfTheMessageAboutAWrongAddressFail() throws IOException {
        TelegramAction telegram = telegram();
        telegram.setToken("123:my secret");
        String message = run(telegram).getErrorMsg();
        Assert.assertTrue(message, message.contains("not valid"));
        Assert.assertFalse(message, message.contains("secret"));
    }
}
