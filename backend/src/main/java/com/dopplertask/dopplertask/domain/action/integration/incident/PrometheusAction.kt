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
@Table(name = "PrometheusAction")
@DiscriminatorValue("prometheus_action")
class PrometheusAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "runQuery"

    @Lob
    @Column(columnDefinition = "TEXT")
    var query: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = JsonApi.baseAddress(read(siteUrl))
        val operationValue = read(operation)
        val queryValue = read(query).trim()
        if (site.isBlank()) return ActionResult.failure("Prometheus needs the address of the server, such as https://prometheus.example.com.")
        if (operationValue == RUN_QUERY && queryValue.isBlank()) return ActionResult.failure("Prometheus needs a query, such as up == 0.")
        val headers = read(token).trim().takeIf { it.isNotEmpty() }?.let { mapOf("Authorization" to "Bearer $it") } ?: mapOf()
        val result = when (operationValue) {
            RUN_QUERY -> JsonApi.call("Prometheus", "GET", "$site/api/v1/query?query=${JsonApi.encoded(queryValue)}", headers, null)
            LIST_ALERTS -> JsonApi.call("Prometheus", "GET", "$site/api/v1/alerts", headers, null)
            else -> return ActionResult.failure("Prometheus has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val data = JsonApi.parsed(result).path("data")
            if (operationValue == RUN_QUERY) result.outputVariables["resultCount"] = data.path("result").size() else result.outputVariables["alertCount"] = data.path("alerts").size()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("resultCount" to "Number of results", "alertCount" to "Number of alerts")

    override val description: String
        get() = "Runs a Prometheus query or lists the active alerts"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForRunQuery = mapOf("operation" to arrayOf(RUN_QUERY))
            actionInfo.add(PropertyInformation("token", "Bearer token", PropertyInformationType.STRING, "", "Leave empty when the server has no sign-in.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, RUN_QUERY, "", listOf(
                PropertyInformation(RUN_QUERY, "Run a query"),
                PropertyInformation(LIST_ALERTS, "List active alerts")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "Server address", PropertyInformationType.STRING, "", "Such as https://prometheus.example.com."))
            actionInfo.add(PropertyInformation("query", "PromQL query", PropertyInformationType.MULTILINE, "", "Such as up == 0.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForRunQuery))
            return actionInfo
        }

    private companion object {
        const val RUN_QUERY = "runQuery"
        const val LIST_ALERTS = "listAlerts"
    }
}
