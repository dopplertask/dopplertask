package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.filter.Caller;
import com.jayway.jsonpath.JsonPath;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class McpTest {

    @Autowired
    private MockMvc mockMvc;

    private org.springframework.test.web.servlet.ResultActions rpc(String method, String params) throws Exception {
        return this.mockMvc.perform(post("/mcp").requestAttr("dopplertask.caller", Caller.AGENT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions tool(String name, String arguments) throws Exception {
        return rpc("tools/call", "{\"name\":\"" + name + "\",\"arguments\":" + arguments + "}");
    }

    private String textOf(org.springframework.test.web.servlet.ResultActions call) throws Exception {
        return JsonPath.read(call.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.result.content[0].text");
    }

    @Test
    public void testOnlyAnAgentCanConnectFail() throws Exception {
        this.mockMvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testAClientCanStartAndSeeTheToolsSuccess() throws Exception {
        rpc("initialize", "{\"protocolVersion\":\"2025-03-26\"}")
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-03-26"))
                .andExpect(jsonPath("$.result.instructions", containsString("proposal")));
        rpc("tools/list", "{}").andExpect(jsonPath("$.result.tools[*].name", hasItem("propose_task")));
        this.mockMvc.perform(post("/mcp").requestAttr("dopplertask.caller", Caller.AGENT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")).andExpect(status().isAccepted());
        rpc("no/such/method", "{}").andExpect(jsonPath("$.error.code").value(-32601));
    }

    @Test
    public void testAnAgentCanLookUpStepsSuccess() throws Exception {
        Assert.assertTrue(textOf(tool("list_step_types", "{}")).contains("PrintAction"));
        Assert.assertTrue(textOf(tool("get_step_type", "{\"name\":\"PrintAction\"}")).contains("message"));
        tool("get_step_type", "{\"name\":\"Nope\"}").andExpect(jsonPath("$.result.isError").value(true));
    }

    @Test
    public void testAProposalCannotRunUntilApprovedSuccess() throws Exception {
        String name = "mcp_task";
        String task = "{\"name\":\"" + name + "\",\"active\":false,\"parameters\":[],\"actions\":["
                + "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"s\",\"portType\":\"OUTPUT\"}]},"
                + "{\"@type\":\"PrintAction\",\"message\":\"hello\",\"ports\":[{\"externalId\":\"p\",\"portType\":\"INPUT\"}]}],"
                + "\"connections\":[{\"source\":{\"externalId\":\"s\"},\"target\":{\"externalId\":\"p\"}}]}";
        String checksum = JsonPath.read(textOf(tool("propose_task", "{\"task\":" + task + "}")), "$.checksum");

        tool("run_task", "{\"name\":\"" + name + "\"}")
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(jsonPath("$.result.content[0].text", containsString("A person has to approve it")));

        this.mockMvc.perform(post("/task/" + checksum + "/approve")).andExpect(status().isOk());

        String result = textOf(tool("run_task", "{\"name\":\"" + name + "\"}"));
        Assert.assertEquals("FINISHED", JsonPath.read(result, "$.status"));
        Assert.assertTrue(textOf(tool("list_runs", "{\"name\":\"" + name + "\"}")).contains("FINISHED"));
    }

    @Test
    public void testAToolThatDoesNotExistExplainsItselfFail() throws Exception {
        tool("delete_everything", "{}")
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(jsonPath("$.result.content[0].text", containsString("no tool called")));
    }
}
