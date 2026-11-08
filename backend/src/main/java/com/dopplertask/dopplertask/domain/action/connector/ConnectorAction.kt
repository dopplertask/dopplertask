package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.domain.action.integration.OAuthSignIn
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
        // Signing in with an account reads these from the step, as it does for every step that offers it.
        if (spec?.credential?.oauth2 != null) super.useCredentialValues(values.filterKeys { it.startsWith(OAUTH) } + ("oauthProvider" to stepTypeName))
    }

    override fun inlineCredentialValues(fields: Set<String>): Map<String, String> =
        settingValues().filter { it.key in fields && it.value.isNotBlank() }

    override fun forgetCredentialValues(fields: Set<String>) {
        credentialValues = emptyMap()
        settings = JsonApi.mapper.writeValueAsString(settingValues() - fields)
        if (spec?.credential?.oauth2 != null) super.forgetCredentialValues(fields.filter { it.startsWith(OAUTH) }.toSet() + "oauthProvider")
    }

    private val spec: Connector?
        get() = Connectors.find(connector)

    /** The connector's actions, and any call of its API when the file gives where the API starts. */
    private val operations: Map<String, ConnectorOperation>
        get() = spec?.let { if (it.baseUrl.isEmpty()) it.actions else it.actions + (CUSTOM_CALL to CUSTOM_CALL_OPERATION) }.orEmpty()

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
            val choices = operations.filterValues { it.audience != "ai" }
            info.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, choices.keys.firstOrNull().orEmpty(), "",
                choices.map { (key, operation) -> PropertyInformation(key, operation.label) }))
            choices.forEach { (key, operation) ->
                operation.inputs.forEach { (name, input) -> info.add(propertyOf(name, input, PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(key)))) }
            }
            connector.credential?.fields?.forEach { (name, field) ->
                info.add(propertyOf(name, field, if (field.secret) PropertyInformationCategory.SECRET else PropertyInformationCategory.CREDENTIAL, mapOf()))
            }
            // The connector's file names the service's sign-in addresses, so a person only gives the client.
            connector.credential?.oauth2?.let { oauth -> info.addAll(OAuthSignIn.fields(stepTypeName, oauth.scope).filter { it.name !in FIXED_BY_FILE }) }
            return info
        }

    private fun propertyOf(name: String, input: ConnectorInput, category: PropertyInformationCategory, shownFor: Map<String, Array<String>>) =
        PropertyInformation(name, input.label, if (input.optionsFrom != null) PropertyInformationType.CHOICES else TYPES[input.type] ?: PropertyInformationType.STRING, input.defaultValue, input.description,
            input.options.map { PropertyInformation(it.value, it.label) }, category, shownFor)

    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?) =
        perform(execution, variableExtractorUtil)

    /** Sends the chosen action's request. Apart from run, so that the connector checks can call it without a server. */
    fun perform(execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil): ActionResult {
        val connector = spec ?: return ActionResult.failure("There is no connector called $connector. It may have been removed.")
        val chosen = operations[operation]
            ?: return ActionResult.failure("Choose what ${connector.label} should do: ${operations.keys.joinToString(", ")}.")
        val saved = settingValues()
        val values = chosen.inputs.mapValues { (name, input) ->
            variableExtractorUtil.extract(saved[name] ?: input.defaultValue, execution, scriptLanguage)
        }
        chosen.inputs.filter { it.value.required && values[it.key].isNullOrBlank() }.keys.firstOrNull()?.let {
            return ActionResult.failure("${chosen.inputs[it]!!.label} is required.")
        }
        return try {
            send(connector, chosen, values)
        } catch (e: IllegalArgumentException) {
            ActionResult.failure(e.message ?: "The request could not be made.")
        }
    }

    private fun send(connector: Connector, chosen: ConnectorOperation, values: Map<String, String>): ActionResult {
        // A script's own request goes out the same way, and a failed one stops the step with what the service said.
        val scriptRequest = { spec: JsonNode ->
            val answer = deliver(connector, ConnectorRequests.sign(connector, spec, credentialValues))
            require(answer.statusCode == StatusCode.SUCCESS) { answer.errorMsg }
            JsonApi.parsed(answer)
        }
        val request = if (chosen === CUSTOM_CALL_OPERATION) ConnectorRequests.customCall(connector, values, credentialValues)
        else ConnectorRequests.build(connector, chosen, values, credentialValues, scriptRequest)
        val result = deliver(connector, request)
        if (result.statusCode != StatusCode.SUCCESS) return result
        var answer = JsonApi.parsed(result)
        if (chosen.result.isNotEmpty()) {
            answer = JsonPaths.read(answer, chosen.result) ?: JsonApi.mapper.nullNode()
            result.output = if (answer.isValueNode) answer.asText() else answer.toString()
        }
        chosen.afterResponse?.let { script ->
            answer = ConnectorScripts.run(script, values, null, answer, scriptRequest)
            result.output = if (answer.isValueNode) answer.asText() else answer.toString()
        }
        chosen.outputs.forEach { (name, path) -> JsonPaths.read(answer, path)?.let { result.outputVariables[name] = if (it.isValueNode) it.asText() else it.toString() } }
        return result
    }

    /** The choices of an input, such as the audiences of the account, read from the service with the step's credential. */
    fun choices(input: String): List<ConnectorOption> {
        val connector = spec ?: return emptyList()
        val chosen = operations[operation] ?: return emptyList()
        val source = chosen.inputs[input]?.optionsFrom ?: return emptyList()
        val lookup = ConnectorOperation().also { it.inputs = chosen.inputs; it.request = source.request }
        val answer = deliver(connector, ConnectorRequests.build(connector, lookup, settingValues(), credentialValues))
        require(answer.statusCode == StatusCode.SUCCESS) { answer.errorMsg }
        val list = JsonPaths.read(JsonApi.parsed(answer), source.list) ?: return emptyList()
        return list.toList().map { item ->
            ConnectorOption().also { option ->
                option.value = JsonPaths.read(item, source.value)?.asText().orEmpty()
                option.label = JsonPaths.read(item, source.label)?.asText() ?: option.value
            }
        }
    }

    /** Sends a signed request, only to the connector's hosts. */
    private fun deliver(connector: Connector, request: ConnectorRequests.Built): ActionResult {
        require(Connectors.allows(connector, URI.create(request.url).host, credentialValues)) { "${connector.label} may only send its credential to ${connector.hosts.joinToString(", ")}." }
        var headers = request.headers
        if (connector.credential?.oauth2 != null) {
            val signIn = OAuthSignIn.headersFor(this, connector.label)
            signIn.failure?.let { return it }
            headers = headers + signIn.headers
        }
        val address = Connectors.localServerForTests?.let { request.url.replaceFirst(Regex("^https://[^/]+"), it) } ?: request.url
        return JsonApi.call(connector.label, request.method, address, headers, request.body)
    }

    companion object {
        private const val OAUTH = "oauth"
        private val FIXED_BY_FILE = setOf("oauthProvider", "oauthAuthUrl", "oauthTokenUrl", "oauthAuthParameters")

        const val CUSTOM_CALL = "custom_api_call"

        private val CUSTOM_CALL_OPERATION = JsonApi.mapper.readValue(
            """{"label": "Custom API call", "description": "Any call of the service's API, signed in with the credential",
                "inputs": {
                  "method": {"label": "Method", "type": "select", "defaultValue": "GET", "options": [{"value": "GET", "label": "GET"}, {"value": "POST", "label": "POST"},
                    {"value": "PUT", "label": "PUT"}, {"value": "PATCH", "label": "PATCH"}, {"value": "DELETE", "label": "DELETE"}]},
                  "path": {"label": "Path", "required": true, "description": "After the service's address, such as /contacts"},
                  "query": {"label": "Query", "type": "json", "description": "Such as {\"limit\": 10}"},
                  "headers": {"label": "Headers", "type": "json"},
                  "body": {"label": "Body", "type": "json"}}}""", ConnectorOperation::class.java)

        private val TYPES = mapOf(
            "string" to PropertyInformationType.STRING, "multiline" to PropertyInformationType.MULTILINE, "json" to PropertyInformationType.MULTILINE,
            "number" to PropertyInformationType.NUMBER, "boolean" to PropertyInformationType.BOOLEAN, "select" to PropertyInformationType.DROPDOWN
        )
    }
}
