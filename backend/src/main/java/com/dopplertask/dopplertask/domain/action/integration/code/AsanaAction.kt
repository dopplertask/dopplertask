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
@Table(name = "AsanaAction")
@DiscriminatorValue("asana_action")
class AsanaAction : Action() {
    @Column
    var baseUrl: String? = "https://app.asana.com"

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "createTask"

    @Column
    var projectId: String? = null

    @Column
    var taskId: String? = null

    @Column(length = 1024)
    var title: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var taskDescription: String? = null

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
        val projectValue = read(projectId).trim()
        val taskValue = read(taskId).trim()
        val titleValue = read(title).trim()
        val commentValue = read(comment)
        if (tokenValue.isBlank()) return ActionResult.failure("Asana needs a personal access token, which is made in the developer console.")
        if (operationValue == CREATE_TASK && (projectValue.isBlank() || titleValue.isBlank())) return ActionResult.failure("Asana needs the project and a title for the task.")
        if (operationValue != CREATE_TASK && taskValue.isBlank()) return ActionResult.failure("Asana needs the number of the task.")
        if (operationValue == ADD_COMMENT && commentValue.isBlank()) return ActionResult.failure("Asana needs the text of the comment.")

        val api = read(baseUrl).trim().trimEnd('/') + "/api/1.0"
        val headers = mapOf("Authorization" to "Bearer $tokenValue")
        val task = "$api/tasks/${JsonApi.encoded(taskValue)}"
        val result = when (operationValue) {
            CREATE_TASK -> JsonApi.call("Asana", "POST", "$api/tasks?opt_fields=$FIELDS_WANTED", headers, mapOf("data" to mapOf("name" to titleValue, "notes" to read(taskDescription), "projects" to listOf(projectValue))))
            ADD_COMMENT -> JsonApi.call("Asana", "POST", "$task/stories", headers, mapOf("data" to mapOf("text" to commentValue)))
            COMPLETE_TASK -> JsonApi.call("Asana", "PUT", "$task?opt_fields=$FIELDS_WANTED", headers, mapOf("data" to mapOf("completed" to true)))
            else -> return ActionResult.failure("Asana has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result).path("data")
            result.outputVariables["taskId"] = if (operationValue == CREATE_TASK) answer.path("gid").asText() else taskValue
            if (operationValue != ADD_COMMENT) result.outputVariables["url"] = answer.path("permalink_url").asText()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("taskId" to "Task", "url" to "Address")

    override val description: String
        get() = "Creates, comments on or completes an Asana task"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForCreateTask = mapOf("operation" to arrayOf(CREATE_TASK))
            val showForAddCommentCompleteTask = mapOf("operation" to arrayOf(ADD_COMMENT, COMPLETE_TASK))
            val showForAddComment = mapOf("operation" to arrayOf(ADD_COMMENT))
            actionInfo.add(PropertyInformation("token", "Personal access token", PropertyInformationType.STRING, "", "Made in the Asana developer console.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_TASK, "", listOf(
                PropertyInformation(CREATE_TASK, "Create a task"),
                PropertyInformation(ADD_COMMENT, "Comment on a task"),
                PropertyInformation(COMPLETE_TASK, "Complete a task")
            )))
            actionInfo.add(PropertyInformation("projectId", "Project", PropertyInformationType.STRING, "", "The number in the address of the project.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateTask))
            actionInfo.add(PropertyInformation("taskId", "Task", PropertyInformationType.STRING, "", "A step that creates a task gives it as \$lastLog.outputVariables.taskId.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddCommentCompleteTask))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateTask))
            actionInfo.add(PropertyInformation("taskDescription", "Description", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateTask))
            actionInfo.add(PropertyInformation("comment", "Comment", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddComment))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://app.asana.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val CREATE_TASK = "createTask"
        const val ADD_COMMENT = "addComment"
        const val COMPLETE_TASK = "completeTask"
        const val FIELDS_WANTED = "gid,permalink_url"
    }
}
