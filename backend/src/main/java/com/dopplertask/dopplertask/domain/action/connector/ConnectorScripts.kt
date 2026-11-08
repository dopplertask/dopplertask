package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.proxy.ProxyExecutable
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

/**
 * The small pieces of logic a connector action can carry, such as reshaping an answer or asking for the next page. A
 * script gets copies of plain values as JSON and gives back JSON, in an engine with no way into Java, files or the
 * network. It can send another request with call({method, url, query, headers, body}), where url may be a path such as
 * /items, which the connector sends with its credential and only to its own hosts, so the script never sees the
 * credential. An action's script can also keep a file for the run with keepFile(name, base64), which gives the name.
 */
object ConnectorScripts {

    /**
     * Runs a script with the step's inputs, the request, the response read as JSON, and received: what came with the
     * response, its headers and its text, also for a response that is not JSON. A trigger's script also has memory, an
     * object it may change, which is kept until its next check.
     */
    @JvmOverloads
    fun run(script: String, inputs: Map<String, String>, request: JsonNode?, response: JsonNode?, send: (JsonNode) -> JsonNode,
            received: JsonNode? = null, memory: ObjectNode? = null, keepFile: ((String, String) -> String)? = null): JsonNode {
        val source = "const call = (spec) => JSON.parse(sendRequest(JSON.stringify(spec)));\nconst keepFile = (name, base64) => keepRunFile(name, base64);\n" +
            "const memory = ${json(memory ?: emptyMap<String, Any>())};\n" +
            "JSON.stringify([(function (inputs, request, response, received) {\n$script\n})(${json(inputs)}, ${json(request)}, ${json(response)}, ${json(received)}), memory])"
        return VariableExtractorUtil.restrictedContext().use { context ->
            // Only text crosses into the script, so it holds no object of the server.
            context.getBindings("js").putMember("sendRequest", ProxyExecutable { arguments -> json(send(JsonApi.mapper.readTree(arguments[0].asString()))) })
            context.getBindings("js").putMember("keepRunFile", ProxyExecutable { arguments ->
                (keepFile ?: throw IllegalArgumentException("Only an action keeps files for the run."))(arguments[0].asString(), arguments[1].asString())
            })
            try {
                val (result, kept) = JsonApi.mapper.readTree(context.eval("js", source).asString()).let { it.get(0) to it.get(1) }
                memory?.removeAll()
                (kept as ObjectNode).properties().forEach { (name, value) -> memory?.set(name, value) }
                result ?: JsonApi.mapper.nullNode()
            } catch (e: PolyglotException) {
                throw IllegalArgumentException("The connector's script failed: ${e.message}")
            }
        }
    }

    private fun json(value: Any?) = JsonApi.mapper.writeValueAsString(value)
}
