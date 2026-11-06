package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.ActionPort;
import com.dopplertask.dopplertask.domain.ActionPortType;
import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.Connection;
import com.dopplertask.dopplertask.domain.StatusCode;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.common.ExecuteCommandAction;
import com.dopplertask.dopplertask.domain.action.common.IfAction;
import com.dopplertask.dopplertask.domain.action.common.PrintAction;
import com.dopplertask.dopplertask.domain.action.common.RegexAction;
import com.dopplertask.dopplertask.domain.action.common.XMLAction;
import com.dopplertask.dopplertask.domain.action.common.XMLActionType;
import com.dopplertask.dopplertask.service.TaskServiceImpl;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;

import org.apache.velocity.app.VelocityEngine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;

@RunWith(SpringRunner.class)
public class ActionTest {

    private VariableExtractorUtil variableExtractorUtil;

    @Before
    public void startUp() {
        VelocityEngine engine = new VelocityEngine();
        variableExtractorUtil = new VariableExtractorUtil(engine);
    }

    @Test
    public void testIfActionWithNoConditionShouldReturnFalseKotlin() throws IOException {
        IfAction ifAction = new IfAction();
        PrintAction printActionTrue = new PrintAction();
        printActionTrue.setMessage("True path");

        PrintAction printActionFalse = new PrintAction();
        printActionFalse.setMessage("False path");

        TaskExecution taskExecution = new TaskExecution();

        Connection connectionTrue = new Connection();
        connectionTrue.setTarget(new ActionPort() {{
            setAction(printActionTrue);
        }});

        Connection connectionFalse = new Connection();
        connectionFalse.setTarget(new ActionPort() {{
            setAction(printActionFalse);
        }});


        ActionPort output1 = new ActionPort();
        output1.setPortType(ActionPortType.OUTPUT);
        output1.setConnectionSource(connectionTrue);
        output1.setAction(ifAction);

        ActionPort output2 = new ActionPort();
        output2.setPortType(ActionPortType.OUTPUT);
        output2.setConnectionSource(connectionFalse);
        output2.setAction(ifAction);


        ifAction.setPorts(List.of(output1, output2));


        ifAction.setCondition(null);

        ActionResult actionResult = ifAction.run(new TaskServiceImpl(), taskExecution, variableExtractorUtil, (output, outputType) -> {
        });
        Assert.assertEquals("Please enter a condition.", actionResult.getErrorMsg());
    }

    @Test
    public void testIfActionWithEmptyConditionShouldReturnFalseKotlin() throws IOException {
        IfAction ifAction = new IfAction();
        PrintAction printActionTrue = new PrintAction();
        printActionTrue.setMessage("True path");

        PrintAction printActionFalse = new PrintAction();
        printActionFalse.setMessage("False path");

        TaskExecution taskExecution = new TaskExecution();

        Connection connectionTrue = new Connection();
        connectionTrue.setTarget(new ActionPort() {{
            setAction(printActionTrue);
        }});

        Connection connectionFalse = new Connection();
        connectionFalse.setTarget(new ActionPort() {{
            setAction(printActionFalse);
        }});


        ActionPort output1 = new ActionPort();
        output1.setPortType(ActionPortType.OUTPUT);
        output1.setConnectionSource(connectionTrue);
        output1.setAction(ifAction);

        ActionPort output2 = new ActionPort();
        output2.setPortType(ActionPortType.OUTPUT);
        output2.setConnectionSource(connectionFalse);
        output2.setAction(ifAction);


        ifAction.setPorts(List.of(output1, output2));


        ifAction.setCondition("");

        ActionResult actionResult = ifAction.run(new TaskServiceImpl(), taskExecution, variableExtractorUtil, (output, outputType) -> {
        });
        Assert.assertEquals("Please enter a condition.", actionResult.getErrorMsg());
    }


    @Test
    public void testIfActionShouldReturnTrue() throws IOException {
        IfAction ifAction = new IfAction();
        PrintAction printActionTrue = new PrintAction();
        printActionTrue.setMessage("True path");

        PrintAction printActionFalse = new PrintAction();
        printActionFalse.setMessage("False path");

        TaskExecution taskExecution = new TaskExecution();

        Connection connectionTrue = new Connection();
        connectionTrue.setTarget(new ActionPort() {{
            setAction(printActionTrue);
        }});

        Connection connectionFalse = new Connection();
        connectionFalse.setTarget(new ActionPort() {{
            setAction(printActionFalse);
        }});


        ActionPort output1 = new ActionPort();
        output1.setPortType(ActionPortType.OUTPUT);
        output1.setConnectionSource(connectionTrue);
        output1.setAction(ifAction);

        ActionPort output2 = new ActionPort();
        output2.setPortType(ActionPortType.OUTPUT);
        output2.setConnectionSource(connectionFalse);
        output2.setAction(ifAction);


        ifAction.setPorts(List.of(output1, output2));


        ifAction.setCondition("'test' == 'test'");

        ActionResult actionResult = ifAction.run(new TaskServiceImpl(), taskExecution, variableExtractorUtil, (output, outputType) -> {
        });
        Assert.assertEquals("true", actionResult.getOutput());
        Assert.assertEquals("True path", ((PrintAction) taskExecution.getCurrentAction()).getMessage());
    }

    @Test
    public void testIfActionShouldReturnFalse() throws IOException {
        IfAction ifAction = new IfAction();
        PrintAction printActionTrue = new PrintAction();
        printActionTrue.setMessage("True path");

        PrintAction printActionFalse = new PrintAction();
        printActionFalse.setMessage("False path");

        TaskExecution taskExecution = new TaskExecution();

        Connection connectionTrue = new Connection();
        connectionTrue.setTarget(new ActionPort() {{
            setAction(printActionTrue);
        }});

        Connection connectionFalse = new Connection();
        connectionFalse.setTarget(new ActionPort() {{
            setAction(printActionFalse);
        }});


        ActionPort output1 = new ActionPort();
        output1.setPortType(ActionPortType.OUTPUT);
        output1.setConnectionSource(connectionTrue);
        output1.setAction(ifAction);

        ActionPort output2 = new ActionPort();
        output2.setPortType(ActionPortType.OUTPUT);
        output2.setConnectionSource(connectionFalse);
        output2.setAction(ifAction);


        ifAction.setPorts(List.of(output1, output2));

        ifAction.setCondition("'test' == 'test_false'");

        ActionResult actionResult = ifAction.run(new TaskServiceImpl(), taskExecution, variableExtractorUtil);
        Assert.assertEquals("false", actionResult.getOutput());
        Assert.assertEquals("False path", ((PrintAction) taskExecution.getCurrentAction()).getMessage());
    }

    @Test
    public void testXMLActionXMLToJSON() throws IOException {
        TaskExecution taskExecution = new TaskExecution();

        XMLAction xmlAction = new XMLAction();
        xmlAction.setContent("<xml><doppler style=\"blue\"></doppler><example>Some text</example></xml>");
        xmlAction.setType(XMLActionType.XML_TO_JSON);

        ActionResult actionResult = xmlAction.run(new TaskServiceImpl(), taskExecution, variableExtractorUtil);
        Assert.assertEquals("{\"doppler\":{\"style\":\"blue\"},\"example\":\"Some text\"}", actionResult.getOutput());
    }

    @Test
    public void testXMLActionJSONToXML() throws IOException {
        TaskExecution taskExecution = new TaskExecution();

        XMLAction xmlAction = new XMLAction();
        xmlAction.setContent("{\"doppler\":{\"style\":\"blue\"},\"example\":\"Some text\"}");
        xmlAction.setType(XMLActionType.JSON_TO_XML);

        ActionResult actionResult = xmlAction.run(new TaskServiceImpl(), taskExecution, variableExtractorUtil);
        Assert.assertEquals("<xml><doppler><style>blue</style></doppler><example>Some text</example></xml>", actionResult.getOutput());
    }


    @Test
    public void testRegexFindsTheWholeMatchWithoutACaptureGroup() throws IOException {
        ActionResult result = new RegexAction("[0-9]+", "abc123").run(new TaskServiceImpl(), new TaskExecution(), variableExtractorUtil, null);
        Assert.assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        Assert.assertEquals(List.of("123"), result.getOutputVariables().get("matches"));
    }

    @Test
    public void testRegexKeepsReturningTheFirstCaptureGroup() throws IOException {
        ActionResult result = new RegexAction("id=([0-9]+)", "id=42").run(new TaskServiceImpl(), new TaskExecution(), variableExtractorUtil, null);
        Assert.assertEquals(List.of("42"), result.getOutputVariables().get("matches"));
    }

    @Test
    public void testRegexExplainsAnInvalidPattern() throws IOException {
        ActionResult result = new RegexAction("[0-9", "abc").run(new TaskServiceImpl(), new TaskExecution(), variableExtractorUtil, null);
        Assert.assertEquals(StatusCode.FAILURE, result.getStatusCode());
        Assert.assertTrue(result.getErrorMsg().startsWith("Regex is not valid: "));
    }

    @Test
    public void testCommandFailureNamesTheExitCodeAndKeepsTheOutput() throws IOException {
        ExecuteCommandAction action = new ExecuteCommandAction();
        action.setCommand("echo partial; exit 3");
        ActionResult result = action.run(new TaskServiceImpl(), new TaskExecution(), variableExtractorUtil, null);
        Assert.assertEquals(StatusCode.FAILURE, result.getStatusCode());
        Assert.assertEquals("The command exited with code 3.", result.getErrorMsg());
        Assert.assertTrue(result.getOutput().contains("partial"));
    }
}
