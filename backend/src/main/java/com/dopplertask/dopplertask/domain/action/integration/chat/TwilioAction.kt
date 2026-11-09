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
import java.util.Base64
import java.io.IOException
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient

@Entity
@Table(name = "TwilioAction")
@DiscriminatorValue("twilio_action")
class TwilioAction : Action() {
    @Column
    var baseUrl: String? = "https://api.twilio.com"

    @Transient
    var accountSid: String? = null

    @Transient
    var token: String? = null

    @Column
    var sender: String? = null

    @Column(name = "toNumber")
    var to: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var message: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val accountValue = read(accountSid).trim()
        val tokenValue = read(token)
        val senderValue = read(sender).trim()
        val toValue = read(to).trim()
        val messageValue = read(message)
        if (accountValue.isBlank() || tokenValue.isBlank()) return ActionResult.failure("Twilio needs the account SID and the auth token, which the Twilio console shows.")
        if (senderValue.isBlank()) return ActionResult.failure("Twilio needs a number to send from.")
        if (!toValue.startsWith("+")) return ActionResult.failure("Twilio needs the number to send to with its country code, such as +46701234567, and got \"$toValue\".")
        if (messageValue.isBlank()) return ActionResult.failure("Twilio needs a message to send.")
        if (messageValue.length > MAX_MESSAGE_LENGTH) return ActionResult.failure("Twilio allows $MAX_MESSAGE_LENGTH characters in a message, and this one has ${messageValue.length}.")

        val base = JsonApi.baseAddress(read(baseUrl))
        val signIn = Base64.getEncoder().encodeToString("$accountValue:$tokenValue".toByteArray())
        val result = JsonApi.callForm("Twilio", "$base/2010-04-01/Accounts/$accountValue/Messages.json", mapOf("Authorization" to "Basic $signIn"), mapOf("From" to senderValue, "To" to toValue, "Body" to messageValue))
        if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["messageId"] = JsonApi.parsed(result).path("sid").asText()
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("messageId" to "Message id")

    override val description: String
        get() = "Sends an SMS through Twilio"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("accountSid", "Account SID", PropertyInformationType.STRING, "", "The Twilio console shows it.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("token", "Auth token", PropertyInformationType.STRING, "", "The Twilio console shows it.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("sender", "From", PropertyInformationType.STRING, "", "A number you have in Twilio, such as +15005550006."))
            actionInfo.add(PropertyInformation("to", "To", PropertyInformationType.STRING, "", "With the country code, such as +46701234567."))
            actionInfo.add(PropertyInformation("message", "Message", PropertyInformationType.MULTILINE, "", "The text to send, up to 1600 characters."))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.twilio.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 1600
    }
}
