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
import java.util.Base64

@Entity
@Table(name = "BitbucketAction")
@DiscriminatorValue("bitbucket_action")
class BitbucketAction : Action() {
    @Column
    var baseUrl: String? = "https://api.bitbucket.org"

    @Transient
    var email: String? = null

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "createPullRequest"

    @Column
    var workspace: String? = null

    @Column
    var repository: String? = null

    @Column
    var pullRequestId: String? = null

    @Column
    var title: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var pullRequestDescription: String? = null

    @Column
    var sourceBranch: String? = null

    @Column
    var targetBranch: String? = "main"

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
        val emailValue = read(email).trim()
        val tokenValue = read(token).trim()
        val workspaceValue = read(workspace).trim()
        val repositoryValue = read(repository).trim()
        val operationValue = read(operation)
        val numberValue = read(pullRequestId).trim().trimStart('#')
        val titleValue = read(title).trim()
        val sourceValue = read(sourceBranch).trim()
        val commentValue = read(comment)
        if (emailValue.isBlank() || tokenValue.isBlank()) return ActionResult.failure("Bitbucket needs your Atlassian email and an API token.")
        if (workspaceValue.isBlank() || repositoryValue.isBlank()) return ActionResult.failure("Bitbucket needs the workspace and the repository.")
        if (operationValue == CREATE_PULL_REQUEST && (titleValue.isBlank() || sourceValue.isBlank())) return ActionResult.failure("Bitbucket needs a title and the branch that the pull request comes from.")
        if (operationValue != CREATE_PULL_REQUEST && numberValue.toLongOrNull() == null) return ActionResult.failure("Bitbucket needs the number of the pull request, and got \"$numberValue\".")
        if (operationValue == ADD_COMMENT && commentValue.isBlank()) return ActionResult.failure("Bitbucket needs the text of the comment.")

        val headers = mapOf("Authorization" to "Basic " + Base64.getEncoder().encodeToString("$emailValue:$tokenValue".toByteArray()))
        val requests = read(baseUrl).trim().trimEnd('/') + "/2.0/repositories/${JsonApi.encoded(workspaceValue)}/${JsonApi.encoded(repositoryValue)}/pullrequests"
        val request = "$requests/$numberValue"
        val branch = { name: String -> mapOf("branch" to mapOf("name" to name)) }
        val result = when (operationValue) {
            CREATE_PULL_REQUEST -> JsonApi.call("Bitbucket", "POST", requests, headers, mapOf("type" to "pullrequest", "title" to titleValue, "description" to read(pullRequestDescription), "source" to branch(sourceValue), "destination" to branch(read(targetBranch).trim().ifEmpty { "main" })))
            ADD_COMMENT -> JsonApi.call("Bitbucket", "POST", "$request/comments", headers, mapOf("type" to "pullrequest_comment", "content" to mapOf("raw" to commentValue)))
            APPROVE_PULL_REQUEST -> JsonApi.call("Bitbucket", "POST", "$request/approve", headers, null)
            MERGE_PULL_REQUEST -> JsonApi.call("Bitbucket", "POST", "$request/merge", headers, mapOf("type" to "pullrequest_merge_parameters"))
            else -> return ActionResult.failure("Bitbucket has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS && (operationValue == CREATE_PULL_REQUEST || operationValue == MERGE_PULL_REQUEST)) {
            val answer = JsonApi.parsed(result)
            result.outputVariables["pullRequestId"] = answer.path("id").asText()
            result.outputVariables["pullRequestState"] = answer.path("state").asText()
            result.outputVariables["url"] = answer.path("links").path("html").path("href").asText()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("pullRequestId" to "Pull request", "pullRequestState" to "State", "url" to "Address")

    override val description: String
        get() = "Opens, comments on, approves or merges a Bitbucket pull request"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForAddCommentApprovePullRequestMergePullRequest = mapOf("operation" to arrayOf(ADD_COMMENT, APPROVE_PULL_REQUEST, MERGE_PULL_REQUEST))
            val showForCreatePullRequest = mapOf("operation" to arrayOf(CREATE_PULL_REQUEST))
            val showForAddComment = mapOf("operation" to arrayOf(ADD_COMMENT))
            actionInfo.add(PropertyInformation("email", "Atlassian email", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("token", "API token", PropertyInformationType.STRING, "", "At id.atlassian.com, Security, API tokens.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_PULL_REQUEST, "", listOf(
                PropertyInformation(CREATE_PULL_REQUEST, "Open a pull request"),
                PropertyInformation(ADD_COMMENT, "Add a comment"),
                PropertyInformation(APPROVE_PULL_REQUEST, "Approve a pull request"),
                PropertyInformation(MERGE_PULL_REQUEST, "Merge a pull request")
            )))
            actionInfo.add(PropertyInformation("workspace", "Workspace", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("repository", "Repository", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("pullRequestId", "Pull request number", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddCommentApprovePullRequestMergePullRequest))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePullRequest))
            actionInfo.add(PropertyInformation("pullRequestDescription", "Description", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePullRequest))
            actionInfo.add(PropertyInformation("sourceBranch", "From branch", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePullRequest))
            actionInfo.add(PropertyInformation("targetBranch", "Into branch", PropertyInformationType.STRING, "main", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePullRequest))
            actionInfo.add(PropertyInformation("comment", "Comment", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddComment))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.bitbucket.org", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val CREATE_PULL_REQUEST = "createPullRequest"
        const val ADD_COMMENT = "addComment"
        const val APPROVE_PULL_REQUEST = "approvePullRequest"
        const val MERGE_PULL_REQUEST = "mergePullRequest"
    }
}
