package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.filter.Caller;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agents propose, people approve. The filter marks a caller as an agent, which these requests do directly.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProposalTest {

    @Autowired
    private MockMvc mockMvc;

    private String task(String name, String message, boolean active) {
        return "{\"name\":\"" + name + "\",\"active\":" + active + ",\"parameters\":[],\"actions\":["
                + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"s\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"Webhook\",\"method\":\"POST\",\"path\":\"\",\"triggerSuffix\":\"go\",\"authentication\":\"none\",\"ports\":[{\"externalId\":\"w\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"PrintAction\",\"message\":\"" + message + "\",\"ports\":[{\"externalId\":\"p\",\"portType\":\"INPUT\"}]}],"
                + "\"connections\":[{\"source\":{\"externalId\":\"s\"},\"target\":{\"externalId\":\"p\"}},{\"source\":{\"externalId\":\"w\"},\"target\":{\"externalId\":\"p\"}}]}";
    }

    private MockHttpServletRequestBuilder asAgent(MockHttpServletRequestBuilder request) {
        return request.requestAttr("dopplertask.caller", Caller.AGENT);
    }

    private String checksumOf(ResultActions saved) throws Exception {
        return JsonPath.read(saved.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.checksum");
    }

    private MockHttpServletRequestBuilder run(String name) {
        return post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}");
    }

    @Test
    public void testAProposalStaysOutOfRunsAndWebhooksUntilApprovedSuccess() throws Exception {
        String name = "proposal_live";
        this.mockMvc.perform(post("/task").content(task(name, "current", true))).andExpect(status().isOk());
        String proposal = checksumOf(this.mockMvc.perform(asAgent(post("/task")).content(task(name, "proposed", false))));

        this.mockMvc.perform(run(name)).andExpect(jsonPath("$.logs", hasItem("current")));
        this.mockMvc.perform(asAgent(run(name))).andExpect(jsonPath("$.logs", hasItem("current")));
        this.mockMvc.perform(post("/webhook/" + name + "/Webhook/go")).andExpect(jsonPath("$.output", hasItem("current")));
        this.mockMvc.perform(get("/task/grouped"))
                .andExpect(jsonPath("$." + name + "[0].proposed").value(true))
                .andExpect(jsonPath("$." + name + "[0].madeByAgent").value(true))
                .andExpect(jsonPath("$." + name + "[1].proposed").value(false));

        this.mockMvc.perform(post("/task/" + proposal + "/approve")).andExpect(status().isOk());

        this.mockMvc.perform(run(name)).andExpect(jsonPath("$.logs", hasItem("proposed")));
        // The schedule or webhook that was on stays on, and the record of who made it stays.
        this.mockMvc.perform(post("/webhook/" + name + "/Webhook/go")).andExpect(jsonPath("$.output", hasItem("proposed")));
        this.mockMvc.perform(get("/task/grouped"))
                .andExpect(jsonPath("$." + name, hasSize(2)))
                .andExpect(jsonPath("$." + name + "[0].proposed").value(false))
                .andExpect(jsonPath("$." + name + "[0].madeByAgent").value(true));
    }

    @Test
    public void testAnAgentCannotRunAProposalInAnyWayFail() throws Exception {
        String name = "proposal_new";
        String proposal = checksumOf(this.mockMvc.perform(asAgent(post("/task")).content(task(name, "new", false))));

        this.mockMvc.perform(asAgent(run(name))).andExpect(status().isForbidden()).andExpect(jsonPath("$.message", containsString("A person has to approve it")));
        this.mockMvc.perform(asAgent(run(proposal))).andExpect(status().isForbidden());
        this.mockMvc.perform(asAgent(post("/schedule/task")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskName\":\"inline\",\"parameters\":{},\"task\":" + task("inline", "x", false) + "}")).andExpect(status().isForbidden());
    }

    @Test
    public void testAnAgentCannotTurnATaskOnFail() throws Exception {
        this.mockMvc.perform(asAgent(post("/task")).content(task("proposal_active", "x", true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("cannot turn a task on")));
    }

    @Test
    public void testAnAgentSavingWhatExistsChangesNothingSuccess() throws Exception {
        String name = "proposal_same";
        String body = task(name, "same", false);
        this.mockMvc.perform(post("/task").content(body)).andExpect(status().isOk());
        this.mockMvc.perform(asAgent(post("/task")).content(body)).andExpect(status().isOk());
        this.mockMvc.perform(get("/task/grouped")).andExpect(jsonPath("$." + name, hasSize(1))).andExpect(jsonPath("$." + name + "[0].madeByAgent").value(false));
    }

    @Test
    public void testAPersonSavingAProposalAcceptsItSuccess() throws Exception {
        String name = "proposal_saved";
        String body = task(name, "reviewed", false);
        this.mockMvc.perform(asAgent(post("/task")).content(body)).andExpect(status().isOk());
        this.mockMvc.perform(post("/task").content(body)).andExpect(status().isOk());
        this.mockMvc.perform(run(name)).andExpect(jsonPath("$.logs", hasItem("reviewed")));
    }

    @Test
    public void testApprovingWhatIsNotAProposalIsNotFoundFail() throws Exception {
        this.mockMvc.perform(post("/task/nothing-here/approve")).andExpect(status().isNotFound());
    }

    private String taskWithSecret(String name, String command, String password) {
        return "{\"name\":\"" + name + "\",\"active\":false,\"parameters\":[],\"actions\":["
                + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"s\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"SSHAction\",\"hostname\":\"h\",\"username\":\"me\",\"password\":\"" + password + "\",\"command\":\"" + command + "\",\"ports\":[{\"externalId\":\"p\",\"portType\":\"INPUT\"}]}],"
                + "\"connections\":[{\"source\":{\"externalId\":\"s\"},\"target\":{\"externalId\":\"p\"}}]}";
    }

    @Test
    public void testASavedSecretBecomesACredentialThatNobodyCanReadBackSuccess() throws Exception {
        String name = "proposal_secret";
        this.mockMvc.perform(post("/task").content(taskWithSecret(name, "ls", "real-secret"))).andExpect(status().isOk());

        for (String asked : java.util.List.of("/task/" + name + "/taskname", "/credentials")) {
            for (MockHttpServletRequestBuilder request : java.util.List.of(get(asked), asAgent(get(asked)))) {
                String answer = this.mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                org.junit.Assert.assertFalse(answer, answer.contains("real-secret"));
            }
        }
        this.mockMvc.perform(asAgent(get("/task/" + name + "/taskname")))
                .andExpect(jsonPath("$.actions[1].credential").isNotEmpty())
                .andExpect(jsonPath("$.actions[1].command").value("ls"));
    }

    @Test
    public void testAnAgentCannotSendASecretInAStepFail() throws Exception {
        this.mockMvc.perform(asAgent(post("/task")).content(taskWithSecret("proposal_no_secret", "ls", "real-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("list_credentials")));
    }

    @Test
    public void testARunRecordsWhetherAnAgentStartedItSuccess() throws Exception {
        String name = "proposal_record";
        this.mockMvc.perform(post("/task").content(task(name, "x", false))).andExpect(status().isOk());
        this.mockMvc.perform(run(name)).andExpect(status().isOk());
        this.mockMvc.perform(asAgent(run(name))).andExpect(status().isOk());
        this.mockMvc.perform(get("/executions").param("taskName", name))
                .andExpect(jsonPath("$.executions[0].startedByAgent").value(true))
                .andExpect(jsonPath("$.executions[1].startedByAgent").value(false));
    }
}
