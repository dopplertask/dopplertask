package com.dopplertask.dopplertask.domain.action.integration.storage

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
@Table(name = "AirtableAction")
@DiscriminatorValue("airtable_action")
class AirtableAction : Action() {
    @Column
    var baseUrl: String? = "https://api.airtable.com"

    @Transient
    var token: String? = null

    @Column
    var base: String? = null

    @Column
    var tableName: String? = null

    @Column
    var operation: String? = "readRecords"

    @Column(length = 1024)
    var filterFormula: String? = null

    @Column
    var recordId: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var recordFields: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val tokenValue = read(token)
        val baseText = read(base).trim()
        val baseId = BASE_ID.find(baseText)?.value ?: baseText
        val tableValue = read(tableName).trim()
        val operationValue = read(operation)
        val recordValue = read(recordId).trim()
        val fields = JsonApi.objectFrom(read(recordFields))
        if (tokenValue.isBlank()) return ActionResult.failure("Airtable needs a personal access token, which is made in the builder hub.")
        if (baseId.isBlank() || tableValue.isBlank()) return ActionResult.failure("Airtable needs the base and the name of the table.")
        if (operationValue != READ_RECORDS && fields == null) return ActionResult.failure("Airtable needs the fields as a JSON object, such as {\"Name\": \"Ada\"}.")
        if (operationValue == UPDATE_RECORD && recordValue.isBlank()) return ActionResult.failure("Airtable needs the id of the record.")

        val table = read(baseUrl).trim().trimEnd('/') + "/v0/${JsonApi.encoded(baseId)}/${JsonApi.encoded(tableValue)}"
        val headers = mapOf("Authorization" to "Bearer $tokenValue")
        return when (operationValue) {
            READ_RECORDS -> {
                val filterValue = read(filterFormula).trim()
                val filter = if (filterValue.isEmpty()) "" else "&filterByFormula=${JsonApi.encoded(filterValue)}"
                val result = JsonApi.call("Airtable", "GET", "$table?maxRecords=$RECORDS_READ$filter", headers, null)
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["recordCount"] = JsonApi.parsed(result).path("records").size()
                result
            }
            ADD_RECORD, UPDATE_RECORD -> {
                val result = if (operationValue == ADD_RECORD) JsonApi.call("Airtable", "POST", table, headers, mapOf("fields" to fields))
                else JsonApi.call("Airtable", "PATCH", "$table/${JsonApi.encoded(recordValue)}", headers, mapOf("fields" to fields))
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["recordId"] = JsonApi.parsed(result).path("id").asText()
                result
            }
            else -> ActionResult.failure("Airtable has no operation called \"$operationValue\".")
        }
    }

    override val outputs: Map<String, String>
        get() = mapOf("recordCount" to "Number of records", "recordId" to "Record id")

    override val description: String
        get() = "Reads, adds or updates Airtable records"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForReadRecords = mapOf("operation" to arrayOf(READ_RECORDS))
            val showForUpdateRecord = mapOf("operation" to arrayOf(UPDATE_RECORD))
            val showForAddRecordUpdateRecord = mapOf("operation" to arrayOf(ADD_RECORD, UPDATE_RECORD))
            actionInfo.add(PropertyInformation("token", "Personal access token", PropertyInformationType.STRING, "", "Made in the builder hub, with the scopes for records.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, READ_RECORDS, "", listOf(
                PropertyInformation(READ_RECORDS, "Read records"),
                PropertyInformation(ADD_RECORD, "Add a record"),
                PropertyInformation(UPDATE_RECORD, "Update a record")
            )))
            actionInfo.add(PropertyInformation("base", "Base", PropertyInformationType.STRING, "", "The address of the base, or its id that starts with app."))
            actionInfo.add(PropertyInformation("tableName", "Table", PropertyInformationType.STRING, "", "As it is named in the base."))
            actionInfo.add(PropertyInformation("filterFormula", "Filter", PropertyInformationType.STRING, "", "A formula, such as {Status} = \"Open\". Leave it empty for all records.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadRecords))
            actionInfo.add(PropertyInformation("recordId", "Record id", PropertyInformationType.STRING, "", "Starts with rec.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForUpdateRecord))
            actionInfo.add(PropertyInformation("recordFields", "Fields", PropertyInformationType.MULTILINE, "", "As JSON, such as {\"Name\": \"Ada\"}.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddRecordUpdateRecord))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.airtable.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val READ_RECORDS = "readRecords"
        const val ADD_RECORD = "addRecord"
        const val UPDATE_RECORD = "updateRecord"
        const val RECORDS_READ = 100
        val BASE_ID = Regex("app[A-Za-z0-9]+")
    }
}
