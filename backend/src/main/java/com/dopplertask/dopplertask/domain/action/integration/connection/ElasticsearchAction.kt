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
import java.util.Base64

@Entity
@Table(name = "ElasticsearchAction")
@DiscriminatorValue("elasticsearch_action")
class ElasticsearchAction : Action() {
    // The address is part of the credential, so that a step can only send a key to the cluster the key belongs to.
    @Transient
    var siteUrl: String? = null

    @Transient
    var apiKey: String? = null

    @Transient
    var username: String? = null

    @Transient
    var password: String? = null

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

    @Column
    var sortField: String? = null

    @Column
    var sortOrder: String? = NEWEST_FIRST

    @Column
    var maxResults: String? = "10"

    @Column
    var groupByField: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = JsonApi.baseAddress(read(siteUrl))
        val indexValue = read(indexName).trim()
        val operationValue = read(operation)
        val idValue = read(documentId).trim()
        val documentValue = read(document).trim()
        val queryValue = read(query).trim()
        val limitText = read(maxResults).trim().ifEmpty { "10" }
        val limit = limitText.toIntOrNull()
        val sortFieldValue = read(sortField).trim()
        val groupFieldValue = read(groupByField).trim()
        if (site.isBlank()) return ActionResult.failure("Elasticsearch needs the address of the cluster in its credential, such as https://search.example.com:9200.")
        if (operationValue != LIST_INDEXES && indexValue.isBlank()) return ActionResult.failure("Elasticsearch needs the name of the index.")
        if (operationValue in NEEDS_DOCUMENT_ID && idValue.isBlank()) return ActionResult.failure("Elasticsearch needs the ID of the document.")
        if (operationValue == SEARCH && (limit == null || limit !in 0..LARGEST_RESULT)) return ActionResult.failure("Elasticsearch needs the most results as a number from 0 to $LARGEST_RESULT, and got \"$limitText\".")
        val documentFields = if (operationValue == ADD_DOCUMENT || operationValue == UPDATE_DOCUMENT) JsonApi.objectFrom(documentValue) ?: return ActionResult.failure("Elasticsearch needs the document as a JSON object.") else null
        val queryFields = if (operationValue == SEARCH && queryValue.isNotEmpty()) JsonApi.objectFrom(queryValue) ?: return ActionResult.failure("Elasticsearch needs the query as a JSON object.") else null

        val headers = signIn(read(apiKey).trim(), read(username).trim(), read(password))
        val index = "$site/${JsonApi.encoded(indexValue)}"
        val named = "$index/_doc/${JsonApi.encoded(idValue)}"
        val result = when (operationValue) {
            SEARCH -> {
                val search = mutableMapOf<String, Any>("query" to (queryFields ?: mapOf("match_all" to mapOf<String, String>())), "size" to limit!!, "track_total_hits" to true)
                if (sortFieldValue.isNotEmpty()) search["sort"] = listOf(mapOf(sortFieldValue to mapOf("order" to read(sortOrder).ifEmpty { NEWEST_FIRST })))
                if (groupFieldValue.isNotEmpty()) search["aggs"] = mapOf(GROUPS to mapOf("terms" to mapOf("field" to groupFieldValue, "size" to GROUPS_READ)))
                JsonApi.call("Elasticsearch", "POST", "$index/_search", headers, search)
            }
            READ_DOCUMENT -> JsonApi.call("Elasticsearch", "GET", named, headers, null)
            ADD_DOCUMENT -> if (idValue.isEmpty()) JsonApi.call("Elasticsearch", "POST", "$index/_doc", headers, documentFields) else JsonApi.call("Elasticsearch", "PUT", named, headers, documentFields)
            UPDATE_DOCUMENT -> JsonApi.call("Elasticsearch", "POST", "$index/_update/${JsonApi.encoded(idValue)}", headers, mapOf("doc" to documentFields))
            DELETE_DOCUMENT -> JsonApi.call("Elasticsearch", "DELETE", named, headers, null)
            LIST_INDEXES -> JsonApi.call("Elasticsearch", "GET", "$site/_cat/indices?format=json&h=index,health,docs.count,store.size&s=index", headers, null)
            else -> return ActionResult.failure("Elasticsearch has no operation called \"$operationValue\".")
        }
        // It answers 404 without a sentence when a document is missing.
        if (result.statusCode == StatusCode.FAILURE && !JsonApi.parsed(result).path("found").asBoolean(true)) result.errorMsg = "Elasticsearch has no document \"$idValue\" in $indexValue."
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            when (operationValue) {
                SEARCH -> {
                    result.outputVariables["hitCount"] = answer.path("hits").path("total").path("value").asLong()
                    if (groupFieldValue.isNotEmpty()) result.outputVariables["groups"] = answer.path("aggregations").path(GROUPS).path("buckets").joinToString(", ") { "${it.path("key").asText()}: ${it.path("doc_count").asLong()}" }
                }
                LIST_INDEXES -> result.outputVariables["indexCount"] = answer.size()
                else -> result.outputVariables["documentId"] = answer.path("_id").asText()
            }
        }
        return result
    }

    private fun signIn(apiKeyValue: String, usernameValue: String, passwordValue: String): Map<String, String> = when {
        apiKeyValue.isNotEmpty() -> mapOf("Authorization" to "ApiKey $apiKeyValue")
        usernameValue.isNotEmpty() -> mapOf("Authorization" to "Basic " + Base64.getEncoder().encodeToString("$usernameValue:$passwordValue".toByteArray()))
        else -> mapOf()
    }

    override val outputs: Map<String, String>
        get() = mapOf("hitCount" to "Number of hits", "groups" to "Hits per group", "documentId" to "Document ID", "indexCount" to "Number of indexes")

    override val description: String
        get() = "Searches, reads, adds, updates or deletes documents in Elasticsearch, or lists its indexes"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForSearch = mapOf("operation" to arrayOf(SEARCH))
            val showForIndex = mapOf("operation" to arrayOf(SEARCH, READ_DOCUMENT, ADD_DOCUMENT, UPDATE_DOCUMENT, DELETE_DOCUMENT))
            val showForDocumentId = mapOf("operation" to arrayOf(READ_DOCUMENT, ADD_DOCUMENT, UPDATE_DOCUMENT, DELETE_DOCUMENT))
            val showForDocument = mapOf("operation" to arrayOf(ADD_DOCUMENT, UPDATE_DOCUMENT))
            actionInfo.add(PropertyInformation("siteUrl", "Cluster address", PropertyInformationType.STRING, "", "Such as https://search.example.com:9200.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("apiKey", "API key", PropertyInformationType.STRING, "", "Use an API key, or a username and password. Leave them empty when the cluster has no sign-in.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("username", "Username", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("password", "Password", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, SEARCH, "", listOf(
                PropertyInformation(SEARCH, "Search"),
                PropertyInformation(READ_DOCUMENT, "Read a document"),
                PropertyInformation(ADD_DOCUMENT, "Add a document"),
                PropertyInformation(UPDATE_DOCUMENT, "Update a document"),
                PropertyInformation(DELETE_DOCUMENT, "Delete a document"),
                PropertyInformation(LIST_INDEXES, "List indexes")
            )))
            actionInfo.add(PropertyInformation("indexName", "Index", PropertyInformationType.STRING, "", "Such as orders, or logs-* for several.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForIndex))
            actionInfo.add(PropertyInformation("query", "Query", PropertyInformationType.MULTILINE, "", "As JSON, such as {\"match\": {\"status\": \"open\"}}. Empty finds everything.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSearch))
            actionInfo.add(PropertyInformation("sortField", "Sort by", PropertyInformationType.STRING, "", "A field, such as timestamp.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSearch))
            actionInfo.add(PropertyInformation("sortOrder", "Order", PropertyInformationType.DROPDOWN, NEWEST_FIRST, "", listOf(
                PropertyInformation(NEWEST_FIRST, "Newest or largest first"),
                PropertyInformation(OLDEST_FIRST, "Oldest or smallest first")
            ), PropertyInformationCategory.PROPERTY, showForSearch))
            actionInfo.add(PropertyInformation("maxResults", "Most results", PropertyInformationType.STRING, "10", "The number of hits still counts them all.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSearch))
            actionInfo.add(PropertyInformation("groupByField", "Count per", PropertyInformationType.STRING, "", "A keyword field, such as service. The next step reads the counts as groups.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSearch))
            actionInfo.add(PropertyInformation("documentId", "Document ID", PropertyInformationType.STRING, "", "When adding, leave it empty to let Elasticsearch choose one.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForDocumentId))
            actionInfo.add(PropertyInformation("document", "Document", PropertyInformationType.MULTILINE, "", "As JSON. When updating, only the fields to change.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForDocument))
            return actionInfo
        }

    private companion object {
        const val SEARCH = "search"
        const val READ_DOCUMENT = "readDocument"
        const val ADD_DOCUMENT = "addDocument"
        const val UPDATE_DOCUMENT = "updateDocument"
        const val DELETE_DOCUMENT = "deleteDocument"
        const val LIST_INDEXES = "listIndexes"
        const val NEWEST_FIRST = "desc"
        const val OLDEST_FIRST = "asc"
        const val GROUPS = "groups"
        const val GROUPS_READ = 20
        const val LARGEST_RESULT = 10000
        val NEEDS_DOCUMENT_ID = setOf(READ_DOCUMENT, UPDATE_DOCUMENT, DELETE_DOCUMENT)
    }
}
