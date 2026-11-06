package com.dopplertask.dopplertask;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import javax.mail.internet.MimeMessage;
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
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;
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
        } catch (IOException notJson) {
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
    public void testTeamsSendsACardAndKeepsTheWebhookOutOfAnErrorSuccess() throws IOException {
        TeamsAction teams = new TeamsAction();
        teams.setWebhookUrl(baseUrl + "/workflows/secret");
        teams.setMessage("Disk is full");
        answerStatus = 202;
        answerBody = "";
        Assert.assertEquals(StatusCode.SUCCESS, run(teams).getStatusCode());
        Assert.assertEquals("application/vnd.microsoft.card.adaptive", received.path("attachments").path(0).path("contentType").asText());
        Assert.assertEquals("Disk is full", received.path("attachments").path(0).path("content").path("body").path(0).path("text").asText());
        teams.setWebhookUrl("https://example.com/workflows/my secret");
        Assert.assertFalse(run(teams).getErrorMsg().contains("secret"));
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

    @Test
    public void testOpenAIKeepsARefusedKeyOutOfWhatIsStoredWithTheRunFail() throws IOException {
        answerStatus = 401;
        answerBody = "{\"error\":{\"message\":\"Incorrect API key provided: sk-test.\"}}";
        ActionResult result = run(openAI());
        Assert.assertEquals("OpenAI answered with status 401: Incorrect API key provided: (hidden).", result.getErrorMsg());
        Assert.assertFalse(result.getOutput(), result.getOutput().contains("sk-test"));
    }
}
