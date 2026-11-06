package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.filter.Caller;
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
}
