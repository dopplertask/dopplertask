package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.StatusCode;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.connection.PostgresAction;
import com.dopplertask.dopplertask.domain.action.connection.RedisAction;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import com.dopplertask.dopplertask.domain.action.integration.chat.DiscordAction;
import com.dopplertask.dopplertask.domain.action.integration.code.GitHubAction;
import com.dopplertask.dopplertask.domain.action.integration.code.JiraAction;
import com.dopplertask.dopplertask.domain.action.integration.chat.SlackAction;
import com.dopplertask.dopplertask.domain.action.integration.chat.TelegramAction;
import com.dopplertask.dopplertask.domain.action.integration.incident.PagerDutyAction;
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
    private String query;
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
        query = exchange.getRequestURI().getQuery();
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
        slack.setReplyToMessageId("1600.1");
        ActionResult result = run(slack);

        Assert.assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        Assert.assertEquals("/chat.postMessage", path);
        Assert.assertEquals("POST", method);
        Assert.assertEquals("Bearer xoxb-test", authorization);
        Assert.assertEquals("#deploys", received.path("channel").asText());
        Assert.assertEquals("Deployed", received.path("text").asText());
        Assert.assertEquals("1600.1", received.path("thread_ts").asText());
        Assert.assertEquals("1700.5", result.getOutputVariables().get("messageId"));
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
        telegram.setChat("@alerts");
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

    private DiscordAction discord() {
        DiscordAction discord = new DiscordAction();
        discord.setWebhookUrl(baseUrl + "/api/webhooks/1/secret");
        discord.setMessage("Disk is full");
        return discord;
    }

    @Test
    public void testDiscordSendsTheMessageAndGivesItsIdSuccess() throws IOException {
        answerBody = "{\"id\":\"99\",\"content\":\"Disk is full\"}";
        ActionResult result = run(discord());
        Assert.assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        Assert.assertEquals("/api/webhooks/1/secret", path);
        Assert.assertEquals("wait=true", query);
        Assert.assertEquals("Disk is full", received.path("content").asText());
        Assert.assertEquals("99", result.getOutputVariables().get("messageId"));
    }

    @Test
    public void testDiscordKeepsTheWebhookOutOfAnErrorFail() throws IOException {
        answerStatus = 404;
        answerBody = "{\"message\":\"Unknown Webhook\",\"code\":10015}";
        Assert.assertEquals("Discord answered with status 404: Unknown Webhook.", run(discord()).getErrorMsg());
        DiscordAction wrong = discord();
        wrong.setWebhookUrl("https://discord.com/api/webhooks/1/my secret");
        Assert.assertFalse(run(wrong).getErrorMsg().contains("secret"));
    }

    @Test
    public void testDiscordRefusesTooLongAMessageBeforeSendingFail() throws IOException {
        DiscordAction discord = discord();
        discord.setMessage("x".repeat(2001));
        Assert.assertTrue(run(discord).getErrorMsg().contains("2000"));
        Assert.assertNull(path);
    }

    private PagerDutyAction pagerDuty() {
        PagerDutyAction pagerDuty = new PagerDutyAction();
        pagerDuty.setBaseUrl(baseUrl);
        pagerDuty.setIntegrationKey("key");
        pagerDuty.setSummary("Disk is full");
        return pagerDuty;
    }

    @Test
    public void testPagerDutyOpensAnIncidentAndGivesItsKeySuccess() throws IOException {
        answerStatus = 202;
        answerBody = "{\"status\":\"success\",\"message\":\"Event processed\",\"dedup_key\":\"abc\"}";
        ActionResult result = run(pagerDuty());
        Assert.assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        Assert.assertEquals("/v2/enqueue", path);
        Assert.assertEquals("key", received.path("routing_key").asText());
        Assert.assertEquals("trigger", received.path("event_action").asText());
        Assert.assertEquals("Disk is full", received.path("payload").path("summary").asText());
        Assert.assertEquals("error", received.path("payload").path("severity").asText());
        Assert.assertEquals("abc", result.getOutputVariables().get("incidentKey"));
    }

    @Test
    public void testPagerDutyResolvesWithTheKeyAndWithoutAPayloadSuccess() throws IOException {
        answerStatus = 202;
        PagerDutyAction pagerDuty = pagerDuty();
        pagerDuty.setOperation("resolve");
        pagerDuty.setSummary(null);
        pagerDuty.setIncidentKey("abc");
        Assert.assertEquals(StatusCode.SUCCESS, run(pagerDuty).getStatusCode());
        Assert.assertEquals("abc", received.path("dedup_key").asText());
        Assert.assertTrue(received.path("payload").isMissingNode());
    }

    @Test
    public void testPagerDutyAsksForTheKeyBeforeResolvingFail() throws IOException {
        PagerDutyAction pagerDuty = pagerDuty();
        pagerDuty.setOperation("resolve");
        Assert.assertTrue(run(pagerDuty).getErrorMsg().contains("incident key"));
        Assert.assertNull(path);
    }

    private GitHubAction gitHub() {
        GitHubAction gitHub = new GitHubAction();
        gitHub.setBaseUrl(baseUrl);
        gitHub.setToken("ghp");
        gitHub.setRepository("acme/site");
        gitHub.setTitle("Disk is full");
        gitHub.setText("On web-1");
        return gitHub;
    }

    @Test
    public void testGitHubCreatesAnIssueAndGivesItsNumberSuccess() throws IOException {
        answerStatus = 201;
        answerBody = "{\"number\":7,\"html_url\":\"https://github.com/acme/site/issues/7\"}";
        ActionResult result = run(gitHub());
        Assert.assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        Assert.assertEquals("/repos/acme/site/issues", path);
        Assert.assertEquals("Bearer ghp", authorization);
        Assert.assertEquals("Disk is full", received.path("title").asText());
        Assert.assertEquals("On web-1", received.path("body").asText());
        Assert.assertEquals(7L, result.getOutputVariables().get("issueNumber"));
        Assert.assertEquals("https://github.com/acme/site/issues/7", result.getOutputVariables().get("url"));
    }

    @Test
    public void testGitHubCommentsAndClosesOnTheIssueSuccess() throws IOException {
        GitHubAction gitHub = gitHub();
        gitHub.setIssueNumber("7");
        gitHub.setOperation("addComment");
        Assert.assertEquals(StatusCode.SUCCESS, run(gitHub).getStatusCode());
        Assert.assertEquals("/repos/acme/site/issues/7/comments", path);
        gitHub.setOperation("closeIssue");
        Assert.assertEquals(StatusCode.SUCCESS, run(gitHub).getStatusCode());
        Assert.assertEquals("PATCH", method);
        Assert.assertEquals("/repos/acme/site/issues/7", path);
        Assert.assertEquals("closed", received.path("state").asText());
    }

    @Test
    public void testGitHubSaysWhatIsWrongBeforeSendingFail() throws IOException {
        GitHubAction gitHub = gitHub();
        gitHub.setRepository("site");
        Assert.assertTrue(run(gitHub).getErrorMsg().contains("owner/name"));
        gitHub.setRepository("acme/site");
        gitHub.setOperation("closeIssue");
        gitHub.setIssueNumber("seven");
        Assert.assertTrue(run(gitHub).getErrorMsg().contains("number of the issue"));
        Assert.assertNull(path);
    }

    @Test
    public void testGitHubGivesItsOwnReasonFail() throws IOException {
        answerStatus = 404;
        answerBody = "{\"message\":\"Not Found\"}";
        Assert.assertEquals("GitHub answered with status 404: Not Found.", run(gitHub()).getErrorMsg());
    }

    @Test
    public void testAPlainSentenceFromAServiceIsTheReasonButAnErrorPageIsNotFail() throws IOException {
        answerStatus = 400;
        answerBody = "Invalid routing key";
        Assert.assertEquals("PagerDuty answered with status 400: Invalid routing key.", run(pagerDuty()).getErrorMsg());
        answerBody = "<html><body>Bad gateway</body></html>";
        Assert.assertEquals("PagerDuty answered with status 400.", run(pagerDuty()).getErrorMsg());
    }

    private JiraAction jira() {
        JiraAction jira = new JiraAction();
        jira.setSiteUrl(baseUrl);
        jira.setToken("pat");
        jira.setProject("OPS");
        jira.setSummary("Disk is full");
        jira.setText("On web-1");
        return jira;
    }

    @Test
    public void testJiraCreatesAnIssueOnOwnServerWithABearerTokenSuccess() throws IOException {
        answerStatus = 201;
        answerBody = "{\"id\":\"1\",\"key\":\"OPS-12\"}";
        ActionResult result = run(jira());
        Assert.assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        Assert.assertEquals("/rest/api/2/issue", path);
        Assert.assertEquals("Bearer pat", authorization);
        Assert.assertEquals("OPS", received.path("fields").path("project").path("key").asText());
        Assert.assertEquals("Task", received.path("fields").path("issuetype").path("name").asText());
        Assert.assertEquals("On web-1", received.path("fields").path("description").asText());
        Assert.assertEquals("OPS-12", result.getOutputVariables().get("issueKey"));
        Assert.assertEquals(baseUrl + "/browse/OPS-12", result.getOutputVariables().get("url"));
    }

    @Test
    public void testJiraCommentsInTheCloudWithEmailAndTokenSuccess() throws IOException {
        answerStatus = 201;
        JiraAction jira = jira();
        jira.setEmail("a@b.se");
        jira.setOperation("addComment");
        jira.setIssueKey("OPS-12");
        ActionResult result = run(jira);
        Assert.assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        Assert.assertEquals("/rest/api/2/issue/OPS-12/comment", path);
        Assert.assertEquals("Basic YUBiLnNlOnBhdA==", authorization);
        Assert.assertEquals("On web-1", received.path("body").asText());
        Assert.assertEquals(baseUrl + "/browse/OPS-12", result.getOutputVariables().get("url"));
    }

    @Test
    public void testJiraGivesTheReasonInBothOfItsFormsFail() throws IOException {
        answerStatus = 400;
        answerBody = "{\"errorMessages\":[],\"errors\":{\"project\":\"project is required\"}}";
        Assert.assertEquals("Jira answered with status 400: project is required.", run(jira()).getErrorMsg());
        answerStatus = 404;
        answerBody = "{\"errorMessages\":[\"Issue does not exist\"],\"errors\":{}}";
        Assert.assertEquals("Jira answered with status 404: Issue does not exist.", run(jira()).getErrorMsg());
    }

    private RedisAction redis() {
        RedisAction redis = new RedisAction();
        redis.setHostname("localhost");
        redis.setPort("1");
        redis.setKey("visits");
        return redis;
    }

    @Test
    public void testRedisSaysWhereItCouldNotConnectFail() throws IOException {
        Assert.assertEquals("Could not connect to Redis at localhost:1. Check the address, and that the service is running.", run(redis()).getErrorMsg());
    }

    @Test
    public void testRedisSaysWhatIsWrongBeforeConnectingFail() throws IOException {
        RedisAction redis = redis();
        redis.setExpirySeconds("soon");
        Assert.assertTrue(run(redis).getErrorMsg().contains("number of seconds"));
        redis.setExpirySeconds(null);
        redis.setPort("first");
        Assert.assertTrue(run(redis).getErrorMsg().contains("port as a number"));
    }

    private PostgresAction postgres(EmbeddedPostgres server, String command, String values) {
        PostgresAction postgres = new PostgresAction();
        postgres.setHostname("localhost");
        postgres.setPort(String.valueOf(server.getPort()));
        postgres.setDatabase("postgres");
        postgres.setUsername("postgres");
        postgres.setCommand(command);
        postgres.setQueryValues(values);
        return postgres;
    }

    @Test
    public void testPostgresRunsAgainstARealServerAndKeepsValuesApartFromTheQuerySuccess() throws IOException {
        try (EmbeddedPostgres server = EmbeddedPostgres.start()) {
            run(postgres(server, "CREATE TABLE people (id integer, name text, born date)", null));
            String attack = "x'); DROP TABLE people; --";
            ActionResult inserted = run(postgres(server, "INSERT INTO people VALUES (?, ?, ?)", "7\n" + attack + "\n1815-12-10"));
            Assert.assertEquals(StatusCode.SUCCESS, inserted.getStatusCode());
            Assert.assertEquals(1, inserted.getOutputVariables().get("rowCount"));

            ActionResult selected = run(postgres(server, "SELECT id, name, born, NULL AS nothing FROM people WHERE id = ?", "7"));
            JsonNode row = new ObjectMapper().readTree(selected.getOutput()).path(0);
            Assert.assertEquals(7, row.path("id").asInt());
            Assert.assertTrue(row.path("id").isNumber());
            Assert.assertEquals(attack, row.path("name").asText());
            Assert.assertEquals("1815-12-10", row.path("born").asText());
            Assert.assertTrue(row.path("nothing").isNull());
            Assert.assertEquals(1, selected.getOutputVariables().get("rowCount"));

            ActionResult wrong = run(postgres(server, "SELECT * FROM nothing_here", null));
            Assert.assertEquals(StatusCode.FAILURE, wrong.getStatusCode());
            Assert.assertTrue(wrong.getErrorMsg(), wrong.getErrorMsg().startsWith("Postgres answered: ERROR: relation \"nothing_here\" does not exist"));
        }
    }

    @Test
    public void testPostgresSaysWhereItCouldNotConnectFail() throws IOException {
        PostgresAction postgres = new PostgresAction();
        postgres.setHostname("localhost");
        postgres.setPort("1");
        postgres.setDatabase("postgres");
        postgres.setCommand("SELECT 1");
        Assert.assertEquals("Could not connect to Postgres at localhost:1. Check the address, and that the service is running.", run(postgres).getErrorMsg());
    }
}
