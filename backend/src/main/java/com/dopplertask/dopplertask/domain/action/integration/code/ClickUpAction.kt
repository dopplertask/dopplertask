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
@Table(name = "ClickUpAction")
@DiscriminatorValue("clickup_action")
class ClickUpAction : Action() {
    @Column
    var baseUrl: String? = "https://api.clickup.com"

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "createTask"

    @Column
    var listId: String? = null

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

    @Column
    var taskStatus: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val tokenValue = read(token).trim()
        val operationValue = read(operation)
        val listValue = read(listId).trim()
        val taskValue = read(taskId).trim()
        val titleValue = read(title).trim()
        val commentValue = read(comment)
        val statusValue = read(taskStatus).trim()
        if (tokenValue.isBlank()) return ActionResult.failure("ClickUp needs an API token, which is under Settings, Apps.")
        if (operationValue == CREATE_TASK && (listValue.isBlank() || titleValue.isBlank())) return ActionResult.failure("ClickUp needs the list and a title for the task.")
        if (operationValue != CREATE_TASK && taskValue.isBlank()) return ActionResult.failure("ClickUp needs the task, which is the code at the end of its address.")
        if (operationValue == ADD_COMMENT && commentValue.isBlank()) return ActionResult.failure("ClickUp needs the text of the comment.")
        if (operationValue == SET_STATUS && statusValue.isBlank()) return ActionResult.failure("ClickUp needs the new status.")

        val api = JsonApi.baseAddress(baseUrl) + "/api/v2"
        val headers = mapOf("Authorization" to tokenValue)
        val task = "$api/task/${JsonApi.encoded(taskValue)}"
        val result = when (operationValue) {
            CREATE_TASK -> JsonApi.call("ClickUp", "POST", "$api/list/${JsonApi.encoded(listValue)}/task", headers, mapOf("name" to titleValue, "description" to read(taskDescription)))
            ADD_COMMENT -> JsonApi.call("ClickUp", "POST", "$task/comment", headers, mapOf("comment_text" to commentValue, "notify_all" to false))
            SET_STATUS -> JsonApi.call("ClickUp", "PUT", task, headers, mapOf("status" to statusValue))
            else -> return ActionResult.failure("ClickUp has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            result.outputVariables["taskId"] = if (operationValue == CREATE_TASK) answer.path("id").asText() else taskValue
            if (operationValue != ADD_COMMENT) result.outputVariables["url"] = answer.path("url").asText()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("taskId" to "Task", "url" to "Address")

    override val description: String
        get() = "Creates, comments on or changes the status of a ClickUp task"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForCreateTask = mapOf("operation" to arrayOf(CREATE_TASK))
            val showForAddCommentSetStatus = mapOf("operation" to arrayOf(ADD_COMMENT, SET_STATUS))
            val showForAddComment = mapOf("operation" to arrayOf(ADD_COMMENT))
            val showForSetStatus = mapOf("operation" to arrayOf(SET_STATUS))
            actionInfo.add(PropertyInformation("token", "API token", PropertyInformationType.STRING, "", "Under Settings, Apps. It starts with pk_.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_TASK, "", listOf(
                PropertyInformation(CREATE_TASK, "Create a task"),
                PropertyInformation(ADD_COMMENT, "Comment on a task"),
                PropertyInformation(SET_STATUS, "Change the status of a task")
            )))
            actionInfo.add(PropertyInformation("listId", "List", PropertyInformationType.STRING, "", "The last number in the address of the list.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateTask))
            actionInfo.add(PropertyInformation("taskId", "Task", PropertyInformationType.STRING, "", "The code at the end of the address of the task.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddCommentSetStatus))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateTask))
            actionInfo.add(PropertyInformation("taskDescription", "Description", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateTask))
            actionInfo.add(PropertyInformation("comment", "Comment", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddComment))
            actionInfo.add(PropertyInformation("taskStatus", "Status", PropertyInformationType.STRING, "", "As it is written in the list, such as in progress.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSetStatus))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.clickup.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val CREATE_TASK = "createTask"
        const val ADD_COMMENT = "addComment"
        const val SET_STATUS = "setStatus"
    }
}
