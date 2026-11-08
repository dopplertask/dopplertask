package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
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
import java.net.URI

/**
 * A step of a connector file. Its kind is the connector's name, so a task says Slack rather than ConnectorAction, and
 * its settings sit directly on the step, as they do on every other step.
 *
 * The person's settings are evaluated once, as on every step. The file's request is then filled in as plain text, so
 * that what a service answers is never run as a template, and it only goes to the connector's own hosts.
 */
@Entity
@Table(name = "ConnectorAction")
@DiscriminatorValue("connector_action")
class ConnectorAction() : Action() {

    constructor(connector: String) : this() {
        this.connector = connector
    }

    @Column
    @JsonProperty(value = "@type", access = JsonProperty.Access.WRITE_ONLY)
    var connector: String? = null

    @Column
    var operation: String? = null

    /** The settings, as JSON, since every connector asks for different ones. */
    @Lob
    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    var settings: String = "{}"

    /** What the credential gives, only while the step runs. */
    @Transient
    @JsonIgnore
    private var credentialValues: Map<String, String> = emptyMap()

    @JsonAnyGetter
    fun settingValues(): Map<String, String> = JsonApi.mapper.readValue(settings, LinkedHashMap::class.java)
        .entries.associate { it.key.toString() to it.value.toString() }

    @JsonAnySetter
    fun setSetting(name: String, value: Any?) {
        val text = if (value is String) value else JsonApi.mapper.writeValueAsString(value)
        settings = JsonApi.mapper.writeValueAsString(settingValues() + (name to text))
    }

    override fun useCredentialValues(values: Map<String, String>) {
        credentialValues = values
    }

    override fun inlineCredentialValues(fields: Set<String>): Map<String, String> =
        settingValues().filter { it.key in fields && it.value.isNotBlank() }

    override fun forgetCredentialValues(fields: Set<String>) {
        credentialValues = emptyMap()
        settings = JsonApi.mapper.writeValueAsString(settingValues() - fields)
    }

    private val spec: Connector?
        get() = Connectors.find(connector)

    override val stepTypeName: String
        get() = connector ?: "ConnectorAction"

    override val stepLabel: String?
        get() = spec?.label

    override val stepIcon: String?
        get() = spec?.icon

    override val credentialType: String
        get() = stepTypeName

    override val description: String
        get() = spec?.description ?: "A connector that is not installed"

    override val outputs: Map<String, String>
        get() = spec?.actions?.values?.flatMap { it.outputs.keys }?.associateWith { "From the answer" }.orEmpty()

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val info = super.actionInfo
            val connector = spec ?: return info
            val choices = connector.actions.filterValues { it.audience != "ai" }
            info.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, choices.keys.firstOrNull().orEmpty(), "",
                choices.map { (key, operation) -> PropertyInformation(key, operation.label) }))
            choices.forEach { (key, operation) ->
                operation.inputs.forEach { (name, input) -> info.add(propertyOf(name, input, PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(key)))) }
            }
            connector.credential?.fields?.forEach { (name, field) ->
                info.add(propertyOf(name, field, if (field.secret) PropertyInformationCategory.SECRET else PropertyInformationCategory.CREDENTIAL, mapOf()))
            }
            return info
        }

    private fun propertyOf(name: String, input: ConnectorInput, category: PropertyInformationCategory, shownFor: Map<String, Array<String>>) =
        PropertyInformation(name, input.label, TYPES[input.type] ?: PropertyInformationType.STRING, input.defaultValue, input.description,
            input.options.map { PropertyInformation(it.value, it.label) }, category, shownFor)

    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?) =
        perform(execution, variableExtractorUtil)

    /** Sends the chosen action's request. Apart from run, so that the connector checks can call it without a server. */
    fun perform(execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil): ActionResult {
        val connector = spec ?: return ActionResult.failure("There is no connector called $connector. It may have been removed.")
        val chosen = connector.actions[operation]
            ?: return ActionResult.failure("Choose what ${connector.label} should do: ${connector.actions.keys.joinToString(", ")}.")
        val saved = settingValues()
        val values = chosen.inputs.mapValues { (name, input) ->
            variableExtractorUtil.extract(saved[name] ?: input.defaultValue, execution, scriptLanguage)
        }
        chosen.inputs.filter { it.value.required && values[it.key].isNullOrBlank() }.keys.firstOrNull()?.let {
            return ActionResult.failure("${chosen.inputs[it]!!.label} is required.")
        }
        val request = try {
            ConnectorRequests.build(connector, chosen, values, credentialValues)
        } catch (e: IllegalArgumentException) {
            return ActionResult.failure(e.message ?: "The request could not be made.")
        }
        if (!Connectors.allows(connector, URI.create(request.url).host)) {
            return ActionResult.failure("${connector.label} may only send its credential to ${connector.hosts.joinToString(", ")}.")
        }
        val address = Connectors.localServerForTests?.let { request.url.replaceFirst(Regex("^https://[^/]+"), it) } ?: request.url
        val result = JsonApi.call(connector.label, chosen.request.method, address, request.headers, request.body)
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            chosen.outputs.forEach { (name, path) -> JsonPaths.read(answer, path)?.let { result.outputVariables[name] = if (it.isValueNode) it.asText() else it.toString() } }
        }
        return result
    }

    companion object {
        private val TYPES = mapOf(
            "string" to PropertyInformationType.STRING, "multiline" to PropertyInformationType.MULTILINE, "json" to PropertyInformationType.MULTILINE,
            "number" to PropertyInformationType.NUMBER, "boolean" to PropertyInformationType.BOOLEAN, "select" to PropertyInformationType.DROPDOWN
        )
    }
}
