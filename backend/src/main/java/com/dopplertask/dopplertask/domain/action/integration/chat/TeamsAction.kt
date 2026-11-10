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
@Table(name = "TeamsAction")
@DiscriminatorValue("teams_action")
class TeamsAction : Action() {
    override val runsInPreview get() = true

    @Transient
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
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val webhookValue = read(webhookUrl).trim()
        val messageValue = read(message)
        if (webhookValue.isBlank()) return ActionResult.failure("Teams needs a webhook URL, which the Workflows app gives for a channel.")
        if (messageValue.isBlank()) return ActionResult.failure("Teams needs a message to send.")

        val card = mapOf(
            "type" to "AdaptiveCard",
            "version" to "1.4",
            "\$schema" to "http://adaptivecards.io/schemas/adaptive-card.json",
            "body" to listOf(mapOf("type" to "TextBlock", "text" to messageValue, "wrap" to true))
        )
        val post = mapOf("type" to "message", "attachments" to listOf(mapOf("contentType" to "application/vnd.microsoft.card.adaptive", "content" to card)))
        val result = JsonApi.call("Teams", "POST", webhookValue, mapOf(), post)
        // Teams answers with nothing, and a person should still see what happened.
        if (result.statusCode == StatusCode.SUCCESS) result.output = "Sent to Teams."
        return result
    }

    override val description: String
        get() = "Sends a message to a Microsoft Teams channel"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("webhookUrl", "Webhook URL", PropertyInformationType.STRING, "", "Made with the Workflows app in Teams, for a channel. Anyone who has it can write to the channel.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("message", "Message", PropertyInformationType.MULTILINE, "", "The text to send. Markdown can be used."))
            return actionInfo
        }
}
