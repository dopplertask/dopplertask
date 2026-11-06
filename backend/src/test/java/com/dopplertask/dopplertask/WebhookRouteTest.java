package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.controller.TaskController;
import com.dopplertask.dopplertask.service.ExecutionService;
import com.dopplertask.dopplertask.service.TaskRequest;
import com.dopplertask.dopplertask.service.TaskService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import com.dopplertask.dopplertask.service.CredentialService;
import com.dopplertask.dopplertask.service.SsoService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Goes through the real embedded server, because it decides whether an encoded slash reaches the controller at all.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ServletWebServerFactoryAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class, TaskController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebhookRouteTest {

    @LocalServerPort
    private int port;

    @MockBean
    private TaskService taskService;

    @MockBean
    private CredentialService credentialService;

    @MockBean
    private SsoService ssoService;

    @MockBean
    private ExecutionService executionService;

    @Test
    public void testEncodedSlashInTaskNameSuccess() throws IOException {
        Assert.assertEquals("folder/task", taskNameRequestedBy("/webhook/folder%2Ftask/Webhook/suffix/path"));
    }

    @Test
    public void testEncodedSlashInTaskNameWithoutPathSuccess() throws IOException {
        Assert.assertEquals("folder/task", taskNameRequestedBy("/webhook/folder%2Ftask/Webhook/suffix"));
    }

    @Test
    public void testPlainTaskNameSuccess() throws IOException {
        Assert.assertEquals("task", taskNameRequestedBy("/webhook/task/Webhook/suffix/path"));
    }

    private String taskNameRequestedBy(String webhookPath) throws IOException {
        Mockito.clearInvocations(taskService);
        HttpURLConnection connection = (HttpURLConnection) new URL("http", "localhost", port, webhookPath).openConnection();
        connection.getResponseCode();

        ArgumentCaptor<TaskRequest> request = ArgumentCaptor.forClass(TaskRequest.class);
        Mockito.verify(taskService).runRequest(request.capture());
        return request.getValue().getTaskName();
    }
}
