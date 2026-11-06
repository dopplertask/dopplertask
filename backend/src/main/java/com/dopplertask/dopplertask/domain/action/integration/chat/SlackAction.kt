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
@Table(name = "SlackAction")
@DiscriminatorValue("slack_action")
class SlackAction : Action() {
    @Column
    var baseUrl: String? = "https://slack.com/api"

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var token: String? = null

    @Column
    var channel: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var message: String? = null

    @Column
    var threadTs: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val tokenValue = variableExtractorUtil.extract(token, execution, scriptLanguage)
        val channelValue = variableExtractorUtil.extract(channel, execution, scriptLanguage)
        val messageValue = variableExtractorUtil.extract(message, execution, scriptLanguage)
        if (tokenValue.isBlank()) return ActionResult.failure("Slack needs a bot token, which starts with xoxb-.")
        if (channelValue.isBlank()) return ActionResult.failure("Slack needs a channel, such as #deploys or a channel id.")
        if (messageValue.isBlank()) return ActionResult.failure("Slack needs a message to send.")

        val body = mutableMapOf("channel" to channelValue, "text" to messageValue)
        val thread = variableExtractorUtil.extract(threadTs, execution, scriptLanguage)
        if (thread.isNotBlank()) body["thread_ts"] = thread

        val result = JsonApi.call("Slack", "POST", variableExtractorUtil.extract(baseUrl, execution, scriptLanguage).trimEnd('/') + "/chat.postMessage",
            mapOf("Authorization" to "Bearer $tokenValue"), body)

        // Slack answers 200 also when it refuses, and says so in the body.
        val answer = JsonApi.parsed(result)
        if (result.statusCode == StatusCode.SUCCESS && !answer.path("ok").asBoolean(false)) {
            return ActionResult.failure("Slack refused the message: ${answer.path("error").asText("no reason given")}.")
        }
        if (answer.hasNonNull("ts")) result.outputVariables["ts"] = answer.path("ts").asText()
        return result
    }

    override val description: String
        get() = "Sends a message to a Slack channel"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("token", "Bot token", PropertyInformationType.STRING, "", "A bot token that starts with xoxb-, with the chat:write permission.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("channel", "Channel", PropertyInformationType.STRING, "", "Such as #deploys or a channel id. The bot has to be a member of it."))
            actionInfo.add(PropertyInformation("message", "Message", PropertyInformationType.MULTILINE, "", "The text to send."))
            actionInfo.add(PropertyInformation("threadTs", "Reply to thread", PropertyInformationType.STRING, "", "Optional. The ts of the message to reply to, which a Slack step gives as \$lastLog.outputVariables.ts."))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://slack.com/api", "Only change this to go through a proxy."))
            return actionInfo
        }
}
