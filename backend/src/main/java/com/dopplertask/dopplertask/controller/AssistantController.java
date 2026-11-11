package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.filter.AccessFilter;
import com.dopplertask.dopplertask.filter.Caller;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Build with AI: a person says what they want, and a model builds it with the tools an outside agent gets. It works
 * as an agent, so what it makes is a proposal the person approves, and a run it starts is always a preview.
 * Each step it takes is sent as it happens, so the person sees it work and the connection stays busy.
 */
@RestController
public class AssistantController {

    private static final int MAX_ROUNDS = 12;
    private static final int MAX_MESSAGES = 20;
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final String GUIDANCE = " You work for the person in the DopplerTask editor, who sees your proposal on screen and approves or rejects it there."
            + " Build what they ask with propose_task. When the task is ready, say in one or two short sentences what it does and what the person still has to fill in, such as a credential."
            + " To try a task, use run_task: it always runs as a preview that sends nothing. Answer in plain words, and ask when the request is unclear.";
    private static final Map<String, String> PROGRESS = Map.of(
            "list_step_types", "Looking at the kinds of step",
            "get_step_type", "Reading how a step works",
            "list_credentials", "Checking the credentials",
            "list_tasks", "Looking at your tasks",
            "get_task", "Reading the task",
            "propose_task", "Proposing the task",
            "run_task", "Running a preview",
            "get_run", "Reading the preview",
            "list_runs", "Looking at the runs");

    private final McpController tools;
    private final ObjectMapper mapper;
    private final String key;
    private final String model;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public AssistantController(McpController tools, ObjectMapper mapper, @Value("${dopplertask.assistant.openAiKey:}") String key,
                               @Value("${dopplertask.assistant.model:gpt-5}") String model, @Value("${dopplertask.assistant.baseUrl:https://api.openai.com/v1}") String baseUrl) {
        this.tools = tools;
        this.mapper = mapper;
        this.key = key;
        this.model = model;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** Without a key there is no assistant, and the editor leaves its buttons out. */
    @GetMapping("/assistant")
    public Map<String, Object> status() {
        return Map.of("ready", !key.isBlank());
    }

    @PostMapping(path = "/assistant", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void ask(@RequestBody JsonNode body, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (key.isBlank()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "The assistant has no key. Set DOPPLERTASK_ASSISTANT_OPENAI_KEY.");
            return;
        }
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        PrintWriter out = response.getWriter();
        HttpServletRequest asAgent = new AgentView(request);
        AccessFilter.signIn(asAgent, Caller.AGENT, null);
        ArrayNode messages = conversation(body);
        ArrayNode functions = functions();
        try {
            for (int round = 0; round < MAX_ROUNDS; round++) {
                JsonNode reply = complete(messages, functions);
                JsonNode calls = reply.path("tool_calls");
                ObjectNode said = messages.addObject().put("role", "assistant").put("content", reply.path("content").asText(""));
                if (calls.isEmpty()) {
                    send(out, Map.of("type", "answer", "text", reply.path("content").asText("")));
                    return;
                }
                said.set("tool_calls", calls);
                for (JsonNode call : calls) {
                    String name = call.path("function").path("name").asText();
                    ObjectNode arguments = (ObjectNode) mapper.readTree(call.path("function").path("arguments").asText("{}"));
                    if (name.equals("run_task")) {
                        arguments.put("preview", true);
                    }
                    send(out, Map.of("type", "step", "text", PROGRESS.getOrDefault(name, name)));
                    ObjectNode result = tools.callTool(name, arguments, asAgent);
                    if (name.equals("propose_task") && !result.path("isError").asBoolean()) {
                        send(out, Map.of("type", "proposal", "task", arguments.path("task").path("name").asText()));
                    }
                    messages.addObject().put("role", "tool").put("tool_call_id", call.path("id").asText()).put("content", result.path("content").path(0).path("text").asText());
                }
            }
            send(out, Map.of("type", "answer", "text", "I stopped after " + MAX_ROUNDS + " rounds of work. What I proposed so far is in the editor. Ask again to go on."));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            send(out, Map.of("type", "error", "text", e.getMessage() == null ? "The assistant stopped." : e.getMessage()));
        }
    }

    /** The person's words and the answers so far. The page keeps them, so only the latest are taken, cut to size. */
    private ArrayNode conversation(JsonNode body) {
        ArrayNode messages = mapper.createArrayNode();
        String openTask = body.path("task").asText("");
        messages.addObject().put("role", "system").put("content", McpController.INSTRUCTIONS + GUIDANCE
                + (openTask.isBlank() ? "" : " The person has the task " + openTask + " open in the editor. Read it with get_task before changing it."));
        List<JsonNode> said = new java.util.ArrayList<>();
        body.path("messages").forEach(message -> {
            String role = message.path("role").asText();
            if (role.equals("user") || role.equals("assistant")) {
                said.add(message);
            }
        });
        said.subList(Math.max(0, said.size() - MAX_MESSAGES), said.size()).forEach(message -> {
            String content = message.path("content").asText("");
            messages.addObject().put("role", message.path("role").asText()).put("content", content.substring(0, Math.min(content.length(), MAX_MESSAGE_LENGTH)));
        });
        return messages;
    }

    private ArrayNode functions() {
        ArrayNode functions = mapper.createArrayNode();
        tools.toolList().forEach(tool -> {
            ObjectNode function = functions.addObject().put("type", "function").putObject("function");
            function.put("name", tool.path("name").asText()).put("description", tool.path("description").asText());
            function.set("parameters", tool.path("inputSchema"));
        });
        return functions;
    }

    private JsonNode complete(ArrayNode messages, ArrayNode functions) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode().put("model", model);
        body.set("messages", messages);
        body.set("tools", functions);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode answer = mapper.readTree(response.body());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("The AI service answered " + response.statusCode() + ". " + answer.path("error").path("message").asText(""));
        }
        return answer.path("choices").path(0).path("message");
    }

    private void send(PrintWriter out, Map<String, String> event) {
        out.write("data: " + mapper.writeValueAsString(event) + "\n\n");
        out.flush();
    }

    /** The same request, seen by the tools as coming from an agent, while the person's own request stays as it was. */
    private static final class AgentView extends HttpServletRequestWrapper {
        private final Map<String, Object> attributes = new HashMap<>();

        AgentView(HttpServletRequest request) {
            super(request);
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.containsKey(name) ? attributes.get(name) : super.getAttribute(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }
    }
}
