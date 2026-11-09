package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.dao.TaskExecutionDao;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import com.dopplertask.dopplertask.domain.action.connector.Connectors;
import com.dopplertask.dopplertask.service.RunHousekeeping;
import com.dopplertask.dopplertask.service.TaskRequest;
import com.dopplertask.dopplertask.service.TaskService;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A step that waits, such as for an approval, stops its run until its one-time address is called. */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WaitingRunTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskExecutionDao runs;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RunHousekeeping housekeeping;

    /** What a person's approval brings to the address of a waiting run. */
    private static final String APPROVED = "{\"body\":{},\"received\":{\"headers\":{},\"query\":{\"action\":\"approve\"},\"text\":\"\"}}";

    private final List<String> asked = new CopyOnWriteArrayList<>();
    private HttpServer service;

    @Before
    public void startService() throws IOException {
        service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.createContext("/", exchange -> {
            asked.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{\"ok\": true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
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
    public void testAnApprovalContinuesTheRunOnceAtTheWaitingStepSuccess() throws Exception {
        String name = "waiting_approval";
        String createJson = task(name);
        mockMvc.perform(post("/task").content(createJson)).andExpect(status().isOk());

        String waiting = mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andExpect(jsonPath("$.status").value("WAITING")).andReturn().getResponse().getContentAsString();
        int waitingId = JsonPath.read(waiting, "$.executionId");
        String link = URI.create(JsonPath.read(asked.get(0), "$.link")).getPath();
        Assert.assertEquals("Ship it?", JsonPath.read(asked.get(0), "$.text"));

        // Opening the link, as a mail scanner does, only shows the buttons.
        mockMvc.perform(get(link)).andExpect(status().isOk()).andExpect(content().string(containsString("action=approve")));
        mockMvc.perform(get("/execution/" + waitingId)).andExpect(jsonPath("$.status").value("WAITING"));
        // The person's sign-in in the browser stays out of the run.
        mockMvc.perform(post(link + "?action=approve").header("Cookie", "SESSION=editor")).andExpect(status().isOk());
        mockMvc.perform(post(link + "?action=approve")).andExpect(status().isNotFound());

        String resumed = finishedRunAfter(name, waitingId);
        Assert.assertTrue(resumed, resumed.contains("approved true, signed in false"));
        mockMvc.perform(get("/execution/" + waitingId)).andExpect(jsonPath("$.status").value("FINISHED"));
        Assert.assertEquals("The service was asked once, and the resumed step only read the answer.", 1, asked.size());

        // A waiting run is not resumed without its answer.
        String second = mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andReturn().getResponse().getContentAsString();
        int secondId = JsonPath.read(second, "$.executionId");
        mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{},\"resumeFromExecutionId\":" + secondId + "}"))
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.error").value("Could not resume. Execution " + secondId + " waits for its answer."));
    }

    @Test
    public void testAnAnswerThatArrivedWhileTheServerStoppedContinuesTheRunAtStartSuccess() throws Exception {
        String name = "waiting_restart";
        mockMvc.perform(post("/task").content(task(name))).andExpect(status().isOk());
        String waiting = mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andReturn().getResponse().getContentAsString();
        int waitingId = JsonPath.read(waiting, "$.executionId");
        // The address was called, and the server stopped before the run continued.
        String waitKey = runs.findById((long) waitingId).orElseThrow().getWaitKey();
        Assert.assertEquals(1, runs.answerWaiting((long) waitingId, waitKey, APPROVED));

        // A queue that survived the restart still holds the continuation, so a start adds no second one.
        TaskExecution kept = new TaskExecution();
        kept.setResumedFrom((long) waitingId);
        runs.save(kept);
        taskService.continueAnsweredRuns();
        Assert.assertEquals(TaskExecutionStatus.WAITING, runs.findById((long) waitingId).orElseThrow().getStatus());
        Assert.assertEquals(List.of(kept.getId()), runs.queuedContinuationIds(waitingId));

        // A start in the order the server uses: the queue no longer holds it, so it ends as interrupted, and the run
        // continues once.
        housekeeping.failInterruptedRuns();
        taskService.continueAnsweredRuns();
        Assert.assertTrue(finishedRunAfter(name, waitingId).contains("approved true"));
        String history = mockMvc.perform(get("/executions").param("taskName", name)).andReturn().getResponse().getContentAsString();
        Assert.assertEquals(history, List.of("FINISHED", "FINISHED"), JsonPath.read(history, "$.executions[*].status"));
    }

    @Test
    public void testARunThatCannotStartLeavesTheAnswerWithTheWaitingRunSuccess() throws Exception {
        String name = "waiting_changed";
        mockMvc.perform(post("/task").content(task(name))).andExpect(status().isOk());
        String waiting = mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andReturn().getResponse().getContentAsString();
        int waitingId = JsonPath.read(waiting, "$.executionId");
        String link = URI.create(JsonPath.read(asked.get(0), "$.link")).getPath();
        // While it waits, the task gets a parameter that a run needs.
        mockMvc.perform(post("/task").content(task(name).replace("\"parameters\":[]", "\"parameters\":[{\"name\":\"team\",\"required\":true}]"))).andExpect(status().isOk());
        mockMvc.perform(post(link + "?action=approve")).andExpect(status().isOk());
        boolean couldNotStart = false;
        for (int tries = 0; tries < 100 && !couldNotStart; tries++) {
            couldNotStart = runs.findByStatusIn(List.of(TaskExecutionStatus.FAILED)).stream().anyMatch(run -> run.getTask() != null && name.equals(run.getTask().getName()));
            Thread.sleep(100);
        }
        Assert.assertTrue("The run that would continue it never ran.", couldNotStart);
        mockMvc.perform(get("/execution/" + waitingId)).andExpect(jsonPath("$.status").value("WAITING"));

        // The answer was given for the values the person saw, so a resume may not bring others.
        mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskName\":\"" + name + "\",\"parameters\":{\"team\":\"ops\"},\"resumeFromExecutionId\":" + waitingId + "}"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("Could not resume. Execution " + waitingId + " goes on after an answer, so it continues with the values it had. Leave the parameters out or give the same values."));

        // Once the task can run with those values again, the run continues with the answer it got.
        mockMvc.perform(post("/task").content(task(name).replace("\"parameters\":[]", "\"parameters\":[{\"name\":\"team\",\"required\":false}]"))).andExpect(status().isOk());
        String resumed = mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskName\":\"" + name + "\",\"parameters\":{},\"resumeFromExecutionId\":" + waitingId + "}"))
                .andExpect(jsonPath("$.status").value("FINISHED")).andReturn().getResponse().getContentAsString();
        Assert.assertTrue(resumed, resumed.contains("approved true"));
    }

    @Test
    public void testARunThatFailedAfterAnApprovalKeepsTheApprovedValuesFail() throws Exception {
        String name = "waiting_then_failed";
        String task = task(name).replace("\"parameters\":[]", "\"parameters\":[{\"name\":\"stop\",\"required\":false}]")
                .replace("\"message\":\"approved", "\"failOn\":\"#if($parameters.get('stop') == 'yes')stopped#end\",\"message\":\"approved");
        mockMvc.perform(post("/task").content(task)).andExpect(status().isOk());
        mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{\"stop\":\"yes\"}}"))
                .andExpect(jsonPath("$.status").value("WAITING"));
        String link = URI.create(JsonPath.read(asked.get(0), "$.link")).getPath();
        mockMvc.perform(post(link + "?action=approve")).andExpect(status().isOk());
        int failedId = -1;
        for (int tries = 0; tries < 100 && failedId < 0; tries++) {
            String history = mockMvc.perform(get("/executions").param("taskName", name)).andReturn().getResponse().getContentAsString();
            List<String> failed = JsonPath.read(history, "$.executions[?(@.status == 'FAILED')].executionId");
            failedId = failed.isEmpty() ? -1 : Integer.parseInt(failed.get(0));
            Thread.sleep(100);
        }
        Assert.assertTrue("The step after the approval never failed.", failedId > 0);
        // The step after the approval failed. Its run goes on after the answer, so a resume may not bring other values.
        mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskName\":\"" + name + "\",\"parameters\":{\"stop\":\"no\"},\"resumeFromExecutionId\":" + failedId + "}"))
                .andExpect(jsonPath("$.error").value("Could not resume. Execution " + failedId + " goes on after an answer, so it continues with the values it had. Leave the parameters out or give the same values."));
        // The same values, as the editor sends them again with the run's files, are no change: the run continues at the
        // step that failed.
        String again = mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskName\":\"" + name + "\",\"parameters\":{\"stop\":\"yes\",\"invoice.pdf\":\"\\ufffd\"},\"resumeFromExecutionId\":" + failedId + "}"))
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(again, again.contains("stopped"));
        // What a resume brings beyond them is not used: the run has the values the person saw.
        int againId = JsonPath.read(again, "$.executionId");
        mockMvc.perform(get("/execution/" + againId)).andExpect(jsonPath("$.parameters.stop").value("yes")).andExpect(jsonPath("$.parameters['invoice.pdf']").doesNotExist());
    }

    @Test
    public void testAStoppedWaitingRunEndsWithItsAddressSuccess() throws Exception {
        String name = "waiting_stopped";
        mockMvc.perform(post("/task").content(task(name))).andExpect(status().isOk());
        String waiting = mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andReturn().getResponse().getContentAsString();
        int waitingId = JsonPath.read(waiting, "$.executionId");
        String link = URI.create(JsonPath.read(asked.get(0), "$.link")).getPath();

        mockMvc.perform(post("/execution/" + waitingId + "/stop")).andExpect(status().isAccepted());
        mockMvc.perform(get("/execution/" + waitingId)).andExpect(jsonPath("$.status").value("ABORTED"));
        mockMvc.perform(post(link + "?action=approve")).andExpect(status().isNotFound());
    }

    @Test
    public void testStoppingTheRunThatWouldContinueAnAnswerEndsTheAnswerSuccess() throws Exception {
        String name = "waiting_continuation_stopped";
        mockMvc.perform(post("/task").content(task(name))).andExpect(status().isOk());
        String waiting = mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andReturn().getResponse().getContentAsString();
        long waitingId = ((Number) JsonPath.read(waiting, "$.executionId")).longValue();
        // Answered, and its continuation still queued, as when every place to run is busy.
        runs.answerWaiting(waitingId, runs.findById(waitingId).orElseThrow().getWaitKey(), APPROVED);
        TaskExecution continuation = new TaskExecution();
        continuation.setResumedFrom(waitingId);
        runs.save(continuation);
        TaskExecution other = new TaskExecution();
        other.setResumedFrom(waitingId);
        runs.save(other);

        housekeeping.stop(continuation.getId(), "Stopped in the test.");
        Assert.assertEquals(TaskExecutionStatus.ABORTED, runs.findById(waitingId).orElseThrow().getStatus());
        Assert.assertEquals(TaskExecutionStatus.ABORTED, runs.findById(other.getId()).orElseThrow().getStatus());
        // So nothing continues it when the server starts again.
        Assert.assertTrue(runs.answeredRunsToContinue().stream().noneMatch(run -> run.getId() == waitingId));
    }

    @Test
    public void testStoppingAnAnsweredWaitingRunEndsItsQueuedContinuationSuccess() throws Exception {
        String name = "waiting_answered_stopped";
        mockMvc.perform(post("/task").content(task(name))).andExpect(status().isOk());
        String waiting = mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andReturn().getResponse().getContentAsString();
        long waitingId = ((Number) JsonPath.read(waiting, "$.executionId")).longValue();
        runs.answerWaiting(waitingId, runs.findById(waitingId).orElseThrow().getWaitKey(), APPROVED);
        TaskExecution continuation = new TaskExecution();
        continuation.setResumedFrom(waitingId);
        runs.save(continuation);

        housekeeping.stop(waitingId, "Stopped in the test.");
        Assert.assertEquals(TaskExecutionStatus.ABORTED, runs.findById(waitingId).orElseThrow().getStatus());
        Assert.assertEquals(TaskExecutionStatus.ABORTED, runs.findById(continuation.getId()).orElseThrow().getStatus());
    }

    @Test
    public void testAnAnsweredRunContinuesOnlyWithTheCurrentVersionFail() throws Exception {
        String name = "waiting_new_version";
        String firstVersion = JsonPath.read(mockMvc.perform(post("/task").content(task(name))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.checksum");
        String waiting = mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andReturn().getResponse().getContentAsString();
        long waitingId = ((Number) JsonPath.read(waiting, "$.executionId")).longValue();
        runs.answerWaiting(waitingId, runs.findById(waitingId).orElseThrow().getWaitKey(), APPROVED);
        // Before the run continues, the task gets a new version.
        mockMvc.perform(post("/task").content(task(name).replace("Ship it?", "Ship it now?"))).andExpect(status().isOk());

        mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskName\":\"" + firstVersion + "\",\"parameters\":{},\"resumeFromExecutionId\":" + waitingId + "}"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("Could not resume. Execution " + waitingId + " goes on after an answer, so it continues with the current version of " + name + "."));
        Assert.assertEquals(TaskExecutionStatus.WAITING, runs.findById(waitingId).orElseThrow().getStatus());
        // Ended, so that no later start continues it.
        housekeeping.stop(waitingId, "Stopped in the test.");
    }

    @Test
    public void testTwoRunsThatWouldContinueOneAnswerContinueItOnceSuccess() throws Exception {
        String name = "waiting_two_continuations";
        mockMvc.perform(post("/task").content(task(name))).andExpect(status().isOk());
        String waiting = mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andReturn().getResponse().getContentAsString();
        long waitingId = ((Number) JsonPath.read(waiting, "$.executionId")).longValue();
        runs.answerWaiting(waitingId, runs.findById(waitingId).orElseThrow().getWaitKey(), APPROVED);

        TaskRequest resume = new TaskRequest(name, new HashMap<>());
        resume.setResumeFromExecutionId(waitingId);
        List<TaskExecution> continuations = List.of(taskService.delegate(resume), taskService.delegate(resume));
        List<String> ended = List.of();
        for (int tries = 0; tries < 100 && ended.size() < 2; tries++) {
            Thread.sleep(100);
            ended = continuations.stream().map(run -> runs.findById(run.getId()).orElseThrow().getStatus())
                    .filter(status -> status == TaskExecutionStatus.FINISHED || status == TaskExecutionStatus.FAILED).map(Enum::name).sorted().toList();
        }
        Assert.assertEquals(List.of("FAILED", "FINISHED"), ended);
        Assert.assertEquals(TaskExecutionStatus.FINISHED, runs.findById(waitingId).orElseThrow().getStatus());
    }

    /** A step that asks for approval, and a step that prints the answer. */
    private static String task(String name) {
        return "{\"name\":\"" + name + "\",\"active\":false,\"parameters\":[],\"actions\":["
                + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"" + name + "_s\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"wait-test\",\"operation\":\"ask\",\"text\":\"Ship it?\",\"ports\":[{\"externalId\":\"" + name + "_a\",\"portType\":\"INPUT\"},{\"externalId\":\"" + name + "_ao\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"PrintAction\",\"message\":\"approved $lastLog.outputVariables.approved, signed in $lastLog.outputVariables.signedIn\",\"ports\":[{\"externalId\":\"" + name + "_p\",\"portType\":\"INPUT\"}]}],"
                + "\"connections\":[{\"source\":{\"externalId\":\"" + name + "_s\"},\"target\":{\"externalId\":\"" + name + "_a\"}},{\"source\":{\"externalId\":\"" + name + "_ao\"},\"target\":{\"externalId\":\"" + name + "_p\"}}]}";
    }

    /** The logs of the run that continued the waiting one, once it has finished. */
    private String finishedRunAfter(String name, int waitingId) throws Exception {
        for (int tries = 0; tries < 100; tries++) {
            String runs = mockMvc.perform(get("/executions").param("taskName", name)).andReturn().getResponse().getContentAsString();
            List<String> finished = JsonPath.read(runs, "$.executions[?(@.status == 'FINISHED')].executionId");
            String later = finished.stream().filter(id -> Long.parseLong(id) > waitingId).findFirst().orElse(null);
            if (later != null) {
                return mockMvc.perform(get("/execution/" + later)).andReturn().getResponse().getContentAsString();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The waiting run did not continue.");
    }
}
