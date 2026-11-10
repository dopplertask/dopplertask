package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.dao.CredentialDao;
import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.ExecutionParameter;
import com.dopplertask.dopplertask.domain.StatusCode;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.common.PrintAction;
import com.dopplertask.dopplertask.domain.action.common.ScriptLanguage;
import com.dopplertask.dopplertask.domain.action.connection.HttpAction;
import com.dopplertask.dopplertask.domain.action.io.ReadFileAction;
import com.dopplertask.dopplertask.domain.action.io.WriteFileAction;
import com.dopplertask.dopplertask.service.CredentialCipher;
import com.dopplertask.dopplertask.service.CredentialProblemException;
import com.dopplertask.dopplertask.service.CredentialService;
import com.dopplertask.dopplertask.service.CustomerScope;
import com.dopplertask.dopplertask.service.StepRules;
import com.dopplertask.dopplertask.service.StepRunner;
import com.dopplertask.dopplertask.service.TaskService;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CustomerHooksTest {
    private final VariableExtractorUtil extractor = new VariableExtractorUtil(Application.lockedTemplateEngine(-1));
    private final TaskExecution execution = new TaskExecution();

    @After
    public void backToAnInstall() {
        StepRules.use(new StepRules() { });
        StepRunner.use(null);
    }

    private static StepRules restrictedScripts() {
        return new StepRules() {
            @Override
            public boolean scriptsAreRestricted() {
                return true;
            }
        };
    }

    @Test
    public void testTemplateCannotReachJavaSuccess() throws IOException {
        String template = "#set($system = $parameters.getClass().forName(\"java.lang.System\"))[$system]";
        Assert.assertEquals("[$system]", extractor.extract(template, execution, ScriptLanguage.VELOCITY));
    }

    @Test
    public void testTemplateCannotIncludeAFile() {
        Assert.assertThrows(Exception.class, () -> extractor.extract("#include(\"/etc/hosts\")", execution, ScriptLanguage.VELOCITY));
    }

    @Test
    public void testTemplateStillFillsInValuesSuccess() throws IOException {
        execution.getParameters().put("who", new ExecutionParameter("who", "Ada".getBytes(StandardCharsets.UTF_8), false));
        Assert.assertEquals("Hello Ada, 5", extractor.extract("Hello $parameters.get(\"who\"), $mathTool.add(2, 3)", execution, ScriptLanguage.VELOCITY));
    }

    @Test
    public void testInstallScriptsKeepFullAccessSuccess() throws IOException {
        Assert.assertEquals("3", extractor.extract("Java.type('java.lang.Math').max(2, 3)", execution, ScriptLanguage.JAVASCRIPT));
    }

    @Test
    public void testRestrictedScriptCannotReachJava() {
        StepRules.use(restrictedScripts());
        Assert.assertThrows(Exception.class, () -> extractor.extract("Java.type('java.lang.System')", execution, ScriptLanguage.JAVASCRIPT));
    }

    @Test
    public void testRestrictedScriptReadsPlainValuesSuccess() throws IOException {
        StepRules.use(restrictedScripts());
        execution.getParameters().put("who", new ExecutionParameter("who", "Ada".getBytes(StandardCharsets.UTF_8), false));
        Assert.assertEquals("ADA", extractor.extract("parameters.get('who').toUpperCase()", execution, ScriptLanguage.JAVASCRIPT));
    }

    @Test
    public void testRestrictedScriptCannotRunForever() {
        StepRules.use(restrictedScripts());
        Assert.assertThrows(Exception.class, () -> extractor.extract("while (true) {}", execution, ScriptLanguage.JAVASCRIPT));
    }

    @Test
    public void testRefusedStepDoesNotRun() throws IOException {
        StepRules.use(new StepRules() {
            @Override
            public String refusalOf(Action step) {
                return "Not here.";
            }
        });
        CredentialService credentials = new CredentialService(Mockito.mock(CredentialDao.class), Mockito.mock(CredentialCipher.class));
        ActionResult result = credentials.runWith(new PrintAction(), execution, () -> {
            throw new AssertionError("The step ran.");
        });
        Assert.assertEquals(StatusCode.FAILURE, result.getStatusCode());
        Assert.assertEquals("Not here.", result.getErrorMsg());
    }

    @Test
    public void testEverythingIsOfferedOnAnInstallSuccess() {
        Assert.assertTrue(StepRules.active().offers(PrintAction.class));
    }

    @Test
    public void testBase64DecodesToTextSuccess() throws IOException {
        Assert.assertEquals("Ada", extractor.extract("$base64Helper.decode(\"QWRh\")", execution, ScriptLanguage.VELOCITY));
    }

    @Test
    public void testRefusedAddressIsNotCalled() {
        StepRules.use(new StepRules() {
            @Override
            public String refusalOfAddress(URI address) {
                return "Not this one: " + address.getHost();
            }
        });
        ActionResult result = new HttpAction().send("http://192.0.2.1/", "", Map.of(), null, java.time.Duration.ofSeconds(5));
        Assert.assertEquals(StatusCode.FAILURE, result.getStatusCode());
        Assert.assertEquals("Not this one: 192.0.2.1", result.getErrorMsg());
    }

    @Test
    public void testRefusedFileIsNeitherReadNorWritten() throws IOException {
        Path file = Files.createTempFile("refused", ".txt");
        Files.writeString(file, "kept");
        StepRules.use(new StepRules() {
            @Override
            public String refusalOfFile(Path asked) {
                return "Not this file: " + asked.getFileName();
            }
        });
        ReadFileAction read = new ReadFileAction();
        read.setFilename(file.toString());
        WriteFileAction write = new WriteFileAction();
        write.setFilename(file.toString());
        write.setContents("replaced");
        for (Action step : List.of(read, write)) {
            ActionResult result = step.run(Mockito.mock(TaskService.class), execution, extractor, null);
            Assert.assertEquals(StatusCode.FAILURE, result.getStatusCode());
            Assert.assertEquals("Not this file: " + file.getFileName(), result.getErrorMsg());
        }
        Assert.assertTrue(execution.getParameters().isEmpty());
        Assert.assertEquals("kept", Files.readString(file));
        Files.delete(file);
    }

    @Test
    public void testFieldsGoToTheStepRunnerSuccess() throws IOException {
        StepRunner.use(new StepRunner() {
            @Override
            public boolean runsElsewhere(Action step) {
                return true;
            }

            @Override
            public ActionResult runStep(Action step, Map<String, String> credentialValues, TaskExecution run) {
                return new ActionResult(StatusCode.SUCCESS, "ran elsewhere", "");
            }

            @Override
            public String evaluate(String field, TaskExecution run, ActionResult result, ScriptLanguage language) {
                return "evaluated elsewhere";
            }
        });
        Assert.assertEquals("evaluated elsewhere", extractor.extract("$anything", execution, ScriptLanguage.VELOCITY));
        Assert.assertEquals("plain words", extractor.extract("plain words", execution, ScriptLanguage.VELOCITY));
        CredentialService credentials = new CredentialService(Mockito.mock(CredentialDao.class), Mockito.mock(CredentialCipher.class));
        ActionResult result = credentials.runWith(new PrintAction(), execution, () -> {
            throw new AssertionError("The step ran here.");
        });
        Assert.assertEquals("ran elsewhere", result.getOutput());
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<CustomerScope> scopeOf(String customer) {
        ObjectProvider<CustomerScope> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(new CustomerScope() {
            @Override
            public String current() {
                return customer;
            }
        });
        return provider;
    }

    @Test
    public void testEachCustomerHasItsOwnKeySuccess() throws IOException {
        String keyFile = Files.createTempDirectory("keys").resolve("key").toString();
        CredentialCipher acme = new CredentialCipher("", keyFile);
        acme.setCustomers(scopeOf("ACME"));
        CredentialCipher beta = new CredentialCipher("", keyFile);
        beta.setCustomers(scopeOf("BETA"));
        String stored = acme.encrypt("secret");
        Assert.assertEquals("secret", acme.decrypt(stored));
        Assert.assertThrows(CredentialProblemException.class, () -> beta.decrypt(stored));
    }

    @Test
    public void testAnInstallKeepsItsKeySuccess() throws IOException {
        String keyFile = Files.createTempDirectory("keys").resolve("key").toString();
        CredentialCipher before = new CredentialCipher("", keyFile);
        String stored = before.encrypt("secret");
        CredentialCipher after = new CredentialCipher("", keyFile);
        after.setCustomers(scopeOf(""));
        Assert.assertEquals("secret", after.decrypt(stored));
    }
}
