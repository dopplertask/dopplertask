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
@Table(name = "SalesforceAction")
@DiscriminatorValue("salesforce_action")
class SalesforceAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var clientId: String? = null

    @Transient
    var clientSecret: String? = null

    @Column
    var operation: String? = "createRecord"

    @Column
    var objectName: String? = "Contact"

    @Column
    var recordId: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var recordFields: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var query: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = read(siteUrl).trim().trimEnd('/')
        val clientIdValue = read(clientId).trim()
        val clientSecretValue = read(clientSecret)
        val operationValue = read(operation)
        val objectValue = read(objectName).trim()
        val recordIdValue = read(recordId).trim()
        val queryValue = read(query).trim()
        val fieldsText = read(recordFields)
        val fields = JsonApi.objectFrom(fieldsText)
        if (site.isBlank()) return ActionResult.failure("Salesforce needs the My Domain address, such as https://acme.my.salesforce.com.")
        if (clientIdValue.isBlank() || clientSecretValue.isBlank()) return ActionResult.failure("Salesforce needs the consumer key and secret of a connected app.")
        if (operationValue != RUN_QUERY && objectValue.isBlank()) return ActionResult.failure("Salesforce needs the name of the object, such as Contact.")
        if (operationValue == UPDATE_RECORD && recordIdValue.isBlank()) return ActionResult.failure("Salesforce needs the id of the record.")
        if (operationValue != RUN_QUERY && fields == null) return ActionResult.failure("Salesforce needs the fields as a JSON object, such as {\"LastName\": \"Lovelace\"}.")
        if (operationValue == RUN_QUERY && queryValue.isBlank()) return ActionResult.failure("Salesforce needs the query to run.")

        // An access token is asked for each time, as it is cheap and a stored one would expire without warning.
        val signIn = JsonApi.callForm("Salesforce", "$site/services/oauth2/token", mapOf(), mapOf("grant_type" to "client_credentials", "client_id" to clientIdValue, "client_secret" to clientSecretValue))
        if (signIn.statusCode == StatusCode.FAILURE) return signIn
        val headers = mapOf("Authorization" to "Bearer " + JsonApi.parsed(signIn).path("access_token").asText())
        val data = "$site/services/data/$API_VERSION"
        val records = "$data/sobjects/${JsonApi.encoded(objectValue)}"
        return when (operationValue) {
            CREATE_RECORD -> {
                val result = JsonApi.call("Salesforce", "POST", records, headers, fields)
                if (result.statusCode == StatusCode.SUCCESS) {
                    val createdId = JsonApi.parsed(result).path("id").asText()
                    result.outputVariables["recordId"] = createdId
                    result.outputVariables["url"] = "$site/$createdId"
                }
                result
            }
            UPDATE_RECORD -> {
                val result = JsonApi.call("Salesforce", "PATCH", "$records/${JsonApi.encoded(recordIdValue)}", headers, fields)
                if (result.statusCode == StatusCode.SUCCESS) {
                    result.outputVariables["recordId"] = recordIdValue
                    result.outputVariables["url"] = "$site/$recordIdValue"
                }
                result
            }
            RUN_QUERY -> {
                val result = JsonApi.call("Salesforce", "GET", "$data/query?q=${JsonApi.encoded(queryValue)}", headers, null)
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["recordCount"] = JsonApi.parsed(result).path("totalSize").asInt()
                result
            }
            else -> ActionResult.failure("Salesforce has no operation called \"$operationValue\".")
        }
    }

    override val outputs: Map<String, String>
        get() = mapOf("recordId" to "Record id", "url" to "Address", "recordCount" to "Number of records")

    override val description: String
        get() = "Creates or updates a Salesforce record, or runs a query"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForCreateRecordUpdateRecord = mapOf("operation" to arrayOf(CREATE_RECORD, UPDATE_RECORD))
            val showForUpdateRecord = mapOf("operation" to arrayOf(UPDATE_RECORD))
            val showForRunQuery = mapOf("operation" to arrayOf(RUN_QUERY))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_RECORD, "", listOf(
                PropertyInformation(CREATE_RECORD, "Create a record"),
                PropertyInformation(UPDATE_RECORD, "Update a record"),
                PropertyInformation(RUN_QUERY, "Run a query")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "My Domain address", PropertyInformationType.STRING, "", "Such as https://acme.my.salesforce.com."))
            actionInfo.add(PropertyInformation("clientId", "Consumer key", PropertyInformationType.STRING, "", "From a connected app with the client credentials flow.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("clientSecret", "Consumer secret", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("objectName", "Object", PropertyInformationType.STRING, "Contact", "Such as Contact, Lead or Case.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateRecordUpdateRecord))
            actionInfo.add(PropertyInformation("recordId", "Record id", PropertyInformationType.STRING, "", "A step that creates a record gives it as \$lastLog.outputVariables.recordId.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForUpdateRecord))
            actionInfo.add(PropertyInformation("recordFields", "Fields", PropertyInformationType.MULTILINE, "", "As JSON, such as {\"LastName\": \"Lovelace\"}.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateRecordUpdateRecord))
            actionInfo.add(PropertyInformation("query", "Query", PropertyInformationType.MULTILINE, "", "Such as SELECT Id, Name FROM Contact LIMIT 10.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForRunQuery))
            return actionInfo
        }

    private companion object {
        const val CREATE_RECORD = "createRecord"
        const val UPDATE_RECORD = "updateRecord"
        const val RUN_QUERY = "runQuery"
        const val API_VERSION = "v68.0"
    }
}
