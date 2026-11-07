package com.dopplertask.dopplertask.domain.action.integration.connection

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
@Table(name = "ElasticsearchAction")
@DiscriminatorValue("elasticsearch_action")
class ElasticsearchAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var apiKey: String? = null

    @Column
    var operation: String? = "search"

    @Column
    var indexName: String? = null

    @Column
    var documentId: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var document: String? = null

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
        val indexValue = read(indexName).trim()
        val operationValue = read(operation)
        val idValue = read(documentId).trim()
        val documentValue = read(document).trim()
        val queryValue = read(query).trim()
        if (site.isBlank()) return ActionResult.failure("Elasticsearch needs the address of the cluster, such as https://search.example.com:9200.")
        if (indexValue.isBlank()) return ActionResult.failure("Elasticsearch needs the name of the index.")
        if (operationValue == DELETE_DOCUMENT && idValue.isBlank()) return ActionResult.failure("Elasticsearch needs the ID of the document to delete.")
        val documentFields = if (operationValue == ADD_DOCUMENT) JsonApi.objectFrom(documentValue) ?: return ActionResult.failure("Elasticsearch needs the document as a JSON object.") else null
        val queryFields = if (operationValue == SEARCH && queryValue.isNotEmpty()) JsonApi.objectFrom(queryValue) ?: return ActionResult.failure("Elasticsearch needs the query as a JSON object.") else null

        val headers = read(apiKey).trim().takeIf { it.isNotEmpty() }?.let { mapOf("Authorization" to "ApiKey $it") } ?: mapOf()
        val index = "$site/${JsonApi.encoded(indexValue)}"
        val named = "$index/_doc/${JsonApi.encoded(idValue)}"
        val result = when (operationValue) {
            SEARCH -> JsonApi.call("Elasticsearch", "POST", "$index/_search", headers, mapOf("query" to (queryFields ?: mapOf("match_all" to mapOf<String, String>()))))
            ADD_DOCUMENT -> if (idValue.isEmpty()) JsonApi.call("Elasticsearch", "POST", "$index/_doc", headers, documentFields) else JsonApi.call("Elasticsearch", "PUT", named, headers, documentFields)
            DELETE_DOCUMENT -> JsonApi.call("Elasticsearch", "DELETE", named, headers, null)
            else -> return ActionResult.failure("Elasticsearch has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            if (operationValue == SEARCH) result.outputVariables["hitCount"] = answer.path("hits").path("total").path("value").asLong()
            else result.outputVariables["documentId"] = answer.path("_id").asText()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("hitCount" to "Number of hits", "documentId" to "Document ID")

    override val description: String
        get() = "Adds, searches or deletes documents in Elasticsearch"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForSearch = mapOf("operation" to arrayOf(SEARCH))
            val showForAddDocumentDeleteDocument = mapOf("operation" to arrayOf(ADD_DOCUMENT, DELETE_DOCUMENT))
            val showForAddDocument = mapOf("operation" to arrayOf(ADD_DOCUMENT))
            actionInfo.add(PropertyInformation("apiKey", "API key", PropertyInformationType.STRING, "", "Leave empty when the cluster has no sign-in.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, SEARCH, "", listOf(
                PropertyInformation(SEARCH, "Search"),
                PropertyInformation(ADD_DOCUMENT, "Add a document"),
                PropertyInformation(DELETE_DOCUMENT, "Delete a document")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "Cluster address", PropertyInformationType.STRING, "", "Such as https://search.example.com:9200."))
            actionInfo.add(PropertyInformation("indexName", "Index", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("query", "Query", PropertyInformationType.MULTILINE, "", "As JSON, such as {\"match\": {\"status\": \"open\"}}. Empty finds everything.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSearch))
            actionInfo.add(PropertyInformation("documentId", "Document ID", PropertyInformationType.STRING, "", "Leave empty to let Elasticsearch choose one.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddDocumentDeleteDocument))
            actionInfo.add(PropertyInformation("document", "Document", PropertyInformationType.MULTILINE, "", "As JSON.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddDocument))
            return actionInfo
        }

    private companion object {
        const val SEARCH = "search"
        const val ADD_DOCUMENT = "addDocument"
        const val DELETE_DOCUMENT = "deleteDocument"
    }
}
