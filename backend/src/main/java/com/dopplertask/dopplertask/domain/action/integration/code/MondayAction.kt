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
@Table(name = "MondayAction")
@DiscriminatorValue("monday_action")
class MondayAction : Action() {
    @Column
    var baseUrl: String? = "https://api.monday.com"

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "createItem"

    @Column
    var boardId: String? = null

    @Column
    var itemId: String? = null

    @Column(length = 1024)
    var itemName: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var columnValues: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var comment: String? = null

    @Column
    var columnId: String? = null

    @Column(length = 1024)
    var columnValue: String? = null

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
        val boardValue = read(boardId).trim()
        val itemValue = read(itemId).trim()
        val nameValue = read(itemName).trim()
        val columnsText = read(columnValues).trim()
        val commentValue = read(comment)
        val columnValueText = read(columnId).trim()
        if (tokenValue.isBlank()) return ActionResult.failure("monday.com needs an API token, which is under your profile, Developers.")
        if (operationValue != ADD_COMMENT && boardValue.isBlank()) return ActionResult.failure("monday.com needs the board, which is the number in its address.")
        if (operationValue != CREATE_ITEM && itemValue.isBlank()) return ActionResult.failure("monday.com needs the item.")
        if (operationValue == CREATE_ITEM && nameValue.isBlank()) return ActionResult.failure("monday.com needs a name for the item.")
        if (operationValue == CREATE_ITEM && columnsText.isNotEmpty() && JsonApi.objectFrom(columnsText) == null) return ActionResult.failure("monday.com needs the column values as a JSON object, such as {\"status\": \"Done\"}.")
        if (operationValue == ADD_COMMENT && commentValue.isBlank()) return ActionResult.failure("monday.com needs the text of the update.")
        if (operationValue == SET_COLUMN && columnValueText.isBlank()) return ActionResult.failure("monday.com needs the ID of the column.")

        val url = read(baseUrl).trim().trimEnd('/') + "/v2"
        fun ask(query: String, variables: Map<String, Any>, answerName: String): ActionResult {
            val result = JsonApi.call("monday.com", "POST", url, mapOf("Authorization" to tokenValue, "API-Version" to API_VERSION), mapOf("query" to query, "variables" to variables))
            // It answers with 200 also when it refuses.
            val error = JsonApi.parsed(result).path("errors").path(0).path("message").asText("")
            if (result.statusCode == StatusCode.SUCCESS && error.isNotEmpty()) return ActionResult.failure("monday.com refused: ${error.trimEnd('.')}.")
            if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["itemId"] = if (operationValue == CREATE_ITEM) JsonApi.parsed(result).path("data").path(answerName).path("id").asText() else itemValue
            return result
        }
        return when (operationValue) {
            CREATE_ITEM -> ask("mutation(\$board: ID!, \$name: String!, \$columns: JSON) { create_item(board_id: \$board, item_name: \$name, column_values: \$columns) { id } }", mapOf("board" to boardValue, "name" to nameValue, "columns" to columnsText.ifEmpty { "{}" }), "create_item")
            ADD_COMMENT -> ask("mutation(\$item: ID!, \$body: String!) { create_update(item_id: \$item, body: \$body) { id } }", mapOf("item" to itemValue, "body" to commentValue), "create_update")
            SET_COLUMN -> ask("mutation(\$board: ID!, \$item: ID!, \$column: String!, \$value: String!) { change_simple_column_value(board_id: \$board, item_id: \$item, column_id: \$column, value: \$value) { id } }", mapOf("board" to boardValue, "item" to itemValue, "column" to columnValueText, "value" to read(columnValue)), "change_simple_column_value")
            else -> ActionResult.failure("monday.com has no operation called \"$operationValue\".")
        }
    }

    override val outputs: Map<String, String>
        get() = mapOf("itemId" to "Item")

    override val description: String
        get() = "Creates or comments on a monday.com item, or sets one of its columns"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForCreateItemSetColumn = mapOf("operation" to arrayOf(CREATE_ITEM, SET_COLUMN))
            val showForAddCommentSetColumn = mapOf("operation" to arrayOf(ADD_COMMENT, SET_COLUMN))
            val showForCreateItem = mapOf("operation" to arrayOf(CREATE_ITEM))
            val showForAddComment = mapOf("operation" to arrayOf(ADD_COMMENT))
            val showForSetColumn = mapOf("operation" to arrayOf(SET_COLUMN))
            actionInfo.add(PropertyInformation("token", "API token", PropertyInformationType.STRING, "", "Under your profile, Developers, My access tokens.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_ITEM, "", listOf(
                PropertyInformation(CREATE_ITEM, "Create an item"),
                PropertyInformation(ADD_COMMENT, "Add an update to an item"),
                PropertyInformation(SET_COLUMN, "Set a column of an item")
            )))
            actionInfo.add(PropertyInformation("boardId", "Board", PropertyInformationType.STRING, "", "The number in the address of the board.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateItemSetColumn))
            actionInfo.add(PropertyInformation("itemId", "Item", PropertyInformationType.STRING, "", "A step that creates an item gives it as \$lastLog.outputVariables.itemId.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddCommentSetColumn))
            actionInfo.add(PropertyInformation("itemName", "Name", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateItem))
            actionInfo.add(PropertyInformation("columnValues", "Column values", PropertyInformationType.MULTILINE, "", "As JSON, such as {\"status\": \"Done\"}.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateItem))
            actionInfo.add(PropertyInformation("comment", "Update", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddComment))
            actionInfo.add(PropertyInformation("columnId", "Column ID", PropertyInformationType.STRING, "", "Shown in the settings of the column, such as status.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSetColumn))
            actionInfo.add(PropertyInformation("columnValue", "Value", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSetColumn))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.monday.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val CREATE_ITEM = "createItem"
        const val ADD_COMMENT = "addComment"
        const val SET_COLUMN = "setColumn"
        const val API_VERSION = "2026-10"
    }
}
