package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.common.LoopState;
import com.dopplertask.dopplertask.domain.action.common.ScriptLanguage;
import com.dopplertask.dopplertask.domain.action.integration.JsonApi;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.generic.CollectionTool;
import org.apache.velocity.tools.generic.DateTool;
import org.apache.velocity.tools.generic.EscapeTool;
import org.apache.velocity.tools.generic.LoopTool;
import org.apache.velocity.tools.generic.MathTool;
import org.apache.velocity.tools.generic.NumberTool;
import org.apache.velocity.tools.generic.RenderTool;
import org.apache.velocity.tools.generic.ValueParser;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class VariableExtractorUtil {

    private static final long RESTRICTED_SCRIPT_STATEMENTS = 10_000_000;

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
                extraTools.forEach(context::put);
            }

            // Useful for retry
            if (result != null) {
                context.put("result", result);
            }

            LoopState innermost = execution.getInnermostLoop();
            if (innermost != null) {
                context.put("loop", innermost.variables());
                context.put("loops", loopsByName(execution));
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
        StepRunner elsewhere = StepRunner.active();
        if (elsewhere != null && fieldValue != null) {
            boolean plainText = scriptLanguage == ScriptLanguage.VELOCITY && fieldValue.indexOf('$') < 0 && fieldValue.indexOf('#') < 0;
            return plainText ? fieldValue : elsewhere.evaluate(fieldValue, execution, result, scriptLanguage);
        }
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

            if (StepRules.active().scriptsAreRestricted()) {
                return evaluateRestricted(fieldValue, execution, result);
            }

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

            bindLoops(context, execution);

            // Evaluate the original field
            return context.eval(Source.newBuilder("js", fieldValue, "src.js").build());
        }

        return Value.asValue("");
    }

    /** Scripts get copies of plain values, so there is no object to climb from into Java or the machine. */
    private Value evaluateRestricted(String fieldValue, TaskExecution execution, ActionResult result) throws IOException {
        Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(className -> false)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .resourceLimits(ResourceLimits.newBuilder().statementLimit(RESTRICTED_SCRIPT_STATEMENTS, null).build())
                .build();
        Map<String, Object> parameters = new HashMap<>();
        if (execution.getParameters() != null) {
            execution.getParameters().forEach((name, value) -> parameters.put(name, String.valueOf(value)));
        }
        Map<String, Object> parameterObject = new HashMap<>(parameters);
        parameterObject.put("get", (ProxyExecutable) arguments -> parameters.get(arguments[0].asString()));
        context.getBindings("js").putMember("parameters", ProxyObject.fromMap(parameterObject));
        context.getBindings("js").putMember("executionId", execution.getId());
        if (execution.getLogs() != null && !execution.getLogs().isEmpty()) {
            context.getBindings("js").putMember("lastLog", plainLog(execution.getLogs().get(execution.getLogs().size() - 1).getOutput(), execution.getLogs().get(execution.getLogs().size() - 1).getOutputVariables()));
        }
        if (result != null) {
            context.getBindings("js").putMember("result", plainLog(result.getOutput(), result.getOutputVariables()));
        }
        bindLoops(context, execution);
        return context.eval(Source.newBuilder("js", fieldValue, "src.js").build());
    }

    private static Map<String, Object> loopsByName(TaskExecution execution) {
        Map<String, Object> loops = new LinkedHashMap<>();
        execution.getLoops().values().forEach(state -> loops.put(state.getName(), state.variables()));
        return loops;
    }

    /** Scripts get the loops as plain JavaScript values, so that loop.item.name works as it does in Velocity. */
    private static void bindLoops(Context context, TaskExecution execution) {
        LoopState innermost = execution.getInnermostLoop();
        if (innermost == null) {
            return;
        }
        Value parse = context.eval("js", "JSON.parse");
        context.getBindings("js").putMember("loop", parse.execute(JsonApi.INSTANCE.getMapper().writeValueAsString(innermost.variables())));
        context.getBindings("js").putMember("loops", parse.execute(JsonApi.INSTANCE.getMapper().writeValueAsString(loopsByName(execution))));
    }

    private static ProxyObject plainLog(String output, Map<String, ?> variables) {
        Map<String, Object> plainVariables = new HashMap<>();
        if (variables != null) {
            variables.forEach((name, value) -> plainVariables.put(name, String.valueOf(value)));
        }
        Map<String, Object> log = new HashMap<>();
        log.put("output", output == null ? "" : output);
        log.put("outputVariables", ProxyObject.fromMap(plainVariables));
        return ProxyObject.fromMap(log);
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
            return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
        }
    }
}
