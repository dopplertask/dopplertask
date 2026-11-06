package com.dopplertask.dopplertask;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not transactional, because a run only sees port connections once the task has been committed, as in a real request.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BranchingTest {

    private static final String START = "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"%1$s_start\",\"portType\":\"OUTPUT\"}]}";
    private static final String PRINT = "{\"@type\":\"PrintAction\",\"message\":\"first branch\",\"ports\":[{\"externalId\":\"%1$s_print\",\"portType\":\"INPUT\"}]}";
    private static final String CONNECTIONS = "[{\"source\":{\"externalId\":\"%1$s_start\"},\"target\":{\"externalId\":\"%1$s_in\"}},"
            + "{\"source\":{\"externalId\":\"%1$s_0\"},\"target\":{\"externalId\":\"%1$s_print\"}}]";

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testIfTrueRunsConnectedBranchSuccess() throws Exception {
        Assert.assertTrue(run("if_true", ifAction("1 == 1", 2)).contains("first branch"));
    }

    @Test
    public void testIfFalseWithUnconnectedBranchEndsRunSuccess() throws Exception {
        Assert.assertFalse(run("if_false", ifAction("1 == 2", 2)).contains("first branch"));
    }

    @Test
    public void testIfFalseWithOnlyOneBranchEndsRunSuccess() throws Exception {
        Assert.assertFalse(run("if_single", ifAction("1 == 2", 1)).contains("first branch"));
    }

    @Test
    public void testSwitchToUnconnectedBranchEndsRunSuccess() throws Exception {
        Assert.assertFalse(run("switch_b", switchAction("b", 2)).contains("first branch"));
    }

    @Test
    public void testSwitchMatchesFirstCaseSuccess() throws Exception {
        Assert.assertTrue(run("switch_a", switchAction("a", 2)).contains("first branch"));
    }

    @Test
    public void testSwitchValueWithDoubleQuotesSuccess() throws Exception {
        Assert.assertTrue(run("switch_quotes", switchAction("#set($v = \\\"a\\\")$v", 2)).contains("first branch"));
    }

    @Test
    public void testSwitchWithoutMatchUsesExtraPortAsDefaultSuccess() throws Exception {
        Assert.assertTrue(run("switch_default", switchActionWithFirstPortAsDefault()).contains("first branch"));
    }

    @Test
    public void testSwitchWithoutMatchOrDefaultExplainsWhyFail() throws Exception {
        Assert.assertTrue(run("switch_none", switchAction("z", 2)).contains("No case or output port matched the value [z]"));
    }

    private String switchAction(String value, int outputs) {
        return "{\"@type\":\"SwitchAction\",\"value\":\"" + value + "\",\"switchCases\":[{\"currentCase\":\"a\"},{\"currentCase\":\"b\"}],\"ports\":" + ports(outputs) + "}";
    }

    // No cases, so the only port is the one after the last case.
    private String switchActionWithFirstPortAsDefault() {
        return "{\"@type\":\"SwitchAction\",\"value\":\"z\",\"switchCases\":[],\"ports\":" + ports(1) + "}";
    }

    private String ifAction(String condition, int outputs) {
        return "{\"@type\":\"IfAction\",\"condition\":\"" + condition + "\",\"ports\":" + ports(outputs) + "}";
    }

    private String ports(int outputs) {
        StringBuilder ports = new StringBuilder("[{\"externalId\":\"%1$s_in\",\"portType\":\"INPUT\"}");
        for (int output = 0; output < outputs; output++) {
            ports.append(",{\"externalId\":\"%1$s_").append(output).append("\",\"portType\":\"OUTPUT\"}");
        }
        return ports.append("]").toString();
    }

    private String run(String name, String branchingAction) throws Exception {
        String actions = String.format("[" + START + "," + branchingAction + "," + PRINT + "]", name);
        String createJson = "{\"name\":\"" + name + "\",\"description\":\"\",\"active\":false,\"actions\":" + actions
                + ",\"connections\":" + String.format(CONNECTIONS, name) + "}";
        this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isOk());

        return this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
