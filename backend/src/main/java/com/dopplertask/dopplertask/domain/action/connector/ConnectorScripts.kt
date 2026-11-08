package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import org.graalvm.polyglot.PolyglotException
import tools.jackson.databind.JsonNode

/**
 * The small pieces of logic a connector action can carry, such as reshaping an answer. A script gets copies of plain
 * values as JSON and gives back JSON, in an engine with no way into Java, files or the network, and it never sees the
 * credential, since the credential is added to the request after the script has run.
 */
object ConnectorScripts {

    /** Runs the body of a function of inputs, request and response, and gives what it returns. */
    fun run(script: String, inputs: Map<String, String>, request: JsonNode?, response: JsonNode?): JsonNode {
        val call = "JSON.stringify((function (inputs, request, response) {\n$script\n})(${json(inputs)}, ${json(request)}, ${json(response)}))"
        return VariableExtractorUtil.restrictedContext().use { context ->
            try {
                JsonApi.mapper.readTree(context.eval("js", call).asString() ?: "null")
            } catch (e: PolyglotException) {
                throw IllegalArgumentException("The connector's script failed: ${e.message}")
            }
        }
    }

    private fun json(value: Any?) = JsonApi.mapper.writeValueAsString(value)
}
