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
import java.io.IOException
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Transient

@Entity
@Table(name = "PagerDutyAction")
@DiscriminatorValue("pagerduty_action")
class PagerDutyAction : Action() {
    @Column
    var baseUrl: String? = "https://events.pagerduty.com"

    @Transient
    var integrationKey: String? = null

    @Column
    var operation: String? = OPEN

    @Column(length = 1024)
    var summary: String? = null

    @Column
    var severity: String? = "error"

    @Column
    var newIncidentKey: String? = null

    @Column
    var incidentKey: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val integrationKeyValue = variableExtractorUtil.extract(integrationKey, execution, scriptLanguage)
        val operationValue = variableExtractorUtil.extract(operation, execution, scriptLanguage)
        val summaryValue = variableExtractorUtil.extract(summary, execution, scriptLanguage)
        val incidentKeyValue = variableExtractorUtil.extract(incidentKey, execution, scriptLanguage)
        if (integrationKeyValue.isBlank()) return ActionResult.failure("PagerDuty needs an integration key, which an Events API v2 integration on a service gives.")
        if (operationValue == OPEN && summaryValue.isBlank()) return ActionResult.failure("PagerDuty needs a summary to open an incident.")
        // Without the key PagerDuty cannot know which incident is meant.
        if (operationValue != OPEN && incidentKeyValue.isBlank()) return ActionResult.failure("PagerDuty needs the incident key to $operationValue an incident. The step that opened it gives it as \$lastLog.outputVariables.incidentKey.")

        val event = mutableMapOf<String, Any>("routing_key" to integrationKeyValue, "event_action" to (if (operationValue == OPEN) "trigger" else operationValue))
        val keyValue = if (operationValue == OPEN) variableExtractorUtil.extract(newIncidentKey, execution, scriptLanguage) else incidentKeyValue
        if (keyValue.isNotBlank()) event["dedup_key"] = keyValue
        if (operationValue == OPEN) {
            event["payload"] = mapOf(
                "summary" to summaryValue,
                "source" to "DopplerTask",
                "severity" to variableExtractorUtil.extract(severity, execution, scriptLanguage)
            )
        }
        val base = variableExtractorUtil.extract(baseUrl, execution, scriptLanguage).trimEnd('/')
        val result = JsonApi.call("PagerDuty", "POST", "$base/v2/enqueue", mapOf(), event)
        if (result.statusCode == StatusCode.SUCCESS) {
            result.outputVariables["incidentKey"] = JsonApi.parsed(result).path("dedup_key").asText()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("incidentKey" to "Incident key")

    override val description: String
        get() = "Opens, acknowledges or resolves a PagerDuty incident"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val opening = mapOf("operation" to arrayOf(OPEN))
            actionInfo.add(PropertyInformation("integrationKey", "Integration key", PropertyInformationType.STRING, "", "Shown on an Events API v2 integration of a PagerDuty service.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, OPEN, "", listOf(
                PropertyInformation(OPEN, "Open an incident"),
                PropertyInformation("acknowledge", "Acknowledge an incident"),
                PropertyInformation("resolve", "Resolve an incident")
            )))
            actionInfo.add(PropertyInformation("summary", "Summary", PropertyInformationType.STRING, "", "What is wrong, as the person on call should read it.", mutableListOf(), PropertyInformationCategory.PROPERTY, opening))
            actionInfo.add(PropertyInformation("severity", "Severity", PropertyInformationType.DROPDOWN, "error", "", listOf(
                PropertyInformation("critical", "Critical"),
                PropertyInformation("error", "Error"),
                PropertyInformation("warning", "Warning"),
                PropertyInformation("info", "Info")
            ), PropertyInformationCategory.PROPERTY, opening))
            actionInfo.add(PropertyInformation("newIncidentKey", "Key for the incident", PropertyInformationType.STRING, "", "Opening with the same key again does not open a second incident, which is useful for a task on a schedule.", mutableListOf(), PropertyInformationCategory.PROPERTY, opening))
            actionInfo.add(PropertyInformation("incidentKey", "Incident key", PropertyInformationType.STRING, "", "The step that opened the incident gives it as \$lastLog.outputVariables.incidentKey.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf("acknowledge", "resolve"))))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://events.pagerduty.com", "Only change this to go through a proxy, or for an account in the EU, which uses https://events.eu.pagerduty.com."))
            return actionInfo
        }

    private companion object {
        const val OPEN = "open"
    }
}
