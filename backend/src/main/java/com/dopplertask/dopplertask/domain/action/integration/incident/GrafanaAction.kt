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
@Table(name = "GrafanaAction")
@DiscriminatorValue("grafana_action")
class GrafanaAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "addAnnotation"

    @Lob
    @Column(columnDefinition = "TEXT")
    var annotationText: String? = null

    @Column
    var annotationTags: String? = null

    @Column
    var dashboardUid: String? = null

    @Column
    var searchText: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = JsonApi.baseAddress(read(siteUrl))
        val tokenValue = read(token).trim()
        val operationValue = read(operation)
        val textValue = read(annotationText).trim()
        if (site.isBlank()) return ActionResult.failure("Grafana needs its address, such as https://grafana.example.com.")
        if (tokenValue.isBlank()) return ActionResult.failure("Grafana needs the token of a service account.")
        if (operationValue == ADD_ANNOTATION && textValue.isBlank()) return ActionResult.failure("Grafana needs the text of what happened.")
        val headers = mapOf("Authorization" to "Bearer $tokenValue")
        val result = when (operationValue) {
            ADD_ANNOTATION -> {
                val annotation = mutableMapOf<String, Any>("text" to textValue, "tags" to read(annotationTags).split(",").map { it.trim() }.filter { it.isNotEmpty() })
                read(dashboardUid).trim().takeIf { it.isNotEmpty() }?.let { annotation["dashboardUID"] = it }
                JsonApi.call("Grafana", "POST", "$site/api/annotations", headers, annotation)
            }
            FIND_DASHBOARDS -> JsonApi.call("Grafana", "GET", "$site/api/search?type=dash-db&query=${JsonApi.encoded(read(searchText).trim())}", headers, null)
            LIST_FIRING_ALERTS -> JsonApi.call("Grafana", "GET", "$site/api/alertmanager/grafana/api/v2/alerts?active=true&silenced=false&inhibited=false", headers, null)
            else -> return ActionResult.failure("Grafana has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            when (operationValue) {
                ADD_ANNOTATION -> result.outputVariables["annotationId"] = answer.path("id").asText()
                FIND_DASHBOARDS -> result.outputVariables["dashboardCount"] = answer.size()
                LIST_FIRING_ALERTS -> result.outputVariables["alertCount"] = answer.size()
            }
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("annotationId" to "Annotation", "dashboardCount" to "Number of dashboards", "alertCount" to "Number of alerts")

    override val description: String
        get() = "Marks an event on Grafana graphs, finds dashboards or lists firing alerts"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForAddAnnotation = mapOf("operation" to arrayOf(ADD_ANNOTATION))
            val showForFindDashboards = mapOf("operation" to arrayOf(FIND_DASHBOARDS))
            actionInfo.add(PropertyInformation("token", "Service account token", PropertyInformationType.STRING, "", "Under Administration, Users and access, Service accounts.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, ADD_ANNOTATION, "", listOf(
                PropertyInformation(ADD_ANNOTATION, "Mark an event on the graphs"),
                PropertyInformation(FIND_DASHBOARDS, "Find dashboards"),
                PropertyInformation(LIST_FIRING_ALERTS, "List firing alerts")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "Grafana address", PropertyInformationType.STRING, "", "Such as https://grafana.example.com."))
            actionInfo.add(PropertyInformation("annotationText", "What happened", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddAnnotation))
            actionInfo.add(PropertyInformation("annotationTags", "Tags", PropertyInformationType.STRING, "", "Separated by commas.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddAnnotation))
            actionInfo.add(PropertyInformation("dashboardUid", "Dashboard UID", PropertyInformationType.STRING, "", "Leave empty to mark it on every graph.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddAnnotation))
            actionInfo.add(PropertyInformation("searchText", "Search for", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForFindDashboards))
            return actionInfo
        }

    private companion object {
        const val ADD_ANNOTATION = "addAnnotation"
        const val FIND_DASHBOARDS = "findDashboards"
        const val LIST_FIRING_ALERTS = "listFiringAlerts"
    }
}
