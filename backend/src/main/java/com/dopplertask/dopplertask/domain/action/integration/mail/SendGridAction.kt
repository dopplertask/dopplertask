package com.dopplertask.dopplertask.domain.action.integration.mail

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
@Table(name = "SendGridAction")
@DiscriminatorValue("sendgrid_action")
class SendGridAction : Action() {
    override val runsInPreview get() = true

    @Column
    var baseUrl: String? = "https://api.sendgrid.com"

    @Transient
    var apiKey: String? = null

    @Column
    var fromAddress: String? = null

    @Column
    var toAddresses: String? = null

    @Column
    var subject: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var emailBody: String? = null

    @Column
    var isHtml: String? = "false"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val keyValue = read(apiKey).trim()
        val fromValue = read(fromAddress).trim()
        val recipients = read(toAddresses).split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val subjectValue = read(subject).trim()
        val bodyValue = read(emailBody)
        if (keyValue.isBlank()) return ActionResult.failure("SendGrid needs an API key.")
        if (fromValue.isBlank() || recipients.isEmpty()) return ActionResult.failure("SendGrid needs who it is from and who to send to.")
        if (subjectValue.isBlank() || bodyValue.isBlank()) return ActionResult.failure("SendGrid needs a subject and a message.")
        val mail = mapOf("personalizations" to listOf(mapOf("to" to recipients.map { mapOf("email" to it) })), "from" to mapOf("email" to fromValue), "subject" to subjectValue, "content" to listOf(mapOf("type" to (if (read(isHtml) == "true") "text/html" else "text/plain"), "value" to bodyValue)))
        val result = JsonApi.call("SendGrid", "POST", JsonApi.baseAddress(read(baseUrl)) + "/v3/mail/send", mapOf("Authorization" to "Bearer $keyValue"), mail)
        if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["messageId"] = (result.outputVariables["x-message-id"] as? List<*>)?.firstOrNull()?.toString() ?: ""
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("messageId" to "Message id")

    override val description: String
        get() = "Sends an email through SendGrid"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("apiKey", "API key", PropertyInformationType.STRING, "", "Under Settings, API Keys.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("fromAddress", "From", PropertyInformationType.STRING, "", "An address or domain that is verified in SendGrid."))
            actionInfo.add(PropertyInformation("toAddresses", "To", PropertyInformationType.STRING, "", "Separated by commas."))
            actionInfo.add(PropertyInformation("subject", "Subject", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("emailBody", "Message", PropertyInformationType.MULTILINE, "", ""))
            actionInfo.add(PropertyInformation("isHtml", "The message is HTML", PropertyInformationType.BOOLEAN, "false", ""))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.sendgrid.com", "Only change this to go through a proxy."))
            return actionInfo
        }
}
