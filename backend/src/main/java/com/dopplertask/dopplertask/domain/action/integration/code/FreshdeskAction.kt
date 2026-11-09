package com.dopplertask.dopplertask.domain.action.integration.code

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
import java.util.Base64

@Entity
@Table(name = "FreshdeskAction")
@DiscriminatorValue("freshdesk_action")
class FreshdeskAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var apiKey: String? = null

    @Column
    var operation: String? = "createTicket"

    @Column
    var ticketNumber: String? = null

    @Column
    var email: String? = null

    @Column(length = 1024)
    var subject: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var ticketDescription: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var comment: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = JsonApi.baseAddress(read(siteUrl))
        val keyValue = read(apiKey).trim()
        val operationValue = read(operation)
        val numberValue = read(ticketNumber).trim()
        val emailValue = read(email).trim()
        val subjectValue = read(subject)
        val detailValue = read(ticketDescription)
        val commentValue = read(comment)
        if (site.isBlank()) return ActionResult.failure("Freshdesk needs the address of the site, such as https://acme.freshdesk.com.")
        if (keyValue.isBlank()) return ActionResult.failure("Freshdesk needs an API key, which is under Profile settings.")
        if (operationValue == CREATE_TICKET && (emailValue.isBlank() || subjectValue.isBlank() || detailValue.isBlank())) return ActionResult.failure("Freshdesk needs the email of the customer, a subject and a description.")
        if (operationValue != CREATE_TICKET && numberValue.toLongOrNull() == null) return ActionResult.failure("Freshdesk needs the number of the ticket, and got \"$numberValue\".")
        if ((operationValue == ADD_NOTE || operationValue == REPLY_TO_CUSTOMER) && commentValue.isBlank()) return ActionResult.failure("Freshdesk needs the text.")

        val headers = mapOf("Authorization" to "Basic " + Base64.getEncoder().encodeToString("$keyValue:X".toByteArray()))
        val ticket = "$site/api/v2/tickets/$numberValue"
        val result = when (operationValue) {
            CREATE_TICKET -> JsonApi.call("Freshdesk", "POST", "$site/api/v2/tickets", headers, mapOf("subject" to subjectValue, "description" to detailValue, "email" to emailValue, "priority" to PRIORITY_LOW, "status" to STATUS_OPEN))
            ADD_NOTE -> JsonApi.call("Freshdesk", "POST", "$ticket/notes", headers, mapOf("body" to commentValue, "private" to true))
            REPLY_TO_CUSTOMER -> JsonApi.call("Freshdesk", "POST", "$ticket/reply", headers, mapOf("body" to commentValue))
            SOLVE_TICKET -> JsonApi.call("Freshdesk", "PUT", ticket, headers, mapOf("status" to STATUS_RESOLVED))
            else -> return ActionResult.failure("Freshdesk has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val number = if (operationValue == CREATE_TICKET) JsonApi.parsed(result).path("id").asText() else numberValue
            result.outputVariables["ticketNumber"] = number
            result.outputVariables["url"] = "$site/a/tickets/$number"
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("ticketNumber" to "Ticket number", "url" to "Address")

    override val description: String
        get() = "Creates, notes on, replies to or solves a Freshdesk ticket"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForAddNoteReplyToCustomerSolveTicket = mapOf("operation" to arrayOf(ADD_NOTE, REPLY_TO_CUSTOMER, SOLVE_TICKET))
            val showForCreateTicket = mapOf("operation" to arrayOf(CREATE_TICKET))
            val showForAddNoteReplyToCustomer = mapOf("operation" to arrayOf(ADD_NOTE, REPLY_TO_CUSTOMER))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_TICKET, "", listOf(
                PropertyInformation(CREATE_TICKET, "Create a ticket"),
                PropertyInformation(ADD_NOTE, "Add a private note"),
                PropertyInformation(REPLY_TO_CUSTOMER, "Reply to the customer"),
                PropertyInformation(SOLVE_TICKET, "Solve a ticket")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "Site", PropertyInformationType.STRING, "", "Such as https://acme.freshdesk.com."))
            actionInfo.add(PropertyInformation("apiKey", "API key", PropertyInformationType.STRING, "", "Under Profile settings.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("ticketNumber", "Ticket number", PropertyInformationType.STRING, "", "A step that creates a ticket gives it as \$lastLog.outputVariables.ticketNumber.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddNoteReplyToCustomerSolveTicket))
            actionInfo.add(PropertyInformation("email", "Email of the customer", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateTicket))
            actionInfo.add(PropertyInformation("subject", "Subject", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateTicket))
            actionInfo.add(PropertyInformation("ticketDescription", "Description", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateTicket))
            actionInfo.add(PropertyInformation("comment", "Text", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddNoteReplyToCustomer))
            return actionInfo
        }

    private companion object {
        const val CREATE_TICKET = "createTicket"
        const val ADD_NOTE = "addNote"
        const val REPLY_TO_CUSTOMER = "replyToCustomer"
        const val SOLVE_TICKET = "solveTicket"
        const val PRIORITY_LOW = 1
        const val STATUS_OPEN = 2
        const val STATUS_RESOLVED = 4
    }
}
