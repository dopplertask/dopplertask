package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.dao.CredentialDao;
import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.Credential;
import com.dopplertask.dopplertask.domain.StatusCode;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.connection.HttpAction;
import com.dopplertask.dopplertask.domain.action.connection.HttpHeader;
import com.dopplertask.dopplertask.domain.action.connection.SSHAction;
import com.dopplertask.dopplertask.domain.action.connection.SecureCopyAction;
import com.dopplertask.dopplertask.domain.action.integration.chat.SlackAction;
import com.dopplertask.dopplertask.domain.action.integration.code.JiraAction;
import com.dopplertask.dopplertask.domain.action.integration.mail.EmailAction;
import com.dopplertask.dopplertask.domain.action.integration.mail.ReadEmailAction;
import com.dopplertask.dopplertask.domain.action.trigger.Webhook;
import com.dopplertask.dopplertask.service.CredentialCipher;
import com.dopplertask.dopplertask.service.CredentialProblemException;
import com.dopplertask.dopplertask.service.CredentialService;
import tools.jackson.databind.ObjectMapper;
import com.dopplertask.dopplertask.domain.action.StepTypes;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A real database and real encryption, since what matters is what ends up stored and what can be read back.
 */
@RunWith(SpringRunner.class)
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:credentialservice")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = CredentialServiceTest.DatabaseOnly.class)
public class CredentialServiceTest {

    @Configuration
    @EntityScan("com.dopplertask.dopplertask.domain")
    @EnableJpaRepositories("com.dopplertask.dopplertask.dao")
    static class DatabaseOnly {
    }

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Autowired
    private CredentialDao credentialDao;

    private CredentialService credentials;

    @Before
    public void createService() {
        credentials = new CredentialService(credentialDao, new CredentialCipher("a key for tests", folder.getRoot() + "/unused.key"));
    }

    private SlackAction slackUsing(String credential) {
        SlackAction slack = new SlackAction();
        slack.setCredential(credential);
        return slack;
    }

    @Test
    public void testAValueIsNotReadableInTheDatabaseOrTheListSuccess() throws Exception {
        credentials.create("prod-slack", "SlackAction", Map.of("token", "xoxb-the-real-token"));
        Credential stored = credentialDao.findByName("prod-slack").orElseThrow();
        Assert.assertFalse(stored.getEncryptedValues().contains("xoxb-the-real-token"));
        String listed = new ObjectMapper().writeValueAsString(credentials.list());
        Assert.assertFalse(listed, listed.contains("xoxb-the-real-token"));
        Assert.assertTrue(listed, listed.contains("\"fieldsSet\":[\"token\"]"));
    }

    @Test
    public void testAStepHasTheValuesOnlyWhileItRunsSuccess() throws IOException {
        credentials.create("prod-slack", "SlackAction", Map.of("token", "xoxb-the-real-token"));
        SlackAction slack = slackUsing("prod-slack");
        List<String> whileRunning = new ArrayList<>();
        ActionResult result = credentials.runWith(slack, new TaskExecution(), () -> {
            whileRunning.add(slack.getToken());
            return new ActionResult();
        });
        Assert.assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        Assert.assertEquals(List.of("xoxb-the-real-token"), whileRunning);
        Assert.assertNull(slack.getToken());
    }

    @Test
    public void testTheValuesAreTakenAwayAlsoWhenAStepThrowsFail() {
        credentials.create("prod-slack", "SlackAction", Map.of("token", "xoxb-the-real-token"));
        SlackAction slack = slackUsing("prod-slack");
        Assert.assertThrows(IOException.class, () -> credentials.runWith(slack, new TaskExecution(), () -> {
            throw new IOException("broken");
        }));
        Assert.assertNull(slack.getToken());
    }

    @Test
    public void testASecretThatAStepRepeatsIsHiddenButNotWhatIsNotSecretSuccess() throws IOException {
        credentials.create("prod-jira", "Atlassian account", Map.of("email", "ops@example.com", "token", "the-real-token"));
        JiraAction jira = new JiraAction();
        jira.setCredential("prod-jira");
        ActionResult result = credentials.runWith(jira, new TaskExecution(), () -> {
            ActionResult answer = new ActionResult(StatusCode.FAILURE, "refused the-real-token for ops@example.com", "Incorrect key: the-real-token.");
            return answer;
        });
        Assert.assertEquals("refused (hidden) for ops@example.com", result.getOutput());
        Assert.assertEquals("Incorrect key: (hidden).", result.getErrorMsg());
    }

    @Test
    public void testAMissingOrWrongCredentialFailsTheStepWithoutRunningItFail() throws IOException {
        credentials.create("prod-jira", "Atlassian account", Map.of("token", "t"));
        ActionResult missing = credentials.runWith(slackUsing("gone"), new TaskExecution(), () -> {
            throw new AssertionError("The step should not run.");
        });
        Assert.assertTrue(missing.getErrorMsg(), missing.getErrorMsg().contains("\"gone\" does not exist"));
        ActionResult wrongKind = credentials.runWith(slackUsing("prod-jira"), new TaskExecution(), () -> {
            throw new AssertionError("The step should not run.");
        });
        Assert.assertTrue(wrongKind.getErrorMsg(), wrongKind.getErrorMsg().contains("is of the kind Atlassian account"));
    }

    @Test
    public void testAnEmptyFieldKeepsItsValueWhenUpdatingSuccess() throws IOException {
        credentials.create("prod-jira", "Atlassian account", Map.of("email", "old@example.com", "token", "kept"));
        credentials.update("prod-jira", Map.of("email", "new@example.com", "token", ""));
        JiraAction jira = new JiraAction();
        jira.setCredential("prod-jira");
        credentials.runWith(jira, new TaskExecution(), () -> {
            Assert.assertEquals("new@example.com", jira.getEmail());
            Assert.assertEquals("kept", jira.getToken());
            return new ActionResult();
        });
    }

    @Test
    public void testWhatDoesNotBelongInACredentialIsRefusedFail() {
        Assert.assertThrows(CredentialProblemException.class, () -> credentials.create("has space", "SlackAction", Map.of("token", "t")));
        Assert.assertThrows(CredentialProblemException.class, () -> credentials.create("x", "PrintAction", Map.of("message", "t")));
        Assert.assertThrows(CredentialProblemException.class, () -> credentials.create("x", "SlackAction", Map.of("channel", "t")));
        credentials.create("twice", "SlackAction", Map.of("token", "t"));
        Assert.assertThrows(CredentialProblemException.class, () -> credentials.create("twice", "SlackAction", Map.of("token", "t")));
    }

    @Test
    public void testASecretWrittenInAStepBecomesACredentialAndIsNotCopiedSuccess() {
        SlackAction first = new SlackAction();
        first.setToken("xoxb-inline");
        SlackAction second = new SlackAction();
        second.setToken("xoxb-inline");
        SlackAction other = new SlackAction();
        other.setToken("xoxb-other");
        credentials.adoptInlineSecrets(List.of(first, second, other), false);
        Assert.assertEquals("slack-1", first.getCredential());
        Assert.assertNull(first.getToken());
        Assert.assertEquals("slack-1", second.getCredential());
        Assert.assertEquals("slack-2", other.getCredential());
        Assert.assertEquals(2, credentialDao.count());
    }

    @Test
    public void testAnAgentCannotSendASecretFail() {
        SlackAction slack = new SlackAction();
        slack.setToken("xoxb-inline");
        CredentialProblemException refused = Assert.assertThrows(CredentialProblemException.class, () -> credentials.adoptInlineSecrets(List.<Action>of(slack), true));
        Assert.assertTrue(refused.getMessage(), refused.getMessage().contains("list_credentials"));
        Assert.assertEquals(0, credentialDao.count());
    }

    @Test
    public void testASecretHeaderMovesToACredentialAndOtherHeadersStaySuccess() throws IOException {
        HttpHeader secret = new HttpHeader();
        secret.setHeaderName("Authorization");
        secret.setHeaderValue("Bearer inline");
        HttpHeader plain = new HttpHeader();
        plain.setHeaderName("Accept");
        plain.setHeaderValue("text/plain");
        HttpAction http = new HttpAction();
        http.setHeaders(new ArrayList<>(List.of(secret, plain)));
        credentials.adoptInlineSecrets(List.of(http), false);
        Assert.assertEquals(1, http.getHeaders().size());
        Assert.assertEquals("Accept", http.getHeaders().get(0).getHeaderName());
        credentials.runWith(http, new TaskExecution(), () -> {
            Assert.assertEquals("Authorization", http.getAuthHeaderName());
            Assert.assertEquals("Bearer inline", http.getAuthHeaderValue());
            return new ActionResult();
        });
    }

    @Test
    public void testEveryKindOfStepThatSignsInDeclaresItsSecretsSuccess() {
        Assert.assertEquals(Boolean.TRUE, CredentialService.fieldsOf(new SSHAction()).get("password"));
        Assert.assertEquals(Boolean.FALSE, CredentialService.fieldsOf(new SSHAction()).get("username"));
        Assert.assertEquals(Boolean.TRUE, CredentialService.fieldsOf(new Webhook()).get("token"));
        Assert.assertEquals(Boolean.TRUE, CredentialService.fieldsOf(new HttpAction()).get("authHeaderValue"));
        StepTypes.INSTANCE.getClasses().values().forEach(stepClass -> CredentialService.fieldsOf(StepTypes.INSTANCE.newStep(stepClass.getSimpleName())).keySet().forEach(field -> {
            Assert.assertTrue(stepClass.getSimpleName() + " " + field + " cannot be filled in", new BeanWrapperImpl(stepClass).isWritableProperty(field));
            Assert.assertTrue(stepClass.getSimpleName() + " " + field + " is still stored in the step", CredentialService.isOnlyInCredentials(stepClass, field));
        }));
    }

    @Test
    public void testStepsOfOneServiceShareACredentialSuccess() throws IOException {
        credentials.create("office", "Mail account", Map.of("username", "ops@example.com", "password", "the-real-password"));
        Assert.assertEquals(List.of("EmailAction", "ReadEmailAction"), credentials.list().get(0).steps().stream().sorted().toList());
        for (Action step : List.of(new EmailAction(), new ReadEmailAction())) {
            step.setCredential("office");
            ActionResult result = credentials.runWith(step, new TaskExecution(), () -> {
                Assert.assertEquals("the-real-password", new BeanWrapperImpl(step).getPropertyValue("password"));
                return new ActionResult();
            });
            Assert.assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        }
    }

    @Test
    public void testACredentialOfAnotherServiceIsRefusedFailure() throws IOException {
        credentials.create("office", "Mail account", Map.of("username", "ops@example.com", "password", "the-real-password"));
        SecureCopyAction copy = new SecureCopyAction();
        copy.setCredential("office");
        ActionResult result = credentials.runWith(copy, new TaskExecution(), () -> {
            throw new AssertionError("The step should not run");
        });
        Assert.assertEquals(StatusCode.FAILURE, result.getStatusCode());
        Assert.assertTrue(result.getErrorMsg(), result.getErrorMsg().contains("Mail account") && result.getErrorMsg().contains("Server login"));
    }

    @Test
    public void testStepsThatShareACredentialTakeTheSameFieldsSuccess() {
        Assert.assertEquals(CredentialService.fieldsOf(new EmailAction()), CredentialService.fieldsOf(new ReadEmailAction()));
        Assert.assertEquals(CredentialService.fieldsOf(new SSHAction()), CredentialService.fieldsOf(new SecureCopyAction()));
        Map<String, Map<String, Boolean>> fieldsOfType = new java.util.HashMap<>();
        StepTypes.INSTANCE.all().stream().filter(step -> !CredentialService.fieldsOf(step).isEmpty()).forEach(step -> {
            Map<String, Boolean> earlier = fieldsOfType.putIfAbsent(step.getCredentialType(), CredentialService.fieldsOf(step));
            if (earlier != null) {
                Assert.assertEquals(step.getStepTypeName(), earlier, CredentialService.fieldsOf(step));
            }
        });
    }

    @Test
    public void testGoogleAndAtlassianStepsShareTheirAccountsSuccess() {
        Assert.assertEquals(new com.dopplertask.dopplertask.domain.action.integration.storage.GoogleSheetsAction().getCredentialType(), new com.dopplertask.dopplertask.domain.action.integration.storage.GoogleDriveAction().getCredentialType());
        Assert.assertEquals(new com.dopplertask.dopplertask.domain.action.integration.storage.GoogleSheetsAction().getCredentialType(), new com.dopplertask.dopplertask.domain.action.integration.storage.GoogleCalendarAction().getCredentialType());
        Assert.assertEquals(new JiraAction().getCredentialType(), new com.dopplertask.dopplertask.domain.action.integration.storage.ConfluenceAction().getCredentialType());
    }

    @Test
    public void testASecretFoundInAStepGetsANameThatCanBeUsedSuccess() {
        String name = credentials.findOrCreate("Server login", Map.of("username", "deploy", "password", "the-real-password"));
        Assert.assertEquals("server-login-1", name);
    }
}
