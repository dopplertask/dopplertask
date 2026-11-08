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

    /** Where the last check left off: a time in milliseconds, or the id of the newest item. Kept with the trigger. */
    @Lob
    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    var pollState: String? = null

    @Transient
    @JsonIgnore
    private var credentialValues: Map<String, String> = emptyMap()

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
        return null
    }

    override fun trigger(): TriggerResult {
        while (true) {
            nextWaiting()?.let { return TriggerResult(mutableMapOf("item" to it)) }
            if (pollState != null) Thread.sleep(Duration.ofMinutes(every!!.trim().toLong()).toMillis())
            lookForNewItems()
        }
    }

    /** The next new item that has not started the task yet, as JSON. */
    fun nextWaiting(): String? = waiting.removeFirstOrNull()?.toString()

    /** Asks the service for its newest items, keeps the new ones, and notes where it left off. */
    fun lookForNewItems() {
        val connector = spec ?: throw IllegalStateException("There is no connector called ${this.connector}.")
        val poll = connector.triggers[operation] ?: throw IllegalStateException(problem())
        val asked = Instant.now()
        val lookup = ConnectorOperation().also { it.inputs = poll.inputs; it.request = poll.request }
        val sender = ConnectorSender(this, connector, credentialValues)
        val answer = sender.send(ConnectorRequests.build(connector, lookup, settingValues(), credentialValues, sender::answerFor))
        check(answer.statusCode == StatusCode.SUCCESS) { answer.errorMsg }
        val items = JsonPaths.read(JsonApi.parsed(answer), poll.items)?.toList().orEmpty()
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
