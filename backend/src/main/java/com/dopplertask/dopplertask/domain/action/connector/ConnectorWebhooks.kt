package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * Hands the events a service sends to the trigger that subscribed to them. Each trigger is reached by a random key in
 * its address, which only the service it gave the address to knows.
 */
object ConnectorWebhooks {

    const val PATH = "/hooks/"

    /** Where the address of a waiting run starts after PATH, /hooks/resume/<key>. */
    const val RESUME = "resume/"

    /** Where services reach this server, such as https://tasks.example.com. Set from DOPPLERTASK_PUBLIC_URL. */
    @Volatile
    var publicUrl: String = ""

    private val listening = ConcurrentHashMap<String, CopyOnWriteArrayList<Listener>>()

    /** An event as it arrived: its body read as JSON, a form or text, and what came with it, its headers, query and text, and at, when it arrived in seconds. */
    class Event(val body: JsonNode, val received: JsonNode)

    /**
     * What a trigger listens with: where its events wait, what its address answers to each, and which it takes. Several
     * triggers listen on an app's address, each for its own account and kinds of event.
     */
    class Listener(val events: BlockingQueue<Event>, val reply: (Event) -> JsonNode?, val accepts: (Event) -> Boolean)

    /** The key of a connector's app address, /hooks/app/<connector>. */
    fun appKey(connector: String) = "app/$connector"

    fun addressOf(key: String) = publicUrl.trimEnd('/') + PATH + key

    /**
     * An event as the text a waiting run keeps until its step reads it with eventOfStored. A person's sign-in, such as the
     * editor's cookie in a browser that opens an approval link, is not kept.
     */
    fun storedOf(event: Event): String {
        val received = event.received.deepCopy() as ObjectNode
        (received.path("headers") as? ObjectNode)?.remove(SIGN_IN_HEADERS)
        return JsonApi.mapper.writeValueAsString(mapOf("body" to event.body, "received" to received))
    }

    fun eventOfStored(text: String): Event = JsonApi.mapper.readTree(text).let { Event(it.path("body"), it.path("received")) }

    private val SIGN_IN_HEADERS = listOf("cookie", "authorization", "proxy-authorization")

    fun listen(key: String, reply: (Event) -> JsonNode? = { null }, accepts: (Event) -> Boolean = { true }): Listener =
        Listener(LinkedBlockingQueue(), reply, accepts).also { listening.computeIfAbsent(key) { CopyOnWriteArrayList() }.add(it) }

    fun stopListening(key: String, listener: Listener) {
        listening[key]?.remove(listener)
    }

    /**
     * Whether an event carries the signature the service makes with the credential's secret and, when the service signs
     * the time too, was signed within five minutes of when it arrived. The comparison takes the same time whatever it
     * finds, so that it gives nothing away.
     */
    fun isSigned(signature: ConnectorSignature, event: Event, credential: Map<String, String>): Boolean {
        val key = filled(signature.key, event, credential).takeIf { it.isNotEmpty() } ?: return false
        val hash = Mac.getInstance(signature.algorithm).apply { init(SecretKeySpec(key.toByteArray(), signature.algorithm)) }.doFinal(filled(signature.text, event, credential).toByteArray())
        val expected = signature.prefix + if (signature.encoding == "base64") Base64.getEncoder().encodeToString(hash) else HexFormat.of().formatHex(hash)
        val given = event.received.path("headers").path(signature.header.lowercase()).asText()
        if (!MessageDigest.isEqual(expected.toByteArray(), given.toByteArray())) return false
        val template = signature.signedAt ?: return true
        val signedAt = filled(template, event, credential).toLongOrNull() ?: return false
        val arrivedAt = event.received.path("at").asLong()
        return signedAt in arrivedAt - SIGNED_WITHIN.seconds..arrivedAt + SIGNED_WITHIN.seconds
    }

    /** How long a signed time is believed, as Slack asks. */
    private val SIGNED_WITHIN = Duration.ofMinutes(5)

    /** A signature's template with what it names from the event and the credential. */
    private fun filled(template: String, event: Event, credential: Map<String, String>) =
        SIGNED.replace(template) { match ->
            val name = match.groupValues[1]
            when {
                name == "text" -> event.received.path("text").asText()
                name.startsWith("headers.") -> event.received.path("headers").path(name.removePrefix("headers.").lowercase()).asText()
                name.startsWith("query.") -> event.received.path("query").path(name.removePrefix("query.")).asText()
                name.startsWith(Connectors.CREDENTIAL) -> credential[name.removePrefix(Connectors.CREDENTIAL)].orEmpty()
                else -> ""
            }
        }

    // A signature's templates name headers with dashes, such as {{headers.x-slack-signature}}.
    private val SIGNED = Regex("\\{\\{\\s*([\\w.-]+)\\s*}}")

    /**
     * An event as a service sent it: a JSON body as JSON, a form as the fields it holds, nested where a name has
     * brackets, such as contact[email], and any other body as text.
     */
    @JvmOverloads
    fun eventOf(contentType: String?, text: String, headers: Map<String, String>, query: String?, at: Instant = Instant.now()): Event {
        val body = when {
            contentType.orEmpty().startsWith("application/x-www-form-urlencoded") -> formOf(text)
            text.isBlank() -> JsonApi.mapper.createObjectNode()
            else -> runCatching { JsonApi.mapper.readTree(text) }.getOrElse { JsonApi.mapper.getNodeFactory().textNode(text) }
        }
        // The query is read as a form is, as the original's server reads both.
        return Event(body, JsonApi.mapper.valueToTree(mapOf("headers" to headers, "query" to formOf(query.orEmpty()), "text" to text, "at" to at.epochSecond)))
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
            // items[0][name] and items[0][qty] are one item; items[][name] adds one.
            is ArrayNode -> part.toIntOrNull()?.let(parent::get) ?: made().also(parent::add)
            is ObjectNode -> parent.get(part) ?: made().also { parent.set(part, it) }
            else -> parent
        }
    }

    /** Hands over an event, and gives the answer for the service, or null when no trigger listens. */
    fun deliver(key: String, event: Event): JsonNode? {
        val listeners = listening[key]?.takeIf { it.isNotEmpty() } ?: return null
        // What a reply answers, such as a challenge, is a check of the address, not an event.
        listeners.firstNotNullOfOrNull { it.reply(event) }?.let { return it }
        listeners.filter { it.accepts(event) }.forEach { it.events.offer(event) }
        return JsonApi.mapper.nullNode()
    }

    /** Answers a check of the address, which a service sends without a body: by a trigger's reply, or with an empty answer. */
    fun check(key: String, event: Event): JsonNode? =
        listening[key]?.takeIf { it.isNotEmpty() }?.let { listeners -> listeners.firstNotNullOfOrNull { it.reply(event) } ?: JsonApi.mapper.nullNode() }
}
