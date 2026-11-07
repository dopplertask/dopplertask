package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.action.common.LoopAction;
import com.jayway.jsonpath.JsonPath;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not transactional, because a run only sees port connections once the task has been committed, as in a real request.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LoopTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testEachItemOfAJsonListThenDoneWithResultsSuccess() throws Exception {
        List<String> logs = run("loop_json", loop("[{\\\"name\\\":\\\"anna\\\"},{\\\"name\\\":\\\"bo\\\"}]", ""),
                print("body", "hi $loop.item.name ($loop.number of $loop.count)"), print("after", "all: $lastLog.outputVariables.results"));
        Assert.assertTrue(logs.toString(), logs.contains("hi anna (1 of 2)"));
        Assert.assertTrue(logs.toString(), logs.contains("hi bo (2 of 2)"));
        Assert.assertTrue(logs.toString(), logs.contains("all: [\"hi anna (1 of 2)\",\"hi bo (2 of 2)\"]"));
    }

    @Test
    public void testLinesOfTextSuccess() throws Exception {
        List<String> logs = run("loop_lines", loop("first\\nsecond\\n\\nthird", ""), print("body", "line $loop.item"), print("after", "done"));
        Assert.assertEquals(List.of("line first", "line second", "line third"), lines(logs, "line "));
    }

    @Test
    public void testListInsideAFieldSuccess() throws Exception {
        List<String> logs = run("loop_path", loop("{\\\"data\\\":{\\\"orders\\\":[7,8]}}", "data.orders"), print("body", "order $loop.item"), print("after", "done"));
        Assert.assertEquals(List.of("order 7", "order 8"), lines(logs, "order "));
    }

    @Test
    public void testEmptyListGoesStraightToDoneSuccess() throws Exception {
        List<String> logs = run("loop_empty", loop("[]", ""), print("body", "never"), print("after", "finished with $lastLog.outputVariables.count"));
        Assert.assertFalse(logs.contains("never"));
        Assert.assertTrue(logs.contains("finished with 0"));
    }

    @Test
    public void testMoreItemsThanTheVisitLimitSuccess() throws Exception {
        StringBuilder list = new StringBuilder("[");
        for (int number = 0; number < 2500; number++) {
            list.append(number == 0 ? "" : ",").append(number);
        }
        List<String> logs = run("loop_many", loop(list.append("]").toString(), ""), print("body", "n$loop.item"), print("after", "counted $lastLog.outputVariables.count"));
        Assert.assertTrue(logs.contains("counted 2500"));
    }

    @Test
    public void testScriptsReadTheItemSuccess() throws Exception {
        String script = "{\"@type\":\"ScriptAction\",\"scriptLanguage\":\"JAVASCRIPT\",\"script\":\"'js ' + loop.item.name.toUpperCase() + ' ' + loop.isLast\",\"ports\":[{\"externalId\":\"body_in\",\"portType\":\"INPUT\"},{\"externalId\":\"body_out\",\"portType\":\"OUTPUT\"}]}";
        List<String> logs = run("loop_script", loop("[{\\\"name\\\":\\\"a\\\"},{\\\"name\\\":\\\"b\\\"}]", ""), script, print("after", "done"));
        Assert.assertEquals(List.of("js A false", "js B true"), lines(logs, "js "));
    }

    @Test
    public void testAWireBackToTheLoopWorksTooSuccess() throws Exception {
        String actions = "[" + START + "," + loop("[1,2,3]", "") + "," + print("body", "wired $loop.item") + "," + print("after", "done") + "]";
        String connections = "[{\"source\":{\"externalId\":\"start\"},\"target\":{\"externalId\":\"loop_in\"}},"
                + "{\"source\":{\"externalId\":\"loop_0\"},\"target\":{\"externalId\":\"body_in\"}},"
                + "{\"source\":{\"externalId\":\"body_out\"},\"target\":{\"externalId\":\"loop_in\"}},"
                + "{\"source\":{\"externalId\":\"loop_1\"},\"target\":{\"externalId\":\"after_in\"}}]";
        Assert.assertEquals(List.of("wired 1", "wired 2", "wired 3"), lines(runTask("loop_wired", actions, connections), "wired "));
    }

    @Test
    public void testLoopInsideALoopSuccess() throws Exception {
        String outer = "{\"@type\":\"LoopAction\",\"actionName\":\"rows\",\"items\":\"[1,2]\",\"ports\":[{\"externalId\":\"outer_in\",\"portType\":\"INPUT\"},{\"externalId\":\"outer_0\",\"portType\":\"OUTPUT\"},{\"externalId\":\"outer_1\",\"portType\":\"OUTPUT\"}]}";
        String actions = "[" + START + "," + outer + "," + loop("[\\\"a\\\",\\\"b\\\"]", "") + "," + print("body", "cell $loops.rows.item$loop.item") + "," + print("after", "done") + "]";
        String connections = "[{\"source\":{\"externalId\":\"start\"},\"target\":{\"externalId\":\"outer_in\"}},"
                + "{\"source\":{\"externalId\":\"outer_0\"},\"target\":{\"externalId\":\"loop_in\"}},"
                + "{\"source\":{\"externalId\":\"loop_0\"},\"target\":{\"externalId\":\"body_in\"}},"
                + "{\"source\":{\"externalId\":\"outer_1\"},\"target\":{\"externalId\":\"after_in\"}}]";
        List<String> logs = runTask("loop_nested", actions, connections);
        Assert.assertEquals(List.of("cell 1a", "cell 1b", "cell 2a", "cell 2b"), lines(logs, "cell "));
        Assert.assertTrue(logs.contains("done"));
    }

    @Test
    public void testBatchesSuccess() throws Exception {
        String batched = loop("[1,2,3,4,5]", "").replace("\"items\"", "\"batchSize\":\"2\",\"items\"");
        List<String> logs = run("loop_batches", batched, print("body", "batch $loop.batch"), print("after", "done"));
        Assert.assertEquals(List.of("batch [1, 2]", "batch [3, 4]", "batch [5]"), lines(logs, "batch "));
    }

    @Test
    public void testMissingFieldExplainsWhyFail() {
        try {
            LoopAction.itemsOf("{\"data\":{\"rows\":[]}}", "data.orders");
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("There is no field orders in the JSON. It has rows.", e.getMessage());
        }
    }

    @Test
    public void testOnlyListInAnObjectIsUsedSuccess() {
        Assert.assertEquals(List.of(Map.of("id", 1)), LoopAction.itemsOf("{\"total\":1,\"rows\":[{\"id\":1}]}", ""));
    }

    private static final String START = "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"start\",\"portType\":\"OUTPUT\"}]}";

    private static String loop(String items, String path) {
        return "{\"@type\":\"LoopAction\",\"items\":\"" + items + "\",\"path\":\"" + path + "\",\"ports\":[{\"externalId\":\"loop_in\",\"portType\":\"INPUT\"},"
                + "{\"externalId\":\"loop_0\",\"portType\":\"OUTPUT\"},{\"externalId\":\"loop_1\",\"portType\":\"OUTPUT\"}]}";
    }

    private static String print(String id, String message) {
        return "{\"@type\":\"PrintAction\",\"message\":\"" + message + "\",\"ports\":[{\"externalId\":\"" + id + "_in\",\"portType\":\"INPUT\"},{\"externalId\":\"" + id + "_out\",\"portType\":\"OUTPUT\"}]}";
    }

    private List<String> run(String name, String loop, String body, String after) throws Exception {
        String connections = "[{\"source\":{\"externalId\":\"start\"},\"target\":{\"externalId\":\"loop_in\"}},"
                + "{\"source\":{\"externalId\":\"loop_0\"},\"target\":{\"externalId\":\"body_in\"}},"
                + "{\"source\":{\"externalId\":\"loop_1\"},\"target\":{\"externalId\":\"after_in\"}}]";
        return runTask(name, "[" + START + "," + loop + "," + body + "," + after + "]", connections);
    }

    private List<String> runTask(String name, String actions, String connections) throws Exception {
        String createJson = "{\"name\":\"" + name + "\",\"description\":\"\",\"active\":false,\"actions\":" + actions + ",\"connections\":" + connections + "}";
        this.mockMvc.perform(post("/task").content(createJson)).andExpect(status().isOk());
        String response = this.mockMvc.perform(post("/schedule/directtask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskName\":\"" + name + "\",\"parameters\":{}}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.logs");
    }

    private static List<String> lines(List<String> logs, String prefix) {
        return logs.stream().filter(line -> line != null && line.startsWith(prefix)).collect(Collectors.toList());
    }
}
