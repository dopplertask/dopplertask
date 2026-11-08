package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.domain.action.trigger.Trigger
import com.dopplertask.dopplertask.domain.action.trigger.TriggerResult
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient
import tools.jackson.databind.JsonNode
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Starts the task when something new appears in a connector's service. It asks the service every few minutes for its
 * newest items and starts the task once for each new one, oldest first. The first check only notes where the service
 * is, so that turning a trigger on does not start the task for everything that already exists.
 */
@Entity
@Table(name = "ConnectorTrigger")
@DiscriminatorValue("connector_trigger")
class ConnectorTrigger : Trigger() {

    /** Read from and written as the kind, such as slack.trigger. */
    @Column
    @get:JsonIgnore
    var connector: String? = null

    /** The connector's trigger, such as new_contact. */
    @Column
    var operation: String? = null

    @Column
    var every: String? = "5"

    @Lob
    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    var settings: String = "{}"

    /** The random part of the address a service sends this trigger's events to. */
    @Column
    @JsonIgnore
    var hookKey: String? = null

    /**
     * Where the last check left off: a time in milliseconds, or the id of the newest item. For a webhook, the id of the
     * subscription. Kept with the trigger.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    var pollState: String? = null

    @Transient
    @JsonIgnore
    private var credentialValues: Map<String, String> = emptyMap()

    /** Saves where the trigger left off, so that a restart neither repeats items nor subscribes twice. Set by the listener. */
    @Transient
    @JsonIgnore
    var keepPlace: () -> Unit = {}

    // New items wait here when one check finds several, since each starts the task on its own.
    @Transient
    @JsonIgnore
    private val waiting = ArrayDeque<JsonNode>()

    @JsonProperty(value = "@type", access = JsonProperty.Access.WRITE_ONLY)
    fun setKind(kind: String) {
        connector = kind.removeSuffix(KIND_SUFFIX)
    }

    @JsonAnyGetter
    fun settingValues(): Map<String, String> = ConnectorSettings.read(settings)

    @JsonAnySetter
    fun setSetting(name: String, value: Any?) {
        settings = ConnectorSettings.with(settings, name, value)
    }

    override fun useCredentialValues(values: Map<String, String>) {
        credentialValues = values
        spec?.let { super.useCredentialValues(ConnectorSender.accountFields(it, values)) }
    }

    override fun inlineCredentialValues(fields: Set<String>): Map<String, String> =
        settingValues().filter { it.key in fields && it.value.isNotBlank() }

    override fun forgetCredentialValues(fields: Set<String>) {
        credentialValues = emptyMap()
        settings = ConnectorSettings.without(settings, fields)
        spec?.let { super.forgetCredentialValues(ConnectorSender.accountFields(it, fields.associateWith { "" }).keys) }
    }

    private val spec: Connector?
        get() = Connectors.find(connector)

    override val stepTypeName: String
        get() = connector + KIND_SUFFIX

    override val stepLabel: String?
        get() = spec?.let { "${it.label} trigger" }

    override val stepIcon: String?
        get() = spec?.icon

    override val credentialType: String
        get() = connector.orEmpty()

    override val description: String
        get() = spec?.let { "Starts the task when something new appears in ${it.label}" } ?: "A connector that is not installed"

    override val outputs: Map<String, String>
        get() = mapOf("item" to "The new item, as JSON")

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val info = super.actionInfo
            val connector = spec ?: return info
            info.add(PropertyInformation("operation", "When", PropertyInformationType.DROPDOWN, connector.triggers.keys.firstOrNull().orEmpty(), "",
                connector.triggers.map { (key, poll) -> PropertyInformation(key, poll.label) }))
            connector.triggers.forEach { (key, poll) ->
                poll.inputs.forEach { (name, input) -> info.add(ConnectorSettings.propertyOf(name, input, mapOf("operation" to arrayOf(key)))) }
            }
            info.add(PropertyInformation("every", "Check every", PropertyInformationType.NUMBER, "5", "Minutes between two checks."))
            info.addAll(ConnectorSettings.credentialProperties(connector))
            return info
        }

    override fun problem(): String? {
        val connector = spec ?: return "There is no connector called ${this.connector}."
        if (connector.triggers[operation] == null) return "Choose what ${connector.label} should watch: ${connector.triggers.keys.joinToString(", ")}."
        if ((every?.trim()?.toLongOrNull() ?: 0) < 1) return "Check every should be a whole number of minutes, from 1."
        if (connector.triggers[operation]?.webhook != null && ConnectorWebhooks.publicUrl.isBlank()) {
            return "${connector.label} sends its events to this server, so set DOPPLERTASK_PUBLIC_URL to the address it can reach, such as https://tasks.example.com."
        }
        return null
    }

    override fun trigger(): TriggerResult {
        val webhook = spec?.triggers?.get(operation)?.webhook
        while (true) {
            nextWaiting()?.let { return TriggerResult(mutableMapOf("item" to it)) }
            if (webhook != null) {
                if (pollState == null) subscribe(webhook)
                takeItemsOf(ConnectorWebhooks.listen(hookKey!!).take())
            } else {
                if (pollState != null) Thread.sleep(Duration.ofMinutes(every!!.trim().toLong()).toMillis())
                lookForNewItems()
            }
        }
    }

    /** Asks the service to send this trigger's events here, and keeps the subscription's id. */
    fun subscribe(webhook: ConnectorWebhook) {
        val connector = spec!!
        hookKey = hookKey ?: UUID.randomUUID().toString()
        val values = settingValues() + ("webhookUrl" to ConnectorWebhooks.addressOf(hookKey!!))
        val sender = ConnectorSender(this, connector, credentialValues)
        val answer = JsonApi.parsed(send(connector, webhook.subscribe, values, webhook.beforeRequest))
        pollState = (webhook.afterResponse?.let { ConnectorScripts.run(it, values, null, answer, sender::answerFor) } ?: JsonPaths.read(answer, webhook.id))?.asText() ?: ""
        keepPlace()
    }

    /** Ends the subscription when the task stops, so that the service no longer sends events. */
    override fun stop() {
        val webhook = spec?.triggers?.get(operation)?.webhook ?: return
        hookKey?.let(ConnectorWebhooks::stopListening)
        webhook.unsubscribe?.takeIf { pollState != null }?.let { send(spec!!, it, settingValues() + ("webhookId" to pollState!!)) }
        pollState = null
        keepPlace()
    }

    /** The new items in an event a service sent, read as on a check. */
    fun takeItemsOf(event: JsonNode) {
        val poll = spec!!.triggers[operation]!!
        val reply = poll.afterResponse?.let { ConnectorScripts.run(it, settingValues(), null, event) { throw IllegalArgumentException("A webhook event cannot send requests.") } } ?: event
        waiting.addAll(JsonPaths.read(reply, poll.items)?.let { if (it.isArray) it.toList() else listOf(it) }.orEmpty().filterNot { it.isNull }.take(MOST_ITEMS_PER_CHECK))
    }

    private fun send(connector: Connector, request: ConnectorRequest, values: Map<String, String>, beforeRequest: String? = null): ActionResult {
        val operation = ConnectorOperation().also { it.request = request; it.inputs = linkedMapOf(); it.beforeRequest = beforeRequest }
        val sender = ConnectorSender(this, connector, credentialValues)
        val answer = sender.send(ConnectorRequests.build(connector, operation, values, credentialValues, sender::answerFor))
        check(answer.statusCode == StatusCode.SUCCESS) { answer.errorMsg }
        return answer
    }

    /** The next new item that has not started the task yet, as JSON. */
    fun nextWaiting(): String? = waiting.removeFirstOrNull()?.toString()

    /** Asks the service for its newest items, keeps the new ones, and notes where it left off. */
    fun lookForNewItems() {
        val connector = spec ?: throw IllegalStateException("There is no connector called ${this.connector}.")
        val poll = connector.triggers[operation] ?: throw IllegalStateException(problem())
        val asked = Instant.now()
        val lookup = ConnectorOperation().also { it.inputs = poll.inputs; it.request = poll.request; it.beforeRequest = poll.beforeRequest }
        val sender = ConnectorSender(this, connector, credentialValues)
        val values = settingValues()
        val answer = sender.send(ConnectorRequests.build(connector, lookup, values, credentialValues, sender::answerFor))
        check(answer.statusCode == StatusCode.SUCCESS) { answer.errorMsg }
        val reply = poll.afterResponse?.let { ConnectorScripts.run(it, values, null, JsonApi.parsed(answer), sender::answerFor) } ?: JsonApi.parsed(answer)
        val items = JsonPaths.read(reply, poll.items)?.toList().orEmpty()
        val fresh = when (poll.newBy) {
            "lastItem" -> {
                val seen = pollState
                pollState = items.firstOrNull()?.let { JsonPaths.read(it, poll.key)?.asText() } ?: seen
                if (seen == null) emptyList() else items.takeWhile { JsonPaths.read(it, poll.key)?.asText() != seen }.reversed()
            }
            else -> {
                val since = pollState?.toLongOrNull()
                pollState = asked.toEpochMilli().toString()
                if (since == null) emptyList() else items.filter { (timeOf(JsonPaths.read(it, poll.key)) ?: 0) > since }.sortedBy { timeOf(JsonPaths.read(it, poll.key)) }
            }
        }
        waiting.addAll(fresh.take(MOST_ITEMS_PER_CHECK))
        keepPlace()
    }

    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?): ActionResult {
        val item = execution.parameters["TRIGGER_item"]?.toString().orEmpty()
        return ActionResult(StatusCode.SUCCESS, item, "").also { result -> result.outputVariables["item"] = item }
    }

    companion object {
        /** An empty step of a connector, which also describes the connector's kind of step. */
        @JvmStatic
        fun of(connector: String) = ConnectorTrigger().also { it.connector = connector }

        const val KIND_SUFFIX = ".trigger"

        // Enough for a busy service between two checks, without starting thousands of runs when one is turned on late.
        private const val MOST_ITEMS_PER_CHECK = 100

        /** A time in an item, as milliseconds: a number of seconds or milliseconds, or a date and time. */
        fun timeOf(value: JsonNode?): Long? = when {
            value == null || value.isNull -> null
            value.isNumber -> value.asLong().let { if (it < 100_000_000_000) it * 1000 else it }
            else -> runCatching { OffsetDateTime.parse(value.asText()).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { Instant.parse(value.asText()).toEpochMilli() }.getOrNull()
        }
    }
}
