package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.dao.TaskExecutionDao;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import com.dopplertask.dopplertask.domain.action.connector.Connectors;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A step that runs another task gives that task values, and gets the answer of its last step. */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LinkedTaskTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskExecutionDao runs;

    @Test
    public void testTheNextStepReadsTheAnswerOfTheLinkedTaskSuccess() throws Exception {
        save("linked_double", "[{\"name\":\"number\",\"required\":true}]", print("linked_double", "order $parameters.get(\\\"number\\\")#if($parameters.get(\\\"number\\\") > 5) big#end"));
        save("linked_caller", "[]", runs("linked_caller", "linked_double", "[{\"parameterName\":\"number\",\"parameterValue\":\"7\"}]"));

        String run = run("linked_caller");

        List<String> logs = JsonPath.read(run, "$.logs");
        Assert.assertTrue(run, logs.contains("got order 7 big"));
    }

    @Test
    public void testAChangeToTheLinkedTaskRunsWithoutSavingTheCallerAgainSuccess() throws Exception {
        save("linked_changing", "[]", print("linked_changing", "first version"));
        save("linked_follower", "[]", runs("linked_follower", "linked_changing", "[]"));
        save("linked_changing", "[]", print("linked_changing", "second version"));

        String run = run("linked_follower");

        List<String> logs = JsonPath.read(run, "$.logs");
        Assert.assertTrue(run, logs.contains("got second version"));
    }

    @Test
    public void testAFailedLinkedTaskFailsTheStepWithItsReasonFailure() throws Exception {
        save("linked_strict", "[{\"name\":\"number\",\"required\":true}]", print("linked_strict", "never"));
        save("linked_forgetful", "[]", runs("linked_forgetful", "linked_strict", "[]"));

        String run = run("linked_forgetful");

        Assert.assertEquals(run, "FAILED", JsonPath.read(run, "$.status"));
        String error = JsonPath.read(run, "$.error");
        Assert.assertTrue(run, error.contains("The task linked_strict failed: Task execution failed, missing parameters: [number]"));
    }

    @Test
    public void testAWaitingLinkedTaskFailsTheStepAndEndsFailure() throws Exception {
        HttpServer service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.createContext("/", exchange -> {
            byte[] bytes = "{\"ok\": true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        service.start();
        Connectors.INSTANCE.setLocalServerForTests("http://127.0.0.1:" + service.getAddress().getPort());
        try {
            String ask = "{\"@type\":\"wait-test\",\"operation\":\"ask\",\"text\":\"Ship it?\",\"ports\":[{\"externalId\":\"linked_asking_in\",\"portType\":\"INPUT\"}]}";
            save("linked_asking", "[]", ask);
            save("linked_impatient", "[]", runs("linked_impatient", "linked_asking", "[]"));

            String run = run("linked_impatient");

            Assert.assertEquals(run, "FAILED", JsonPath.read(run, "$.status"));
            String error = JsonPath.read(run, "$.error");
            Assert.assertTrue(run, error.contains("The task linked_asking waits for an answer."));
            List<TaskExecutionStatus> asking = this.runs.findAll().stream().filter(linked -> linked.getTask() != null && "linked_asking".equals(linked.getTask().getName()))
                    .map(linked -> linked.getStatus()).toList();
            Assert.assertEquals(List.of(TaskExecutionStatus.FAILED), asking);
        } finally {
            Connectors.INSTANCE.setLocalServerForTests(null);
            service.stop(0);
        }
    }

    private static String print(String id, String message) {
        return "{\"@type\":\"PrintAction\",\"message\":\"" + message + "\",\"ports\":[{\"externalId\":\"" + id + "_in\",\"portType\":\"INPUT\"}]}";
    }

    /** A step that runs the linked task, and a step that prints what it answered. */
    private static String runs(String id, String linkedTask, String parameters) {
        return "{\"@type\":\"LinkedTaskAction\",\"name\":\"" + linkedTask + "\",\"parameters\":" + parameters
                + ",\"ports\":[{\"externalId\":\"" + id + "_in\",\"portType\":\"INPUT\"},{\"externalId\":\"" + id + "_out\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"PrintAction\",\"message\":\"got $lastLog.output\",\"ports\":[{\"externalId\":\"" + id + "_after\",\"portType\":\"INPUT\"}]}";
    }

    /** Saves a task whose start leads to the steps; a second step is connected after the first. */
    private void save(String name, String parameters, String steps) throws Exception {
        String start = "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"" + name + "_start\",\"portType\":\"OUTPUT\"}]}";
        String connections = wire(name + "_start", name + "_in") + (steps.contains(name + "_after") ? "," + wire(name + "_out", name + "_after") : "");
        String createJson = "{\"name\":\"" + name + "\",\"description\":\"\",\"active\":false,\"parameters\":" + parameters + ",\"actions\":[" + start + "," + steps + "],\"connections\":[" + connections + "]}";
        this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isOk());
    }

    private static String wire(String from, String to) {
        return "{\"source\":{\"externalId\":\"" + from + "\"},\"target\":{\"externalId\":\"" + to + "\"}}";
    }

    private String run(String name) throws Exception {
        return this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
