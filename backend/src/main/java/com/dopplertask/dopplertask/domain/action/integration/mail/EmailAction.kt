package com.dopplertask.dopplertask.domain.action.integration.mail

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.ColumnEncryptor
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table

@Entity
@Table(name = "EmailAction")
@DiscriminatorValue("email_action")
class EmailAction : Action() {
    @Column
    var hostname: String? = null

    @Column
    var port: String? = "587"

    @Column
    var security: String? = STARTTLS

    @Column
    var username: String? = null

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var password: String? = null

    @Column(name = "fromAddress")
    var from: String? = null

    @Column(name = "toAddresses", length = 1024)
    var to: String? = null

    @Column(length = 1024)
    var subject: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var message: String? = null

    @Column
    var asHtml: String? = "false"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val hostValue = variableExtractorUtil.extract(hostname, execution, scriptLanguage).trim()
        val portValue = variableExtractorUtil.extract(port, execution, scriptLanguage).trim().ifEmpty { "587" }
        val securityValue = variableExtractorUtil.extract(security, execution, scriptLanguage)
        val usernameValue = variableExtractorUtil.extract(username, execution, scriptLanguage).trim()
        val fromValue = variableExtractorUtil.extract(from, execution, scriptLanguage).trim()
        val toValue = variableExtractorUtil.extract(to, execution, scriptLanguage).trim()
        val messageValue = variableExtractorUtil.extract(message, execution, scriptLanguage)
        // A line break in a subject would start a new header, and the text can come from an earlier step.
        val subjectValue = variableExtractorUtil.extract(subject, execution, scriptLanguage).lines().joinToString(" ")
        if (hostValue.isBlank()) return ActionResult.failure("Email needs the hostname of a mail server.")
        if (portValue.toIntOrNull() == null) return ActionResult.failure("Email needs the port as a number, such as 587.")
        if (fromValue.isBlank()) return ActionResult.failure("Email needs an address to send from.")
        if (toValue.isBlank()) return ActionResult.failure("Email needs an address to send to.")

        val properties = Properties()
        properties["mail.smtp.host"] = hostValue
        properties["mail.smtp.port"] = portValue
        properties["mail.smtp.connectiontimeout"] = TIMEOUT_MILLISECONDS
        properties["mail.smtp.timeout"] = TIMEOUT_MILLISECONDS
        properties["mail.smtp.auth"] = (usernameValue.isNotEmpty()).toString()
        // Required, so that a password is never sent in the clear when a server does not offer encryption.
        if (securityValue == STARTTLS) properties["mail.smtp.starttls.required"] = "true"
        if (securityValue == STARTTLS) properties["mail.smtp.starttls.enable"] = "true"
        if (securityValue == TLS) properties["mail.smtp.ssl.enable"] = "true"
        if (securityValue != NONE) properties["mail.smtp.ssl.checkserveridentity"] = "true"

        return try {
            val email = MimeMessage(Session.getInstance(properties))
            email.setFrom(InternetAddress(fromValue, true))
            email.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toValue, true))
            email.setSubject(subjectValue, "UTF-8")
            if (variableExtractorUtil.extract(asHtml, execution, scriptLanguage) == "true") email.setContent(messageValue, "text/html; charset=UTF-8")
            else email.setText(messageValue, "UTF-8")
            email.saveChanges()
            if (usernameValue.isEmpty()) Transport.send(email)
            else Transport.send(email, usernameValue, variableExtractorUtil.extract(password, execution, scriptLanguage))

            val result = ActionResult()
            result.output = "Sent to $toValue."
            result.outputVariables["messageId"] = email.messageID
            result
        } catch (e: AddressException) {
            ActionResult.failure("Email could not read the address \"${e.ref}\".")
        } catch (e: AuthenticationFailedException) {
            ActionResult.failure("The mail server did not accept the username and password.")
        } catch (e: MessagingException) {
            when (e.cause) {
                is ConnectException, is SocketTimeoutException, is UnknownHostException -> ActionResult.failure("Could not connect to the mail server at $hostValue:$portValue. Check the address, and that the service is running.")
                else -> ActionResult.failure("The mail server answered: ${serverReason(e)}")
            }
        }
    }

    // The first message only says that something failed, and what the server said is at the end of the chain.
    private fun serverReason(failure: MessagingException): String? =
        generateSequence(failure as Exception) { (it as? MessagingException)?.nextException }.last().message?.trim()

    override val outputs: Map<String, String>
        get() = mapOf("messageId" to "Message id")

    override val description: String
        get() = "Sends an email through a mail server"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("hostname", "Mail server", PropertyInformationType.STRING, "", "Such as smtp.example.com."))
            actionInfo.add(PropertyInformation("port", "Port", PropertyInformationType.NUMBER, "587", "Usually 587 with STARTTLS and 465 with TLS."))
            actionInfo.add(PropertyInformation("security", "Encryption", PropertyInformationType.DROPDOWN, STARTTLS, "", listOf(
                PropertyInformation(STARTTLS, "STARTTLS"),
                PropertyInformation(TLS, "TLS"),
                PropertyInformation(NONE, "None")
            )))
            actionInfo.add(PropertyInformation("username", "Username", PropertyInformationType.STRING, "", "Leave it empty if the server does not ask for one.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("password", "Password", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("from", "From", PropertyInformationType.STRING, "", "Such as alerts@example.com."))
            actionInfo.add(PropertyInformation("to", "To", PropertyInformationType.STRING, "", "One address, or several separated by commas."))
            actionInfo.add(PropertyInformation("subject", "Subject", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("message", "Message", PropertyInformationType.MULTILINE, "", ""))
            actionInfo.add(PropertyInformation("asHtml", "Send as HTML", PropertyInformationType.BOOLEAN, "false", ""))
            return actionInfo
        }

    private companion object {
        const val STARTTLS = "starttls"
        const val TLS = "tls"
        const val NONE = "none"
        const val TIMEOUT_MILLISECONDS = "10000"
    }
}
