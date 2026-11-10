package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.filter.Caller;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not transactional, because a run only sees port connections once the task has been committed, as in a real request.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PreviewTest {
    private static final String ORDER = "{\"customer\":{\"email\":\"anna@example.com\"},\"created\":\"2026-03-09T14:05:00\",\"total\":1234.5,\"items\":[{\"sku\":\"a1\"},{\"sku\":\"b2\"}],\"note\":null}";

    private static HttpServer service;
    private static String address;
    // Each request the service got, as its method and path.
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @BeforeClass
    public static void startService() throws Exception {
        service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.createContext("/", exchange -> {
            RECEIVED.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            byte[] answer = (exchange.getRequestMethod().equals("GET") ? ORDER : "{\"id\":77}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        service.start();
        address = "http://127.0.0.1:" + service.getAddress().getPort();
    }

    @AfterClass
    public static void stopService() {
        service.stop(0);
    }

    @Before
    public void forgetRequests() {
        RECEIVED.clear();
    }

    @Test
    public void testAnEarlierStepIsReadByItsNameSuccess() throws Exception {
        String run = preview("preview_by_name", "", getOrder(), print("after", "mail $steps[\\\"Get order\\\"].data.customer.email"), print("last", "still $steps[\\\"Get order\\\"].data.items[1].sku"));
        Assert.assertTrue(run, outputs(run).contains("mail anna@example.com"));
        Assert.assertTrue(run, outputs(run).contains("still b2"));
    }

    @Test
    public void testADateAndANumberAreFormattedInTheSettingSuccess() throws Exception {
        String run = preview("preview_formats", "", getOrder(),
                print("after", "on $dateTool.format('dd.MM.yyyy', $dateTool.toDate(\\\"yyyy-MM-dd'T'HH:mm:ss\\\", $steps[\\\"Get order\\\"].data.created)) for $mathTool.round($steps[\\\"Get order\\\"].data.total)"),
                print("last", "$stringUtils.upperCase($steps[\\\"Get order\\\"].data.customer.email)"));
        Assert.assertTrue(run, outputs(run).contains("on 09.03.2026 for 1234"));
        Assert.assertTrue(run, outputs(run).contains("ANNA@EXAMPLE.COM"));
    }

    @Test
    public void testAMissingValueSaysWhatIsThereSuccess() throws Exception {
        String run = preview("preview_missing", "", getOrder(), print("after", "mail $steps[\\\"Get order\\\"].data.customer.mail"), print("last", "fine"));
        List<String> missing = JsonPath.read(run, "$.steps[2].missingValues");
        Assert.assertEquals(run, 1, missing.size());
        Assert.assertEquals("$steps[\"Get order\"].data.customer.mail has no value. There is no mail there. It has: email.", missing.get(0));
        Assert.assertEquals(run, List.of(), JsonPath.read(run, "$.steps[3].missingValues"));
    }

    @Test
    public void testAnEmptyValueAndAWrongKindAreNotedSuccess() throws Exception {
        String run = preview("preview_kinds", "", getOrder(), print("after", "note $steps[\\\"Get order\\\"].data.note end"), print("last", "sku $steps[\\\"Get order\\\"].data.total.sku"));
        List<String> emptyValue = JsonPath.read(run, "$.steps[2].missingValues");
        List<String> wrongKind = JsonPath.read(run, "$.steps[3].missingValues");
        Assert.assertEquals(run, List.of("$steps[\"Get order\"].data.note has no value. The field note is there and it is empty."), emptyValue);
        Assert.assertTrue(run, wrongKind.get(0).contains("is a number, which has no sku"));
    }

    @Test
    public void testAWrongStepNameAndAValueThatAToolCannotReadAreNotedSuccess() throws Exception {
        String run = preview("preview_wrong_name", "", getOrder(), print("after", "mail $steps[\\\"Get ordr\\\"].data.customer.email"), print("last", "sum $numberTool.format('#0.00', $steps[\\\"Get order\\\"].data.customer.email)"));
        List<String> wrongName = JsonPath.read(run, "$.steps[2].missingValues");
        List<String> wrongKind = JsonPath.read(run, "$.steps[3].missingValues");
        Assert.assertTrue(run, wrongName.get(0).endsWith("has no value. No step named Get ordr has run before this one. The steps that have: StartAction, Get order."));
        Assert.assertTrue(run, wrongKind.get(0).endsWith("has no value. format could not use the value that it got."));
    }

    @Test
    public void testAValueToUseWhenEmptyIsNotMissingSuccess() throws Exception {
        String run = preview("preview_fallback", "", getOrder(), print("after", "note ${steps[\\\"Get order\\\"].data.note|'none'} and $!steps[\\\"Get order\\\"].data.note."), print("last", "fine"));
        Assert.assertTrue(run, outputs(run).contains("note none and ."));
        Assert.assertEquals(run, List.of(), JsonPath.read(run, "$.steps[2].missingValues"));
    }

    @Test
    public void testAWriteIsShownAndNotSentSuccess() throws Exception {
        String run = preview("preview_holds", "", getOrder(), send("after", "POST", "{\\\"to\\\":\\\"$steps[\\\"Get order\\\"].data.customer.email\\\"}"), print("last", "after the write"));
        Assert.assertEquals(List.of("GET /order"), RECEIVED);
        Assert.assertEquals(run, "POST " + address + "/send\n{\"to\":\"anna@example.com\"}", JsonPath.read(run, "$.steps[2].held"));
        Assert.assertEquals(run, "SUCCESS", JsonPath.read(run, "$.steps[2].status"));
        Assert.assertTrue(run, outputs(run).contains("after the write"));
        Assert.assertEquals(run, true, JsonPath.read(run, "$.preview"));
    }

    @Test
    public void testAHeldStepGivesItsExampleAnswerSuccess() throws Exception {
        String run = preview("preview_example", "\"exampleAnswers\":{\"after_in\":\"{\\\"id\\\":5}\"}", getOrder(), send("after", "POST", "{}"), print("last", "made $steps[\\\"Send\\\"].data.id"));
        Assert.assertEquals(List.of("GET /order"), RECEIVED);
        Assert.assertTrue(run, outputs(run).contains("made 5"));
    }

    @Test
    public void testAPersonPreviewsAProposalAndNothingIsSentSuccess() throws Exception {
        String proposed = task("preview_proposal", "[" + START + "," + getOrder() + "," + send("after", "POST", "{}") + "," + print("last", "after the write") + "]", CHAIN);
        String checksum = JsonPath.read(this.mockMvc.perform(post("/task").requestAttr("dopplertask.caller", Caller.AGENT).content(proposed))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.checksum");

        // A step the person lets a preview send waits too, until the proposal is approved.
        String run = runToItsEnd("{\"taskName\":\"" + checksum + "\",\"parameters\":{},\"preview\":{\"sentForReal\":[\"after_in\"]}}");
        Assert.assertEquals(List.of("GET /order"), RECEIVED);
        Assert.assertEquals(run, "POST " + address + "/send\n{}", JsonPath.read(run, "$.steps[2].held"));
        Assert.assertTrue(run, outputs(run).contains("after the write"));

        String realRun = runToItsEnd("{\"taskName\":\"" + checksum + "\",\"parameters\":{}}");
        Assert.assertTrue(realRun, realRun.contains("\"FAILED\""));
        this.mockMvc.perform(post("/schedule/task").requestAttr("dopplertask.caller", Caller.AGENT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskName\":\"" + checksum + "\",\"parameters\":{},\"preview\":{}}")).andExpect(status().isForbidden());
        Assert.assertEquals(List.of("GET /order"), RECEIVED);
    }

    @Test
    public void testAStepThePersonChoseIsSentSuccess() throws Exception {
        String run = preview("preview_real", "\"sentForReal\":[\"after_in\"]", getOrder(), send("after", "POST", "{}"), print("last", "made $steps[\\\"Send\\\"].data.id"));
        Assert.assertEquals(List.of("GET /order", "POST /send"), RECEIVED);
        Assert.assertTrue(run, outputs(run).contains("made 77"));
        Assert.assertNull(run, JsonPath.read(run, "$.steps[2].held"));
    }

    @Test
    public void testAStepMadeToFailTakesTheFailurePathSuccess() throws Exception {
        String stops = preview("preview_fails", "\"madeToFail\":[\"first_in\"]", getOrder(), print("after", "never"), print("last", "never"));
        Assert.assertEquals(stops, "FAILED", JsonPath.read(stops, "$.status"));
        Assert.assertEquals(List.of(), RECEIVED);
        Assert.assertFalse(stops, outputs(stops).contains("never"));

        String goesOn = preview("preview_fails_on", "\"madeToFail\":[\"first_in\"]", getOrder().replace("\"url\"", "\"continueOnFailure\":true,\"url\""), print("after", "went on"), print("last", "done"));
        Assert.assertEquals(goesOn, "FINISHED", JsonPath.read(goesOn, "$.status"));
        Assert.assertTrue(goesOn, outputs(goesOn).contains("went on"));
    }

    @Test
    public void testAStepWithItsOwnWayOutIsShownAndNotRunSuccess() throws Exception {
        Path file = Files.createTempFile("preview", ".txt");
        Files.delete(file);
        String write = "{\"@type\":\"WriteFileAction\",\"actionName\":\"Keep\",\"filename\":\"" + file + "\",\"contents\":\"for $steps[\\\"Get order\\\"].data.customer.email\","
                + "\"ports\":[{\"externalId\":\"after_in\",\"portType\":\"INPUT\"},{\"externalId\":\"after_out\",\"portType\":\"OUTPUT\"}]}";
        String run = preview("preview_own_way", "", getOrder(), write, print("last", "done"));
        Assert.assertFalse(Files.exists(file));
        String held = JsonPath.read(run, "$.steps[2].held");
        Assert.assertTrue(held, held.contains("for anna@example.com"));
        Assert.assertTrue(run, outputs(run).contains("done"));
    }

    @Test
    public void testBranchesAndEachItemOfAListSuccess() throws Exception {
        String branch = "{\"@type\":\"IfAction\",\"condition\":\"$steps[\\\"Get order\\\"].data.total > 1000\",\"ports\":[{\"externalId\":\"if_in\",\"portType\":\"INPUT\"},{\"externalId\":\"if_true\",\"portType\":\"OUTPUT\"},{\"externalId\":\"if_false\",\"portType\":\"OUTPUT\"}]}";
        String each = "{\"@type\":\"LoopAction\",\"items\":\"$steps[\\\"Get order\\\"].output\",\"path\":\"items\",\"ports\":[{\"externalId\":\"loop_in\",\"portType\":\"INPUT\"},{\"externalId\":\"loop_0\",\"portType\":\"OUTPUT\"},{\"externalId\":\"loop_1\",\"portType\":\"OUTPUT\"}]}";
        String actions = "[" + START + "," + getOrder() + "," + branch + "," + print("small", "a small order") + "," + each + "," + send("item", "POST", "{\\\"sku\\\":\\\"$loop.item.sku\\\"}") + "]";
        String connections = "[" + wire("start", "first_in") + "," + wire("first_out", "if_in") + "," + wire("if_true", "loop_in") + "," + wire("if_false", "small_in") + "," + wire("loop_0", "item_in") + "]";
        String run = startRun("preview_branches", actions, connections, "{\"taskName\":\"preview_branches\",\"parameters\":{},\"preview\":{}}");
        List<String> held = JsonPath.read(run, "$.steps[?(@.portId == 'item_in')].held");
        Assert.assertEquals(run, List.of("POST " + address + "/send\n{\"sku\":\"a1\"}", "POST " + address + "/send\n{\"sku\":\"b2\"}"), held);
        Assert.assertFalse(run, outputs(run).contains("a small order"));
        Assert.assertEquals(List.of("GET /order"), RECEIVED);
    }

    @Test
    public void testARunThatIsNoPreviewSendsAsBeforeSuccess() throws Exception {
        String actions = "[" + START + "," + getOrder() + "," + send("after", "POST", "{}") + "," + print("last", "made $lastLog.output") + "]";
        String run = startRun("preview_not_one", actions, CHAIN, "{\"taskName\":\"preview_not_one\",\"parameters\":{}}");
        Assert.assertEquals(List.of("GET /order", "POST /send"), RECEIVED);
        Assert.assertEquals(run, false, JsonPath.read(run, "$.preview"));
        Assert.assertTrue(run, outputs(run).contains("made {\"id\":77}"));
    }

    @Test
    public void testAStepTestReadsEarlierStepsAndHoldsSuccess() throws Exception {
        String request = "{\"action\":" + send("after", "POST", "{\\\"to\\\":\\\"$steps[\\\"Get order\\\"].data.customer.email\\\",\\\"ref\\\":\\\"$steps[\\\"Get order\\\"].data.reference\\\"}") + ",\"parameters\":{},\"preview\":true,"
                + "\"steps\":{\"Get order\":{\"output\":" + new tools.jackson.databind.ObjectMapper().writeValueAsString(ORDER) + "}}}";
        String answer = this.mockMvc.perform(post("/task/action/test").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Assert.assertEquals(List.of(), RECEIVED);
        String held = JsonPath.read(answer, "$.held");
        Assert.assertTrue(answer, held.contains("\"to\":\"anna@example.com\""));
        List<String> missing = JsonPath.read(answer, "$.missingValues");
        Assert.assertTrue(answer, missing.get(0).contains("There is no reference there."));
    }

    /** Every text the editor writes for a guided choice, with what the server makes of it. Month names and number signs follow the server's language, so those are matched loosely. */
    @Test
    public void testTheGuidedFormsOfTheEditorSuccess() throws Exception {
        String data = "$steps[\"Get order\"].data";
        String[][] texts = {
                {"$dateTool.format(\"dd.MM.yyyy\", $dateTool.toDate(\"yyyy-MM-dd'T'HH:mm:ss\", " + data + ".created))", "09\\.03\\.2026"},
                {"$dateTool.format(\"yyyy-MM-dd\", $dateTool.toDate(\"yyyy-MM-dd'T'HH:mm:ssX\", " + data + ".zoned))", "2026-03-(08|09|10)"},
                {"$dateTool.format(\"MM/dd/yyyy\", $dateTool.toDate(\"yyyy-MM-dd'T'HH:mm:ss.SSSXXX\", " + data + ".offset))", "03/(08|09|10)/2026"},
                {"$dateTool.format(\"d MMMM yyyy\", $dateTool.toDate(\"yyyy-MM-dd\", " + data + ".day))", "9 \\p{L}+ 2026"},
                {"$dateTool.format(\"dd.MM.yyyy HH:mm\", $dateTool.toDate(\"yyyy-MM-dd HH:mm:ss\", " + data + ".spaced))", "09\\.03\\.2026 14:05"},
                {"$dateTool.format(\"HH:mm\", $dateTool.toDate(\"yyyy-MM-dd'T'HH:mm\", " + data + ".short))", "14:05"},
                {"$dateTool.format(\"EEEE d MMMM\", $dateTool.toDate(\"yyyy-MM-dd\", " + data + ".day))", "\\p{L}+ 9 \\p{L}+"},
                {"$numberTool.format('0', " + data + ".total)", "123[45]"},
                {"$numberTool.format('0.00', " + data + ".total)", "1234[.,]50"},
                {"$numberTool.format('#,##0', " + data + ".total)", "1\\P{N}23[45]"},
                {"$numberTool.format('#,##0.00', " + data + ".total)", "1\\P{N}234[.,]50"},
                {"$stringUtils.upperCase(" + data + ".name)", "  ANNA BERG "},
                {"$stringUtils.lowerCase(" + data + ".name)", "  anna berg "},
                {"$stringUtils.capitalize(" + data + ".customer.email)", "Anna@example\\.com"},
                {"$stringUtils.trim(" + data + ".name)", "anna Berg"},
                {"${steps[\"Get order\"].data.note|'it''s none'}", "it's none"},
                {"${steps[\"Get order\"].data.customer.email|'none'}", "anna@example\\.com"},
                {data + "[\"odd key\"] and " + data + ".items[1].sku and $loop.item.sku", "odd and b2 and b2"},
                {"$loop.number of $loop.count", "2 of 2"},
        };
        for (String[] text : texts) {
            String answer = testStep("{\"@type\":\"PrintAction\",\"message\":" + MAPPER.writeValueAsString(text[0]) + ",\"ports\":[]}");
            String output = JsonPath.read(answer, "$.output");
            Assert.assertTrue(text[0] + " gave " + answer, output.matches(text[1]));
            Assert.assertEquals(text[0] + " gave " + answer, List.of(), JsonPath.<List<String>>read(answer, "$.missingValues"));
        }
    }

    /** Every condition the editor writes from its plain words, with the branch the server takes. */
    @Test
    public void testTheConditionsOfTheEditorSuccess() throws Exception {
        String data = "$steps[\"Get order\"].data";
        String[][] conditions = {
                {data + ".customer.email == 'anna@example.com'", "true"},
                {data + ".customer.email == 'it''s'", "false"},
                {data + ".quote == 'it''s'", "true"},
                {data + ".customer.email != 'bo@example.com'", "true"},
                {data + ".customer.email.contains('@example')", "true"},
                {data + ".quote.contains('t''s')", "true"},
                {data + ".customer.email.contains('@other')", "false"},
                {data + ".total == 1234.5", "true"},
                {data + ".total == '1234.5'", "true"},
                {data + ".total != '1234.5'", "false"},
                {data + ".total != 1234.5", "false"},
                {data + ".total > 1000", "true"},
                {data + ".total < 1000", "false"},
                {data + ".customer.email", "true"},
                {data + ".note", "false"},
                {"!" + data + ".note", "true"},
                {"!" + data + ".customer.email", "false"},
                {data + ".paid", "true"},
                {data + ".paid == true", "true"},
                {"$loop.item.sku == 'b2'", "true"},
        };
        for (String[] condition : conditions) {
            String answer = testStep("{\"@type\":\"IfAction\",\"condition\":" + MAPPER.writeValueAsString(condition[0]) + ",\"ports\":[]}");
            Assert.assertEquals(condition[0] + " gave " + answer, condition[1], JsonPath.read(answer, "$.output"));
        }
    }

    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();
    private static final String ORDER_FOR_FORMS = "{\"customer\":{\"email\":\"anna@example.com\"},\"name\":\"  anna Berg \",\"quote\":\"it's\",\"created\":\"2026-03-09T14:05:00\",\"zoned\":\"2026-03-09T12:00:00Z\",\"offset\":\"2026-03-09T12:00:00.123+02:00\","
            + "\"day\":\"2026-03-09\",\"spaced\":\"2026-03-09 14:05:00\",\"short\":\"2026-03-09T14:05\",\"total\":1234.5,\"paid\":true,\"odd key\":\"odd\",\"items\":[{\"sku\":\"a1\"},{\"sku\":\"b2\"}],\"note\":null}";

    /** Tests one step on its own as a preview, after a step named Get order, inside a pass of a loop, as the editor does for a preview. */
    private String testStep(String action) throws Exception {
        String request = "{\"action\":" + action + ",\"parameters\":{},\"preview\":true,\"loopItem\":\"{\\\"sku\\\":\\\"b2\\\"}\",\"loopIndex\":1,\"loopCount\":2,\"steps\":{\"Get order\":{\"output\":" + MAPPER.writeValueAsString(ORDER_FOR_FORMS) + ",\"outputVariables\":{\"statusCode\":\"200\"}}}}";
        return this.mockMvc.perform(post("/task/action/test").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static final String START = "{\"@type\":\"StartAction\",\"ports\":[{\"externalId\":\"start\",\"portType\":\"OUTPUT\"}]}";
    private static final String CHAIN = "[" + wire("start", "first_in") + "," + wire("first_out", "after_in") + "," + wire("after_out", "last_in") + "]";

    private static String wire(String from, String to) {
        return "{\"source\":{\"externalId\":\"" + from + "\"},\"target\":{\"externalId\":\"" + to + "\"}}";
    }

    private static String getOrder() {
        return "{\"@type\":\"HttpAction\",\"actionName\":\"Get order\",\"method\":\"GET\",\"url\":\"" + address + "/order\",\"ports\":[{\"externalId\":\"first_in\",\"portType\":\"INPUT\"},{\"externalId\":\"first_out\",\"portType\":\"OUTPUT\"}]}";
    }

    private static String send(String id, String method, String body) {
        return "{\"@type\":\"HttpAction\",\"actionName\":\"Send\",\"method\":\"" + method + "\",\"url\":\"" + address + "/send\",\"body\":\"" + body + "\",\"ports\":[{\"externalId\":\"" + id + "_in\",\"portType\":\"INPUT\"},{\"externalId\":\"" + id + "_out\",\"portType\":\"OUTPUT\"}]}";
    }

    private static String print(String id, String message) {
        return "{\"@type\":\"PrintAction\",\"message\":\"" + message + "\",\"ports\":[{\"externalId\":\"" + id + "_in\",\"portType\":\"INPUT\"},{\"externalId\":\"" + id + "_out\",\"portType\":\"OUTPUT\"}]}";
    }

    /** Runs three steps in a row as a preview, through the queue as the editor does, and gives the run once it has ended. */
    private String preview(String name, String choices, String first, String after, String last) throws Exception {
        return startRun(name, "[" + START + "," + first + "," + after + "," + last + "]", CHAIN, "{\"taskName\":\"" + name + "\",\"parameters\":{},\"preview\":{" + choices + "}}");
    }

    private static String task(String name, String actions, String connections) {
        return "{\"name\":\"" + name + "\",\"description\":\"\",\"active\":false,\"actions\":" + actions + ",\"connections\":" + connections + "}";
    }

    private String startRun(String name, String actions, String connections, String request) throws Exception {
        this.mockMvc.perform(post("/task").content(task(name, actions, connections))).andExpect(status().isOk());
        return runToItsEnd(request);
    }

    private String runToItsEnd(String request) throws Exception {
        String answer = this.mockMvc.perform(post("/schedule/task").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Object executionId = JsonPath.read(answer, "$.id");
        String run = "";
        for (int attempt = 0; attempt < 100 && !run.contains("\"FINISHED\"") && !run.contains("\"FAILED\""); attempt++) {
            Thread.sleep(100);
            run = this.mockMvc.perform(get("/execution/" + executionId)).andReturn().getResponse().getContentAsString();
        }
        return run;
    }

    private static List<String> outputs(String run) {
        return JsonPath.read(run, "$.output");
    }
}
