package com.dopplertask.dopplertask.domain.action.integration.incident

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.ColumnEncryptor
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Table

@Entity
@Table(name = "PagerDutyAction")
@DiscriminatorValue("pagerduty_action")
class PagerDutyAction : Action() {
    @Column
    var baseUrl: String? = "https://events.pagerduty.com"

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var routingKey: String? = null

    @Column
    var eventAction: String? = TRIGGER

    @Column(length = 1024)
    var summary: String? = null

    @Column
    var severity: String? = "error"

    @Column
    var dedupKey: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val routingKeyValue = variableExtractorUtil.extract(routingKey, execution, scriptLanguage)
        val actionValue = variableExtractorUtil.extract(eventAction, execution, scriptLanguage)
        val summaryValue = variableExtractorUtil.extract(summary, execution, scriptLanguage)
        val dedupKeyValue = variableExtractorUtil.extract(dedupKey, execution, scriptLanguage)
        if (routingKeyValue.isBlank()) return JsonApi.failure("PagerDuty needs a routing key, which is the integration key of an Events API v2 integration on a service.")
        if (actionValue == TRIGGER && summaryValue.isBlank()) return JsonApi.failure("PagerDuty needs a summary to open an incident.")
        // Without the key PagerDuty cannot know which incident is meant.
        if (actionValue != TRIGGER && dedupKeyValue.isBlank()) return JsonApi.failure("PagerDuty needs the incident key to $actionValue an incident. The step that opened it gives it as \$lastLog.outputVariables.dedupKey.")

        val event = mutableMapOf<String, Any>("routing_key" to routingKeyValue, "event_action" to actionValue)
        if (dedupKeyValue.isNotBlank()) event["dedup_key"] = dedupKeyValue
        if (actionValue == TRIGGER) {
            event["payload"] = mapOf(
                "summary" to summaryValue,
                "source" to "DopplerTask",
                "severity" to variableExtractorUtil.extract(severity, execution, scriptLanguage)
            )
        }
        val base = variableExtractorUtil.extract(baseUrl, execution, scriptLanguage).trimEnd('/')
        val result = JsonApi.call("PagerDuty", "POST", "$base/v2/enqueue", mapOf(), event)
        if (result.statusCode == StatusCode.SUCCESS) {
            result.outputVariables["dedupKey"] = JsonApi.parsed(result).path("dedup_key").asText()
        }
        return result
    }

    override val description: String
        get() = "Opens, acknowledges or resolves a PagerDuty incident"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val opening = mapOf("eventAction" to arrayOf(TRIGGER))
            actionInfo.add(PropertyInformation("routingKey", "Routing key", PropertyInformationType.STRING, "", "The integration key of an Events API v2 integration on a PagerDuty service.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("eventAction", "Action", PropertyInformationType.DROPDOWN, TRIGGER, "", listOf(
                PropertyInformation(TRIGGER, "Open an incident"),
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
            actionInfo.add(PropertyInformation("dedupKey", "Incident key", PropertyInformationType.STRING, "", "Opening with the same key does not open a second incident. To acknowledge or resolve, use the key of the incident, which the step that opened it gives as \$lastLog.outputVariables.dedupKey."))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://events.pagerduty.com", "Only change this to go through a proxy, or for an account in the EU, which uses https://events.eu.pagerduty.com."))
            return actionInfo
        }

    private companion object {
        const val TRIGGER = "trigger"
    }
}
