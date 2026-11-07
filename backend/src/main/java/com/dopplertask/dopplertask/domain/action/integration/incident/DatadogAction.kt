package com.dopplertask.dopplertask.domain.action.integration.incident

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
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException

@Entity
@Table(name = "DatadogAction")
@DiscriminatorValue("datadog_action")
class DatadogAction : Action() {
    @Column
    var baseUrl: String? = "https://api.datadoghq.com"

    @Transient
    var apiKey: String? = null

    @Transient
    var applicationKey: String? = null

    @Column
    var operation: String? = "postEvent"

    @Column
    var title: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var eventText: String? = null

    @Column
    var eventTags: String? = null

    @Column
    var monitorId: String? = null

    @Column
    var reason: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val apiKeyValue = read(apiKey).trim()
        val applicationValue = read(applicationKey).trim()
        val operationValue = read(operation)
        val titleValue = read(title).trim()
        val monitorValue = read(monitorId).trim()
        if (apiKeyValue.isBlank()) return ActionResult.failure("Datadog needs an API key.")
        if (operationValue != POST_EVENT && applicationValue.isBlank()) return ActionResult.failure("Datadog needs an application key for this.")
        if (operationValue == POST_EVENT && titleValue.isBlank()) return ActionResult.failure("Datadog needs a title for the event.")
        if (operationValue == POST_EVENT && read(eventText).isBlank()) return ActionResult.failure("Datadog needs the text of the event.")
        if (operationValue != POST_EVENT && monitorValue.toLongOrNull() == null) return ActionResult.failure("Datadog needs the monitor ID as a number, and got \"$monitorValue\".")

        val base = read(baseUrl).trim().trimEnd('/')
        val headers = mutableMapOf("DD-API-KEY" to apiKeyValue)
        if (applicationValue.isNotEmpty()) headers["DD-APPLICATION-KEY"] = applicationValue
        val result = when (operationValue) {
            POST_EVENT -> JsonApi.call("Datadog", "POST", "$base/api/v1/events", headers, mapOf("title" to titleValue, "text" to read(eventText), "tags" to read(eventTags).split(",").map { it.trim() }.filter { it.isNotEmpty() }))
            READ_MONITOR -> JsonApi.call("Datadog", "GET", "$base/api/v1/monitor/$monitorValue", headers, null)
            PAUSE_MONITOR -> {
                val attributes = mutableMapOf<String, Any>("monitor_identifier" to mapOf("monitor_id" to monitorValue.toLong()), "scope" to "*")
                read(reason).trim().takeIf { it.isNotEmpty() }?.let { attributes["message"] = it }
                JsonApi.call("Datadog", "POST", "$base/api/v2/downtime", headers, mapOf("data" to mapOf("type" to "downtime", "attributes" to attributes)))
            }
            else -> return ActionResult.failure("Datadog has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            when (operationValue) {
                POST_EVENT -> result.outputVariables["eventId"] = answer.path("event").path("id_str").asText()
                READ_MONITOR -> result.outputVariables["monitorState"] = answer.path("overall_state").asText()
                PAUSE_MONITOR -> result.outputVariables["downtimeId"] = answer.path("data").path("id").asText()
            }
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("eventId" to "Event", "monitorState" to "State of the monitor", "downtimeId" to "Downtime")

    override val description: String
        get() = "Posts a Datadog event, reads a monitor or pauses its alerts"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForPostEvent = mapOf("operation" to arrayOf(POST_EVENT))
            val showForReadMonitorPauseMonitor = mapOf("operation" to arrayOf(READ_MONITOR, PAUSE_MONITOR))
            val showForPauseMonitor = mapOf("operation" to arrayOf(PAUSE_MONITOR))
            actionInfo.add(PropertyInformation("apiKey", "API key", PropertyInformationType.STRING, "", "Under Organization settings, API keys.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("applicationKey", "Application key", PropertyInformationType.STRING, "", "Under Organization settings, Application keys.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, POST_EVENT, "", listOf(
                PropertyInformation(POST_EVENT, "Post an event"),
                PropertyInformation(READ_MONITOR, "Read a monitor"),
                PropertyInformation(PAUSE_MONITOR, "Pause the alerts of a monitor")
            )))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForPostEvent))
            actionInfo.add(PropertyInformation("eventText", "Text", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForPostEvent))
            actionInfo.add(PropertyInformation("eventTags", "Tags", PropertyInformationType.STRING, "", "Separated by commas, such as env:production.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForPostEvent))
            actionInfo.add(PropertyInformation("monitorId", "Monitor ID", PropertyInformationType.STRING, "", "The number in the address of the monitor.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadMonitorPauseMonitor))
            actionInfo.add(PropertyInformation("reason", "Reason", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForPauseMonitor))
            actionInfo.add(PropertyInformation("baseUrl", "Datadog site", PropertyInformationType.STRING, "https://api.datadoghq.com", "Such as https://api.datadoghq.eu in Europe."))
            return actionInfo
        }

    private companion object {
        const val POST_EVENT = "postEvent"
        const val READ_MONITOR = "readMonitor"
        const val PAUSE_MONITOR = "pauseMonitor"
    }
}
