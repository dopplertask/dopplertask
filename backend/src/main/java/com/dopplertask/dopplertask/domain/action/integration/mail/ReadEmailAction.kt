package com.dopplertask.dopplertask.domain.action.integration.mail

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Properties
import jakarta.mail.AuthenticationFailedException
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.search.FlagTerm
import java.io.IOException
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Transient

@Entity
@Table(name = "ReadEmailAction")
@DiscriminatorValue("read_email_action")
class ReadEmailAction : Action() {
    @Column
    var hostname: String? = null

    @Column
    var port: String? = "993"

    @Column
    var security: String? = "tls"

    @Transient
    var username: String? = null

    @Transient
    var password: String? = null

    @Column
    var folder: String? = "INBOX"

    @Column
    var onlyUnread: String? = "true"

    @Column
    var markAsRead: String? = "false"

    @Column
    var maxMessages: String? = "10"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val hostValue = read(hostname).trim()
        val portValue = read(port).trim().ifEmpty { "993" }
        val securityValue = read(security)
        val usernameValue = read(username).trim()
        val folderValue = read(folder).trim().ifEmpty { "INBOX" }
        val limit = read(maxMessages).trim().ifEmpty { "10" }.toIntOrNull()
        val markValue = read(markAsRead) == "true"
        if (hostValue.isBlank()) return ActionResult.failure("Read email needs the hostname of a mail server.")
        if (portValue.toIntOrNull() == null) return ActionResult.failure("Read email needs the port as a number, such as 993.")
        if (usernameValue.isBlank()) return ActionResult.failure("Read email needs a username.")
        if (limit == null || limit <= 0) return ActionResult.failure("Read email needs the most messages as a number above 0.")

        val protocol = if (securityValue == TLS) "imaps" else "imap"
        val properties = Properties()
        properties["mail.$protocol.connectiontimeout"] = TIMEOUT_MILLISECONDS
        properties["mail.$protocol.timeout"] = TIMEOUT_MILLISECONDS
        // Reading must not mark a message as read, since only the setting for that should.
        properties["mail.$protocol.peek"] = "true"
        // Required, so that a password is never sent in the clear when a server does not offer encryption.
        if (securityValue == STARTTLS) {
            properties["mail.imap.starttls.enable"] = "true"
            properties["mail.imap.starttls.required"] = "true"
        }
        if (securityValue != NONE) properties["mail.$protocol.ssl.checkserveridentity"] = "true"

        return try {
            Session.getInstance(properties).getStore(protocol).use { store ->
                store.connect(hostValue, portValue.toInt(), usernameValue, read(password))
                val box = store.getFolder(folderValue)
                if (!box.exists()) return ActionResult.failure("The mail server has no folder called \"$folderValue\".")
                box.open(if (markValue) Folder.READ_WRITE else Folder.READ_ONLY)
                try {
                    val found = if (read(onlyUnread) == "true") box.search(FlagTerm(Flags(Flags.Flag.SEEN), false)) else box.messages
                    val newestFirst = found.reversed().take(limit)
                    val messages = newestFirst.map { message ->
                        val email = Emails.read(message as MimeMessage, withAttachments = false)
                        // A mail often holds both text and HTML, and the next step wants the text.
                        mapOf(
                            "from" to email.path("from").path("text").asText(null),
                            "subject" to email.path("subject").asText(null),
                            "date" to email.path("date").asText(null),
                            "text" to (email.path("text").takeIf { it.isTextual } ?: email.path("html").takeIf { it.isTextual })?.asText()
                        )
                    }
                    if (markValue) newestFirst.forEach { it.setFlag(Flags.Flag.SEEN, true) }
                    val result = ActionResult()
                    result.output = JsonApi.mapper.writeValueAsString(messages)
                    result.outputVariables["messageCount"] = messages.size
                    result
                } finally {
                    // False, since closing with true removes messages that someone else has marked as deleted.
                    box.close(false)
                }
            }
        } catch (e: AuthenticationFailedException) {
            ActionResult.failure("The mail server did not accept the username and password.")
        } catch (e: MessagingException) {
            when (e.cause) {
                is ConnectException, is SocketTimeoutException, is UnknownHostException -> ActionResult.failure("Could not connect to the mail server at $hostValue:$portValue. Check the address, and that the service is running.")
                else -> ActionResult.failure("The mail server answered: ${e.message?.trim()}")
            }
        }
    }

    override val outputs: Map<String, String>
        get() = mapOf("messageCount" to "Number of messages")

    override val description: String
        get() = "Reads messages from a mailbox over IMAP"

    override val credentialType: String
        get() = MAIL_ACCOUNT

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("hostname", "Mail server", PropertyInformationType.STRING, "", "Such as imap.example.com."))
            actionInfo.add(PropertyInformation("port", "Port", PropertyInformationType.NUMBER, "993", "Usually 993 with TLS and 143 with STARTTLS."))
            actionInfo.add(PropertyInformation("security", "Encryption", PropertyInformationType.DROPDOWN, TLS, "", listOf(
                PropertyInformation(TLS, "TLS"),
                PropertyInformation(STARTTLS, "STARTTLS"),
                PropertyInformation(NONE, "None")
            )))
            actionInfo.add(PropertyInformation("username", "Username", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("password", "Password", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("folder", "Folder", PropertyInformationType.STRING, "INBOX", ""))
            actionInfo.add(PropertyInformation("onlyUnread", "Only unread messages", PropertyInformationType.BOOLEAN, "true", ""))
            actionInfo.add(PropertyInformation("markAsRead", "Mark as read", PropertyInformationType.BOOLEAN, "false", "So that the next run does not read them again."))
            actionInfo.add(PropertyInformation("maxMessages", "Most messages", PropertyInformationType.NUMBER, "10", "The newest are read first."))
            return actionInfo
        }

    private companion object {
        const val TLS = "tls"
        const val STARTTLS = "starttls"
        const val NONE = "none"
        const val TIMEOUT_MILLISECONDS = "10000"
    }
}
