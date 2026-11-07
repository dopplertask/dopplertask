package com.dopplertask.dopplertask.domain.action.integration.sales

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
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException

@Entity
@Table(name = "HubSpotAction")
@DiscriminatorValue("hubspot_action")
class HubSpotAction : Action() {
    @Column
    var baseUrl: String? = "https://api.hubapi.com"

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "saveContact"

    @Column
    var email: String? = null

    @Column
    var firstName: String? = null

    @Column
    var lastName: String? = null

    @Column
    var dealName: String? = null

    @Column
    var amount: String? = null

    @Column
    var dealStage: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val tokenValue = read(token)
        val operationValue = read(operation)
        val emailValue = read(email).trim()
        val dealNameValue = read(dealName).trim()
        if (tokenValue.isBlank()) return ActionResult.failure("HubSpot needs the access token of a private app.")
        if (operationValue != CREATE_DEAL && emailValue.isBlank()) return ActionResult.failure("HubSpot needs the email of the contact.")
        if (operationValue == CREATE_DEAL && dealNameValue.isBlank()) return ActionResult.failure("HubSpot needs a name for the deal.")

        val base = read(baseUrl).trim().trimEnd('/')
        val headers = mapOf("Authorization" to "Bearer $tokenValue")
        fun filled(vararg properties: Pair<String, String>) = properties.filter { it.second.isNotBlank() }.toMap()
        return when (operationValue) {
            SAVE_CONTACT -> {
                val contact = mapOf("idProperty" to "email", "id" to emailValue, "properties" to filled("email" to emailValue, "firstname" to read(firstName).trim(), "lastname" to read(lastName).trim()))
                val result = JsonApi.call("HubSpot", "POST", "$base/crm/objects/$API_VERSION/contacts/batch/upsert", headers, mapOf("inputs" to listOf(contact)))
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["contactId"] = JsonApi.parsed(result).path("results").path(0).path("id").asText()
                result
            }
            FIND_CONTACT -> {
                val filter = mapOf("propertyName" to "email", "operator" to "EQ", "value" to emailValue)
                val result = JsonApi.call("HubSpot", "POST", "$base/crm/objects/$API_VERSION/contacts/search", headers, mapOf("filterGroups" to listOf(mapOf("filters" to listOf(filter))), "properties" to listOf("email"), "sorts" to listOf<String>(), "limit" to 1, "after" to "0"))
                if (result.statusCode == StatusCode.SUCCESS) {
                    val contactId = JsonApi.parsed(result).path("results").path(0).path("id").asText("")
                    result.outputVariables["contactId"] = contactId
                    result.outputVariables["found"] = contactId.isNotEmpty()
                }
                result
            }
            CREATE_DEAL -> {
                val properties = filled("dealname" to dealNameValue, "amount" to read(amount).trim(), "dealstage" to read(dealStage).trim())
                val result = JsonApi.call("HubSpot", "POST", "$base/crm/objects/$API_VERSION/$DEALS_OBJECT", headers, mapOf("properties" to properties, "associations" to listOf<String>()))
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["dealId"] = JsonApi.parsed(result).path("id").asText()
                result
            }
            else -> ActionResult.failure("HubSpot has no operation called \"$operationValue\".")
        }
    }

    override val outputs: Map<String, String>
        get() = mapOf("contactId" to "Contact id", "found" to "Whether a contact was found", "dealId" to "Deal id")

    override val description: String
        get() = "Saves or finds a HubSpot contact, or creates a deal"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForSaveContactFindContact = mapOf("operation" to arrayOf(SAVE_CONTACT, FIND_CONTACT))
            val showForSaveContact = mapOf("operation" to arrayOf(SAVE_CONTACT))
            val showForCreateDeal = mapOf("operation" to arrayOf(CREATE_DEAL))
            actionInfo.add(PropertyInformation("token", "Access token", PropertyInformationType.STRING, "", "From a private app in HubSpot.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, SAVE_CONTACT, "", listOf(
                PropertyInformation(SAVE_CONTACT, "Save a contact"),
                PropertyInformation(FIND_CONTACT, "Find a contact"),
                PropertyInformation(CREATE_DEAL, "Create a deal")
            )))
            actionInfo.add(PropertyInformation("email", "Email", PropertyInformationType.STRING, "", "A contact with this email is updated, and otherwise created.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSaveContactFindContact))
            actionInfo.add(PropertyInformation("firstName", "First name", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSaveContact))
            actionInfo.add(PropertyInformation("lastName", "Last name", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSaveContact))
            actionInfo.add(PropertyInformation("dealName", "Deal name", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateDeal))
            actionInfo.add(PropertyInformation("amount", "Amount", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateDeal))
            actionInfo.add(PropertyInformation("dealStage", "Deal stage", PropertyInformationType.STRING, "", "The internal name of the stage, such as appointmentscheduled.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateDeal))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.hubapi.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val SAVE_CONTACT = "saveContact"
        const val FIND_CONTACT = "findContact"
        const val CREATE_DEAL = "createDeal"
        const val API_VERSION = "2026-09"
        const val DEALS_OBJECT = "0-3"
    }
}
