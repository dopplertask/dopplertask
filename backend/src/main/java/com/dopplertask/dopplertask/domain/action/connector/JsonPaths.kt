package com.dopplertask.dopplertask.domain.action.connector

import tools.jackson.databind.JsonNode

/** Reads a value out of JSON by a path such as $.channel.id or $.items[0].name. */
object JsonPaths {
    private val PART = Regex("\\.([^.\\[]+)|\\[(\\d+)]")

    fun read(json: JsonNode, path: String): JsonNode? {
        var node: JsonNode = json
        for (part in PART.findAll(path.removePrefix("$"))) {
            val (field, index) = part.destructured
            node = if (field.isNotEmpty()) node.get(field) ?: return null else node.get(index.toInt()) ?: return null
        }
        return node
    }
}
