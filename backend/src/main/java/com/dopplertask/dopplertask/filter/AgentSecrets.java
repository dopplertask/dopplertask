package com.dopplertask.dopplertask.filter;

import com.dopplertask.dopplertask.domain.Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An agent works with tasks without seeing their secrets. It reads a mask, and if it sends the mask back the real
 * value stays, so that it can change a step without knowing the password in it.
 */
@ControllerAdvice
public class AgentSecrets implements ResponseBodyAdvice<Object> {

    static final String MASK = "********";
    private static final List<String> SECRET_FIELDS = List.of("password", "apiKey", "apiToken", "token", "headerValue");

    private final ObjectMapper mapper;

    public AgentSecrets(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType, Class converterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || !(request instanceof ServletServerHttpRequest servletRequest) || AccessFilter.callerOf(servletRequest.getServletRequest()) != Caller.AGENT) {
            return body;
        }
        return masked(body);
    }

    /**
     * The same content with its secrets replaced by the mask.
     */
    public JsonNode masked(Object body) {
        JsonNode masked = mapper.valueToTree(body);
        mask(masked);
        return masked;
    }

    private void mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            SECRET_FIELDS.stream().filter(object::hasNonNull).forEach(field -> object.put(field, MASK));
        }
        node.forEach(this::mask);
    }

    /**
     * Puts back the real values where an agent sent the mask, from the step with the same first port in the current
     * version. A mask with no real value behind it is removed, and a person can fill it in when reviewing.
     */
    public String restore(String body, Task currentVersion) throws IOException {
        JsonNode task = mapper.readTree(body);
        Map<String, JsonNode> currentSteps = new HashMap<>();
        if (currentVersion != null) {
            mapper.valueToTree(currentVersion.getActionList()).forEach(step -> currentSteps.put(firstPort(step), step));
        }
        task.path("actions").forEach(step -> SECRET_FIELDS.stream()
                .filter(field -> MASK.equals(step.path(field).asText()))
                .forEach(field -> {
                    JsonNode current = currentSteps.get(firstPort(step));
                    if (current != null && current.hasNonNull(field)) {
                        ((ObjectNode) step).set(field, current.get(field));
                    } else {
                        ((ObjectNode) step).remove(field);
                    }
                }));
        return mapper.writeValueAsString(task);
    }

    private String firstPort(JsonNode step) {
        return step.path("ports").path(0).path("externalId").asText();
    }
}
