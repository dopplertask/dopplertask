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
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException

@Entity
@Table(name = "SentryAction")
@DiscriminatorValue("sentry_action")
class SentryAction : Action() {
    @Column
    var baseUrl: String? = "https://sentry.io"

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "listIssues"

    @Column
    var organization: String? = null

    @Column
    var issueId: String? = null

    @Column
    var searchText: String? = "is:unresolved"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val tokenValue = read(token).trim()
        val organizationValue = read(organization).trim()
        val operationValue = read(operation)
        val issueValue = read(issueId).trim()
        if (tokenValue.isBlank()) return ActionResult.failure("Sentry needs an auth token.")
        if (organizationValue.isBlank()) return ActionResult.failure("Sentry needs the slug of the organization.")
        if (operationValue != LIST_ISSUES && issueValue.isBlank()) return ActionResult.failure("Sentry needs the ID of the issue.")

        val headers = mapOf("Authorization" to "Bearer $tokenValue")
        val issues = JsonApi.baseAddress(read(baseUrl)) + "/api/0/organizations/${JsonApi.encoded(organizationValue)}/issues/"
        val issue = "$issues${JsonApi.encoded(issueValue)}/"
        val result = when (operationValue) {
            LIST_ISSUES -> JsonApi.call("Sentry", "GET", "$issues?query=${JsonApi.encoded(read(searchText).trim())}", headers, null)
            READ_ISSUE -> JsonApi.call("Sentry", "GET", issue, headers, null)
            RESOLVE_ISSUE -> JsonApi.call("Sentry", "PUT", issue, headers, mapOf("status" to "resolved"))
            else -> return ActionResult.failure("Sentry has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            if (operationValue == LIST_ISSUES) result.outputVariables["issueCount"] = answer.size()
            else result.outputVariables["issueStatus"] = answer.path("status").asText()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("issueCount" to "Number of issues", "issueStatus" to "Status")

    override val description: String
        get() = "Lists or reads Sentry issues, or marks one as resolved"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForListIssues = mapOf("operation" to arrayOf(LIST_ISSUES))
            val showForReadIssueResolveIssue = mapOf("operation" to arrayOf(READ_ISSUE, RESOLVE_ISSUE))
            actionInfo.add(PropertyInformation("token", "Auth token", PropertyInformationType.STRING, "", "Under Settings, Developer settings, Personal tokens.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, LIST_ISSUES, "", listOf(
                PropertyInformation(LIST_ISSUES, "List issues"),
                PropertyInformation(READ_ISSUE, "Read an issue"),
                PropertyInformation(RESOLVE_ISSUE, "Mark an issue as resolved")
            )))
            actionInfo.add(PropertyInformation("organization", "Organization slug", PropertyInformationType.STRING, "", "Shown in the address, as sentry.io/organizations/acme."))
            actionInfo.add(PropertyInformation("searchText", "Search for", PropertyInformationType.STRING, "is:unresolved", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForListIssues))
            actionInfo.add(PropertyInformation("issueId", "Issue ID", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadIssueResolveIssue))
            actionInfo.add(PropertyInformation("baseUrl", "Sentry address", PropertyInformationType.STRING, "https://sentry.io", "Only change this for a self-hosted Sentry."))
            return actionInfo
        }

    private companion object {
        const val LIST_ISSUES = "listIssues"
        const val READ_ISSUE = "readIssue"
        const val RESOLVE_ISSUE = "resolveIssue"
    }
}
