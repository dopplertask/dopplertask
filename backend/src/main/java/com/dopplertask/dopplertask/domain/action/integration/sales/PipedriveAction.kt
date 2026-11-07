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
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException

@Entity
@Table(name = "PipedriveAction")
@DiscriminatorValue("pipedrive_action")
class PipedriveAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "createPerson"

    @Column
    var personName: String? = null

    @Column
    var email: String? = null

    @Column
    var dealTitle: String? = null

    @Column
    var dealValue: String? = null

    @Column
    var personId: String? = null

    @Column
    var dealId: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var note: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val base = read(siteUrl).trim().trimEnd('/')
        val tokenValue = read(token)
        val operationValue = read(operation)
        val nameValue = read(personName).trim()
        val titleValue = read(dealTitle).trim()
        val noteValue = read(note)
        val dealIdValue = read(dealId).trim()
        val personIdValue = read(personId).trim()
        val valueText = read(dealValue).trim()
        if (base.isBlank()) return ActionResult.failure("Pipedrive needs the address of your company, such as https://acme.pipedrive.com.")
        if (tokenValue.isBlank()) return ActionResult.failure("Pipedrive needs an API token, which is under Personal preferences, API.")
        if (operationValue == CREATE_PERSON && nameValue.isBlank()) return ActionResult.failure("Pipedrive needs the name of the person.")
        if (operationValue == CREATE_DEAL && titleValue.isBlank()) return ActionResult.failure("Pipedrive needs a title for the deal.")
        if (operationValue == CREATE_DEAL && valueText.isNotEmpty() && valueText.toDoubleOrNull() == null) return ActionResult.failure("Pipedrive needs the value of the deal as a number, and got \"$valueText\".")
        if (operationValue == CREATE_DEAL && personIdValue.isNotEmpty() && personIdValue.toLongOrNull() == null) return ActionResult.failure("Pipedrive needs the person id as a number, and got \"$personIdValue\".")
        if (operationValue == ADD_NOTE && (dealIdValue.toLongOrNull() == null || noteValue.isBlank())) return ActionResult.failure("Pipedrive needs the number of the deal and the text of the note.")

        val headers = mapOf("x-api-token" to tokenValue)
        fun idOf(result: ActionResult) = JsonApi.parsed(result).path("data").path("id").asText()
        return when (operationValue) {
            CREATE_PERSON -> {
                val emailValue = read(email).trim()
                val person = mutableMapOf<String, Any>("name" to nameValue)
                if (emailValue.isNotEmpty()) person["emails"] = listOf(mapOf("value" to emailValue, "primary" to true))
                val result = JsonApi.call("Pipedrive", "POST", "$base/api/v2/persons", headers, person)
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["personId"] = idOf(result)
                result
            }
            CREATE_DEAL -> {
                val deal = mutableMapOf<String, Any>("title" to titleValue)
                if (valueText.isNotEmpty()) deal["value"] = valueText.toDouble()
                if (personIdValue.isNotEmpty()) deal["person_id"] = personIdValue.toLong()
                val result = JsonApi.call("Pipedrive", "POST", "$base/api/v2/deals", headers, deal)
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["dealId"] = idOf(result)
                result
            }
            ADD_NOTE -> JsonApi.call("Pipedrive", "POST", "$base/api/v1/notes", headers, mapOf("content" to noteValue, "deal_id" to dealIdValue.toLong()))
            else -> ActionResult.failure("Pipedrive has no operation called \"$operationValue\".")
        }
    }

    override val outputs: Map<String, String>
        get() = mapOf("personId" to "Person id", "dealId" to "Deal id")

    override val description: String
        get() = "Creates a Pipedrive person or deal, or adds a note to a deal"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForCreatePerson = mapOf("operation" to arrayOf(CREATE_PERSON))
            val showForCreateDeal = mapOf("operation" to arrayOf(CREATE_DEAL))
            val showForAddNote = mapOf("operation" to arrayOf(ADD_NOTE))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_PERSON, "", listOf(
                PropertyInformation(CREATE_PERSON, "Create a person"),
                PropertyInformation(CREATE_DEAL, "Create a deal"),
                PropertyInformation(ADD_NOTE, "Add a note to a deal")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "Company address", PropertyInformationType.STRING, "", "Such as https://acme.pipedrive.com."))
            actionInfo.add(PropertyInformation("token", "API token", PropertyInformationType.STRING, "", "Under Personal preferences, API.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("personName", "Name", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePerson))
            actionInfo.add(PropertyInformation("email", "Email", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePerson))
            actionInfo.add(PropertyInformation("dealTitle", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateDeal))
            actionInfo.add(PropertyInformation("dealValue", "Value", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateDeal))
            actionInfo.add(PropertyInformation("personId", "Person id", PropertyInformationType.STRING, "", "A step that creates a person gives it as \$lastLog.outputVariables.personId.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateDeal))
            actionInfo.add(PropertyInformation("dealId", "Deal id", PropertyInformationType.STRING, "", "A step that creates a deal gives it as \$lastLog.outputVariables.dealId.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddNote))
            actionInfo.add(PropertyInformation("note", "Note", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddNote))
            return actionInfo
        }

    private companion object {
        const val CREATE_PERSON = "createPerson"
        const val CREATE_DEAL = "createDeal"
        const val ADD_NOTE = "addNote"
    }
}
