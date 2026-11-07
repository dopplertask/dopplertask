package com.dopplertask.dopplertask;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not transactional, because a run only sees port connections once the task has been committed, as in a real request.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WebhookReplyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testReplyStepSetsStatusHeadersAndBodySuccess() throws Exception {
        MockHttpServletResponse response = call("reply_step", "replyStep", "", step("WebhookReplyAction",
                "\"replyWith\":\"JSON\",\"statusCode\":\"201\",\"headers\":\"X-Answered-By: DopplerTask\",\"body\":\"{\\\"got\\\": $parameters.get(\\\"TRIGGER_body\\\")}\""), "{\"a\":1}");
        Assert.assertEquals(201, response.getStatus());
        Assert.assertEquals("DopplerTask", response.getHeader("X-Answered-By"));
        Assert.assertEquals("application/json", response.getContentType());
        Assert.assertEquals("{\"got\": {\"a\":1}}", response.getContentAsString());
    }

    @Test
    public void testReplyStepRedirectsSuccess() throws Exception {
        MockHttpServletResponse response = call("reply_redirect", "replyStep", "", step("WebhookReplyAction",
                "\"replyWith\":\"REDIRECT\",\"redirectUrl\":\"https://example.com/thanks\""), "");
        Assert.assertEquals(307, response.getStatus());
        Assert.assertEquals("https://example.com/thanks", response.getHeader("Location"));
    }

    @Test
    public void testAnswerAtOnceDoesNotWaitForTheTaskSuccess() throws Exception {
        long start = System.currentTimeMillis();
        MockHttpServletResponse response = call("reply_at_once", "immediately", "\"responseCode\":\"202\",", step("TimedWait", "\"seconds\":3"), "");
        Assert.assertTrue("Answered after " + (System.currentTimeMillis() - start) + " ms", System.currentTimeMillis() - start < 2500);
        Assert.assertEquals(202, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("\"status\":\"started\""));
    }

    @Test
    public void testAnswerWithWhatTheLastStepGaveSuccess() throws Exception {
        MockHttpServletResponse response = call("reply_last_step", "lastStep", "", step("PrintAction", "\"message\":\"hello there\""), "");
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("hello there", response.getContentAsString());
    }

    @Test
    public void testReplyModeWithoutReplyStepExplainsWhyFail() throws Exception {
        MockHttpServletResponse response = call("reply_missing", "replyStep", "", step("PrintAction", "\"message\":\"no reply\""), "");
        Assert.assertEquals(500, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("without a Reply to webhook step"));
    }

    @Test
    public void testReplyWithBrokenJsonExplainsWhyFail() throws Exception {
        MockHttpServletResponse response = call("reply_bad_json", "replyStep", "", step("WebhookReplyAction", "\"replyWith\":\"JSON\",\"body\":\"{not json\""), "");
        Assert.assertEquals(500, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("not valid JSON"));
    }

    @Test
    public void testRunLogStaysTheDefaultSuccess() throws Exception {
        String name = "reply_default";
        this.mockMvc.perform(post("/task").content(task(name, "", "", step("PrintAction", "\"message\":\"as before\"")))).andExpect(status().isOk());
        this.mockMvc.perform(post("/webhook/" + name + "/Webhook/go")).andExpect(jsonPath("$.output[0]").value("as before"));
    }

    @Test
    public void testReplyStepOutsideAWebhookRunCarriesOnSuccess() throws Exception {
        String name = "reply_by_hand";
        this.mockMvc.perform(post("/task").content(task(name, "replyStep", "", step("WebhookReplyAction", "\"replyWith\":\"TEXT\",\"body\":\"hi\"")))).andExpect(status().isOk());
        this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINISHED"));
    }

    private MockHttpServletResponse call(String name, String mode, String webhookFields, String step, String body) throws Exception {
        this.mockMvc.perform(post("/task").content(task(name, mode, webhookFields, step))).andExpect(status().isOk());
        return this.mockMvc.perform(post("/webhook/" + name + "/Webhook/go").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    private static String step(String type, String fields) {
        return "{\"@type\":\"" + type + "\"," + fields + ",\"ports\":[{\"externalId\":\"p\",\"portType\":\"INPUT\"}]}";
    }

    private static String task(String name, String mode, String webhookFields, String step) {
        String replyMode = mode.isEmpty() ? "" : "\"responseMode\":\"" + mode + "\",";
        return "{\"name\":\"" + name + "\",\"active\":true,\"parameters\":[],\"actions\":["
                + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"s\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"Webhook\"," + replyMode + webhookFields + "\"method\":\"POST\",\"path\":\"\",\"triggerSuffix\":\"go\",\"authentication\":\"none\",\"ports\":[{\"externalId\":\"w\",\"portType\":\"OUTPUT\"}]},"
                + step + "],"
                + "\"connections\":[{\"source\":{\"externalId\":\"s\"},\"target\":{\"externalId\":\"p\"}},{\"source\":{\"externalId\":\"w\"},\"target\":{\"externalId\":\"p\"}}]}";
    }
}
