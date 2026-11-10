package com.dopplertask.dopplertask.domain.action.integration.mail

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.domain.action.integration.storage.MICROSOFT_ACCOUNT
import com.dopplertask.dopplertask.domain.action.integration.storage.MicrosoftAccount
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
@Table(name = "OutlookAction")
@DiscriminatorValue("outlook_action")
class OutlookAction : Action() {
    override val runsInPreview get() = true

    @Transient
    var tenantId: String? = null

    @Transient
    var clientId: String? = null

    @Transient
    var clientSecret: String? = null

    @Column
    var baseUrl: String? = "https://graph.microsoft.com"

    @Column
    var signInUrl: String? = "https://login.microsoftonline.com"

    @Column
    var operation: String? = "sendEmail"

    @Column
    var mailbox: String? = null

    @Column
    var toAddresses: String? = null

    @Column
    var subject: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var emailBody: String? = null

    @Column
    var isHtml: String? = "false"

    @Column
    var messageId: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val mailboxValue = read(mailbox).trim()
        val operationValue = read(operation)
        val recipients = read(toAddresses).split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val subjectValue = read(subject).trim()
        val messageValue = read(messageId).trim()
        if (mailboxValue.isBlank()) return ActionResult.failure("Outlook needs the email address of the mailbox.")
        if (operationValue == SEND_EMAIL && (recipients.isEmpty() || subjectValue.isBlank())) return ActionResult.failure("Outlook needs who to send to and a subject.")
        if (operationValue == MARK_AS_READ && messageValue.isBlank()) return ActionResult.failure("Outlook needs the ID of the email.")

        val signIn = MicrosoftAccount.signIn("Outlook", JsonApi.baseAddress(read(signInUrl)), read(tenantId).trim(), read(clientId).trim(), read(clientSecret))
        signIn.failure?.let { return it }
        val user = JsonApi.baseAddress(read(baseUrl)) + "/v1.0/users/${JsonApi.encoded(mailboxValue)}"
        val result = when (operationValue) {
            SEND_EMAIL -> {
                val message = mapOf("subject" to subjectValue, "body" to mapOf("contentType" to (if (read(isHtml) == "true") "HTML" else "Text"), "content" to read(emailBody)), "toRecipients" to recipients.map { mapOf("emailAddress" to mapOf("address" to it)) })
                JsonApi.call("Outlook", "POST", "$user/sendMail", signIn.headers, mapOf("message" to message, "saveToSentItems" to true))
            }
            LIST_UNREAD -> JsonApi.call("Outlook", "GET", "$user/messages?\$filter=${JsonApi.encoded("isRead eq false")}&\$top=$EMAILS_READ&\$select=id,subject,from,receivedDateTime,bodyPreview", signIn.headers, null)
            MARK_AS_READ -> JsonApi.call("Outlook", "PATCH", "$user/messages/${JsonApi.encoded(messageValue)}", signIn.headers, mapOf("isRead" to true))
            else -> return ActionResult.failure("Outlook has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS && operationValue == LIST_UNREAD) result.outputVariables["emailCount"] = JsonApi.parsed(result).path("value").size()
        return result
    }

    override val credentialType: String
        get() = MICROSOFT_ACCOUNT

    override val outputs: Map<String, String>
        get() = mapOf("emailCount" to "Number of emails")

    override val description: String
        get() = "Sends email from an Outlook mailbox, lists unread email or marks it as read"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForSendEmail = mapOf("operation" to arrayOf(SEND_EMAIL))
            val showForMarkAsRead = mapOf("operation" to arrayOf(MARK_AS_READ))
            actionInfo.add(PropertyInformation("tenantId", "Directory (tenant) ID", PropertyInformationType.STRING, "", "On the overview page of the app registration.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("clientId", "Application (client) ID", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("clientSecret", "Client secret", PropertyInformationType.STRING, "", "Under Certificates and secrets.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, SEND_EMAIL, "", listOf(
                PropertyInformation(SEND_EMAIL, "Send an email"),
                PropertyInformation(LIST_UNREAD, "List unread email"),
                PropertyInformation(MARK_AS_READ, "Mark an email as read")
            )))
            actionInfo.add(PropertyInformation("mailbox", "Mailbox", PropertyInformationType.STRING, "", "The email address that it is sent from or read in."))
            actionInfo.add(PropertyInformation("toAddresses", "To", PropertyInformationType.STRING, "", "Separated by commas.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSendEmail))
            actionInfo.add(PropertyInformation("subject", "Subject", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSendEmail))
            actionInfo.add(PropertyInformation("emailBody", "Message", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSendEmail))
            actionInfo.add(PropertyInformation("isHtml", "The message is HTML", PropertyInformationType.BOOLEAN, "false", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSendEmail))
            actionInfo.add(PropertyInformation("messageId", "Email ID", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForMarkAsRead))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://graph.microsoft.com", "Only change this to go through a proxy."))
            actionInfo.add(PropertyInformation("signInUrl", "Sign-in URL", PropertyInformationType.STRING, "https://login.microsoftonline.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val SEND_EMAIL = "sendEmail"
        const val LIST_UNREAD = "listUnread"
        const val MARK_AS_READ = "markAsRead"
        const val EMAILS_READ = 25
    }
}
