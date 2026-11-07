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
@Table(name = "TrelloAction")
@DiscriminatorValue("trello_action")
class TrelloAction : Action() {
    @Column
    var baseUrl: String? = "https://api.trello.com"

    @Transient
    var apiKey: String? = null

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "createCard"

    @Column
    var listId: String? = null

    @Column
    var cardId: String? = null

    @Column(length = 1024)
    var title: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var cardDescription: String? = null

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
        val keyValue = read(apiKey).trim()
        val tokenValue = read(token).trim()
        val operationValue = read(operation)
        val listValue = read(listId).trim()
        val cardValue = read(cardId).trim()
        val titleValue = read(title).trim()
        val commentValue = read(comment)
        if (keyValue.isBlank() || tokenValue.isBlank()) return ActionResult.failure("Trello needs an API key and a token, from a Power-Up of your own.")
        if (operationValue != ADD_COMMENT && listValue.isBlank()) return ActionResult.failure("Trello needs the id of the list.")
        if (operationValue == CREATE_CARD && titleValue.isBlank()) return ActionResult.failure("Trello needs a title for the card.")
        if (operationValue != CREATE_CARD && cardValue.isBlank()) return ActionResult.failure("Trello needs the card, which is the short code in its address.")
        if (operationValue == ADD_COMMENT && commentValue.isBlank()) return ActionResult.failure("Trello needs the text of the comment.")

        // In a header rather than the address, where keys would end up in logs along the way.
        val headers = mapOf("Authorization" to "OAuth oauth_consumer_key=\"$keyValue\", oauth_token=\"$tokenValue\"")
        val api = read(baseUrl).trim().trimEnd('/') + "/1/cards"
        val card = "$api/${JsonApi.encoded(cardValue)}"
        val result = when (operationValue) {
            CREATE_CARD -> JsonApi.call("Trello", "POST", "$api?idList=${JsonApi.encoded(listValue)}&name=${JsonApi.encoded(titleValue)}&desc=${JsonApi.encoded(read(cardDescription))}", headers, null)
            MOVE_CARD -> JsonApi.call("Trello", "PUT", "$card?idList=${JsonApi.encoded(listValue)}", headers, null)
            ADD_COMMENT -> JsonApi.call("Trello", "POST", "$card/actions/comments?text=${JsonApi.encoded(commentValue)}", headers, null)
            else -> return ActionResult.failure("Trello has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            result.outputVariables["cardId"] = if (operationValue == CREATE_CARD) answer.path("id").asText() else cardValue
            if (operationValue != ADD_COMMENT) result.outputVariables["url"] = answer.path("shortUrl").asText()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("cardId" to "Card", "url" to "Address")

    override val description: String
        get() = "Creates, moves or comments on a Trello card"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForCreateCardMoveCard = mapOf("operation" to arrayOf(CREATE_CARD, MOVE_CARD))
            val showForMoveCardAddComment = mapOf("operation" to arrayOf(MOVE_CARD, ADD_COMMENT))
            val showForCreateCard = mapOf("operation" to arrayOf(CREATE_CARD))
            val showForAddComment = mapOf("operation" to arrayOf(ADD_COMMENT))
            actionInfo.add(PropertyInformation("apiKey", "API key", PropertyInformationType.STRING, "", "Under Power-Ups and integrations, in your own Power-Up.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("token", "Token", PropertyInformationType.STRING, "", "Made from the same page, with the link next to the API key.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_CARD, "", listOf(
                PropertyInformation(CREATE_CARD, "Create a card"),
                PropertyInformation(MOVE_CARD, "Move a card to a list"),
                PropertyInformation(ADD_COMMENT, "Comment on a card")
            )))
            actionInfo.add(PropertyInformation("listId", "List ID", PropertyInformationType.STRING, "", "Add .json to the address of a board to see the id of each list.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateCardMoveCard))
            actionInfo.add(PropertyInformation("cardId", "Card", PropertyInformationType.STRING, "", "The short code in the address of the card.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForMoveCardAddComment))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateCard))
            actionInfo.add(PropertyInformation("cardDescription", "Description", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateCard))
            actionInfo.add(PropertyInformation("comment", "Comment", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddComment))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.trello.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val CREATE_CARD = "createCard"
        const val MOVE_CARD = "moveCard"
        const val ADD_COMMENT = "addComment"
    }
}
