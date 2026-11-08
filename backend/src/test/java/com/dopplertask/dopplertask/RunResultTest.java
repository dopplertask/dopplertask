package com.dopplertask.dopplertask;

import com.jayway.jsonpath.JsonPath;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;
import com.dopplertask.dopplertask.service.ExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RunResultTest {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jms.config.JmsListenerEndpointRegistry listeners;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExecutionService executionService;

    @Test
    public void testSuccessfulRunReportsFinishedSuccess() throws Exception {
        save("run_ok", "[]", false, "");
        run("run_ok", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.executionId").isNumber())
                .andExpect(jsonPath("$.logs", hasItem("hi")))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    public void testExecutionListsEveryStepWithItsResultSuccess() throws Exception {
        save("run_steps", "[]", false, "boom");
        String runResponse = run("run_steps", "{}").andReturn().getResponse().getContentAsString();
        int executionId = JsonPath.read(runResponse, "$.executionId");

        this.mockMvc.perform(get("/execution/" + executionId))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.startDate").exists())
                .andExpect(jsonPath("$.steps[0].portId").value("run_steps_s"))
                .andExpect(jsonPath("$.steps[0].actionType").value("StartAction"))
                .andExpect(jsonPath("$.steps[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.steps[1].portId").value("run_steps_p"))
                .andExpect(jsonPath("$.steps[1].output").value("hi"))
                .andExpect(jsonPath("$.steps[1].status").value("FAILURE"))
                .andExpect(jsonPath("$.steps[1].error", containsString("boom")));
    }

    // A slow task must not make every other task wait.
    @Test
    public void testRunsDoNotWaitForEachOtherSuccess() {
        org.springframework.jms.listener.DefaultMessageListenerContainer container = (org.springframework.jms.listener.DefaultMessageListenerContainer) listeners.getListenerContainers().iterator().next();
        org.junit.Assert.assertEquals(50, container.getConcurrentConsumers());
    }

    // Through the queue, as a run with parameters goes when it is started from the editor.
    @Test
    public void testAScheduledRunWithParametersFinishesSuccess() throws Exception {
        String name = "run_scheduled_parameters";
        this.mockMvc.perform(post("/task").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\",\"active\":false,\"parameters\":[{\"name\":\"who\"}],\"actions\":["
                + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"" + name + "_s\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"PrintAction\",\"message\":\"hello $parameters.get('who')\",\"ports\":[{\"externalId\":\"" + name + "_i\",\"portType\":\"INPUT\"},{\"externalId\":\"" + name + "_o\",\"portType\":\"OUTPUT\"}]}],"
                + "\"connections\":[{\"source\":{\"externalId\":\"" + name + "_s\"},\"target\":{\"externalId\":\"" + name + "_i\"}}]}")).andExpect(status().isOk());
        String answer = this.mockMvc.perform(post("/schedule/task").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{\"who\":\"Ada\"}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Object executionId = JsonPath.read(answer, "$.id");
        String status = "";
        for (int attempt = 0; attempt < 100 && !status.equals("FINISHED") && !status.equals("FAILED"); attempt++) {
            Thread.sleep(100);
            status = JsonPath.read(this.mockMvc.perform(get("/execution/" + executionId)).andReturn().getResponse().getContentAsString(), "$.status");
        }
        org.junit.Assert.assertEquals("FINISHED", status);
    }

    @Test
    public void testFailedActionReportsReasonFail() throws Exception {
        save("run_failon", "[]", false, "boom");
        run("run_failon", "{}").andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error", containsString("boom")))
                .andExpect(jsonPath("$.output[?(@ == 'Failed on: boom')]", hasSize(1)));
    }

    @Test
    public void testMissingRequiredParameterReportsItsNameFail() throws Exception {
        save("run_param", "[{\"name\":\"city\",\"required\":true}]", false, "");
        run("run_param", "{}").andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error", containsString("city")));
    }

    @Test
    public void testUnknownTaskReportsItsNameFail() throws Exception {
        run("run_does_not_exist", "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("run_does_not_exist")));
    }

    @Test
    public void testWrongWebhookSuffixHidesDetailsFail() throws Exception {
        save("run_hook", "[]", true, "");
        assertWebhookFailsWithoutDetails("/webhook/run_hook/Webhook/wrong");
    }

    @Test
    public void testInactiveWebhookHidesDetailsFail() throws Exception {
        save("run_inactive", "[]", false, "");
        assertWebhookFailsWithoutDetails("/webhook/run_inactive/Webhook/right");
    }

    private void assertWebhookFailsWithoutDetails(String url) throws Exception {
        this.mockMvc.perform(post(url))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.executionId").isNumber())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.logs").doesNotExist());
    }

    private void save(String name, String parameters, boolean active, String failOn) throws Exception {
        String createJson = "{\"name\":\"" + name + "\",\"active\":" + active + ",\"parameters\":" + parameters + ",\"actions\":["
                + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"" + name + "_s\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"Webhook\",\"method\":\"POST\",\"path\":\"\",\"triggerSuffix\":\"right\",\"authentication\":\"none\",\"ports\":[]},"
                + "{\"@type\":\"PrintAction\",\"message\":\"hi\",\"failOn\":\"" + failOn + "\",\"ports\":[{\"externalId\":\"" + name + "_p\",\"portType\":\"INPUT\"}]}],"
                + "\"connections\":[{\"source\":{\"externalId\":\"" + name + "_s\"},\"target\":{\"externalId\":\"" + name + "_p\"}}]}";
        this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isOk());
    }

    @Test
    public void testTurningOnABrokenScheduleExplainsWhyFail() throws Exception {
        String createJson = "{\"name\":\"run_bad_schedule\",\"active\":true,\"parameters\":[],\"actions\":["
                + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"bad_s\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"ScheduleTrigger\",\"actionName\":\"Mornings\",\"rule\":\"CRON\",\"cronExpression\":\"every morning\",\"ports\":[{\"externalId\":\"bad_t\",\"portType\":\"OUTPUT\"}]}],"
                + "\"connections\":[]}";
        this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Mornings cannot start the task. The cron line \"every morning\" needs 5 fields")));
        this.mockMvc.perform(post("/task").content(createJson.replace("\"active\":true", "\"active\":false"))).andExpect(status().isOk());
        // Turning the saved version on is refused too.
        this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isBadRequest());
    }

    private ResultActions run(String name, String parameters) throws Exception {
        return this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskName\":\"" + name + "\",\"parameters\":" + parameters + "}"));
    }

    @Test
    public void testActionTestSeesTheValuesItIsGivenSuccess() throws Exception {
        this.mockMvc.perform(post("/task/action/test").contentType(MediaType.APPLICATION_JSON).content(
                "{\"action\": {\"@type\": \"PrintAction\", \"message\": \"$parameters.get('name') after $lastLog.output\"}, \"parameters\": {\"name\": \"Ada\"}, \"lastOutput\": \"build\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("Ada after build"));
    }

    private void saveResumable(String name, String marker) throws Exception {
        String createJson = "{\"name\":\"" + name + "\",\"active\":false,\"parameters\":[],\"actions\":["
                + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"" + name + "_s\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"ExecuteCommandAction\",\"command\":\"echo once >> " + marker + "; printf first\",\"ports\":[{\"externalId\":\"" + name + "_a\",\"portType\":\"INPUT\"},{\"externalId\":\"" + name + "_a_out\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"ExecuteCommandAction\",\"command\":\"echo 'from-$lastLog.output'; $parameters.get('gate')\",\"ports\":[{\"externalId\":\"" + name + "_b\",\"portType\":\"INPUT\"},{\"externalId\":\"" + name + "_b_out\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"PrintAction\",\"message\":\"$parameters.get('who')\",\"ports\":[{\"externalId\":\"" + name + "_c\",\"portType\":\"INPUT\"}]}],"
                + "\"connections\":[{\"source\":{\"externalId\":\"" + name + "_s\"},\"target\":{\"externalId\":\"" + name + "_a\"}},"
                + "{\"source\":{\"externalId\":\"" + name + "_a_out\"},\"target\":{\"externalId\":\"" + name + "_b\"}},"
                + "{\"source\":{\"externalId\":\"" + name + "_b_out\"},\"target\":{\"externalId\":\"" + name + "_c\"}}]}";
        this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isOk());
    }

    private ResultActions resume(String name, String parameters, int executionId) throws Exception {
        return this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskName\":\"" + name + "\",\"parameters\":" + parameters + ",\"resumeFromExecutionId\":" + executionId + "}"));
    }

    @Test
    public void testResumeContinuesAtTheFailedStepWithItsValuesSuccess() throws Exception {
        Path marker = Files.createTempFile("resume", ".txt");
        saveResumable("run_resume", marker.toString());

        String failed = run("run_resume", "{\"gate\":\"exit 3\",\"who\":\"Ada\"}").andExpect(jsonPath("$.status").value("FAILED")).andReturn().getResponse().getContentAsString();
        int failedId = JsonPath.read(failed, "$.executionId");

        String resumed = resume("run_resume", "{\"gate\":\"true\"}", failedId).andExpect(jsonPath("$.status").value("FINISHED")).andReturn().getResponse().getContentAsString();
        int resumedId = JsonPath.read(resumed, "$.executionId");

        Assert.assertEquals("The step before the failure must not run twice", 1, Files.readAllLines(marker).size());
        this.mockMvc.perform(get("/execution/" + resumedId))
                .andExpect(jsonPath("$.steps", hasSize(4)))
                .andExpect(jsonPath("$.steps[1].output", containsString("first")))
                .andExpect(jsonPath("$.steps[2].output", containsString("from-first")))
                .andExpect(jsonPath("$.steps[2].status").value("SUCCESS"))
                .andExpect(jsonPath("$.steps[3].output").value("Ada"));
    }

    @Test
    public void testResumeContinuesAtTheStepWhereAPersonStoppedTheRunSuccess() throws Exception {
        Path marker = Files.createTempFile("resume", ".txt");
        saveResumable("run_stopped", marker.toString());
        String ended = run("run_stopped", "{\"gate\":\"exit 3\",\"who\":\"Ada\"}").andReturn().getResponse().getContentAsString();
        int stoppedId = JsonPath.read(ended, "$.executionId");
        executionService.abortRun(stoppedId, "This run was stopped.");

        resume("run_stopped", "{\"gate\":\"true\"}", stoppedId).andExpect(jsonPath("$.status").value("FINISHED"));

        Assert.assertEquals("The step before the stop must not run twice", 1, Files.readAllLines(marker).size());
    }

    @Test
    public void testResumeExplainsARunThatDidNotFailSuccess() throws Exception {
        save("run_no_resume", "[]", false, "");
        String finished = run("run_no_resume", "{}").andReturn().getResponse().getContentAsString();
        int finishedId = JsonPath.read(finished, "$.executionId");

        resume("run_no_resume", "{}", finishedId)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error", containsString("did not fail or stop at a step")));
    }

    @Test
    public void testResumeExplainsARunOfAnotherTaskSuccess() throws Exception {
        save("run_other_a", "[]", false, "boom");
        save("run_other_b", "[]", false, "");
        String failed = run("run_other_a", "{}").andReturn().getResponse().getContentAsString();
        int failedId = JsonPath.read(failed, "$.executionId");

        resume("run_other_b", "{}", failedId)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error", containsString("is not a run of run_other_b")));
    }

    @Test
    public void testExecutionsCanListTheLatestRunsOfOneTaskSuccess() throws Exception {
        save("run_list_a", "[]", false, "");
        save("run_list_b", "[]", false, "");
        run("run_list_a", "{}");
        run("run_list_b", "{}");
        String newest = run("run_list_a", "{}").andReturn().getResponse().getContentAsString();
        int newestId = JsonPath.read(newest, "$.executionId");

        this.mockMvc.perform(get("/executions").param("taskName", "run_list_a"))
                .andExpect(jsonPath("$.executions", hasSize(2)))
                .andExpect(jsonPath("$.executions[0].executionId").value(String.valueOf(newestId)));
        this.mockMvc.perform(get("/executions").param("taskName", "run_list_a").param("limit", "1"))
                .andExpect(jsonPath("$.executions", hasSize(1)));
    }

    private static final String PRINT_ACTION = "{\"@type\":\"PrintAction\",\"message\":\"hi\"}";

    @Test
    public void testAnotherSiteCannotChangeOrRunAnythingFail() throws Exception {
        for (String origin : List.of("http://evil.example", "null")) {
            this.mockMvc.perform(post("/task/action").contentType(MediaType.APPLICATION_JSON).header("Origin", origin).content(PRINT_ACTION))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message", containsString("another site")));
        }
    }

    @Test
    public void testTheAppItselfAndOtherClientsAreStillServedSuccess() throws Exception {
        this.mockMvc.perform(post("/task/action").contentType(MediaType.APPLICATION_JSON).header("Origin", "http://localhost:8090").header("Host", "localhost:8090").content(PRINT_ACTION))
                .andExpect(status().isOk());
        this.mockMvc.perform(post("/task/action").contentType(MediaType.APPLICATION_JSON).content(PRINT_ACTION))
                .andExpect(status().isOk());
        this.mockMvc.perform(get("/task/grouped").header("Origin", "http://evil.example"))
                .andExpect(status().isOk());
    }

    @Test
    public void testAWebhookCanStillBeCalledFromAnotherSiteSuccess() throws Exception {
        // It reaches the webhook handler, which reports the missing task, rather than being refused here.
        this.mockMvc.perform(post("/webhook/no_such_task/Webhook/x/").header("Origin", "http://evil.example"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testExecutionShowsFinishedStepsWhileARunIsGoingSuccess() throws Exception {
        String name = "run_live";
        this.mockMvc.perform(post("/task").content("{\"name\":\"" + name + "\",\"active\":false,\"parameters\":[],\"actions\":["
                + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"" + name + "_s\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"ExecuteCommandAction\",\"command\":\"sleep 1\",\"ports\":[{\"externalId\":\"" + name + "_c\",\"portType\":\"INPUT\"}]}],"
                + "\"connections\":[{\"source\":{\"externalId\":\"" + name + "_s\"},\"target\":{\"externalId\":\"" + name + "_c\"}}]}")).andExpect(status().isOk());
        String scheduled = this.mockMvc.perform(post("/schedule/task").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andReturn().getResponse().getContentAsString();
        String executionId = JsonPath.read(scheduled, "$.id");

        boolean sawStepWhileRunning = false;
        String status = "";
        long giveUpAt = System.currentTimeMillis() + 10000;
        while (!status.equals("FINISHED") && System.currentTimeMillis() < giveUpAt) {
            String execution = this.mockMvc.perform(get("/execution/" + executionId)).andReturn().getResponse().getContentAsString();
            status = JsonPath.read(execution, "$.status");
            if (status.equals("STARTED") && execution.contains("StartAction")) {
                sawStepWhileRunning = true;
            }
            Thread.sleep(50);
        }

        Assert.assertTrue("The finished step must show while the run is going", sawStepWhileRunning);
        this.mockMvc.perform(get("/execution/" + executionId)).andExpect(jsonPath("$.steps", hasSize(2)));
    }
}
