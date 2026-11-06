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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.io.IOException
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table
import javax.persistence.Transient

@Entity
@Table(name = "GitLabAction")
@DiscriminatorValue("gitlab_action")
class GitLabAction : Action() {
    @Column
    var baseUrl: String? = "https://gitlab.com"

    @Transient
    var token: String? = null

    @Column
    var project: String? = null

    @Column
    var operation: String? = "createIssue"

    @Column
    var issueNumber: String? = null

    @Column(length = 1024)
    var title: String? = null

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
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val tokenValue = read(token)
        val projectValue = read(project).trim().trim('/')
        val operationValue = read(operation)
        val numberValue = read(issueNumber).trim()
        val titleValue = read(title)
        val commentValue = read(comment)
        if (tokenValue.isBlank()) return ActionResult.failure("GitLab needs a token, with the api scope.")
        if (projectValue.isBlank()) return ActionResult.failure("GitLab needs a project, written as group/name.")
        if (operationValue == CREATE_ISSUE && titleValue.isBlank()) return ActionResult.failure("GitLab needs a title for the issue.")
        if (operationValue != CREATE_ISSUE && numberValue.toLongOrNull() == null) return ActionResult.failure("GitLab needs the number of the issue, and got \"$numberValue\".")
        if (operationValue == ADD_COMMENT && commentValue.isBlank()) return ActionResult.failure("GitLab needs the text of the comment.")

        val base = read(baseUrl).trim().trimEnd('/')
        val issues = "$base/api/v4/projects/" + URLEncoder.encode(projectValue, StandardCharsets.UTF_8) + "/issues"
        val headers = mapOf("PRIVATE-TOKEN" to tokenValue)
        val result = when (operationValue) {
            CREATE_ISSUE -> JsonApi.call("GitLab", "POST", issues, headers, mapOf("title" to titleValue, "description" to read(issueDescription)))
            ADD_COMMENT -> JsonApi.call("GitLab", "POST", "$issues/$numberValue/notes", headers, mapOf("body" to commentValue))
            CLOSE_ISSUE -> JsonApi.call("GitLab", "PUT", "$issues/$numberValue", headers, mapOf("state_event" to "close"))
            else -> ActionResult.failure("GitLab has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val number = if (operationValue == CREATE_ISSUE) JsonApi.parsed(result).path("iid").asText() else numberValue
            result.outputVariables["issueNumber"] = number
            result.outputVariables["url"] = "$base/$projectValue/-/issues/$number"
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("issueNumber" to "Issue number", "url" to "Address")

    override val description: String
        get() = "Creates, comments on or closes a GitLab issue"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("token", "Token", PropertyInformationType.STRING, "", "A personal or project access token, with the api scope.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("project", "Project", PropertyInformationType.STRING, "", "Written as group/name."))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_ISSUE, "", listOf(
                PropertyInformation(CREATE_ISSUE, "Create an issue"),
                PropertyInformation(ADD_COMMENT, "Comment on an issue"),
                PropertyInformation(CLOSE_ISSUE, "Close an issue")
            )))
            actionInfo.add(PropertyInformation("issueNumber", "Issue number", PropertyInformationType.STRING, "", "A step that creates an issue gives it as \$lastLog.outputVariables.issueNumber.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(ADD_COMMENT, CLOSE_ISSUE))))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(CREATE_ISSUE))))
            actionInfo.add(PropertyInformation("issueDescription", "Description", PropertyInformationType.MULTILINE, "", "Markdown can be used.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(CREATE_ISSUE))))
            actionInfo.add(PropertyInformation("comment", "Comment", PropertyInformationType.MULTILINE, "", "Markdown can be used.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(ADD_COMMENT))))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://gitlab.com", "The address of your own GitLab, if you run one."))
            return actionInfo
        }

    private companion object {
        const val CREATE_ISSUE = "createIssue"
        const val ADD_COMMENT = "addComment"
        const val CLOSE_ISSUE = "closeIssue"
    }
}
