package com.dopplertask.dopplertask.domain.action.integration.code

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
import java.util.Base64
import java.io.IOException
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table

@Entity
@Table(name = "ZendeskAction")
@DiscriminatorValue("zendesk_action")
class ZendeskAction : Action() {
    @Column
    var siteUrl: String? = null

    @Column
    var email: String? = null

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var token: String? = null

    @Column
    var operation: String? = "createTicket"

    @Column
    var ticketNumber: String? = null

    @Column(length = 1024)
    var subject: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var ticketDescription: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var comment: String? = null

    @Column
    var visibleToCustomer: String? = "false"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = read(siteUrl).trim().trimEnd('/')
        val emailValue = read(email).trim()
        val tokenValue = read(token)
        val operationValue = read(operation)
        val numberValue = read(ticketNumber).trim()
        val subjectValue = read(subject)
        val detailValue = read(ticketDescription)
        val commentValue = read(comment)
        if (site.isBlank()) return ActionResult.failure("Zendesk needs the address of the site, such as https://acme.zendesk.com.")
        if (emailValue.isBlank() || tokenValue.isBlank()) return ActionResult.failure("Zendesk needs the email of an agent and an API token.")
        if (operationValue == CREATE_TICKET && (subjectValue.isBlank() || detailValue.isBlank())) return ActionResult.failure("Zendesk needs a subject and a description for the ticket.")
        if (operationValue != CREATE_TICKET && numberValue.toLongOrNull() == null) return ActionResult.failure("Zendesk needs the number of the ticket, and got \"$numberValue\".")
        if (operationValue == ADD_COMMENT && commentValue.isBlank()) return ActionResult.failure("Zendesk needs the text of the comment.")

        val headers = mapOf("Authorization" to "Basic " + Base64.getEncoder().encodeToString("$emailValue/token:$tokenValue".toByteArray()))
        val ticket = "$site/api/v2/tickets/$numberValue.json"
        val result = when (operationValue) {
            CREATE_TICKET -> JsonApi.call("Zendesk", "POST", "$site/api/v2/tickets.json", headers, mapOf("ticket" to mapOf("subject" to subjectValue, "comment" to mapOf("body" to detailValue))))
            // Zendesk makes a comment public unless told otherwise, and text from an automation should not reach a customer by accident.
            ADD_COMMENT -> JsonApi.call("Zendesk", "PUT", ticket, headers, mapOf("ticket" to mapOf("comment" to mapOf("body" to commentValue, "public" to (read(visibleToCustomer) == "true")))))
            SOLVE_TICKET -> JsonApi.call("Zendesk", "PUT", ticket, headers, mapOf("ticket" to mapOf("status" to "solved")))
            else -> ActionResult.failure("Zendesk has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val number = if (operationValue == CREATE_TICKET) JsonApi.parsed(result).path("ticket").path("id").asText() else numberValue
            result.outputVariables["ticketNumber"] = number
            result.outputVariables["url"] = "$site/agent/tickets/$number"
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("ticketNumber" to "Ticket number", "url" to "Address")

    override val description: String
        get() = "Creates, comments on or solves a Zendesk ticket"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val creating = mapOf("operation" to arrayOf(CREATE_TICKET))
            val commenting = mapOf("operation" to arrayOf(ADD_COMMENT))
            actionInfo.add(PropertyInformation("siteUrl", "Site", PropertyInformationType.STRING, "", "Such as https://acme.zendesk.com."))
            actionInfo.add(PropertyInformation("email", "Email", PropertyInformationType.STRING, "", "The email of an agent.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("token", "API token", PropertyInformationType.STRING, "", "Made in the Admin Center, under Zendesk API.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_TICKET, "", listOf(
                PropertyInformation(CREATE_TICKET, "Create a ticket"),
                PropertyInformation(ADD_COMMENT, "Comment on a ticket"),
                PropertyInformation(SOLVE_TICKET, "Solve a ticket")
            )))
            actionInfo.add(PropertyInformation("ticketNumber", "Ticket number", PropertyInformationType.STRING, "", "A step that creates a ticket gives it as \$lastLog.outputVariables.ticketNumber.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(ADD_COMMENT, SOLVE_TICKET))))
            actionInfo.add(PropertyInformation("subject", "Subject", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, creating))
            actionInfo.add(PropertyInformation("ticketDescription", "Description", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, creating))
            actionInfo.add(PropertyInformation("comment", "Comment", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, commenting))
            actionInfo.add(PropertyInformation("visibleToCustomer", "Visible to the customer", PropertyInformationType.BOOLEAN, "false", "Otherwise the comment is an internal note.", mutableListOf(), PropertyInformationCategory.PROPERTY, commenting))
            return actionInfo
        }

    private companion object {
        const val CREATE_TICKET = "createTicket"
        const val ADD_COMMENT = "addComment"
        const val SOLVE_TICKET = "solveTicket"
    }
}
