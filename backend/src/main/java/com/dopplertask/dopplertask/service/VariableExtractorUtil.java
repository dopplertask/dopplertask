package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.common.ScriptLanguage;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.generic.*;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class VariableExtractorUtil {

    private VelocityEngine velocityEngine;

    @Autowired
    public VariableExtractorUtil(VelocityEngine velocityEngine) {
        this.velocityEngine = velocityEngine;
    }

    private String extractVelocity(String fieldValue, TaskExecution execution, ActionResult result, Map<String, Object> extraTools) {
        if (fieldValue != null) {
            VelocityContext context = new VelocityContext(getVelocityTools());
            context.put("parameters", execution.getParameters());
            context.put("executionId", execution.getId());
            context.put("logs", execution.getLogs());

            if (extraTools != null && extraTools.size() > 0) {
                extraTools.forEach((toolName, toolValue) -> context.put(toolName, toolValue));
            }

            // Useful for retry
            if (result != null) {
                context.put("result", result);
            }

            // Easy access to lastLog
            if (execution.getLogs() != null && !execution.getLogs().isEmpty()) {
                context.put("lastLog", execution.getLogs().get(execution.getLogs().size() - 1));
            }

            StringWriter writer = new StringWriter();

            // Evaluate the original field
            velocityEngine.evaluate(context, writer, "VelExtract", fieldValue);
            return writer.toString();
        }

        return "";
    }

    public String extract(String fieldValue, TaskExecution execution, ScriptLanguage scriptLanguage) throws IOException {
        return extract(fieldValue, execution, null, scriptLanguage);
    }

    public String extract(String fieldValue, TaskExecution execution, ActionResult result, ScriptLanguage scriptLanguage, Map<String, Object> extraTools) throws IOException {
        switch (scriptLanguage) {
            case VELOCITY:
                return extractVelocity(fieldValue, execution, result, extraTools);
            case JAVASCRIPT:
                return extractJavascript(fieldValue, execution, result, extraTools).toString();
            default:
                throw new RuntimeException("Script language is not supported.");
        }

    }

    public String extract(String fieldValue, TaskExecution execution, ActionResult result, ScriptLanguage scriptLanguage) throws IOException {
        return extract(fieldValue, execution, result, scriptLanguage, null);
    }

    private Value extractJavascript(String fieldValue, TaskExecution execution, ActionResult result, Map<String, Object> extraTools) throws IOException {
        if (fieldValue != null) {

            Context context = Context.newBuilder().allowAllAccess(true).build();

            getVelocityTools().forEach((toolName, toolValue) -> context.getBindings("js").putMember(toolName, toolValue));
            if (extraTools != null && extraTools.size() > 0) {
                extraTools.forEach((toolName, toolValue) -> context.getBindings("js").putMember(toolName, toolValue));
            }

            context.getBindings("js").putMember("parameters", execution.getParameters());
            context.getBindings("js").putMember("executionId", execution.getId());
            context.getBindings("js").putMember("logs", execution.getLogs());

            // Useful for retry
            if (result != null) {
                context.getBindings("js").putMember("result", result);
            }

            // Easy access to lastLog
            if (execution.getLogs() != null && !execution.getLogs().isEmpty()) {
                context.getBindings("js").putMember("lastLog", execution.getLogs().get(execution.getLogs().size() - 1));
            }

            // Evaluate the original field
            return context.eval(Source.newBuilder("js", fieldValue, "src.js").build());
        }

        return Value.asValue("");
    }

    public Map<String, Object> getVelocityTools() {
        Map<String, Object> tools = new HashMap<>();
        tools.put("dateTool", new DateTool());
        tools.put("escapeTool", new EscapeTool());
        tools.put("loopTool", new LoopTool());
        tools.put("mathTool", new MathTool());
        tools.put("numberTool", new NumberTool());
        tools.put("renderTool", new RenderTool());
        tools.put("collectionTool", new CollectionTool());
        tools.put("valueParser", new ValueParser(new HashMap<>()));
        tools.put("stringUtils", new StringUtils());
        tools.put("base64Helper", new Base64Helper());
        return tools;
    }

    public static class Base64Helper {
        public String encode(String text) {
            return Base64.getEncoder().encodeToString(text.getBytes());
        }

        public String decode(String text) {
            return Arrays.toString(Base64.getDecoder().decode(text));
        }
    }
}
