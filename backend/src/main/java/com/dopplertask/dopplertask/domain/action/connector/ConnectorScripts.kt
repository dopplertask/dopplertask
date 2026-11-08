package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.proxy.ProxyExecutable
import tools.jackson.databind.JsonNode

/**
 * The small pieces of logic a connector action can carry, such as reshaping an answer or asking for the next page. A
 * script gets copies of plain values as JSON and gives back JSON, in an engine with no way into Java, files or the
 * network. It can send another request with call({method, url, query, headers, body}), where url may be a path such as /items, which the connector sends
 * with its credential and only to its own hosts, so the script never sees the credential.
 */
object ConnectorScripts {

    /** Runs the body of a function of inputs, request and response, and gives what it returns. */
    fun run(script: String, inputs: Map<String, String>, request: JsonNode?, response: JsonNode?, send: (JsonNode) -> JsonNode): JsonNode {
        val source = "const call = (spec) => JSON.parse(sendRequest(JSON.stringify(spec)));\n" +
            "JSON.stringify((function (inputs, request, response) {\n$script\n})(${json(inputs)}, ${json(request)}, ${json(response)}))"
        return VariableExtractorUtil.restrictedContext().use { context ->
            // Only text crosses into the script, so it holds no object of the server.
            context.getBindings("js").putMember("sendRequest", ProxyExecutable { arguments -> json(send(JsonApi.mapper.readTree(arguments[0].asString()))) })
            try {
                JsonApi.mapper.readTree(context.eval("js", source).asString() ?: "null")
            } catch (e: PolyglotException) {
                throw IllegalArgumentException("The connector's script failed: ${e.message}")
            }
        }
    }

    private fun json(value: Any?) = JsonApi.mapper.writeValueAsString(value)
}
