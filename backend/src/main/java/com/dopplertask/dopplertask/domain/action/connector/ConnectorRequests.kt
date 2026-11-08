package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import java.util.Base64

/**
 * Fills in a connector's request with the step's values, runs the action's script on it if it has one, and only then
 * adds the credential, so that a script never sees it. Each value is encoded for where it goes.
 */
object ConnectorRequests {

    class Built(val method: String, val url: String, val headers: Map<String, String>, val body: JsonNode?)

    private val NODES = JsonNodeFactory.instance

    // A credential value that names part of a host, such as a subdomain, may only hold what a host name holds.
    private val HOST_PART = Regex("[A-Za-z0-9.-]+")

    private const val CREDENTIAL = "credential."

    fun build(connector: Connector, operation: ConnectorOperation, values: Map<String, String>, credential: Map<String, String>): Built {
        val request = operation.request
        val draft = NODES.objectNode()
        draft.put("method", request.method)
        draft.put("url", address(request.url, values, credential))
        draft.set("query", JsonApi.mapper.valueToTree(request.query.mapValues { Connectors.fill(it.value) { name -> values[name].orEmpty() } }.filterValues { it.isNotEmpty() }))
        draft.set("headers", JsonApi.mapper.valueToTree(request.headers.mapValues { Connectors.fill(it.value) { name -> values[name].orEmpty() } }))
        draft.set("body", request.body?.let { bodyOf(it, operation, values) } ?: NODES.nullNode())
        val ready = operation.beforeRequest?.let { ConnectorScripts.run(it, values, draft, null) } ?: draft
        return signed(connector, ready, credential)
    }

    /**
     * Any call of the service's API, with its credential, for what the connector's actions do not cover. The address
     * starts at the connector's base, so the credential still only goes to the connector's hosts.
     */
    fun customCall(connector: Connector, values: Map<String, String>, credential: Map<String, String>): Built {
        val path = values["path"].orEmpty().trim()
        require(!path.contains("://") && !path.startsWith("//")) { "The path should start after the address of ${connector.label}, such as /contacts." }
        val draft = NODES.objectNode()
        draft.put("method", values["method"].orEmpty().ifBlank { "GET" })
        draft.put("url", address(connector.baseUrl, values, credential).trimEnd('/') + "/" + path.trimStart('/'))
        draft.set("query", json(values["query"]) ?: NODES.objectNode())
        draft.set("headers", json(values["headers"]) ?: NODES.objectNode())
        draft.set("body", json(values["body"]) ?: NODES.nullNode())
        return signed(connector, draft, credential)
    }

    private fun signed(connector: Connector, draft: JsonNode, credential: Map<String, String>): Built {
        val signIn = connector.credential
        val credentialValue = { name: String -> credential[name.removePrefix(CREDENTIAL)].orEmpty() }
        val query = textsOf(draft.path("query")) + signIn?.query.orEmpty().mapValues { Connectors.fill(it.value, credentialValue) }
        val url = draft.path("url").asText() + query.entries.joinToString("&", prefix = if (query.isEmpty()) "" else if ('?' in draft.path("url").asText()) "&" else "?") {
            "${JsonApi.encoded(it.key)}=${JsonApi.encoded(it.value)}"
        }
        val headers = textsOf(draft.path("headers")) + signIn?.headers.orEmpty().mapValues { Connectors.fill(it.value, credentialValue) } +
            listOfNotNull(signIn?.basic?.let { basic ->
                val pair = Connectors.fill(basic.username, credentialValue) + ":" + Connectors.fill(basic.password, credentialValue)
                "Authorization" to "Basic " + Base64.getEncoder().encodeToString(pair.toByteArray())
            })
        headers.forEach { (name, value) -> require('\r' !in value && '\n' !in value) { "The header $name may not hold a line break." } }
        val body = draft.path("body").takeUnless { it.isNull || it.isMissingNode }.let { body ->
            val fields = signIn?.body.orEmpty().mapValues { Connectors.fill(it.value, credentialValue) }
            if (fields.isEmpty()) body else ((body as? ObjectNode)?.deepCopy() ?: NODES.objectNode()).also { filled -> fields.forEach { (key, value) -> filled.put(key, value) } }
        }
        return Built(draft.path("method").asText("GET"), url, headers, body)
    }

    /** The address with its host from the credential, checked as a host name, and every other value percent-encoded. */
    private fun address(template: String, values: Map<String, String>, credential: Map<String, String>): String {
        val hostEnd = template.indexOf('/', template.indexOf("://") + 3).let { if (it < 0) template.length else it }
        val path = Connectors.fill(template.substring(hostEnd)) { name ->
            JsonApi.encoded(if (name.startsWith(CREDENTIAL)) credential[name.removePrefix(CREDENTIAL)].orEmpty() else values[name].orEmpty())
        }
        return Connectors.fill(template.substring(0, hostEnd)) { hostPart(credential, it) } + path
    }

    private fun hostPart(credential: Map<String, String>, name: String): String {
        val value = credential[name.removePrefix(CREDENTIAL)].orEmpty().trim().removePrefix("https://").trimEnd('/')
        require(HOST_PART.matches(value)) { "The credential's ${name.removePrefix(CREDENTIAL)} should be a host name, such as example.zendesk.com." }
        return value
    }

    private fun textsOf(node: JsonNode): Map<String, String> =
        node.properties().associate { (key, value) -> key to if (value.isValueNode) value.asText() else value.toString() }

    private fun json(text: String?): JsonNode? = text?.takeIf { it.isNotBlank() }?.let {
        try {
            JsonApi.mapper.readTree(it)
        } catch (e: Exception) {
            throw IllegalArgumentException("\"$it\" is not valid JSON.")
        }
    }

    /**
     * The body with each placeholder replaced. A value that is only a placeholder takes the input's type, such as a
     * number or JSON, and is left out when the person left it empty, as the services expect of optional fields.
     */
    private fun bodyOf(template: JsonNode, operation: ConnectorOperation, values: Map<String, String>): JsonNode? = when {
        template.isObject -> NODES.objectNode().also { filled ->
            template.properties().forEach { (key, value) -> bodyOf(value, operation, values)?.let { filled.set(key, it) } }
        }
        template.isArray -> NODES.arrayNode().also { filled -> template.forEach { item -> bodyOf(item, operation, values)?.let(filled::add) } }
        template.isTextual -> {
            val text = template.asText()
            val whole = Connectors.placeholdersIn(text).singleOrNull()?.takeIf { text.trim() == "{{$it}}" }
            if (whole == null) NODES.textNode(Connectors.fill(text) { values[it].orEmpty() })
            else typed(values[whole].orEmpty(), operation.inputs[whole]?.type)
        }
        else -> template
    }

    private fun typed(value: String, type: String?): JsonNode? {
        if (value.isEmpty()) return null
        return when (type) {
            "number" -> value.toBigDecimalOrNull()?.let(NODES::numberNode) ?: throw IllegalArgumentException("\"$value\" is not a number.")
            "boolean" -> NODES.booleanNode(value.toBooleanStrictOrNull() ?: throw IllegalArgumentException("\"$value\" is not true or false."))
            "json" -> json(value)
            else -> NODES.textNode(value)
        }
    }
}
