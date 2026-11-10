package com.dopplertask.dopplertask;

import com.jayway.jsonpath.JsonPath;
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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DeactivatedStepTest {

    private static final String START = "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"start\",\"portType\":\"OUTPUT\"}]}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testTheRunPassesOverTheStepAndTheNextStepReadsTheOneBeforeSuccess() throws Exception {
        String actions = "[" + START + "," + print("first", "order 7", false) + "," + print("off", "never", true) + "," + print("last", "got $lastLog.output", false) + "]";
        String connections = "[" + wire("start", "first_in") + "," + wire("first_out", "off_in") + "," + wire("off_out", "last_in") + "]";
        List<String> logs = runTask("deactivated_chain", actions, connections);
        Assert.assertFalse(logs.toString(), logs.contains("never"));
        Assert.assertTrue(logs.toString(), logs.contains("got order 7"));
    }

    @Test
    public void testALoopGoesOnWhenItsLastStepIsDeactivatedSuccess() throws Exception {
        String loop = "{\"@type\":\"LoopAction\",\"items\":\"[1,2]\",\"ports\":[{\"externalId\":\"loop_in\",\"portType\":\"INPUT\"},"
                + "{\"externalId\":\"loop_0\",\"portType\":\"OUTPUT\"},{\"externalId\":\"loop_1\",\"portType\":\"OUTPUT\"}]}";
        String actions = "[" + START + "," + loop + "," + print("body", "item $loop.item", false) + "," + print("off", "never", true) + "," + print("after", "done", false) + "]";
        String connections = "[" + wire("start", "loop_in") + "," + wire("loop_0", "body_in") + "," + wire("body_out", "off_in") + "," + wire("loop_1", "after_in") + "]";
        List<String> logs = runTask("deactivated_in_loop", actions, connections);
        Assert.assertFalse(logs.toString(), logs.contains("never"));
        Assert.assertTrue(logs.toString(), logs.containsAll(List.of("item 1", "item 2", "done")));
    }

    @Test
    public void testASavedTaskKeepsTheStepDeactivatedSuccess() throws Exception {
        runTask("deactivated_saved", "[" + START + "," + print("on", "always", false) + "," + print("off", "never", true) + "]", "[" + wire("start", "on_in") + "," + wire("on_out", "off_in") + "]");
        String saved = this.mockMvc.perform(get("/task/deactivated_saved/taskname")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Assert.assertEquals(saved, List.of(true), JsonPath.read(saved, "$.actions[?(@.message=='never')].deactivated"));
        Assert.assertEquals(saved, List.of(), JsonPath.read(saved, "$.actions[?(@.message=='always')].deactivated"));
    }

    private static String print(String id, String message, boolean deactivated) {
        return "{\"@type\":\"PrintAction\",\"message\":\"" + message + "\",\"deactivated\":" + deactivated
                + ",\"ports\":[{\"externalId\":\"" + id + "_in\",\"portType\":\"INPUT\"},{\"externalId\":\"" + id + "_out\",\"portType\":\"OUTPUT\"}]}";
    }

    private static String wire(String from, String to) {
        return "{\"source\":{\"externalId\":\"" + from + "\"},\"target\":{\"externalId\":\"" + to + "\"}}";
    }

    private List<String> runTask(String name, String actions, String connections) throws Exception {
        String createJson = "{\"name\":\"" + name + "\",\"description\":\"\",\"active\":false,\"actions\":" + actions + ",\"connections\":" + connections + "}";
        this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isOk());
        String run = this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"" + name + "\",\"parameters\":{}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(run, "$.logs");
    }
}
