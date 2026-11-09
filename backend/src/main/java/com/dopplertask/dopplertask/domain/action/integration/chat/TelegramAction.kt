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
import java.io.IOException
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient

@Entity
@Table(name = "TelegramAction")
@DiscriminatorValue("telegram_action")
class TelegramAction : Action() {
    @Column
    var baseUrl: String? = "https://api.telegram.org"

    @Transient
    var token: String? = null

    @Column
    var chat: String? = null

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
        val tokenValue = variableExtractorUtil.extract(token, execution, scriptLanguage)
        val chatValue = variableExtractorUtil.extract(chat, execution, scriptLanguage)
        val messageValue = variableExtractorUtil.extract(message, execution, scriptLanguage)
        if (tokenValue.isBlank()) return ActionResult.failure("Telegram needs a bot token, which BotFather gives.")
        if (chatValue.isBlank()) return ActionResult.failure("Telegram needs a chat id, or a channel such as @alerts.")
        if (messageValue.isBlank()) return ActionResult.failure("Telegram needs a message to send.")
        // A message is limited to 4096 characters, and saying so is better than Telegram's own answer.
        if (messageValue.length > MAX_MESSAGE_LENGTH) return ActionResult.failure("Telegram allows $MAX_MESSAGE_LENGTH characters in a message, and this one has ${messageValue.length}.")

        val base = JsonApi.baseAddress(variableExtractorUtil.extract(baseUrl, execution, scriptLanguage))
        val result = JsonApi.call("Telegram", "POST", "$base/bot$tokenValue/sendMessage", mapOf(), mapOf("chat_id" to chatValue, "text" to messageValue))
        if (result.statusCode == StatusCode.SUCCESS) {
            result.outputVariables["messageId"] = JsonApi.parsed(result).path("result").path("message_id").asLong()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("messageId" to "Message id")

    override val description: String
        get() = "Sends a message to a Telegram chat"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("token", "Bot token", PropertyInformationType.STRING, "", "The token that BotFather gives for your bot.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("chat", "Chat", PropertyInformationType.STRING, "", "A chat id, or a channel such as @alerts. The bot has to be able to write there."))
            actionInfo.add(PropertyInformation("message", "Message", PropertyInformationType.MULTILINE, "", "The text to send, up to 4096 characters."))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.telegram.org", "Only change this to go through a proxy or your own Bot API server."))
            return actionInfo
        }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 4096
    }
}
