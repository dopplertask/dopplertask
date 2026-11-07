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
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException

@Entity
@Table(name = "GiteaAction")
@DiscriminatorValue("gitea_action")
class GiteaAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "createIssue"

    @Column
    var owner: String? = null

    @Column
    var repository: String? = null

    @Column
    var issueNumber: String? = null

    @Column
    var title: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var issueDescription: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var comment: String? = null

    @Column
    var sourceBranch: String? = null

    @Column
    var targetBranch: String? = "main"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = read(siteUrl).trim().trimEnd('/')
        val tokenValue = read(token).trim()
        val ownerValue = read(owner).trim()
        val repositoryValue = read(repository).trim()
        val operationValue = read(operation)
        val numberValue = read(issueNumber).trim().trimStart('#')
        val titleValue = read(title).trim()
        val commentValue = read(comment)
        val sourceValue = read(sourceBranch).trim()
        val targetValue = read(targetBranch).trim().ifEmpty { "main" }
        if (site.isBlank()) return ActionResult.failure("Gitea needs its address, such as https://git.example.com.")
        if (tokenValue.isBlank()) return ActionResult.failure("Gitea needs an access token.")
        if (ownerValue.isBlank() || repositoryValue.isBlank()) return ActionResult.failure("Gitea needs the owner and the name of the repository.")
        if ((operationValue == ADD_COMMENT || operationValue == CLOSE_ISSUE) && numberValue.toLongOrNull() == null) return ActionResult.failure("Gitea needs the number of the issue, and got \"$numberValue\".")
        if ((operationValue == CREATE_ISSUE || operationValue == CREATE_PULL_REQUEST) && titleValue.isBlank()) return ActionResult.failure("Gitea needs a title.")
        if (operationValue == ADD_COMMENT && commentValue.isBlank()) return ActionResult.failure("Gitea needs the text of the comment.")
        if (operationValue == CREATE_PULL_REQUEST && sourceValue.isBlank()) return ActionResult.failure("Gitea needs the branch that the pull request comes from.")

        val headers = mapOf("Authorization" to "Bearer $tokenValue")
        val repo = "$site/api/v1/repos/${JsonApi.encoded(ownerValue)}/${JsonApi.encoded(repositoryValue)}"
        val result = when (operationValue) {
            CREATE_ISSUE -> JsonApi.call("Gitea", "POST", "$repo/issues", headers, mapOf("title" to titleValue, "body" to read(issueDescription)))
            ADD_COMMENT -> JsonApi.call("Gitea", "POST", "$repo/issues/$numberValue/comments", headers, mapOf("body" to commentValue))
            CLOSE_ISSUE -> JsonApi.call("Gitea", "PATCH", "$repo/issues/$numberValue", headers, mapOf("state" to "closed"))
            CREATE_PULL_REQUEST -> JsonApi.call("Gitea", "POST", "$repo/pulls", headers, mapOf("title" to titleValue, "body" to read(issueDescription), "head" to sourceValue, "base" to targetValue))
            else -> return ActionResult.failure("Gitea has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            if (operationValue != ADD_COMMENT) result.outputVariables["number"] = answer.path("number").asText()
            result.outputVariables["url"] = answer.path("html_url").asText()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("number" to "Number", "url" to "Address")

    override val description: String
        get() = "Creates, comments on or closes Gitea issues, or opens a pull request"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForAddCommentCloseIssue = mapOf("operation" to arrayOf(ADD_COMMENT, CLOSE_ISSUE))
            val showForCreateIssueCreatePullRequest = mapOf("operation" to arrayOf(CREATE_ISSUE, CREATE_PULL_REQUEST))
            val showForAddComment = mapOf("operation" to arrayOf(ADD_COMMENT))
            val showForCreatePullRequest = mapOf("operation" to arrayOf(CREATE_PULL_REQUEST))
            actionInfo.add(PropertyInformation("token", "Access token", PropertyInformationType.STRING, "", "Under Settings, Applications.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_ISSUE, "", listOf(
                PropertyInformation(CREATE_ISSUE, "Create an issue"),
                PropertyInformation(ADD_COMMENT, "Add a comment"),
                PropertyInformation(CLOSE_ISSUE, "Close an issue"),
                PropertyInformation(CREATE_PULL_REQUEST, "Open a pull request")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "Gitea address", PropertyInformationType.STRING, "", "Such as https://git.example.com."))
            actionInfo.add(PropertyInformation("owner", "Owner", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("repository", "Repository", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("issueNumber", "Issue number", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddCommentCloseIssue))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateIssueCreatePullRequest))
            actionInfo.add(PropertyInformation("issueDescription", "Description", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateIssueCreatePullRequest))
            actionInfo.add(PropertyInformation("comment", "Comment", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddComment))
            actionInfo.add(PropertyInformation("sourceBranch", "From branch", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePullRequest))
            actionInfo.add(PropertyInformation("targetBranch", "Into branch", PropertyInformationType.STRING, "main", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePullRequest))
            return actionInfo
        }

    private companion object {
        const val CREATE_ISSUE = "createIssue"
        const val ADD_COMMENT = "addComment"
        const val CLOSE_ISSUE = "closeIssue"
        const val CREATE_PULL_REQUEST = "createPullRequest"
    }
}
