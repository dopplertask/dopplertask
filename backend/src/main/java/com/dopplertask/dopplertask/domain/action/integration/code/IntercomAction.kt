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
@Table(name = "IntercomAction")
@DiscriminatorValue("intercom_action")
class IntercomAction : Action() {
    @Column
    var baseUrl: String? = "https://api.intercom.io"

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "createContact"

    @Column
    var email: String? = null

    @Column
    var contactName: String? = null

    @Column
    var conversationId: String? = null

    @Column
    var teammateId: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var comment: String? = null

    @Column
    var visibleToCustomer: String? = "false"

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
        val emailValue = read(email).trim()
        val conversationValue = read(conversationId).trim()
        val teammateValue = read(teammateId).trim()
        val commentValue = read(comment)
        if (tokenValue.isBlank()) return ActionResult.failure("Intercom needs an access token, from an app in the Developer Hub.")
        if (operationValue == CREATE_CONTACT && emailValue.isBlank()) return ActionResult.failure("Intercom needs the email of the contact.")
        if (operationValue != CREATE_CONTACT && (conversationValue.isBlank() || teammateValue.isBlank())) return ActionResult.failure("Intercom needs the conversation ID and your teammate ID.")
        if (operationValue == REPLY_TO_CONVERSATION && commentValue.isBlank()) return ActionResult.failure("Intercom needs the text of the reply.")

        val base = read(baseUrl).trim().trimEnd('/')
        val headers = mapOf("Authorization" to "Bearer $tokenValue", "Intercom-Version" to API_VERSION)
        val conversation = "$base/conversations/${JsonApi.encoded(conversationValue)}"
        return when (operationValue) {
            CREATE_CONTACT -> {
                val contact = mapOf("role" to "user", "email" to emailValue, "name" to read(contactName).trim()).filterValues { it.isNotEmpty() }
                val result = JsonApi.call("Intercom", "POST", "$base/contacts", headers, contact)
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["contactId"] = JsonApi.parsed(result).path("id").asText()
                result
            }
            // Text from an automation should not reach a customer by accident, so a note is what happens unless asked for.
            REPLY_TO_CONVERSATION -> JsonApi.call("Intercom", "POST", "$conversation/reply", headers, mapOf("message_type" to (if (read(visibleToCustomer) == "true") "comment" else "note"), "type" to "admin", "admin_id" to teammateValue, "body" to commentValue))
            CLOSE_CONVERSATION -> JsonApi.call("Intercom", "POST", "$conversation/parts", headers, mapOf("message_type" to "close", "type" to "admin", "admin_id" to teammateValue))
            else -> ActionResult.failure("Intercom has no operation called \"$operationValue\".")
        }
    }

    override val outputs: Map<String, String>
        get() = mapOf("contactId" to "Contact id")

    override val description: String
        get() = "Creates an Intercom contact, or replies to or closes a conversation"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForCreateContact = mapOf("operation" to arrayOf(CREATE_CONTACT))
            val showForReplyToConversationCloseConversation = mapOf("operation" to arrayOf(REPLY_TO_CONVERSATION, CLOSE_CONVERSATION))
            val showForReplyToConversation = mapOf("operation" to arrayOf(REPLY_TO_CONVERSATION))
            actionInfo.add(PropertyInformation("token", "Access token", PropertyInformationType.STRING, "", "From an app in the Developer Hub.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_CONTACT, "", listOf(
                PropertyInformation(CREATE_CONTACT, "Create a contact"),
                PropertyInformation(REPLY_TO_CONVERSATION, "Reply to a conversation"),
                PropertyInformation(CLOSE_CONVERSATION, "Close a conversation")
            )))
            actionInfo.add(PropertyInformation("email", "Email", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateContact))
            actionInfo.add(PropertyInformation("contactName", "Name", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateContact))
            actionInfo.add(PropertyInformation("conversationId", "Conversation ID", PropertyInformationType.STRING, "", "The last number in the address of the conversation.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReplyToConversationCloseConversation))
            actionInfo.add(PropertyInformation("teammateId", "Teammate ID", PropertyInformationType.STRING, "", "The last number in the address of your teammate profile. The reply is sent in this name.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReplyToConversationCloseConversation))
            actionInfo.add(PropertyInformation("comment", "Reply", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReplyToConversation))
            actionInfo.add(PropertyInformation("visibleToCustomer", "Visible to the customer", PropertyInformationType.BOOLEAN, "false", "Otherwise the reply is an internal note.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReplyToConversation))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.intercom.io", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val CREATE_CONTACT = "createContact"
        const val REPLY_TO_CONVERSATION = "replyToConversation"
        const val CLOSE_CONVERSATION = "closeConversation"
        const val API_VERSION = "2.17"
    }
}
