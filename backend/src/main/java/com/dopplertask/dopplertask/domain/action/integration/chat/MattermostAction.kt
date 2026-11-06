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
@Table(name = "MattermostAction")
@DiscriminatorValue("mattermost_action")
class MattermostAction : Action() {
    @Column
    var siteUrl: String? = null

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var token: String? = null

    @Column
    var channelId: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var message: String? = null

    @Column
    var replyToMessageId: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = read(siteUrl).trim().trimEnd('/')
        val tokenValue = read(token)
        val channelValue = read(channelId).trim()
        val messageValue = read(message)
        if (site.isBlank()) return ActionResult.failure("Mattermost needs the address of the server, such as https://chat.example.com.")
        if (tokenValue.isBlank()) return ActionResult.failure("Mattermost needs a bot token.")
        if (channelValue.isBlank()) return ActionResult.failure("Mattermost needs the id of a channel, which View Info on the channel shows.")
        if (messageValue.isBlank()) return ActionResult.failure("Mattermost needs a message to send.")

        val post = mutableMapOf("channel_id" to channelValue, "message" to messageValue)
        read(replyToMessageId).trim().takeIf { it.isNotEmpty() }?.let { post["root_id"] = it }
        val result = JsonApi.call("Mattermost", "POST", "$site/api/v4/posts", mapOf("Authorization" to "Bearer $tokenValue"), post)
        if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["messageId"] = JsonApi.parsed(result).path("id").asText()
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("messageId" to "Message id")

    override val description: String
        get() = "Sends a message to a Mattermost channel"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("siteUrl", "Server", PropertyInformationType.STRING, "", "Such as https://chat.example.com."))
            actionInfo.add(PropertyInformation("token", "Bot token", PropertyInformationType.STRING, "", "The token of a bot account that is a member of the channel.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("channelId", "Channel id", PropertyInformationType.STRING, "", "View Info on the channel shows it."))
            actionInfo.add(PropertyInformation("message", "Message", PropertyInformationType.MULTILINE, "", "The text to send. Markdown can be used."))
            actionInfo.add(PropertyInformation("replyToMessageId", "Reply to message", PropertyInformationType.STRING, "", "Answers in the thread of this message. A Mattermost step gives the id as \$lastLog.outputVariables.messageId."))
            return actionInfo
        }
}
