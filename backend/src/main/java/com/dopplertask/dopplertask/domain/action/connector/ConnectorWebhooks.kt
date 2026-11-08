package com.dopplertask.dopplertask.domain.action.connector

import tools.jackson.databind.JsonNode
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

    private val listening = ConcurrentHashMap<String, BlockingQueue<JsonNode>>()

    fun addressOf(key: String) = publicUrl.trimEnd('/') + PATH + key

    fun listen(key: String): BlockingQueue<JsonNode> = listening.computeIfAbsent(key) { LinkedBlockingQueue() }

    fun stopListening(key: String) {
        listening.remove(key)
    }

    /** Hands over an event, and says whether a trigger is listening for it. */
    fun deliver(key: String, event: JsonNode): Boolean = listening[key]?.offer(event) ?: false
}
