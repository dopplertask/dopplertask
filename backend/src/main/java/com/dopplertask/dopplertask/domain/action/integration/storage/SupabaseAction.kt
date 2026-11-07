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
@Table(name = "SupabaseAction")
@DiscriminatorValue("supabase_action")
class SupabaseAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var secretKey: String? = null

    @Column
    var tableName: String? = null

    @Column
    var operation: String? = "readRows"

    @Column(length = 1024)
    var filter: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var rowValues: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = read(siteUrl).trim().trimEnd('/')
        val keyValue = read(secretKey).trim()
        val tableValue = read(tableName).trim()
        val operationValue = read(operation)
        val filterValue = read(filter).trim().trimStart('?', '&')
        val valuesText = read(rowValues).trim()
        val values = try { JsonApi.mapper.readValue(valuesText.ifEmpty { "null" }, Any::class.java) } catch (e: Exception) { null }
        if (site.isBlank()) return ActionResult.failure("Supabase needs the project URL, such as https://abc.supabase.co.")
        if (keyValue.isBlank()) return ActionResult.failure("Supabase needs the secret key of the project.")
        if (tableValue.isBlank()) return ActionResult.failure("Supabase needs the name of the table.")
        if (operationValue != READ_ROWS && values == null) return ActionResult.failure("Supabase needs the values as JSON, such as {\"status\": \"done\"}.")
        // Without a filter this would change every row in the table, which is rarely what was meant.
        if (operationValue == UPDATE_ROWS && filterValue.isBlank()) return ActionResult.failure("Supabase needs a filter to know which rows to update, such as id=eq.5.")

        val table = "$site/rest/v1/${JsonApi.encoded(tableValue)}"
        // The new secret keys are not tokens and only go in apikey, while the earlier service keys need both.
        val signedIn = if (keyValue.startsWith("sb_")) mapOf("apikey" to keyValue) else mapOf("apikey" to keyValue, "Authorization" to "Bearer $keyValue")
        val headers = signedIn + mapOf("Prefer" to "return=representation")
        val filterPart = if (filterValue.isEmpty()) "" else "&$filterValue"
        val result = when (operationValue) {
            READ_ROWS -> JsonApi.call("Supabase", "GET", "$table?select=*$filterPart", headers, null)
            ADD_ROWS -> JsonApi.call("Supabase", "POST", table, headers, values)
            UPDATE_ROWS -> JsonApi.call("Supabase", "PATCH", "$table?$filterValue", headers, values)
            else -> return ActionResult.failure("Supabase has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["rowCount"] = JsonApi.parsed(result).size()
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("rowCount" to "Number of rows")

    override val description: String
        get() = "Reads, adds or updates rows in a Supabase table"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForReadRowsUpdateRows = mapOf("operation" to arrayOf(READ_ROWS, UPDATE_ROWS))
            val showForAddRowsUpdateRows = mapOf("operation" to arrayOf(ADD_ROWS, UPDATE_ROWS))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, READ_ROWS, "", listOf(
                PropertyInformation(READ_ROWS, "Read rows"),
                PropertyInformation(ADD_ROWS, "Add rows"),
                PropertyInformation(UPDATE_ROWS, "Update rows")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "Project URL", PropertyInformationType.STRING, "", "Such as https://abc.supabase.co. Under Project settings, API."))
            actionInfo.add(PropertyInformation("secretKey", "Secret key", PropertyInformationType.STRING, "", "Under Project settings, API keys. It can read and change all data, so only store it here.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("tableName", "Table", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("filter", "Filter", PropertyInformationType.STRING, "", "Which rows, such as status=eq.open. Combine with &.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadRowsUpdateRows))
            actionInfo.add(PropertyInformation("rowValues", "Values", PropertyInformationType.MULTILINE, "", "As JSON, such as {\"status\": \"done\"}, or a list of these to add several.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddRowsUpdateRows))
            return actionInfo
        }

    private companion object {
        const val READ_ROWS = "readRows"
        const val ADD_ROWS = "addRows"
        const val UPDATE_ROWS = "updateRows"
    }
}
