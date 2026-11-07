package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.action.integration.sales.HubSpotAction;
import com.dopplertask.dopplertask.domain.action.integration.sales.SalesforceAction;
import com.dopplertask.dopplertask.domain.action.integration.sales.PipedriveAction;
import com.dopplertask.dopplertask.domain.action.integration.sales.MailchimpAction;
import com.dopplertask.dopplertask.domain.action.integration.commerce.StripeAction;
import com.dopplertask.dopplertask.domain.action.integration.commerce.ShopifyAction;
import com.dopplertask.dopplertask.domain.action.integration.commerce.WooCommerceAction;
import com.dopplertask.dopplertask.domain.action.integration.code.IntercomAction;
import com.dopplertask.dopplertask.domain.action.integration.code.FreshdeskAction;
import com.dopplertask.dopplertask.domain.action.integration.code.AsanaAction;
import com.dopplertask.dopplertask.domain.action.integration.code.TrelloAction;
import com.dopplertask.dopplertask.domain.action.integration.code.ClickUpAction;
import com.dopplertask.dopplertask.domain.action.integration.code.MondayAction;
import com.dopplertask.dopplertask.domain.action.integration.chat.WhatsAppAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.AirtableAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.SupabaseAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.GoogleDriveAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.GoogleCalendarAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.ConfluenceAction;
import com.dopplertask.dopplertask.domain.action.integration.connection.MongoDBAction;
import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.StatusCode;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionStep;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.velocity.app.VelocityEngine;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import com.dopplertask.dopplertask.domain.action.integration.mail.EmailAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.S3Action;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import com.dopplertask.dopplertask.domain.ExecutionParameter;
import com.dopplertask.dopplertask.domain.action.integration.anthropic.ClaudeAction;
import com.dopplertask.dopplertask.domain.action.integration.openai.OpenAIAction;
import com.dopplertask.dopplertask.domain.action.integration.chat.MattermostAction;
import com.dopplertask.dopplertask.domain.action.integration.chat.TeamsAction;
import com.dopplertask.dopplertask.domain.action.integration.chat.TwilioAction;
import com.dopplertask.dopplertask.domain.action.integration.code.GitLabAction;
import com.dopplertask.dopplertask.domain.action.integration.code.LinearAction;
import com.dopplertask.dopplertask.domain.action.integration.code.ZendeskAction;
import com.dopplertask.dopplertask.domain.action.integration.mail.ReadEmailAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.GoogleSheetsAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.NotionAction;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import java.net.URLDecoder;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.Store;
import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private String rawPath;
    private String receivedText;
    private Map<String, String> requestHeaders;
    private final Map<String, String> answersByRequest = new LinkedHashMap<>();
    private final List<String> requests = new ArrayList<>();
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
        rawPath = exchange.getRequestURI().getRawPath();
        requestHeaders = new HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> requestHeaders.put(name.toLowerCase(), values.get(0)));
        query = exchange.getRequestURI().getQuery();
        method = exchange.getRequestMethod();
        authorization = exchange.getRequestHeaders().getFirst("Authorization");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        receivedText = body;
        requests.add(path);
        received = readJsonOrNull(body);
        String request = path + " " + body;
        String answer = answersByRequest.entrySet().stream().filter(entry -> request.contains(entry.getKey())).map(Map.Entry::getValue).findFirst().orElse(answerBody);
        byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answerStatus, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private JsonNode readJsonOrNull(String body) {
        try {
            return body.isEmpty() ? null : mapper.readTree(body);
        } catch (tools.jackson.core.JacksonException notJson) {
            return null;
        }
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
    public void testDiscordGivesTheReasonFromItsAnswerFail() throws IOException {
        answerStatus = 404;
        answerBody = "{\"message\":\"Unknown Webhook\",\"code\":10015}";
        Assert.assertEquals("Discord answered with status 404: Unknown Webhook.", run(discord()).getErrorMsg());
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
    public void testPagerDutyOpensWithTheKeyForTheNewIncidentSuccess() throws IOException {
        answerStatus = 202;
        PagerDutyAction pagerDuty = pagerDuty();
        pagerDuty.setNewIncidentKey("disk-web-1");
        pagerDuty.setIncidentKey("an-old-one");
        run(pagerDuty);
        Assert.assertEquals("disk-web-1", received.path("dedup_key").asText());
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
        gitHub.setIssueDescription("On web-1");
        gitHub.setComment("Looking at it");
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
        jira.setIssueDescription("On web-1");
        jira.setComment("Looking at it");
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
        Assert.assertEquals("Looking at it", received.path("body").asText());
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

    @Test
    public void testARunKeepsOnlyWhatAStepDeclaresSoCookiesAreNotStoredSuccess() {
        TaskExecutionStep step = new TaskExecutionStep();
        step.keepOutputs(new GitHubAction().getOutputs().keySet(), Map.of("issueNumber", 7, "set-cookie", List.of("session=secret")));
        Assert.assertEquals(Map.of("issueNumber", 7), step.getOutputVariables());
        Assert.assertEquals(Map.of("issueNumber", 7), step.copyTo(new TaskExecution()).getOutputVariables());
        Assert.assertEquals(Map.of(), new TaskExecutionStep().getOutputVariables());
    }

    private EmailAction email(int port) {
        EmailAction email = new EmailAction();
        email.setHostname("localhost");
        email.setPort(String.valueOf(port));
        email.setSecurity("none");
        email.setFrom("alerts@example.com");
        email.setTo("ada@example.com, grace@example.com");
        email.setSubject("Disk is full\nBcc: everyone@example.com");
        email.setMessage("On web-1");
        return email;
    }

    @Test
    public void testEmailReachesARealServerAndAnEarlierStepCannotAddAHeaderSuccess() throws Exception {
        GreenMail server = new GreenMail(ServerSetupTest.SMTP);
        server.start();
        try {
            ActionResult result = run(email(server.getSmtp().getPort()));
            Assert.assertEquals(result.getErrorMsg(), StatusCode.SUCCESS, result.getStatusCode());
            Assert.assertNotNull(result.getOutputVariables().get("messageId"));
            MimeMessage[] messages = server.getReceivedMessages();
            Assert.assertEquals(2, messages.length);
            Assert.assertEquals("Disk is full Bcc: everyone@example.com", messages[0].getSubject());
            Assert.assertNull(messages[0].getHeader("Bcc"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void testEmailDoesNotSendWithoutEncryptionWhenItIsAskedForFail() throws IOException {
        GreenMail server = new GreenMail(ServerSetupTest.SMTP);
        server.start();
        try {
            EmailAction email = email(server.getSmtp().getPort());
            email.setSecurity("starttls");
            ActionResult result = run(email);
            Assert.assertEquals(StatusCode.FAILURE, result.getStatusCode());
            Assert.assertEquals(0, server.getReceivedMessages().length);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testEmailSaysWhatIsWrongFail() throws IOException {
        EmailAction email = email(1);
        Assert.assertEquals("Could not connect to the mail server at localhost:1. Check the address, and that the service is running.", run(email).getErrorMsg());
        email.setTo("not an address");
        Assert.assertTrue(run(email).getErrorMsg().startsWith("Email could not read the address"));
    }

    // The example that AWS publishes for Signature Version 4, so the signature is checked against AWS and not against this code.
    @Test
    public void testS3SignsAsThePublishedExampleFromAwsSuccess() {
        Map<String, String> headers = Map.of(
                "host", "examplebucket.s3.amazonaws.com",
                "range", "bytes=0-9",
                "x-amz-content-sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "x-amz-date", "20130524T000000Z");
        String authorization = S3Action.Companion.authorization("GET", "/test.txt", headers, "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1", "20130524T000000Z");
        Assert.assertEquals("AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,SignedHeaders=host;range;x-amz-content-sha256;x-amz-date,Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41", authorization);
    }

    private S3Action s3() {
        S3Action s3 = new S3Action();
        s3.setBaseUrl(baseUrl);
        s3.setAccessKeyId("key");
        s3.setSecretAccessKey("secret");
        s3.setBucket("reports");
        s3.setKey("2026/today report.txt");
        return s3;
    }

    @Test
    public void testS3WritesToAnEncodedPathSuccess() throws IOException {
        S3Action s3 = s3();
        s3.setOperation("write");
        s3.setContent("{\"state\":\"fine\"}");
        ActionResult result = run(s3);
        Assert.assertEquals(result.getErrorMsg(), StatusCode.SUCCESS, result.getStatusCode());
        Assert.assertEquals("PUT", method);
        Assert.assertEquals("/reports/2026/today report.txt", path);
        Assert.assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 Credential=key/"));
    }

    @Test
    public void testS3ReadsTheReasonFromItsAnswerFail() throws IOException {
        answerStatus = 404;
        answerBody = "<Error><Code>NoSuchKey</Code><Message>The specified key does not exist.</Message></Error>";
        Assert.assertEquals("S3 answered with status 404: The specified key does not exist.", run(s3()).getErrorMsg());
    }

    @Test
    public void testTeamsSendsACardSuccess() throws IOException {
        TeamsAction teams = new TeamsAction();
        teams.setWebhookUrl(baseUrl + "/workflows/secret");
        teams.setMessage("Disk is full");
        answerStatus = 202;
        answerBody = "";
        Assert.assertEquals(StatusCode.SUCCESS, run(teams).getStatusCode());
        Assert.assertEquals("application/vnd.microsoft.card.adaptive", received.path("attachments").path(0).path("contentType").asText());
        Assert.assertEquals("Disk is full", received.path("attachments").path(0).path("content").path("body").path(0).path("text").asText());
    }

    @Test
    public void testMattermostPostsAndRepliesInAThreadSuccess() throws IOException {
        MattermostAction mattermost = new MattermostAction();
        mattermost.setSiteUrl(baseUrl + "/");
        mattermost.setToken("bot");
        mattermost.setChannelId("c1");
        mattermost.setMessage("Disk is full");
        mattermost.setReplyToMessageId("m0");
        answerStatus = 201;
        answerBody = "{\"id\":\"m1\"}";
        ActionResult result = run(mattermost);
        Assert.assertEquals("/api/v4/posts", path);
        Assert.assertEquals("Bearer bot", authorization);
        Assert.assertEquals("c1", received.path("channel_id").asText());
        Assert.assertEquals("m0", received.path("root_id").asText());
        Assert.assertEquals("m1", result.getOutputVariables().get("messageId"));
    }

    private TwilioAction twilio() {
        TwilioAction twilio = new TwilioAction();
        twilio.setBaseUrl(baseUrl);
        twilio.setAccountSid("AC1");
        twilio.setToken("secret");
        twilio.setSender("+15005550006");
        twilio.setTo("+46701234567");
        twilio.setMessage("Disk is full & hot");
        return twilio;
    }

    @Test
    public void testTwilioSendsAFormAndGivesTheIdSuccess() throws IOException {
        answerStatus = 201;
        answerBody = "{\"sid\":\"SM1\"}";
        ActionResult result = run(twilio());
        Assert.assertEquals("/2010-04-01/Accounts/AC1/Messages.json", path);
        Assert.assertEquals("Basic " + Base64.getEncoder().encodeToString("AC1:secret".getBytes(StandardCharsets.UTF_8)), authorization);
        Assert.assertEquals("application/x-www-form-urlencoded", requestHeaders.get("content-type"));
        Assert.assertEquals("From=%2B15005550006&To=%2B46701234567&Body=Disk+is+full+%26+hot", receivedText);
        Assert.assertEquals("SM1", result.getOutputVariables().get("messageId"));
    }

    @Test
    public void testTwilioAsksForTheCountryCodeBeforeSendingFail() throws IOException {
        TwilioAction twilio = twilio();
        twilio.setTo("0701234567");
        Assert.assertTrue(run(twilio).getErrorMsg().contains("country code"));
        Assert.assertNull(path);
    }

    private GitLabAction gitLab() {
        GitLabAction gitLab = new GitLabAction();
        gitLab.setBaseUrl(baseUrl);
        gitLab.setToken("glpat");
        gitLab.setProject("acme/site");
        gitLab.setTitle("Disk is full");
        gitLab.setIssueDescription("On web-1");
        gitLab.setComment("Looking at it");
        gitLab.setIssueNumber("7");
        return gitLab;
    }

    @Test
    public void testGitLabCreatesCommentsAndClosesOnAnEncodedProjectSuccess() throws IOException {
        answerStatus = 201;
        answerBody = "{\"iid\":7}";
        GitLabAction gitLab = gitLab();
        ActionResult created = run(gitLab);
        Assert.assertEquals("/api/v4/projects/acme%2Fsite/issues", rawPath);
        Assert.assertEquals("glpat", requestHeaders.get("private-token"));
        Assert.assertEquals("On web-1", received.path("description").asText());
        Assert.assertEquals("7", created.getOutputVariables().get("issueNumber"));
        Assert.assertEquals(baseUrl + "/acme/site/-/issues/7", created.getOutputVariables().get("url"));

        gitLab.setOperation("addComment");
        run(gitLab);
        Assert.assertEquals("/api/v4/projects/acme%2Fsite/issues/7/notes", rawPath);
        Assert.assertEquals("Looking at it", received.path("body").asText());

        gitLab.setOperation("closeIssue");
        answerStatus = 200;
        run(gitLab);
        Assert.assertEquals("PUT", method);
        Assert.assertEquals("close", received.path("state_event").asText());
    }

    private LinearAction linear() {
        LinearAction linear = new LinearAction();
        linear.setBaseUrl(baseUrl);
        linear.setToken("lin");
        linear.setTeam("ENG");
        linear.setTitle("Disk is full");
        return linear;
    }

    @Test
    public void testLinearFindsTheTeamByItsKeyThenCreatesSuccess() throws IOException {
        answersByRequest.put("teams(filter", "{\"data\":{\"teams\":{\"nodes\":[{\"id\":\"team-uuid\"}]}}}");
        answersByRequest.put("issueCreate", "{\"data\":{\"issueCreate\":{\"issue\":{\"identifier\":\"ENG-12\",\"url\":\"https://linear.app/i/ENG-12\"}}}}");
        ActionResult result = run(linear());
        Assert.assertEquals("lin", authorization);
        Assert.assertEquals(2, requests.size());
        Assert.assertEquals("team-uuid", received.path("variables").path("input").path("teamId").asText());
        Assert.assertEquals("ENG-12", result.getOutputVariables().get("issueKey"));
        Assert.assertEquals("https://linear.app/i/ENG-12", result.getOutputVariables().get("url"));
    }

    @Test
    public void testLinearSaysWhenATeamIsMissingAndReadsErrorsWithStatus200Fail() throws IOException {
        answerBody = "{\"data\":{\"teams\":{\"nodes\":[]}}}";
        Assert.assertEquals("Linear has no team with the key \"ENG\".", run(linear()).getErrorMsg());
        answerBody = "{\"errors\":[{\"message\":\"Authentication required.\"}]}";
        Assert.assertEquals("Linear refused: Authentication required.", run(linear()).getErrorMsg());
    }

    @Test
    public void testLinearCommentsOnTheIssueItFoundByKeySuccess() throws IOException {
        answersByRequest.put("issue(id", "{\"data\":{\"issue\":{\"id\":\"issue-uuid\",\"url\":\"u\"}}}");
        answersByRequest.put("commentCreate", "{\"data\":{\"commentCreate\":{\"success\":true}}}");
        LinearAction linear = linear();
        linear.setOperation("addComment");
        linear.setIssueKey("ENG-12");
        linear.setComment("Looking at it");
        ActionResult result = run(linear);
        Assert.assertEquals("issue-uuid", received.path("variables").path("input").path("issueId").asText());
        Assert.assertEquals("ENG-12", result.getOutputVariables().get("issueKey"));
    }

    private ZendeskAction zendesk() {
        ZendeskAction zendesk = new ZendeskAction();
        zendesk.setSiteUrl(baseUrl);
        zendesk.setEmail("a@b.se");
        zendesk.setToken("tok");
        zendesk.setSubject("Printer");
        zendesk.setTicketDescription("It is broken");
        zendesk.setComment("Looking at it");
        zendesk.setTicketNumber("35");
        return zendesk;
    }

    @Test
    public void testZendeskCreatesASignedInTicketSuccess() throws IOException {
        answerStatus = 201;
        answerBody = "{\"ticket\":{\"id\":35}}";
        ActionResult result = run(zendesk());
        Assert.assertEquals("/api/v2/tickets.json", path);
        Assert.assertEquals("Basic " + Base64.getEncoder().encodeToString("a@b.se/token:tok".getBytes(StandardCharsets.UTF_8)), authorization);
        Assert.assertEquals("It is broken", received.path("ticket").path("comment").path("body").asText());
        Assert.assertEquals("35", result.getOutputVariables().get("ticketNumber"));
    }

    @Test
    public void testZendeskCommentIsHiddenFromTheCustomerUnlessAskedForSuccess() throws IOException {
        ZendeskAction zendesk = zendesk();
        zendesk.setOperation("addComment");
        run(zendesk);
        Assert.assertEquals("/api/v2/tickets/35.json", path);
        Assert.assertFalse(received.path("ticket").path("comment").path("public").asBoolean(true));
        zendesk.setVisibleToCustomer("true");
        run(zendesk);
        Assert.assertTrue(received.path("ticket").path("comment").path("public").asBoolean(false));
        zendesk.setOperation("solveTicket");
        run(zendesk);
        Assert.assertEquals("solved", received.path("ticket").path("status").asText());
    }

    @Test
    public void testNotionTakesTheAddressOfAPageAndSplitsLongTextSuccess() throws IOException {
        NotionAction notion = new NotionAction();
        notion.setBaseUrl(baseUrl);
        notion.setToken("ntn");
        notion.setPage("https://www.notion.so/acme/Reports-1429989fe8ac4effbc8f57f56486db54?pvs=4");
        notion.setTitle("Today");
        notion.setContent("First\n\n" + "x".repeat(2001));
        answerBody = "{\"id\":\"new\",\"url\":\"https://notion.so/new\"}";
        ActionResult created = run(notion);
        Assert.assertEquals("/v1/pages", path);
        Assert.assertEquals("2022-06-28", requestHeaders.get("notion-version"));
        Assert.assertEquals("1429989fe8ac4effbc8f57f56486db54", received.path("parent").path("page_id").asText());
        Assert.assertEquals("Today", received.path("properties").path("title").path("title").path(0).path("text").path("content").asText());
        Assert.assertEquals(3, received.path("children").size());
        Assert.assertEquals("new", created.getOutputVariables().get("pageId"));

        notion.setOperation("addText");
        notion.setAddedText("More");
        run(notion);
        Assert.assertEquals("PATCH", method);
        Assert.assertEquals("/v1/blocks/1429989fe8ac4effbc8f57f56486db54/children", path);
    }

    private KeyPair sheetsKeys;

    private GoogleSheetsAction sheets() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        sheetsKeys = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(sheetsKeys.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        GoogleSheetsAction sheets = new GoogleSheetsAction();
        sheets.setBaseUrl(baseUrl);
        sheets.setServiceAccountKey(mapper.writeValueAsString(Map.of("client_email", "bot@acme.iam.gserviceaccount.com", "private_key", pem, "token_uri", baseUrl + "/token")));
        sheets.setSpreadsheet("https://docs.google.com/spreadsheets/d/sheet-id_1/edit#gid=0");
        sheets.setSheet("My tab");
        answersByRequest.put("/token", "{\"access_token\":\"goog\"}");
        return sheets;
    }

    @Test
    public void testGoogleSheetsSignsInAndReadsRowsByColumnNameSuccess() throws Exception {
        answerBody = "{\"values\":[[\"name\",\"city\"],[\"Ada\",\"London\"],[\"Grace\"]]}";
        ActionResult result = run(sheets());
        Assert.assertEquals(result.getErrorMsg(), StatusCode.SUCCESS, result.getStatusCode());
        Assert.assertEquals("/v4/spreadsheets/sheet-id_1/values/My%20tab", rawPath);
        Assert.assertEquals("Bearer goog", authorization);
        JsonNode rows = mapper.readTree(result.getOutput());
        Assert.assertEquals("London", rows.path(0).path("city").asText());
        Assert.assertTrue(rows.path(1).path("city").isMissingNode());
        Assert.assertEquals(2, result.getOutputVariables().get("rowCount"));
    }

    @Test
    public void testGoogleSheetsSignsTheTokenRequestWithTheKeyFileSuccess() throws Exception {
        GoogleSheetsAction sheets = sheets();
        List<String> tokenRequests = new ArrayList<>();
        service.removeContext("/");
        service.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            tokenRequests.add(body);
            byte[] bytes = "{\"access_token\":\"goog\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        run(sheets);
        String form = URLDecoder.decode(tokenRequests.get(0), StandardCharsets.UTF_8);
        JWSObject assertion = JWSObject.parse(form.substring(form.indexOf("assertion=") + "assertion=".length()));
        Assert.assertTrue(assertion.verify(new RSASSAVerifier((RSAPublicKey) sheetsKeys.getPublic())));
        Assert.assertEquals("bot@acme.iam.gserviceaccount.com", assertion.getPayload().toJSONObject().get("iss"));
    }

    @Test
    public void testGoogleSheetsWritesValuesAsTextSoTheyCannotBeFormulasSuccess() throws Exception {
        GoogleSheetsAction sheets = sheets();
        sheets.setOperation("addRow");
        sheets.setRowValues("Ada\n=IMPORTXML(\"https://evil\",\"//a\")");
        Assert.assertEquals(StatusCode.SUCCESS, run(sheets).getStatusCode());
        Assert.assertEquals("valueInputOption=RAW", query);
        Assert.assertEquals("=IMPORTXML(\"https://evil\",\"//a\")", received.path("values").path(0).path(1).asText());
    }

    @Test
    public void testGoogleSheetsSaysWhenTheKeyFileIsWrongFail() throws Exception {
        GoogleSheetsAction sheets = sheets();
        sheets.setServiceAccountKey("not json");
        Assert.assertTrue(run(sheets).getErrorMsg().contains("could not read the key file"));
        sheets.setServiceAccountKey("{\"private_key\":\"nope\"}");
        Assert.assertTrue(run(sheets).getErrorMsg().contains("private key"));
    }

    private ClaudeAction claude() {
        ClaudeAction claude = new ClaudeAction();
        claude.setBaseUrl(baseUrl);
        claude.setApiKey("sk-test");
        claude.setInstructions("Be short.");
        claude.setUserInput("Is the disk full?");
        return claude;
    }

    @Test
    public void testClaudeAsksWithTheCurrentApiAndGivesTheTextSuccess() throws IOException {
        answerBody = "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"},{\"type\":\"text\",\"text\":\"Yes.\"}],\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}";
        ActionResult result = run(claude());
        Assert.assertEquals("/v1/messages", path);
        Assert.assertEquals("sk-test", requestHeaders.get("x-api-key"));
        Assert.assertEquals("2026-10-21", requestHeaders.get("anthropic-version"));
        Assert.assertEquals("server-side-fallback-2026-07-01", requestHeaders.get("anthropic-beta"));
        Assert.assertEquals("claude-opus-5-5", received.path("model").asText());
        Assert.assertEquals(16000, received.path("max_tokens").asInt());
        Assert.assertEquals("Be short.", received.path("system").asText());
        Assert.assertEquals("default", received.path("fallbacks").asText());
        Assert.assertEquals("medium", received.path("output_config").path("effort").asText());
        Assert.assertEquals("Is the disk full?", received.path("messages").path(0).path("content").asText());
        Assert.assertEquals("Yes.", result.getOutput());
        Assert.assertEquals(12L, result.getOutputVariables().get("inputTokens"));
    }

    @Test
    public void testClaudeOnlyAsksForAFallbackFromAModelThatHasOneSuccess() throws IOException {
        ClaudeAction claude = claude();
        claude.setModel("claude-haiku-5-5");
        run(claude);
        Assert.assertNull(requestHeaders.get("anthropic-beta"));
        Assert.assertTrue(received.path("fallbacks").isMissingNode());
    }

    @Test
    public void testClaudeFailsWhenItDeclinesOrIsCutOffFail() throws IOException {
        answerBody = "{\"content\":[],\"stop_reason\":\"refusal\",\"stop_details\":{\"type\":\"refusal\",\"category\":\"cyber\",\"explanation\":\"Not this.\"}}";
        Assert.assertEquals("Claude declined to answer: Not this.", run(claude()).getErrorMsg());
        answerBody = "{\"content\":[{\"type\":\"text\",\"text\":\"Half an\"}],\"stop_reason\":\"max_tokens\"}";
        ActionResult cutOff = run(claude());
        Assert.assertEquals(StatusCode.FAILURE, cutOff.getStatusCode());
        Assert.assertTrue(cutOff.getErrorMsg().contains("cut off"));
    }

    @Test
    public void testClaudeGivesTheReasonFromItsNestedErrorFail() throws IOException {
        answerStatus = 401;
        answerBody = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
        Assert.assertEquals("Claude answered with status 401: invalid x-api-key.", run(claude()).getErrorMsg());
    }

    private ReadEmailAction readEmail(GreenMail server) {
        ReadEmailAction readEmail = new ReadEmailAction();
        readEmail.setHostname("localhost");
        readEmail.setPort(String.valueOf(server.getImap().getPort()));
        readEmail.setSecurity("none");
        readEmail.setUsername("ops");
        readEmail.setPassword("pw");
        return readEmail;
    }

    @Test
    public void testReadEmailReadsARealMailboxNewestFirstAndCanMarkAsReadSuccess() throws Exception {
        GreenMail server = new GreenMail(ServerSetupTest.SMTP_IMAP);
        server.start();
        try {
            server.setUser("ops@example.com", "ops", "pw");
            GreenMailUtil.sendTextEmail("ops@example.com", "a@example.com", "First", "One", ServerSetupTest.SMTP);
            GreenMailUtil.sendTextEmail("ops@example.com", "b@example.com", "Second", "Two", ServerSetupTest.SMTP);
            server.waitForIncomingEmail(5000, 2);

            ReadEmailAction readEmail = readEmail(server);
            readEmail.setMaxMessages("1");
            ActionResult newest = run(readEmail);
            Assert.assertEquals(newest.getErrorMsg(), StatusCode.SUCCESS, newest.getStatusCode());
            JsonNode message = mapper.readTree(newest.getOutput()).path(0);
            Assert.assertEquals("Second", message.path("subject").asText());
            Assert.assertEquals("b@example.com", message.path("from").asText());
            Assert.assertEquals("Two", message.path("text").asText().trim());
            Assert.assertEquals(1, newest.getOutputVariables().get("messageCount"));

            Assert.assertEquals("Reading alone marks nothing as read.", 1, run(readEmail).getOutputVariables().get("messageCount"));
            readEmail.setMaxMessages("10");
            readEmail.setMarkAsRead("true");
            Assert.assertEquals(2, run(readEmail).getOutputVariables().get("messageCount"));
            readEmail.setMarkAsRead("false");
            Assert.assertEquals(0, run(readEmail).getOutputVariables().get("messageCount"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void testReadEmailDoesNotRemoveWhatSomeoneMarkedAsDeletedSuccess() throws Exception {
        GreenMail server = new GreenMail(ServerSetupTest.SMTP_IMAP);
        server.start();
        try {
            server.setUser("ops@example.com", "ops", "pw");
            GreenMailUtil.sendTextEmail("ops@example.com", "a@example.com", "Kept", "One", ServerSetupTest.SMTP);
            server.waitForIncomingEmail(5000, 1);
            Store store = Session.getInstance(new java.util.Properties()).getStore("imap");
            store.connect("localhost", server.getImap().getPort(), "ops", "pw");
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            inbox.getMessage(1).setFlag(Flags.Flag.DELETED, true);
            inbox.close(false);
            store.close();

            ReadEmailAction readEmail = readEmail(server);
            readEmail.setMarkAsRead("true");
            Assert.assertEquals(StatusCode.SUCCESS, run(readEmail).getStatusCode());
            Assert.assertEquals(1, server.getReceivedMessages().length);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testReadEmailSaysWhatIsWrongFail() throws Exception {
        GreenMail server = new GreenMail(ServerSetupTest.SMTP_IMAP);
        server.start();
        try {
            server.setUser("ops@example.com", "ops", "pw");
            ReadEmailAction readEmail = readEmail(server);
            readEmail.setFolder("Nowhere");
            Assert.assertEquals("The mail server has no folder called \"Nowhere\".", run(readEmail).getErrorMsg());
            readEmail.setFolder("INBOX");
            readEmail.setPassword("wrong");
            Assert.assertEquals("The mail server did not accept the username and password.", run(readEmail).getErrorMsg());
        } finally {
            server.stop();
        }
        ReadEmailAction closed = new ReadEmailAction();
        closed.setHostname("localhost");
        closed.setPort("1");
        closed.setSecurity("none");
        closed.setUsername("ops");
        Assert.assertEquals("Could not connect to the mail server at localhost:1. Check the address, and that the service is running.", run(closed).getErrorMsg());
    }

    private OpenAIAction openAI() {
        OpenAIAction openAI = new OpenAIAction();
        openAI.setBaseUrl(baseUrl + "/v1/");
        openAI.setApiKey("sk-test");
        openAI.setModel("gpt-test");
        openAI.setUserInput("A\ttab and a \"quote\" from an earlier step");
        return openAI;
    }

    @Test
    public void testOpenAISendsValidJsonForAnyTextAndKeepsTheWholeAnswerAsOutputSuccess() throws IOException {
        answerBody = "{\"choices\":[{\"message\":{\"content\":\"Yes.\"}}]}";
        ActionResult result = run(openAI());
        Assert.assertEquals("/v1/chat/completions", path);
        Assert.assertEquals("Bearer sk-test", authorization);
        Assert.assertEquals("gpt-test", received.path("model").asText());
        Assert.assertEquals("A\ttab and a \"quote\" from an earlier step", received.path("messages").path(0).path("content").asText());
        Assert.assertEquals(answerBody, result.getOutput());
    }

    @Test
    public void testOpenAISendsNoKeyToAServerThatDoesNotAskForOneSuccess() throws IOException {
        OpenAIAction openAI = openAI();
        openAI.setApiKey(null);
        Assert.assertEquals(StatusCode.SUCCESS, run(openAI).getStatusCode());
        Assert.assertNull(authorization);
    }

    @Test
    public void testOpenAIDoesNotEvaluateTextFromAnEarlierStepTwiceSuccess() throws IOException {
        TaskExecution execution = new TaskExecution();
        execution.getParameters().put("text", new ExecutionParameter("text", "$executionId".getBytes(StandardCharsets.UTF_8), false));
        OpenAIAction openAI = openAI();
        openAI.setUserInput("$parameters.get(\"text\")");
        openAI.run(new TaskServiceImpl(), execution, new VariableExtractorUtil(new VelocityEngine()), null);
        Assert.assertEquals("$executionId", received.path("messages").path(0).path("content").asText());
    }

    @Test
    public void testOpenAIGivesTheReasonFromItsAnswerFail() throws IOException {
        answerStatus = 401;
        answerBody = "{\"error\":{\"message\":\"Incorrect API key provided.\",\"type\":\"invalid_request_error\"}}";
        Assert.assertEquals("OpenAI answered with status 401: Incorrect API key provided.", run(openAI()).getErrorMsg());
    }


    private void assertSucceeded(ActionResult result) {
        Assert.assertEquals(result.getErrorMsg(), StatusCode.SUCCESS, result.getStatusCode());
    }

    private void assertFailsWith(Action action, String reason) throws IOException {
        ActionResult result = run(action);
        Assert.assertEquals(StatusCode.FAILURE, result.getStatusCode());
        Assert.assertTrue(result.getErrorMsg(), result.getErrorMsg().contains(reason));
    }

    private String basicOf(String login) {
        return "Basic " + Base64.getEncoder().encodeToString(login.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testHubSpotSavesAContactByEmailAndFindsItSuccess() throws IOException {
        HubSpotAction hubspot = new HubSpotAction();
        hubspot.setBaseUrl(baseUrl);
        hubspot.setToken("tok");
        hubspot.setEmail("ada@example.com");
        hubspot.setFirstName("Ada");
        answerBody = "{\"results\":[{\"id\":\"51\"}]}";
        ActionResult saved = run(hubspot);
        assertSucceeded(saved);
        Assert.assertEquals("/crm/objects/2026-09/contacts/batch/upsert", path);
        Assert.assertEquals("Bearer tok", authorization);
        Assert.assertEquals("email", received.path("inputs").path(0).path("idProperty").asText());
        Assert.assertEquals("Ada", received.path("inputs").path(0).path("properties").path("firstname").asText());
        Assert.assertTrue(received.path("inputs").path(0).path("properties").path("lastname").isMissingNode());
        Assert.assertEquals("51", saved.getOutputVariables().get("contactId"));
        hubspot.setOperation("findContact");
        answerBody = "{\"results\":[]}";
        ActionResult missing = run(hubspot);
        assertSucceeded(missing);
        Assert.assertEquals(false, missing.getOutputVariables().get("found"));
    }

    @Test
    public void testHubSpotCreatesADealAndNeedsAToken() throws IOException {
        HubSpotAction hubspot = new HubSpotAction();
        hubspot.setBaseUrl(baseUrl);
        hubspot.setOperation("createDeal");
        hubspot.setDealName("Big one");
        assertFailsWith(hubspot, "access token");
        hubspot.setToken("tok");
        answerBody = "{\"id\":\"7\"}";
        ActionResult created = run(hubspot);
        Assert.assertEquals("Big one", received.path("properties").path("dealname").asText());
        Assert.assertEquals("7", created.getOutputVariables().get("dealId"));
    }

    private SalesforceAction salesforce() {
        SalesforceAction salesforce = new SalesforceAction();
        salesforce.setSiteUrl(baseUrl);
        salesforce.setClientId("key");
        salesforce.setClientSecret("secret");
        answersByRequest.put("/services/oauth2/token", "{\"access_token\":\"sf\"}");
        return salesforce;
    }

    @Test
    public void testSalesforceSignsInAndCreatesARecordSuccess() throws IOException {
        SalesforceAction salesforce = salesforce();
        salesforce.setRecordFields("{\"LastName\": \"Lovelace\"}");
        answerBody = "{\"id\":\"003\"}";
        ActionResult created = run(salesforce);
        assertSucceeded(created);
        Assert.assertEquals(List.of("/services/oauth2/token", "/services/data/v68.0/sobjects/Contact"), requests);
        Assert.assertEquals("Bearer sf", authorization);
        Assert.assertEquals("Lovelace", received.path("LastName").asText());
        Assert.assertEquals("003", created.getOutputVariables().get("recordId"));
        Assert.assertEquals(baseUrl + "/003", created.getOutputVariables().get("url"));
    }

    @Test
    public void testSalesforceRunsAQueryAndGivesItsReasonWhenRefusedSuccess() throws IOException {
        SalesforceAction salesforce = salesforce();
        salesforce.setOperation("runQuery");
        salesforce.setQuery("SELECT Id FROM Contact");
        answerBody = "{\"totalSize\":2,\"records\":[]}";
        ActionResult found = run(salesforce);
        Assert.assertEquals("q=SELECT Id FROM Contact", query);
        Assert.assertEquals(2, found.getOutputVariables().get("recordCount"));
        answerStatus = 400;
        answerBody = "[{\"message\":\"unexpected token\",\"errorCode\":\"MALFORMED_QUERY\"}]";
        answersByRequest.clear();
        assertFailsWith(salesforce, "unexpected token");
    }

    @Test
    public void testSalesforceRefusesFieldsThatAreNotJsonFailure() throws IOException {
        SalesforceAction salesforce = salesforce();
        salesforce.setRecordFields("LastName: Lovelace");
        assertFailsWith(salesforce, "JSON object");
        Assert.assertTrue(requests.isEmpty());
    }

    @Test
    public void testPipedriveCreatesAPersonADealAndANoteSuccess() throws IOException {
        PipedriveAction pipedrive = new PipedriveAction();
        pipedrive.setSiteUrl(baseUrl);
        pipedrive.setToken("tok");
        pipedrive.setPersonName("Ada");
        pipedrive.setEmail("ada@example.com");
        answerBody = "{\"data\":{\"id\":9}}";
        ActionResult person = run(pipedrive);
        Assert.assertEquals("/api/v2/persons", path);
        Assert.assertEquals("tok", requestHeaders.get("x-api-token"));
        Assert.assertEquals("ada@example.com", received.path("emails").path(0).path("value").asText());
        Assert.assertEquals("9", person.getOutputVariables().get("personId"));
        pipedrive.setOperation("createDeal");
        pipedrive.setDealTitle("Big one");
        pipedrive.setDealValue("12.5");
        pipedrive.setPersonId("9");
        run(pipedrive);
        Assert.assertEquals(12.5, received.path("value").asDouble(), 0);
        Assert.assertEquals(9, received.path("person_id").asInt());
        pipedrive.setOperation("addNote");
        pipedrive.setDealId("3");
        pipedrive.setNote("Called");
        assertSucceeded(run(pipedrive));
        Assert.assertEquals("/api/v1/notes", path);
        Assert.assertEquals(3, received.path("deal_id").asInt());
    }

    @Test
    public void testMailchimpFindsTheContactByTheHashOfItsLowercaseEmailSuccess() throws IOException {
        MailchimpAction mailchimp = new MailchimpAction();
        mailchimp.setBaseUrl(baseUrl);
        mailchimp.setApiKey("abc-us21");
        mailchimp.setAudienceId("list1");
        mailchimp.setEmail("Urist.McVankab@freddiesjokes.com");
        assertSucceeded(run(mailchimp));
        // This hash is the example in the documentation of Mailchimp.
        Assert.assertEquals("/3.0/lists/list1/members/62eeb292278cc15f5817cb78f7790b08", path);
        Assert.assertEquals("PUT", method);
        Assert.assertEquals("subscribed", received.path("status_if_new").asText());
        Assert.assertTrue("Someone who has left must not be subscribed again", received.path("status").isMissingNode());
        mailchimp.setOperation("unsubscribeContact");
        assertSucceeded(run(mailchimp));
        Assert.assertEquals("PATCH", method);
        Assert.assertEquals("unsubscribed", received.path("status").asText());
    }

    @Test
    public void testStripeSendsAFormAndRefundsAllUnlessToldAnAmountSuccess() throws IOException {
        StripeAction stripe = new StripeAction();
        stripe.setBaseUrl(baseUrl);
        stripe.setSecretKey("sk_test");
        stripe.setOperation("createPaymentLink");
        stripe.setPriceId("price_1");
        answerBody = "{\"url\":\"https://buy.stripe.com/x\"}";
        ActionResult link = run(stripe);
        Assert.assertEquals("line_items%5B0%5D%5Bprice%5D=price_1&line_items%5B0%5D%5Bquantity%5D=1", receivedText);
        Assert.assertEquals("https://buy.stripe.com/x", link.getOutputVariables().get("url"));
        stripe.setOperation("refundPayment");
        stripe.setPaymentId("pi_1");
        answerBody = "{\"id\":\"re_1\",\"status\":\"succeeded\"}";
        ActionResult refund = run(stripe);
        Assert.assertEquals("/v1/refunds", path);
        Assert.assertEquals("payment_intent=pi_1", receivedText);
        Assert.assertEquals("re_1", refund.getOutputVariables().get("refundId"));
        stripe.setAmount("ten");
        assertFailsWith(stripe, "smallest unit");
    }

    @Test
    public void testShopifyFindsAnOrderByItsNumberBeforeCancellingItSuccess() throws IOException {
        ShopifyAction shopify = new ShopifyAction();
        shopify.setSiteUrl(baseUrl);
        shopify.setToken("shpat");
        shopify.setOperation("cancelOrder");
        shopify.setOrderNumber("#1001");
        answerBody = "{\"orders\":[{\"id\":450789}]}";
        ActionResult cancelled = run(shopify);
        assertSucceeded(cancelled);
        Assert.assertEquals(List.of("/admin/api/2026-10/orders.json", "/admin/api/2026-10/orders/450789/cancel.json"), requests);
        Assert.assertEquals("shpat", requestHeaders.get("x-shopify-access-token"));
        Assert.assertEquals("450789", cancelled.getOutputVariables().get("orderId"));
    }

    @Test
    public void testShopifyAsksForATokenWithAClientIdAndSaysWhenAnOrderIsMissingFailure() throws IOException {
        ShopifyAction shopify = new ShopifyAction();
        shopify.setSiteUrl(baseUrl);
        shopify.setOrderNumber("1001");
        assertFailsWith(shopify, "client ID");
        shopify.setClientId("id");
        shopify.setClientSecret("secret");
        answersByRequest.put("/admin/oauth/access_token", "{\"access_token\":\"short\"}");
        answerBody = "{\"orders\":[]}";
        assertFailsWith(shopify, "no order with the number");
        Assert.assertEquals("short", requestHeaders.get("x-shopify-access-token"));
    }

    @Test
    public void testWooCommerceNoteIsOnlyForTheShopUnlessAskedForSuccess() throws IOException {
        WooCommerceAction shop = new WooCommerceAction();
        shop.setSiteUrl(baseUrl);
        shop.setConsumerKey("ck");
        shop.setConsumerSecret("cs");
        shop.setOperation("addOrderNote");
        shop.setOrderNumber("123");
        shop.setNote("Packed");
        assertSucceeded(run(shop));
        Assert.assertEquals("/wp-json/wc/v3/orders/123/notes", path);
        Assert.assertEquals(basicOf("ck:cs"), authorization);
        Assert.assertFalse(received.path("customer_note").asBoolean());
        shop.setOperation("setStock");
        shop.setProductId("8");
        shop.setStockQuantity("40");
        assertSucceeded(run(shop));
        Assert.assertEquals("/wp-json/wc/v3/products/8", path);
        Assert.assertEquals(40, received.path("stock_quantity").asInt());
        Assert.assertTrue(received.path("manage_stock").asBoolean());
    }

    @Test
    public void testIntercomReplyIsANoteUnlessAskedForSuccess() throws IOException {
        IntercomAction intercom = new IntercomAction();
        intercom.setBaseUrl(baseUrl);
        intercom.setToken("tok");
        intercom.setOperation("replyToConversation");
        intercom.setConversationId("77");
        intercom.setTeammateId("5");
        intercom.setComment("Looking at it");
        assertSucceeded(run(intercom));
        Assert.assertEquals("/conversations/77/reply", path);
        Assert.assertEquals("note", received.path("message_type").asText());
        Assert.assertEquals("2.17", requestHeaders.get("intercom-version"));
        intercom.setVisibleToCustomer("true");
        assertSucceeded(run(intercom));
        Assert.assertEquals("comment", received.path("message_type").asText());
    }

    @Test
    public void testFreshdeskCreatesATicketAndKeepsANotePrivateSuccess() throws IOException {
        FreshdeskAction freshdesk = new FreshdeskAction();
        freshdesk.setSiteUrl(baseUrl);
        freshdesk.setApiKey("key");
        freshdesk.setEmail("ada@example.com");
        freshdesk.setSubject("Printer");
        freshdesk.setTicketDescription("It is broken");
        answerBody = "{\"id\":35}";
        ActionResult ticket = run(freshdesk);
        Assert.assertEquals(basicOf("key:X"), authorization);
        Assert.assertEquals(2, received.path("status").asInt());
        Assert.assertEquals("35", ticket.getOutputVariables().get("ticketNumber"));
        freshdesk.setOperation("addNote");
        freshdesk.setTicketNumber("35");
        freshdesk.setComment("Ordered a part");
        assertSucceeded(run(freshdesk));
        Assert.assertEquals("/api/v2/tickets/35/notes", path);
        Assert.assertTrue(received.path("private").asBoolean());
        freshdesk.setOperation("solveTicket");
        assertSucceeded(run(freshdesk));
        Assert.assertEquals(4, received.path("status").asInt());
    }

    @Test
    public void testWhatsAppSendsAMessageAndATemplateSuccess() throws IOException {
        WhatsAppAction whatsApp = new WhatsAppAction();
        whatsApp.setBaseUrl(baseUrl);
        whatsApp.setToken("tok");
        whatsApp.setPhoneNumberId("106");
        whatsApp.setTo("+46 70 123 45 67");
        whatsApp.setMessage("Hello");
        answerBody = "{\"messages\":[{\"id\":\"wamid.1\"}]}";
        ActionResult sent = run(whatsApp);
        Assert.assertEquals("/v26.0/106/messages", path);
        Assert.assertEquals("46701234567", received.path("to").asText());
        Assert.assertEquals("whatsapp", received.path("messaging_product").asText());
        Assert.assertEquals("wamid.1", sent.getOutputVariables().get("messageId"));
        whatsApp.setOperation("sendTemplate");
        whatsApp.setTemplateName("hello_world");
        run(whatsApp);
        Assert.assertEquals("en_US", received.path("template").path("language").path("code").asText());
        whatsApp.setTo("not a number");
        assertFailsWith(whatsApp, "country code");
    }

    @Test
    public void testAsanaAsksForTheAddressOfTheTaskItCreatesSuccess() throws IOException {
        AsanaAction asana = new AsanaAction();
        asana.setBaseUrl(baseUrl);
        asana.setToken("tok");
        asana.setProjectId("12");
        asana.setTitle("Call Ada");
        answerBody = "{\"data\":{\"gid\":\"99\",\"permalink_url\":\"https://app.asana.com/99\"}}";
        ActionResult created = run(asana);
        Assert.assertEquals("/api/1.0/tasks", path);
        Assert.assertEquals("opt_fields=gid,permalink_url", query);
        Assert.assertEquals("12", received.path("data").path("projects").path(0).asText());
        Assert.assertEquals("99", created.getOutputVariables().get("taskId"));
        Assert.assertEquals("https://app.asana.com/99", created.getOutputVariables().get("url"));
    }

    @Test
    public void testTrelloKeepsItsKeysOutOfTheAddressSuccess() throws IOException {
        TrelloAction trello = new TrelloAction();
        trello.setBaseUrl(baseUrl);
        trello.setApiKey("key");
        trello.setToken("tok");
        trello.setOperation("addComment");
        trello.setCardId("abc");
        trello.setComment("Done & dusted");
        assertSucceeded(run(trello));
        Assert.assertEquals("/1/cards/abc/actions/comments", path);
        Assert.assertEquals("text=Done & dusted", query);
        Assert.assertEquals("OAuth oauth_consumer_key=\"key\", oauth_token=\"tok\"", authorization);
        trello.setOperation("moveCard");
        trello.setListId("list2");
        assertSucceeded(run(trello));
        Assert.assertEquals("PUT", method);
        Assert.assertEquals("idList=list2", query);
    }

    @Test
    public void testClickUpSaysWhetherToNotifyEveryoneOfACommentSuccess() throws IOException {
        ClickUpAction clickUp = new ClickUpAction();
        clickUp.setBaseUrl(baseUrl);
        clickUp.setToken("pk_1");
        clickUp.setOperation("addComment");
        clickUp.setTaskId("86a");
        clickUp.setComment("Looking at it");
        assertSucceeded(run(clickUp));
        Assert.assertEquals("/api/v2/task/86a/comment", path);
        Assert.assertEquals("pk_1", authorization);
        Assert.assertFalse(received.path("notify_all").asBoolean(true));
    }

    @Test
    public void testMondayGivesTheReasonEvenWhenItAnswersWithSuccess() throws IOException {
        MondayAction monday = new MondayAction();
        monday.setBaseUrl(baseUrl);
        monday.setToken("tok");
        monday.setBoardId("1");
        monday.setItemName("Call Ada");
        answerBody = "{\"data\":{\"create_item\":{\"id\":\"44\"}}}";
        ActionResult created = run(monday);
        assertSucceeded(created);
        Assert.assertEquals("/v2", path);
        Assert.assertEquals("2026-10", requestHeaders.get("api-version"));
        Assert.assertEquals("{}", received.path("variables").path("columns").asText());
        Assert.assertEquals("44", created.getOutputVariables().get("itemId"));
        answerBody = "{\"errors\":[{\"message\":\"Board not found\"}]}";
        assertFailsWith(monday, "Board not found");
    }

    @Test
    public void testAirtableTakesTheAddressOfABaseAndFiltersSuccess() throws IOException {
        AirtableAction airtable = new AirtableAction();
        airtable.setBaseUrl(baseUrl);
        airtable.setToken("pat");
        airtable.setBase("https://airtable.com/appAbC123/tblX/viwY");
        airtable.setTableName("My customers");
        airtable.setFilterFormula("{Status} = \"Open\"");
        answerBody = "{\"records\":[{\"id\":\"rec1\"}]}";
        ActionResult read = run(airtable);
        Assert.assertEquals("/v0/appAbC123/My%20customers", rawPath);
        Assert.assertEquals("maxRecords=100&filterByFormula={Status} = \"Open\"", query);
        Assert.assertEquals(1, read.getOutputVariables().get("recordCount"));
        airtable.setOperation("addRecord");
        airtable.setRecordFields("{\"Name\": \"Ada\"}");
        answerBody = "{\"id\":\"rec2\"}";
        ActionResult added = run(airtable);
        Assert.assertEquals("Ada", received.path("fields").path("Name").asText());
        Assert.assertEquals("rec2", added.getOutputVariables().get("recordId"));
    }

    @Test
    public void testSupabaseNeedsAFilterToUpdateAndSendsTheKeyTheRightWaySuccess() throws IOException {
        SupabaseAction supabase = new SupabaseAction();
        supabase.setSiteUrl(baseUrl);
        supabase.setSecretKey("sb_secret_1");
        supabase.setTableName("orders");
        supabase.setOperation("updateRows");
        supabase.setRowValues("{\"status\": \"done\"}");
        assertFailsWith(supabase, "filter");
        supabase.setFilter("id=eq.5");
        answerBody = "[{\"id\":5}]";
        ActionResult updated = run(supabase);
        assertSucceeded(updated);
        Assert.assertEquals("/rest/v1/orders", path);
        Assert.assertEquals("id=eq.5", query);
        Assert.assertEquals("sb_secret_1", requestHeaders.get("apikey"));
        Assert.assertNull(authorization);
        Assert.assertEquals(1, updated.getOutputVariables().get("rowCount"));
        supabase.setSecretKey("eyJservice");
        run(supabase);
        Assert.assertEquals("Bearer eyJservice", authorization);
    }

    private String googleKeyFile() throws Exception {
        return sheets().getServiceAccountKey();
    }

    @Test
    public void testGoogleDriveEscapesTheNameAndCreatesAFileInAFolderSuccess() throws Exception {
        GoogleDriveAction drive = new GoogleDriveAction();
        drive.setBaseUrl(baseUrl);
        drive.setServiceAccountKey(googleKeyFile());
        drive.setFileName("Ada's plan");
        answerBody = "{\"files\":[{\"id\":\"f1\"}]}";
        ActionResult found = run(drive);
        assertSucceeded(found);
        Assert.assertTrue(query, query.contains("name contains 'Ada\\'s plan'"));
        Assert.assertTrue(query, query.contains("supportsAllDrives=true"));
        Assert.assertEquals("Bearer goog", authorization);
        Assert.assertEquals(1, found.getOutputVariables().get("fileCount"));
        drive.setOperation("createTextFile");
        assertFailsWith(drive, "folder");
        drive.setFolderId("folder1");
        drive.setFileContent("Hello");
        answerBody = "{\"id\":\"f2\",\"webViewLink\":\"https://drive/f2\"}";
        ActionResult created = run(drive);
        assertSucceeded(created);
        Assert.assertEquals("/upload/drive/v3/files", path);
        Assert.assertTrue(requestHeaders.get("content-type"), requestHeaders.get("content-type").startsWith("multipart/related; boundary="));
        Assert.assertTrue(receivedText, receivedText.contains("\"parents\":[\"folder1\"]") && receivedText.contains("\r\n\r\nHello\r\n"));
        Assert.assertEquals("f2", created.getOutputVariables().get("fileId"));
    }

    @Test
    public void testGoogleCalendarCreatesAnEventAndRefusesATimeWithoutAZoneSuccess() throws Exception {
        GoogleCalendarAction calendar = new GoogleCalendarAction();
        calendar.setBaseUrl(baseUrl);
        calendar.setServiceAccountKey(googleKeyFile());
        calendar.setCalendar("team@example.com");
        calendar.setTitle("Standup");
        calendar.setStartTime("2026-12-01T09:00:00");
        calendar.setEndTime("2026-12-01T10:00:00+01:00");
        assertFailsWith(calendar, "time zone");
        calendar.setStartTime("2026-12-01T09:00:00+01:00");
        answerBody = "{\"id\":\"ev1\",\"htmlLink\":\"https://calendar/ev1\"}";
        ActionResult created = run(calendar);
        assertSucceeded(created);
        Assert.assertEquals("/calendar/v3/calendars/team%40example.com/events", rawPath);
        Assert.assertEquals("2026-12-01T09:00:00+01:00", received.path("start").path("dateTime").asText());
        Assert.assertEquals("ev1", created.getOutputVariables().get("eventId"));
    }

    @Test
    public void testConfluenceFindsTheSpaceAndUpdatesToTheNextVersionSuccess() throws IOException {
        ConfluenceAction confluence = new ConfluenceAction();
        confluence.setSiteUrl(baseUrl);
        confluence.setEmail("a@b.se");
        confluence.setToken("tok");
        confluence.setSpaceKey("ENG");
        confluence.setTitle("Plan");
        confluence.setPageContent("<p>Hello</p>");
        answersByRequest.put("/wiki/api/v2/spaces", "{\"results\":[{\"id\":\"42\"}]}");
        answerBody = "{\"id\":\"900\",\"title\":\"Old\",\"version\":{\"number\":3},\"_links\":{\"webui\":\"/spaces/ENG/pages/900\"}}";
        ActionResult created = run(confluence);
        assertSucceeded(created);
        Assert.assertEquals("42", received.path("spaceId").asText());
        Assert.assertEquals("storage", received.path("body").path("representation").asText());
        Assert.assertEquals(basicOf("a@b.se:tok"), authorization);
        Assert.assertEquals(baseUrl + "/wiki/spaces/ENG/pages/900", created.getOutputVariables().get("url"));
        confluence.setOperation("updatePage");
        confluence.setPageId("900");
        confluence.setTitle("");
        assertSucceeded(run(confluence));
        Assert.assertEquals("PUT", method);
        Assert.assertEquals(4, received.path("version").path("number").asInt());
        Assert.assertEquals("Old", received.path("title").asText());
    }

    @Test
    public void testMongoDBRefusesToChangeEverythingWithoutBeingToldFailure() throws IOException {
        MongoDBAction mongo = new MongoDBAction();
        mongo.setConnectionString("mongodb://localhost:1");
        mongo.setDatabase("shop");
        mongo.setCollection("orders");
        mongo.setOperation("deleteDocuments");
        assertFailsWith(mongo, "filter");
        mongo.setOperation("addDocument");
        assertFailsWith(mongo, "document");
    }
}
