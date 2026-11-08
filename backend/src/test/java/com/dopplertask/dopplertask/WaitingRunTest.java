package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.action.connector.Connectors;
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
