package com.dopplertask.dopplertask.domain.action.integration.code

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
import java.util.Base64
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient

private const val ATLASSIAN_ACCOUNT = "Atlassian account"

@Entity
@Table(name = "JiraAction")
@DiscriminatorValue("jira_action")
class JiraAction : Action() {
    override val runsInPreview get() = true

    @Column
    var siteUrl: String? = null

    @Transient
    var email: String? = null

    @Transient
    var token: String? = null

    @Column
    var operation: String? = CREATE_ISSUE

    @Column
    var project: String? = null

    @Column
    var issueType: String? = "Task"

    @Column
    var issueKey: String? = null

    @Column(length = 1024)
    var summary: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var issueDescription: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var comment: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val site = JsonApi.baseAddress(variableExtractorUtil.extract(siteUrl, execution, scriptLanguage))
        val emailValue = variableExtractorUtil.extract(email, execution, scriptLanguage).trim()
        val tokenValue = variableExtractorUtil.extract(token, execution, scriptLanguage)
        val operationValue = variableExtractorUtil.extract(operation, execution, scriptLanguage)
        val projectValue = variableExtractorUtil.extract(project, execution, scriptLanguage).trim()
        val keyValue = variableExtractorUtil.extract(issueKey, execution, scriptLanguage).trim()
        val summaryValue = variableExtractorUtil.extract(summary, execution, scriptLanguage)
        val descriptionValue = variableExtractorUtil.extract(issueDescription, execution, scriptLanguage)
        val commentValue = variableExtractorUtil.extract(comment, execution, scriptLanguage)
        if (site.isBlank()) return ActionResult.failure("Jira needs the address of the site, such as https://acme.atlassian.net.")
        if (tokenValue.isBlank()) return ActionResult.failure("Jira needs an API token.")
        if (operationValue == CREATE_ISSUE && projectValue.isBlank()) return ActionResult.failure("Jira needs the key of a project, such as OPS.")
        if (operationValue == CREATE_ISSUE && summaryValue.isBlank()) return ActionResult.failure("Jira needs a summary for the issue.")
        if (operationValue == ADD_COMMENT && keyValue.isBlank()) return ActionResult.failure("Jira needs the key of the issue, such as OPS-12.")
        if (operationValue == ADD_COMMENT && commentValue.isBlank()) return ActionResult.failure("Jira needs the text of the comment.")

        // Jira in the cloud signs in with an email and a token, and on your own server with the token alone.
        val authorization = if (emailValue.isEmpty()) "Bearer $tokenValue"
        else "Basic " + Base64.getEncoder().encodeToString("$emailValue:$tokenValue".toByteArray())
        val headers = mapOf("Authorization" to authorization)
        // Version 2 of the API takes plain text in every version of Jira, where version 3 only exists in the cloud.
        val result = when (operationValue) {
            CREATE_ISSUE -> JsonApi.call("Jira", "POST", "$site/rest/api/2/issue", headers, mapOf("fields" to mapOf(
                "project" to mapOf("key" to projectValue),
                "issuetype" to mapOf("name" to variableExtractorUtil.extract(issueType, execution, scriptLanguage)),
                "summary" to summaryValue,
                "description" to descriptionValue
            )))
            ADD_COMMENT -> JsonApi.call("Jira", "POST", "$site/rest/api/2/issue/$keyValue/comment", headers, mapOf("body" to commentValue))
            else -> ActionResult.failure("Jira has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val key = if (operationValue == CREATE_ISSUE) JsonApi.parsed(result).path("key").asText() else keyValue
            result.outputVariables["issueKey"] = key
            result.outputVariables["url"] = "$site/browse/$key"
        }
        return result
    }

    override val credentialType: String
        get() = ATLASSIAN_ACCOUNT

    override val outputs: Map<String, String>
        get() = mapOf("issueKey" to "Issue key", "url" to "Address")

    override val description: String
        get() = "Creates or comments on a Jira issue"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val creating = mapOf("operation" to arrayOf(CREATE_ISSUE))
            actionInfo.add(PropertyInformation("siteUrl", "Site", PropertyInformationType.STRING, "", "Such as https://acme.atlassian.net."))
            actionInfo.add(PropertyInformation("email", "Email", PropertyInformationType.STRING, "", "The email of the account, for Jira in the cloud. Leave it empty on your own server.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("token", "API token", PropertyInformationType.STRING, "", "An API token in the cloud, or a personal access token on your own server.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_ISSUE, "", listOf(
                PropertyInformation(CREATE_ISSUE, "Create an issue"),
                PropertyInformation(ADD_COMMENT, "Comment on an issue")
            )))
            actionInfo.add(PropertyInformation("project", "Project", PropertyInformationType.STRING, "", "The key of the project, such as OPS.", mutableListOf(), PropertyInformationCategory.PROPERTY, creating))
            actionInfo.add(PropertyInformation("issueType", "Issue type", PropertyInformationType.STRING, "Task", "As it is named in the project, such as Task or Bug.", mutableListOf(), PropertyInformationCategory.PROPERTY, creating))
            actionInfo.add(PropertyInformation("summary", "Summary", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, creating))
            actionInfo.add(PropertyInformation("issueKey", "Issue key", PropertyInformationType.STRING, "", "Such as OPS-12. A step that creates an issue gives it as \$lastLog.outputVariables.issueKey.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(ADD_COMMENT))))
            actionInfo.add(PropertyInformation("issueDescription", "Description", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, creating))
            actionInfo.add(PropertyInformation("comment", "Comment", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(ADD_COMMENT))))
            return actionInfo
        }

    private companion object {
        const val CREATE_ISSUE = "createIssue"
        const val ADD_COMMENT = "addComment"
    }
}
