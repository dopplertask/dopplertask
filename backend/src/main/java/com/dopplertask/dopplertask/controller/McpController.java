package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.dto.TaskRequestDTO;
import com.dopplertask.dopplertask.filter.AccessFilter;
import com.dopplertask.dopplertask.filter.AgentSecrets;
import com.dopplertask.dopplertask.filter.Caller;
import com.dopplertask.dopplertask.service.ApprovalRequiredException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Lets any agent that speaks the Model Context Protocol work with tasks. Every tool goes through the same controller
 * methods as the API, so an agent can do here exactly what its token allows there, and nothing more.
 */
@RestController
public class McpController {

    private static final String LATEST_PROTOCOL = "2025-06-18";
    private static final List<String> PROTOCOLS = List.of(LATEST_PROTOCOL, "2025-03-26");
    private static final String INSTRUCTIONS = "DopplerTask runs automations on the machine it is installed on. What you save with propose_task is a proposal: "
            + "nothing runs until a person approves it in the editor, so tell the person when one is ready. Start with list_step_types and get_step_type. "
            + "When changing a task, read it with get_task and keep the externalId of its ports, so that the person sees what you changed. "
            + "Passwords and keys read as ********, and sending that back keeps the real value.";

    private final TaskController tasks;
    private final ExecutionController executions;
    private final AgentSecrets agentSecrets;
    private final ObjectMapper mapper;

    public McpController(TaskController tasks, ExecutionController executions, AgentSecrets agentSecrets, ObjectMapper mapper) {
        this.tasks = tasks;
        this.executions = executions;
        this.agentSecrets = agentSecrets;
        this.mapper = mapper;
    }

    @PostMapping("/mcp")
    public ResponseEntity<JsonNode> handle(@RequestBody JsonNode message, HttpServletRequest request) {
        if (AccessFilter.callerOf(request) != Caller.AGENT) {
            ObjectNode refusal = mapper.createObjectNode().put("message", "This endpoint is for agents. Set DOPPLERTASK_PERSON_TOKEN and DOPPLERTASK_AGENT_TOKEN, and connect with the agent token.");
            return new ResponseEntity<>(refusal, HttpStatus.FORBIDDEN);
        }
        // A notification has no id and expects no answer.
        if (!message.has("id")) {
            return new ResponseEntity<>(HttpStatus.ACCEPTED);
        }
        ObjectNode response = mapper.createObjectNode().put("jsonrpc", "2.0").set("id", message.get("id"));
        JsonNode params = message.path("params");
        switch (message.path("method").asText()) {
            case "initialize" -> response.set("result", initialize(params));
            case "ping" -> response.set("result", mapper.createObjectNode());
            case "tools/list" -> response.set("result", mapper.createObjectNode().set("tools", toolList()));
            case "tools/call" -> response.set("result", callTool(params.path("name").asText(), params.path("arguments"), request));
            default -> response.set("error", mapper.createObjectNode().put("code", -32601).put("message", "Unknown method " + message.path("method").asText()));
        }
        return ResponseEntity.ok(response);
    }

    private ObjectNode initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText();
        ObjectNode result = mapper.createObjectNode().put("protocolVersion", PROTOCOLS.contains(requested) ? requested : LATEST_PROTOCOL);
        result.putObject("capabilities").putObject("tools");
        result.putObject("serverInfo").put("name", "dopplertask").put("version", "0.14.7");
        return result.put("instructions", INSTRUCTIONS);
    }

    private ArrayNode toolList() {
        ArrayNode tools = mapper.createArrayNode();
        tools.add(tool("list_step_types", "List every kind of step a task can use, with a description of each.", Map.of()));
        tools.add(tool("get_step_type", "Get the settings of one kind of step.", Map.of("name", "string")));
        tools.add(tool("list_tasks", "List saved tasks with their versions. A version with proposed set to true waits for a person.", Map.of()));
        tools.add(tool("get_task", "Read the current version of a task.", Map.of("name", "string")));
        tools.add(tool("propose_task", "Save a new task, or a change to one, as a proposal for a person to approve. The task has a name without spaces, active set to false, "
                + "parameters (a list), actions and connections. Actions have @type and ports. One action must be a StartAction with one OUTPUT port, and other actions have one INPUT port and an OUTPUT port for what follows. "
                + "A connection joins them: {\"source\": {\"externalId\": \"...\"}, \"target\": {\"externalId\": \"...\"}}. An IfAction has two OUTPUT ports, first for true.", Map.of("task", "object")));
        tools.add(tool("run_task", "Run the approved version of a task and wait for the result. A proposal cannot run. With resumeFromExecutionId it continues a failed run at the step that failed, without repeating earlier steps.",
                Map.of("name", "string", "parameters", "object", "resumeFromExecutionId", "number")));
        tools.add(tool("get_run", "Read the status of a run and the result of each step.", Map.of("id", "number")));
        tools.add(tool("list_runs", "List the latest runs of a task, newest first.", Map.of("name", "string")));
        return tools;
    }

    private ObjectNode tool(String name, String description, Map<String, String> properties) {
        ObjectNode tool = mapper.createObjectNode().put("name", name).put("description", description);
        ObjectNode schema = tool.putObject("inputSchema").put("type", "object");
        ObjectNode schemaProperties = schema.putObject("properties");
        properties.forEach((property, type) -> schemaProperties.putObject(property).put("type", type));
        ArrayNode required = schema.putArray("required");
        properties.keySet().stream().filter(property -> !List.of("parameters", "resumeFromExecutionId").contains(property)).forEach(required::add);
        return tool;
    }

    private ObjectNode callTool(String name, JsonNode arguments, HttpServletRequest request) {
        try {
            ResponseEntity<?> answer = switch (name) {
                case "list_step_types", "get_step_type" -> tasks.getAllActions();
                case "list_tasks" -> tasks.getTasksGrouped();
                case "get_task" -> tasks.getTask(arguments.path("name").asText());
                case "propose_task" -> tasks.createTask(mapper.writeValueAsString(arguments.path("task")), false, request);
                case "run_task" -> tasks.runTask(runRequest(arguments), request);
                case "get_run" -> executions.getTask(arguments.path("id").asLong());
                case "list_runs" -> tasks.getExecutions(arguments.path("name").asText(), 20);
                default -> throw new IllegalArgumentException("There is no tool called " + name + ".");
            };
            if (!answer.getStatusCode().is2xxSuccessful()) {
                return toolResult("That failed with status " + answer.getStatusCodeValue() + ". Check the name or id.", true);
            }
            JsonNode body = shaped(name, arguments, agentSecrets.masked(answer.getBody()));
            return toolResult(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(body), false);
        } catch (ApprovalRequiredException | IllegalArgumentException e) {
            return toolResult(e.getMessage(), true);
        } catch (Exception e) {
            return toolResult("That failed: " + e.getMessage(), true);
        }
    }

    private TaskRequestDTO runRequest(JsonNode arguments) {
        TaskRequestDTO run = new TaskRequestDTO();
        run.setTaskName(arguments.path("name").asText());
        run.setParameters(arguments.has("parameters") ? mapper.convertValue(arguments.get("parameters"), Map.class) : Map.of());
        if (arguments.hasNonNull("resumeFromExecutionId")) {
            run.setResumeFromExecutionId(arguments.get("resumeFromExecutionId").asLong());
        }
        return run;
    }

    // The whole catalogue is large, so an agent gets a list first and asks for the details it needs.
    private JsonNode shaped(String tool, JsonNode arguments, JsonNode body) {
        if (tool.equals("list_step_types")) {
            ArrayNode summary = mapper.createArrayNode();
            body.path("actions").forEach(step -> summary.addObject().put("name", step.path("name").asText()).put("description", step.path("description").asText()));
            return summary;
        }
        if (tool.equals("get_step_type")) {
            for (JsonNode step : body.path("actions")) {
                if (step.path("name").asText().equals(arguments.path("name").asText())) {
                    return step;
                }
            }
            throw new IllegalArgumentException("There is no step type called " + arguments.path("name").asText() + ". Use list_step_types.");
        }
        return body;
    }

    private ObjectNode toolResult(String text, boolean isError) {
        ObjectNode result = mapper.createObjectNode().put("isError", isError);
        result.putArray("content").addObject().put("type", "text").put("text", text);
        return result;
    }
}
