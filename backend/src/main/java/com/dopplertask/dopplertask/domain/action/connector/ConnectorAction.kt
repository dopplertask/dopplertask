package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.RunFiles
import com.dopplertask.dopplertask.domain.RunFiles.RunFile
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
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
import tools.jackson.databind.JsonNode
import java.util.Base64
import java.util.UUID

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
class ConnectorAction : Action() {

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
            val choices = operations
            info.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, choices.keys.firstOrNull().orEmpty(), "",
                choices.map { (key, operation) -> PropertyInformation(key, if (operation.group.isEmpty()) operation.label else "${operation.group}: ${operation.label}") }))
            choices.forEach { (key, operation) ->
                operation.inputs.forEach { (name, input) -> info.add(ConnectorSettings.propertyOf(name, input, mapOf("operation" to arrayOf(key)), connector)) }
            }
            info.addAll(ConnectorSettings.credentialProperties(connector))
            return info
        }

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
        // A file input names a file the run keeps, such as one Read file read.
        val files = chosen.inputs.filter { it.value.type == FILE && !values[it.key].isNullOrBlank() }.mapValues { (input, _) ->
            val name = values[input]!!
            RunFile(name, RunFiles.read(execution, name) ?: return ActionResult.failure("The run has no file called \"$name\". Read or download it in an earlier step."))
        }
        return try {
            val arrived = execution.resumeEvent
            if (chosen.resume != null && arrived != null) resume(connector, chosen, values, ConnectorWebhooks.eventOfStored(arrived))
            else send(connector, chosen, values, files, execution)
        } catch (e: IllegalArgumentException) {
            ActionResult.failure(e.message ?: "The request could not be made.")
        }
    }

    private fun send(connector: Connector, chosen: ConnectorOperation, inputs: Map<String, String>, files: Map<String, RunFile>, execution: TaskExecution): ActionResult {
        // An action that waits gives the service the one address that continues this run.
        val waitKey = chosen.resume?.let { UUID.randomUUID().toString() }
        val values = if (waitKey == null) inputs else inputs + (RESUME_URL to ConnectorWebhooks.addressOf(ConnectorWebhooks.RESUME + waitKey))
        val sender = ConnectorSender(this, connector, credentialValues).also { it.openSession(values) }
        val request = if (chosen === CUSTOM_CALL_OPERATION) ConnectorRequests.customCall(connector, values, credentialValues, sender.session)
        else ConnectorRequests.build(connector, chosen, values, credentialValues, sender::answerFor, files, session = sender.session)
        var downloaded = ByteArray(0)
        val result = sender.send(request) { downloaded = it }
        if (result.statusCode != StatusCode.SUCCESS) return result
        result.waitKey = waitKey
        chosen.download?.let { template ->
            // The answer is a file: the run keeps it, and the step tells later steps about it.
            val name = Connectors.fill(template) { values[it].orEmpty() }.ifBlank { "${connector.name}-download" }
            RunFiles.keep(execution, name, downloaded)
            val about = RunFiles.describe(name, downloaded)
            result.output = JsonApi.mapper.writeValueAsString(about)
            result.outputVariables.putAll(about)
            return result
        }
        var answer = ConnectorSender.answerOf(result)
        if (chosen.result.isNotEmpty()) {
            answer = JsonPaths.read(answer, chosen.result) ?: JsonApi.mapper.nullNode()
            result.output = textOf(answer)
        }
        chosen.afterResponse?.let { script ->
            // A script may keep a file it finds in the answer, such as an attachment sent as base64, for later steps.
            val keepFile = { name: String, base64: String ->
                RunFiles.freeName(name) { it in execution.parameters }.also { RunFiles.keep(execution, it, Base64.getDecoder().decode(base64)) }
            }
            answer = ConnectorScripts.run(script, values, request.unsigned, answer, sender::answerFor, ConnectorSender.receivedOf(result), keepFile = keepFile)
            result.output = textOf(answer)
        }
        return withOutputs(result, chosen, answer)
    }

    /** The step again, when the address of its waiting run was called: its resume script reads what arrived. */
    private fun resume(connector: Connector, chosen: ConnectorOperation, values: Map<String, String>, event: ConnectorWebhooks.Event): ActionResult {
        val sender = ConnectorSender(this, connector, credentialValues).also { it.openSession(values) }
        val answer = ConnectorScripts.run(chosen.resume!!, values, null, event.body, sender::answerFor, event.received)
        return withOutputs(ActionResult(StatusCode.SUCCESS, textOf(answer), ""), chosen, answer)
    }

    private fun withOutputs(result: ActionResult, chosen: ConnectorOperation, answer: JsonNode): ActionResult {
        chosen.outputs.forEach { (name, path) -> JsonPaths.read(answer, path)?.let { result.outputVariables[name] = textOf(it) } }
        return result
    }

    private fun textOf(answer: JsonNode): String = if (answer.isValueNode) answer.asText() else answer.toString()

    /** The choices of an input, such as the audiences of the account, read from the service with the step's credential. */
    fun choices(input: String): List<ConnectorOption> {
        val source = spec?.lists?.get(inputOf(input)?.list) ?: return emptyList()
        return listAt(source.request, source.items).map { item ->
            ConnectorOption().also { option ->
                option.value = JsonPaths.read(item, source.value)?.asText().orEmpty()
                option.label = JsonPaths.read(item, source.label)?.asText() ?: option.value
            }
        }
    }

    /** The fields of a form that follows the service, such as the columns of a table. */
    fun fields(input: String): List<ConnectorField> {
        val source = inputOf(input)?.fieldsFrom ?: return emptyList()
        return listAt(source.request, source.list).map { item ->
            ConnectorField().also { field ->
                field.key = JsonPaths.read(item, source.key)?.asText().orEmpty()
                field.label = JsonPaths.read(item, source.label)?.asText() ?: field.key
                field.type = source.types[JsonPaths.read(item, source.type)?.asText()] ?: "string"
            }
        }
    }

    private fun inputOf(input: String): ConnectorInput? = inputsOfOperation()[input]

    /** The inputs of the chosen action, or of the chosen trigger, whose form asks the same lookups. */
    private fun inputsOfOperation(): Map<String, ConnectorInput> = operations[operation]?.inputs ?: spec?.triggers?.get(operation)?.inputs.orEmpty()

    /** A list read from the service, with the request filled in from the step's other settings. */
    private fun listAt(request: ConnectorRequest, list: String): List<JsonNode> {
        val connector = spec ?: return emptyList()
        val lookup = ConnectorOperation().also { it.inputs = LinkedHashMap(inputsOfOperation()); it.request = request }
        val answer = ConnectorSender(this, connector, credentialValues).send(ConnectorRequests.build(connector, lookup, settingValues(), credentialValues))
        require(answer.statusCode == StatusCode.SUCCESS) { answer.errorMsg }
        return JsonPaths.read(ConnectorSender.answerOf(answer), list)?.toList().orEmpty()
    }

    companion object {
        private const val FILE = "file"

        /** An empty step of a connector, which also describes the connector's kind of step. */
        @JvmStatic
        fun of(connector: String) = ConnectorAction().also { it.connector = connector }

        const val CUSTOM_CALL = "custom_api_call"

        /** The one-time address of an action that waits, for its request and scripts. */
        const val RESUME_URL = "resumeUrl"

        private val CUSTOM_CALL_OPERATION = JsonApi.mapper.readValue(
            """{"label": "Custom API call", "description": "Any call of the service's API, signed in with the credential",
                "inputs": {
                  "method": {"label": "Method", "type": "select", "defaultValue": "GET", "options": [{"value": "GET", "label": "GET"}, {"value": "POST", "label": "POST"},
                    {"value": "PUT", "label": "PUT"}, {"value": "PATCH", "label": "PATCH"}, {"value": "DELETE", "label": "DELETE"}]},
                  "path": {"label": "Path", "required": true, "description": "After the service's address, such as /contacts"},
                  "query": {"label": "Query", "type": "json", "description": "Such as {\"limit\": 10}"},
                  "headers": {"label": "Headers", "type": "json"},
                  "body": {"label": "Body", "type": "json"}}}""", ConnectorOperation::class.java)

    }
}
