package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * Hands the events a service sends to the trigger that subscribed to them. Each trigger is reached by a random key in
 * its address, which only the service it gave the address to knows.
 */
object ConnectorWebhooks {

    const val PATH = "/hooks/"

    /** Where services reach this server, such as https://tasks.example.com. Set from DOPPLERTASK_PUBLIC_URL. */
    @Volatile
    var publicUrl: String = ""

    private val listening = ConcurrentHashMap<String, Listener>()

    /** An event as it arrived: its body read as JSON, a form or text, and what came with it, its headers, query and text. */
    class Event(val body: JsonNode, val received: JsonNode)

    /** What a trigger listens with: where its events wait, and what its address answers to each. */
    private class Listener(val events: BlockingQueue<Event>, val reply: (Event) -> JsonNode?)

    fun addressOf(key: String) = publicUrl.trimEnd('/') + PATH + key

    fun listen(key: String, reply: (Event) -> JsonNode? = { null }): BlockingQueue<Event> =
        listening.compute(key) { _, listener -> Listener(listener?.events ?: LinkedBlockingQueue(), reply) }!!.events

    fun stopListening(key: String) {
        listening.remove(key)
    }

    fun isListening(key: String) = listening.containsKey(key)

    /**
     * An event as a service sent it: a JSON body as JSON, a form as the fields it holds, nested where a name has
     * brackets, such as contact[email], and any other body as text.
     */
    fun eventOf(contentType: String?, text: String, headers: Map<String, String>, query: Map<String, String>): Event {
        val body = when {
            contentType.orEmpty().startsWith("application/x-www-form-urlencoded") -> formOf(text)
            text.isBlank() -> JsonApi.mapper.createObjectNode()
            else -> runCatching { JsonApi.mapper.readTree(text) }.getOrElse { JsonApi.mapper.getNodeFactory().textNode(text) }
        }
        return Event(body, JsonApi.mapper.valueToTree(mapOf("headers" to headers, "query" to query, "text" to text)))
    }

    private fun formOf(text: String): JsonNode {
        val form = JsonApi.mapper.createObjectNode()
        text.split('&').filter { it.isNotEmpty() }.forEach { pair ->
            val (name, value) = pair.split('=', limit = 2).map { URLDecoder.decode(it, StandardCharsets.UTF_8) }.let { it[0] to it.getOrElse(1) { "" } }
            // contact[email] is the path contact, email; tags[] and tags[0] add to a list.
            val path = listOf(name.substringBefore('[')) + Regex("\\[([^\\]]*)]").findAll(name).map { it.groupValues[1] }
            var parent: JsonNode = form
            path.dropLast(1).forEachIndexed { index, part -> parent = childOf(parent, part, isList = path[index + 1].all(Char::isDigit)) }
            when (val last = parent) {
                is ArrayNode -> last.add(value)
                is ObjectNode -> last.put(path.last(), value)
                else -> Unit
            }
        }
        return form
    }

    /** The field or list item to go into, made when it is not there yet. */
    private fun childOf(parent: JsonNode, part: String, isList: Boolean): JsonNode {
        val made = { if (isList) JsonApi.mapper.createArrayNode() else JsonApi.mapper.createObjectNode() }
        return when (parent) {
            is ArrayNode -> made().also(parent::add)
            is ObjectNode -> parent.get(part) ?: made().also { parent.set(part, it) }
            else -> parent
        }
    }

    /** Hands over an event, and gives the answer for the service, or null when no trigger listens. */
    fun deliver(key: String, event: Event): JsonNode? {
        val listener = listening[key] ?: return null
        listener.events.offer(event)
        return listener.reply(event) ?: JsonApi.mapper.nullNode()
    }
}
