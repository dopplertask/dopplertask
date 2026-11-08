package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory

/** Fills in a connector's request with the step's values and its credential, each encoded for where it goes. */
object ConnectorRequests {

    class Built(val url: String, val headers: Map<String, String>, val body: JsonNode?)

    fun build(connector: Connector, operation: ConnectorOperation, values: Map<String, String>, credential: Map<String, String>): Built {
        val request = operation.request
        val credentialValue = { name: String -> credential[name.removePrefix("credential.")].orEmpty() }
        val query = request.query.mapValues { Connectors.fill(it.value) { name -> values[name].orEmpty() } }.filterValues { it.isNotEmpty() } +
            connector.credential?.query.orEmpty().mapValues { Connectors.fill(it.value, credentialValue) }
        val url = Connectors.fill(request.url) { name -> JsonApi.encoded(values[name].orEmpty()) } +
            query.entries.joinToString("&", prefix = if (query.isEmpty()) "" else if (request.url.contains('?')) "&" else "?") { "${JsonApi.encoded(it.key)}=${JsonApi.encoded(it.value)}" }
        val headers = request.headers.mapValues { Connectors.fill(it.value) { name -> values[name].orEmpty() } } +
            connector.credential?.headers.orEmpty().mapValues { Connectors.fill(it.value, credentialValue) }
        headers.forEach { (name, value) -> require('\r' !in value && '\n' !in value) { "The header $name may not hold a line break." } }
        return Built(url, headers, request.body?.let { bodyOf(it, operation, values) })
    }

    /**
     * The body with each placeholder replaced. A value that is only a placeholder takes the input's type, such as a
     * number or JSON, and is left out when the person left it empty, as the services expect of optional fields.
     */
    private fun bodyOf(template: JsonNode, operation: ConnectorOperation, values: Map<String, String>): JsonNode? = when {
        template.isObject -> JsonNodeFactory.instance.objectNode().also { filled ->
            template.properties().forEach { (key, value) -> bodyOf(value, operation, values)?.let { filled.set(key, it) } }
        }
        template.isArray -> JsonNodeFactory.instance.arrayNode().also { filled -> template.forEach { item -> bodyOf(item, operation, values)?.let(filled::add) } }
        template.isTextual -> {
            val text = template.asText()
            val whole = Connectors.placeholdersIn(text).singleOrNull()?.takeIf { text.trim() == "{{$it}}" }
            if (whole == null) JsonNodeFactory.instance.textNode(Connectors.fill(text) { values[it].orEmpty() })
            else typed(values[whole].orEmpty(), operation.inputs[whole]?.type)
        }
        else -> template
    }

    private fun typed(value: String, type: String?): JsonNode? {
        if (value.isEmpty()) return null
        return when (type) {
            "number" -> value.toBigDecimalOrNull()?.let(JsonNodeFactory.instance::numberNode) ?: throw IllegalArgumentException("\"$value\" is not a number.")
            "boolean" -> JsonNodeFactory.instance.booleanNode(value.toBooleanStrictOrNull() ?: throw IllegalArgumentException("\"$value\" is not true or false."))
            "json" -> try {
                JsonApi.mapper.readTree(value)
            } catch (e: Exception) {
                throw IllegalArgumentException("\"$value\" is not valid JSON.")
            }
            else -> JsonNodeFactory.instance.textNode(value)
        }
    }
}
