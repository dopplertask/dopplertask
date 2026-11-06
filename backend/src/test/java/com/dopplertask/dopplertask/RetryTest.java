package com.dopplertask.dopplertask;

import com.sun.net.httpserver.HttpServer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

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
    public void testFailOnStatusCodeRetriesUntilTheServiceRecoversSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer service = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        service.createContext("/", exchange -> {
            exchange.sendResponseHeaders(calls.incrementAndGet() < 3 ? 503 : 200, -1);
            exchange.close();
        });
        service.start();
        try {
            save("retry_status", "{\"@type\":\"HttpAction\",\"method\":\"GET\",\"url\":\"http://localhost:" + service.getAddress().getPort() + "/\",\"retries\":2,"
                    + "\"failOn\":\"#if($result.outputVariables.statusCode >= 500)unavailable#end\"");
            run("retry_status").andExpect(jsonPath("$.status").value("FINISHED"));
        } finally {
            service.stop(0);
        }
        Assert.assertEquals(3, calls.get());
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
