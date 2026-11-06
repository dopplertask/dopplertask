package com.dopplertask.dopplertask.domain.action.integration.code

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
import javax.persistence.Lob
import javax.persistence.Table

@Entity
@Table(name = "GitHubAction")
@DiscriminatorValue("github_action")
class GitHubAction : Action() {
    @Column
    var baseUrl: String? = "https://api.github.com"

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var token: String? = null

    @Column
    var repository: String? = null

    @Column
    var operation: String? = CREATE_ISSUE

    @Column
    var issueNumber: String? = null

    @Column(length = 1024)
    var title: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var text: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val tokenValue = variableExtractorUtil.extract(token, execution, scriptLanguage)
        val repositoryValue = variableExtractorUtil.extract(repository, execution, scriptLanguage).trim()
        val operationValue = variableExtractorUtil.extract(operation, execution, scriptLanguage)
        val numberValue = variableExtractorUtil.extract(issueNumber, execution, scriptLanguage).trim()
        val titleValue = variableExtractorUtil.extract(title, execution, scriptLanguage)
        val textValue = variableExtractorUtil.extract(text, execution, scriptLanguage)
        if (tokenValue.isBlank()) return ActionResult.failure("GitHub needs a token, with permission to write issues in the repository.")
        if (repositoryValue.count { it == '/' } != 1) return ActionResult.failure("GitHub needs a repository written as owner/name, and got \"$repositoryValue\".")
        if (operationValue == CREATE_ISSUE && titleValue.isBlank()) return ActionResult.failure("GitHub needs a title for the issue.")
        if (operationValue != CREATE_ISSUE && numberValue.toLongOrNull() == null) return ActionResult.failure("GitHub needs the number of the issue, and got \"$numberValue\".")
        if (operationValue == ADD_COMMENT && textValue.isBlank()) return ActionResult.failure("GitHub needs the text of the comment.")

        val issues = variableExtractorUtil.extract(baseUrl, execution, scriptLanguage).trimEnd('/') + "/repos/$repositoryValue/issues"
        val headers = mapOf("Authorization" to "Bearer $tokenValue")
        val result = when (operationValue) {
            CREATE_ISSUE -> JsonApi.call("GitHub", "POST", issues, headers, mapOf("title" to titleValue, "body" to textValue))
            ADD_COMMENT -> JsonApi.call("GitHub", "POST", "$issues/$numberValue/comments", headers, mapOf("body" to textValue))
            CLOSE_ISSUE -> JsonApi.call("GitHub", "PATCH", "$issues/$numberValue", headers, mapOf("state" to "closed"))
            else -> ActionResult.failure("GitHub has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            result.outputVariables["url"] = answer.path("html_url").asText()
            if (operationValue != ADD_COMMENT) result.outputVariables["issueNumber"] = answer.path("number").asLong()
        }
        return result
    }

    override val description: String
        get() = "Creates, comments on or closes a GitHub issue"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("token", "Token", PropertyInformationType.STRING, "", "A personal access token, with permission to write issues in the repository.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("repository", "Repository", PropertyInformationType.STRING, "", "Written as owner/name."))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_ISSUE, "", listOf(
                PropertyInformation(CREATE_ISSUE, "Create an issue"),
                PropertyInformation(ADD_COMMENT, "Comment on an issue"),
                PropertyInformation(CLOSE_ISSUE, "Close an issue")
            )))
            actionInfo.add(PropertyInformation("issueNumber", "Issue number", PropertyInformationType.STRING, "", "A step that creates an issue gives it as \$lastLog.outputVariables.issueNumber.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(ADD_COMMENT, CLOSE_ISSUE))))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(CREATE_ISSUE))))
            actionInfo.add(PropertyInformation("text", "Text", PropertyInformationType.MULTILINE, "", "The description of the issue, or the comment. Markdown can be used.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(CREATE_ISSUE, ADD_COMMENT))))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.github.com", "Only change this for GitHub Enterprise, which uses https://your-host/api/v3."))
            return actionInfo
        }

    private companion object {
        const val CREATE_ISSUE = "createIssue"
        const val ADD_COMMENT = "addComment"
        const val CLOSE_ISSUE = "closeIssue"
    }
}
