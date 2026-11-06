package com.dopplertask.dopplertask.domain.action.integration.chat

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
@Table(name = "DiscordAction")
@DiscriminatorValue("discord_action")
class DiscordAction : Action() {
    @Column(length = 1024)
    @Convert(converter = ColumnEncryptor::class)
    var webhookUrl: String? = null

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
        val webhookValue = variableExtractorUtil.extract(webhookUrl, execution, scriptLanguage).trim()
        val messageValue = variableExtractorUtil.extract(message, execution, scriptLanguage)
        if (webhookValue.isBlank()) return ActionResult.failure("Discord needs a webhook URL, which is made in the settings of the channel under Integrations.")
        if (messageValue.isBlank()) return ActionResult.failure("Discord needs a message to send.")
        if (messageValue.length > MAX_MESSAGE_LENGTH) return ActionResult.failure("Discord allows $MAX_MESSAGE_LENGTH characters in a message, and this one has ${messageValue.length}.")

        // Without wait=true Discord answers with nothing, and the next step would not get the id of the message.
        val separator = if (webhookValue.contains("?")) "&" else "?"
        val result = JsonApi.withoutSecret(JsonApi.call("Discord", "POST", "$webhookValue${separator}wait=true", mapOf(), mapOf("content" to messageValue)), webhookValue)
        if (result.statusCode == StatusCode.SUCCESS) {
            result.outputVariables["messageId"] = JsonApi.parsed(result).path("id").asText()
        }
        return result
    }

    override val description: String
        get() = "Sends a message to a Discord channel"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("webhookUrl", "Webhook URL", PropertyInformationType.STRING, "", "Made in the settings of the channel under Integrations. Anyone who has it can write to the channel.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("message", "Message", PropertyInformationType.MULTILINE, "", "The text to send, up to 2000 characters."))
            return actionInfo
        }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 2000
    }
}
