package com.dopplertask.dopplertask;

import com.jayway.jsonpath.JsonPath;
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

    @Autowired
    private MockMvc mockMvc;

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

    private ResultActions run(String name, String parameters) throws Exception {
        return this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskName\":\"" + name + "\",\"parameters\":" + parameters + "}"));
    }
}
