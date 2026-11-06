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
@Table(name = "LinearAction")
@DiscriminatorValue("linear_action")
class LinearAction : Action() {
    @Column
    var baseUrl: String? = "https://api.linear.app"

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var token: String? = null

    @Column
    var operation: String? = "createIssue"

    @Column
    var team: String? = null

    @Column
    var issueKey: String? = null

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
        val operationValue = read(operation)
        val teamValue = read(team).trim()
        val keyValue = read(issueKey).trim()
        val titleValue = read(title)
        val commentValue = read(comment)
        if (tokenValue.isBlank()) return ActionResult.failure("Linear needs an API key, which is made under Settings, Security and access.")
        if (operationValue == CREATE_ISSUE && teamValue.isBlank()) return ActionResult.failure("Linear needs the key of a team, such as ENG.")
        if (operationValue == CREATE_ISSUE && titleValue.isBlank()) return ActionResult.failure("Linear needs a title for the issue.")
        if (operationValue == ADD_COMMENT && keyValue.isBlank()) return ActionResult.failure("Linear needs the key of the issue, such as ENG-12.")
        if (operationValue == ADD_COMMENT && commentValue.isBlank()) return ActionResult.failure("Linear needs the text of the comment.")

        val url = read(baseUrl).trim().trimEnd('/') + "/graphql"
        // Linear wants its own ids, while a person knows the short keys that are shown on its screens.
        fun ask(query: String, variables: Map<String, Any>): ActionResult = graphql(url, tokenValue, query, variables)
        return when (operationValue) {
            CREATE_ISSUE -> {
                val teams = ask("query(\$key: String!) { teams(filter: { key: { eq: \$key } }) { nodes { id } } }", mapOf("key" to teamValue))
                if (teams.statusCode == StatusCode.FAILURE) return teams
                val teamId = JsonApi.parsed(teams).path("data").path("teams").path("nodes").path(0).path("id").asText("")
                if (teamId.isEmpty()) return ActionResult.failure("Linear has no team with the key \"$teamValue\".")
                val created = ask("mutation(\$input: IssueCreateInput!) { issueCreate(input: \$input) { issue { identifier url } } }", mapOf("input" to mapOf("teamId" to teamId, "title" to titleValue, "description" to read(issueDescription))))
                if (created.statusCode == StatusCode.SUCCESS) {
                    val issue = JsonApi.parsed(created).path("data").path("issueCreate").path("issue")
                    created.outputVariables["issueKey"] = issue.path("identifier").asText()
                    created.outputVariables["url"] = issue.path("url").asText()
                }
                created
            }
            ADD_COMMENT -> {
                val found = ask("query(\$key: String!) { issue(id: \$key) { id url } }", mapOf("key" to keyValue))
                if (found.statusCode == StatusCode.FAILURE) return found
                val issue = JsonApi.parsed(found).path("data").path("issue")
                val commented = ask("mutation(\$input: CommentCreateInput!) { commentCreate(input: \$input) { success } }", mapOf("input" to mapOf("issueId" to issue.path("id").asText(), "body" to commentValue)))
                if (commented.statusCode == StatusCode.SUCCESS) {
                    commented.outputVariables["issueKey"] = keyValue
                    commented.outputVariables["url"] = issue.path("url").asText()
                }
                commented
            }
            else -> ActionResult.failure("Linear has no operation called \"$operationValue\".")
        }
    }

    // A GraphQL service can answer with status 200 and name the error in the answer.
    private fun graphql(url: String, tokenValue: String, query: String, variables: Map<String, Any>): ActionResult {
        val result = JsonApi.call("Linear", "POST", url, mapOf("Authorization" to tokenValue), mapOf("query" to query, "variables" to variables))
        val error = JsonApi.parsed(result).path("errors").path(0).path("message").asText("")
        return if (result.statusCode == StatusCode.SUCCESS && error.isNotEmpty()) ActionResult.failure("Linear refused: ${error.trimEnd('.')}.") else result
    }

    override val outputs: Map<String, String>
        get() = mapOf("issueKey" to "Issue key", "url" to "Address")

    override val description: String
        get() = "Creates or comments on a Linear issue"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val creating = mapOf("operation" to arrayOf(CREATE_ISSUE))
            val commenting = mapOf("operation" to arrayOf(ADD_COMMENT))
            actionInfo.add(PropertyInformation("token", "API key", PropertyInformationType.STRING, "", "Made under Settings, Security and access.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_ISSUE, "", listOf(
                PropertyInformation(CREATE_ISSUE, "Create an issue"),
                PropertyInformation(ADD_COMMENT, "Comment on an issue")
            )))
            actionInfo.add(PropertyInformation("team", "Team", PropertyInformationType.STRING, "", "The key of the team, such as ENG.", mutableListOf(), PropertyInformationCategory.PROPERTY, creating))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, creating))
            actionInfo.add(PropertyInformation("issueDescription", "Description", PropertyInformationType.MULTILINE, "", "Markdown can be used.", mutableListOf(), PropertyInformationCategory.PROPERTY, creating))
            actionInfo.add(PropertyInformation("issueKey", "Issue key", PropertyInformationType.STRING, "", "Such as ENG-12. A step that creates an issue gives it as \$lastLog.outputVariables.issueKey.", mutableListOf(), PropertyInformationCategory.PROPERTY, commenting))
            actionInfo.add(PropertyInformation("comment", "Comment", PropertyInformationType.MULTILINE, "", "Markdown can be used.", mutableListOf(), PropertyInformationCategory.PROPERTY, commenting))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.linear.app", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val CREATE_ISSUE = "createIssue"
        const val ADD_COMMENT = "addComment"
    }
}
