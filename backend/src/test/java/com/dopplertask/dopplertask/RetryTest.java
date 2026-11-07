package com.dopplertask.dopplertask;

import com.sun.net.httpserver.HttpServer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RetryTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testRetriesWithoutRetryWaitReportTheRealFailureFail() throws Exception {
        save("retry_no_wait", "{\"@type\":\"PrintAction\",\"message\":\"hi\",\"retries\":1,\"failOn\":\"boom\"");
        run("retry_no_wait").andExpect(jsonPath("$.error").value("Failed on: boom"));
    }

    @Test
    public void testErrorStatusRetriesUntilTheServiceRecoversSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer service = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        service.createContext("/", exchange -> {
            exchange.sendResponseHeaders(calls.incrementAndGet() < 3 ? 503 : 200, -1);
            exchange.close();
        });
        service.start();
        try {
            save("retry_status", "{\"@type\":\"HttpAction\",\"method\":\"GET\",\"url\":\"http://localhost:" + service.getAddress().getPort() + "/\",\"retries\":2");
            run("retry_status").andExpect(jsonPath("$.status").value("FINISHED"));
        } finally {
            service.stop(0);
        }
        Assert.assertEquals(3, calls.get());
    }

    private HttpServer serviceAnswering(int status) throws IOException {
        HttpServer service = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        service.createContext("/", exchange -> {
            byte[] body = "not here".getBytes();
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        service.start();
        return service;
    }

    @Test
    public void testErrorStatusFailsTheStepAndKeepsTheBodyFail() throws Exception {
        HttpServer service = serviceAnswering(404);
        try {
            save("http_404", "{\"@type\":\"HttpAction\",\"method\":\"GET\",\"url\":\"http://localhost:" + service.getAddress().getPort() + "/\"");
            run("http_404").andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.error").value("The server answered with status 404."))
                    .andExpect(jsonPath("$.logs", org.hamcrest.Matchers.hasItem("not here")));
        } finally {
            service.stop(0);
        }
    }

    @Test
    public void testContinueOnFailureLetsTheNextStepReadTheStatusSuccess() throws Exception {
        HttpServer service = serviceAnswering(404);
        try {
            String name = "http_continue";
            String createJson = "{\"name\":\"" + name + "\",\"actions\":[{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"" + name + "_s\",\"portType\":\"OUTPUT\"}]},"
                    + "{\"@type\":\"HttpAction\",\"method\":\"GET\",\"continueOnFailure\":true,\"url\":\"http://localhost:" + service.getAddress().getPort() + "/\",\"ports\":[{\"externalId\":\"" + name + "_h\",\"portType\":\"INPUT\"},{\"externalId\":\"" + name + "_h2\",\"portType\":\"OUTPUT\"}]},"
                    + "{\"@type\":\"PrintAction\",\"message\":\"status $lastLog.outputVariables.statusCode\",\"ports\":[{\"externalId\":\"" + name + "_p\",\"portType\":\"INPUT\"}]}],"
                    + "\"connections\":[{\"source\":{\"externalId\":\"" + name + "_s\"},\"target\":{\"externalId\":\"" + name + "_h\"}},{\"source\":{\"externalId\":\"" + name + "_h2\"},\"target\":{\"externalId\":\"" + name + "_p\"}}]}";
            this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isOk());
            run(name).andExpect(jsonPath("$.status").value("FINISHED"))
                    .andExpect(jsonPath("$.logs", org.hamcrest.Matchers.hasItem("status 404")));
        } finally {
            service.stop(0);
        }
    }

    private void save(String name, String actionWithoutPorts) throws Exception {
        String createJson = "{\"name\":\"" + name + "\",\"actions\":[{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"" + name + "_s\",\"portType\":\"OUTPUT\"}]},"
                + actionWithoutPorts + ",\"ports\":[{\"externalId\":\"" + name + "_i\",\"portType\":\"INPUT\"}]}],"
                + "\"connections\":[{\"source\":{\"externalId\":\"" + name + "_s\"},\"target\":{\"externalId\":\"" + name + "_i\"}}]}";
        this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isOk());
    }

    private ResultActions run(String name) throws Exception {
        return this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"));
    }
}
