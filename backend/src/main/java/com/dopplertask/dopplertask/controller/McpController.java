package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.domain.Preview;
import com.dopplertask.dopplertask.dto.TaskRequestDTO;
import com.dopplertask.dopplertask.filter.AccessFilter;
import com.dopplertask.dopplertask.service.CredentialProblemException;
import com.dopplertask.dopplertask.service.CredentialService;
import com.dopplertask.dopplertask.filter.Caller;
import com.dopplertask.dopplertask.service.ApprovalRequiredException;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lets any agent that speaks the Model Context Protocol work with tasks. Every tool goes through the same controller
 * methods as the API, so an agent can do here exactly what its token allows there, and nothing more.
 */
@RestController
public class McpController {

    private static final String LATEST_PROTOCOL = "2025-06-18";
    private static final List<String> PROTOCOLS = List.of(LATEST_PROTOCOL, "2025-03-26");
    static final String INSTRUCTIONS = "DopplerTask runs automations on the machine it is installed on. What you save with propose_task is a proposal: "
            + "nothing runs until a person approves it in the editor, so tell the person when one is ready. Start with list_step_types and get_step_type. "
            + "When changing a task, read it with get_task and keep the externalId of its ports, so that the person sees what you changed. "
            + "Steps never hold passwords or keys. Set credential on a step to a name from list_credentials whose steps include that step. You can use a credential but not read or create one, so ask a person when none fits. "
            + "Settings go directly on the action. Values in settings are written in Velocity: $parameters.get(\"name\") reads a task parameter, $lastLog.output the output of the previous step, "
            + "and $lastLog.outputVariables.name what the step before gives, which get_step_type lists under outputs, such as statusCode for an HTTP step. A condition is what goes inside #if( ), such as $lastLog.output.contains(\"ok\"). "
            + "An HTTP status of 400 or above fails the step: to branch on it, set continueOnFailure to true on that step and read the status in the next one. "
            + "$result is only available in failOn. An unknown variable is empty rather than an error, so read the warnings from propose_task.";

    private static final List<String> OPTIONAL_ARGUMENTS = List.of("parameters", "resumeFromExecutionId", "checksum", "preview");
    private static final Pattern VELOCITY_VARIABLE = Pattern.compile("\\$!?\\{?([a-zA-Z_]\\w*)");
    private static final Set<String> RUN_VARIABLES = Set.of("parameters", "lastLog", "logs", "executionId", "steps", "loop", "loops");
    // In these, $HOME and the like belong to the shell or the script.
    private static final Set<String> FIELDS_WITH_THEIR_OWN_VARIABLES = Set.of("command", "script");

    private final TaskController tasks;
    private final ExecutionController executions;
    private final CredentialController credentials;
    private final CredentialService credentialNames;
    private final ObjectMapper mapper;
    private final Set<String> velocityTools;

    public McpController(TaskController tasks, ExecutionController executions, CredentialController credentials, CredentialService credentialNames, ObjectMapper mapper, VariableExtractorUtil variables) {
        this.velocityTools = variables.getVelocityTools().keySet();
        this.tasks = tasks;
        this.executions = executions;
        this.credentials = credentials;
        this.credentialNames = credentialNames;
        this.mapper = mapper;
    }

    @PostMapping("/mcp")
    public ResponseEntity<JsonNode> handle(@RequestBody JsonNode message, HttpServletRequest request) {
        if (AccessFilter.callerOf(request) != Caller.AGENT) {
            ObjectNode refusal = mapper.createObjectNode().put("message", "This endpoint is for agents. Connect with the agent token, set with DOPPLERTASK_AGENT_TOKEN.");
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
        result.putObject("serverInfo").put("name", "dopplertask").put("version", "0.15.0");
        return result.put("instructions", INSTRUCTIONS);
    }

    ArrayNode toolList() {
        ArrayNode tools = mapper.createArrayNode();
        tools.add(tool("list_step_types", "List every kind of step a task can use, with a description of each.", Map.of()));
        tools.add(tool("get_step_type", "Get the settings of one kind of step.", Map.of("name", "string")));
        tools.add(tool("list_credentials", "List the credentials that steps can use, with the steps each one fits. Their values are never shown.", Map.of()));
        tools.add(tool("list_tasks", "List saved tasks with their versions. A version with proposed set to true waits for a person.", Map.of()));
        tools.add(tool("get_task", "Read the current version of a task, or a specific version such as a proposal with its checksum.", Map.of("name", "string", "checksum", "string")));
        tools.add(tool("propose_task", "Save a new task, or a change to one, as a proposal for a person to approve. The task has a name without spaces, active set to false, "
                + "parameters (a list), actions and connections. Actions have @type, their settings and ports, where a port is {\"externalId\": \"any-id\", \"portType\": \"INPUT\" or \"OUTPUT\"}. A parameter is {\"name\": \"...\", \"required\": false}. One action must be a StartAction with one OUTPUT port, and other actions have one INPUT port and an OUTPUT port for what follows. "
                + "A connection joins them: {\"source\": {\"externalId\": \"...\"}, \"target\": {\"externalId\": \"...\"}}. An IfAction has two OUTPUT ports, first for true. A SwitchAction has value and switchCases: [{\"currentCase\": \"...\"}], with one OUTPUT port per case and then one for the default. A LoopAction has two OUTPUT ports, first for each item and then for Done, and a pass ends where its steps end, reading $loop.item. A Webhook with responseMode replyStep answers with what a WebhookReplyAction sends. The answer lists warnings to fix.", Map.of("task", "object")));
        tools.add(tool("run_task", "Run the approved version of a task and wait for the result. A proposal cannot run. With resumeFromExecutionId it continues a failed run at the step that failed, without repeating earlier steps, or a waiting run that a person has answered. A run that goes on after a person's answer keeps exactly the values it had, so give it no parameters or the same values. "
                        + "With preview set to true the run sends nothing: steps that read run as usual, and get_run shows for each other step what it would have sent (held) and the values its settings asked for that nothing gave (missingValues).",
                Map.of("name", "string", "parameters", "object", "resumeFromExecutionId", "number", "preview", "boolean")));
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
        properties.keySet().stream().filter(property -> !OPTIONAL_ARGUMENTS.contains(property)).forEach(required::add);
        return tool;
    }

    ObjectNode callTool(String name, JsonNode arguments, HttpServletRequest request) {
        try {
            ResponseEntity<?> answer = switch (name) {
                case "list_step_types", "get_step_type" -> tasks.getAllActions();
                case "list_credentials" -> ResponseEntity.ok(credentials.list());
                case "list_tasks" -> tasks.getTasksGrouped();
                case "get_task" -> arguments.hasNonNull("checksum") ? tasks.getTaskByChecksum(arguments.get("checksum").asText()) : tasks.getTask(arguments.path("name").asText());
                case "propose_task" -> tasks.createTask(mapper.writeValueAsString(arguments.path("task")), false, request);
                case "run_task" -> tasks.runTask(runRequest(arguments), request);
                case "get_run" -> executions.getTask(arguments.path("id").asLong());
                case "list_runs" -> tasks.getExecutions(arguments.path("name").asText(), 20);
                default -> throw new IllegalArgumentException("There is no tool called " + name + ".");
            };
            if (!answer.getStatusCode().is2xxSuccessful()) {
                return toolResult("That failed with status " + answer.getStatusCode().value() + ". Check the name or id.", true);
            }
            JsonNode body = shaped(name, arguments, mapper.valueToTree(answer.getBody()));
            if (name.equals("propose_task")) {
                List<String> warnings = unknownVariables(arguments.path("task"));
                warnings.addAll(unknownCredentials(arguments.path("task")));
                ((ObjectNode) body).set("warnings", mapper.valueToTree(warnings));
            }
            return toolResult(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(body), false);
        } catch (ApprovalRequiredException | CredentialProblemException | IllegalArgumentException e) {
            return toolResult(e.getMessage(), true);
        } catch (Exception e) {
            return toolResult("That failed: " + e.getMessage(), true);
        }
    }

    private List<String> unknownCredentials(JsonNode task) {
        Map<String, List<String>> known = new java.util.HashMap<>();
        credentialNames.list().forEach(credential -> known.put(credential.name(), credential.steps()));
        List<String> warnings = new ArrayList<>();
        task.path("actions").forEach(step -> {
            String credential = step.path("credential").asText("");
            String stepType = step.path("@type").asText();
            if (!credential.isEmpty() && !known.getOrDefault(credential, List.of()).contains(stepType)) {
                warnings.add(stepType + " credential: \"" + credential + "\" is not a credential for this kind of step, so it will fail when it runs. Check list_credentials.");
            }
        });
        return warnings;
    }

    /**
     * A variable Velocity does not know gives no error, so a wrong name in a condition gives a task that runs and
     * always takes the same branch. A person reviewing it may not see that either, so the agent is told in time.
     */
    private List<String> unknownVariables(JsonNode task) {
        List<String> warnings = new ArrayList<>();
        task.path("actions").forEach(step -> {
            if ("JAVASCRIPT".equals(step.path("scriptLanguage").asText())) {
                return;
            }
            step.properties().forEach(field -> {
                if (field.getValue().isTextual() && !FIELDS_WITH_THEIR_OWN_VARIABLES.contains(field.getKey())) {
                    String text = field.getValue().asText();
                    Matcher variables = VELOCITY_VARIABLE.matcher(text);
                    while (variables.find()) {
                        String variable = variables.group(1);
                        boolean known = RUN_VARIABLES.contains(variable) || velocityTools.contains(variable)
                                || (variable.equals("result") && field.getKey().equals("failOn")) || text.contains("#set($" + variable);
                        if (!known) {
                            warnings.add(step.path("@type").asText() + " " + field.getKey() + ": $" + variable + " does not exist here, so it would not get a value when the task runs.");
                        }
                    }
                }
            });
        });
        return warnings;
    }

    private TaskRequestDTO runRequest(JsonNode arguments) {
        TaskRequestDTO run = new TaskRequestDTO();
        run.setTaskName(arguments.path("name").asText());
        run.setParameters(arguments.has("parameters") ? mapper.convertValue(arguments.get("parameters"), Map.class) : Map.of());
        if (arguments.hasNonNull("resumeFromExecutionId")) {
            run.setResumeFromExecutionId(arguments.get("resumeFromExecutionId").asLong());
        }
        if (arguments.path("preview").asBoolean()) {
            run.setPreview(new Preview());
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
