package com.dopplertask.dopplertask.domain.action.integration.chat

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
@Table(name = "WhatsAppAction")
@DiscriminatorValue("whatsapp_action")
class WhatsAppAction : Action() {
    override val runsInPreview get() = true

    @Column
    var baseUrl: String? = "https://graph.facebook.com"

    @Transient
    var token: String? = null

    @Column
    var phoneNumberId: String? = null

    @Column
    var operation: String? = "sendMessage"

    @Column(name = "recipient")
    var to: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var message: String? = null

    @Column
    var templateName: String? = null

    @Column
    var templateLanguage: String? = "en_US"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val tokenValue = read(token)
        val phoneValue = read(phoneNumberId).trim()
        val operationValue = read(operation)
        val toValue = read(to).trim().trimStart('+').replace(" ", "")
        val messageValue = read(message)
        val templateValue = read(templateName).trim()
        if (tokenValue.isBlank()) return ActionResult.failure("WhatsApp needs an access token, from a system user in Meta Business settings.")
        if (phoneValue.isBlank()) return ActionResult.failure("WhatsApp needs the phone number ID, which is under WhatsApp, API Setup.")
        if (toValue.toLongOrNull() == null) return ActionResult.failure("WhatsApp needs the number to send to with its country code, and got \"$toValue\".")
        if (operationValue == SEND_MESSAGE && messageValue.isBlank()) return ActionResult.failure("WhatsApp needs the text of the message.")
        if (operationValue == SEND_TEMPLATE && templateValue.isBlank()) return ActionResult.failure("WhatsApp needs the name of a template.")

        val common = mapOf("messaging_product" to "whatsapp", "recipient_type" to "individual", "to" to toValue)
        val sent = when (operationValue) {
            SEND_MESSAGE -> common + mapOf("type" to "text", "text" to mapOf("body" to messageValue))
            SEND_TEMPLATE -> common + mapOf("type" to "template", "template" to mapOf("name" to templateValue, "language" to mapOf("policy" to "deterministic", "code" to read(templateLanguage).trim().ifEmpty { "en_US" })))
            else -> return ActionResult.failure("WhatsApp has no operation called \"$operationValue\".")
        }
        val url = JsonApi.baseAddress(read(baseUrl)) + "/$API_VERSION/${JsonApi.encoded(phoneValue)}/messages"
        val result = JsonApi.call("WhatsApp", "POST", url, mapOf("Authorization" to "Bearer $tokenValue"), sent)
        if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["messageId"] = JsonApi.parsed(result).path("messages").path(0).path("id").asText()
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("messageId" to "Message id")

    override val description: String
        get() = "Sends a WhatsApp message or template"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForSendMessage = mapOf("operation" to arrayOf(SEND_MESSAGE))
            val showForSendTemplate = mapOf("operation" to arrayOf(SEND_TEMPLATE))
            actionInfo.add(PropertyInformation("token", "Access token", PropertyInformationType.STRING, "", "From a system user in Meta Business settings.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, SEND_MESSAGE, "", listOf(
                PropertyInformation(SEND_MESSAGE, "Send a message"),
                PropertyInformation(SEND_TEMPLATE, "Send a template")
            )))
            actionInfo.add(PropertyInformation("phoneNumberId", "Phone number ID", PropertyInformationType.STRING, "", "Under WhatsApp, API Setup. It is not the phone number."))
            actionInfo.add(PropertyInformation("to", "To", PropertyInformationType.STRING, "", "The number with its country code, such as 46701234567."))
            actionInfo.add(PropertyInformation("message", "Message", PropertyInformationType.MULTILINE, "", "WhatsApp only delivers this within 24 hours of the last message from this person. Use a template otherwise.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSendMessage))
            actionInfo.add(PropertyInformation("templateName", "Template", PropertyInformationType.STRING, "", "An approved template, under WhatsApp Manager, Message templates.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSendTemplate))
            actionInfo.add(PropertyInformation("templateLanguage", "Language of the template", PropertyInformationType.STRING, "en_US", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSendTemplate))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://graph.facebook.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val SEND_MESSAGE = "sendMessage"
        const val SEND_TEMPLATE = "sendTemplate"
        const val API_VERSION = "v26.0"
    }
}
