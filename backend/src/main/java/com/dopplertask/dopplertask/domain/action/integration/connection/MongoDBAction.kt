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
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.MongoException
import com.mongodb.client.MongoClients
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.bson.Document
import org.bson.json.JsonParseException

@Entity
@Table(name = "MongoDBAction")
@DiscriminatorValue("mongodb_action")
class MongoDBAction : Action() {
    @Transient
    var connectionString: String? = null

    @Column
    var database: String? = null

    @Column
    var collection: String? = null

    @Column
    var operation: String? = "findDocuments"

    @Lob
    @Column(columnDefinition = "TEXT")
    var filter: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var document: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var changes: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val connectionValue = read(connectionString).trim()
        val databaseValue = read(database).trim()
        val collectionValue = read(collection).trim()
        val operationValue = read(operation)
        val filterValue = read(filter).trim()
        val documentValue = read(document).trim()
        val changesValue = read(changes).trim()
        if (connectionValue.isBlank()) return ActionResult.failure("MongoDB needs a connection string, such as mongodb+srv://user:password@cluster.example.net.")
        if (databaseValue.isBlank() || collectionValue.isBlank()) return ActionResult.failure("MongoDB needs the names of the database and the collection.")
        if (operationValue == ADD_DOCUMENT && documentValue.isBlank()) return ActionResult.failure("MongoDB needs the document as JSON.")
        if (operationValue == UPDATE_DOCUMENTS && changesValue.isBlank()) return ActionResult.failure("MongoDB needs the changes as JSON, such as {\"\$set\": {\"status\": \"done\"}}.")
        // An empty filter means every document, which should be written out when it is really meant.
        if ((operationValue == UPDATE_DOCUMENTS || operationValue == DELETE_DOCUMENTS) && filterValue.isBlank()) return ActionResult.failure("MongoDB needs a filter to know which documents to change. Write {} to mean all of them.")

        return try {
            val settings = MongoClientSettings.builder().applyConnectionString(ConnectionString(connectionValue))
                .applyToClusterSettings { it.serverSelectionTimeout(SECONDS_TO_CONNECT, TimeUnit.SECONDS) }.build()
            MongoClients.create(settings).use { client ->
                val documents = client.getDatabase(databaseValue).getCollection(collectionValue)
                val wanted = Document.parse(filterValue.ifEmpty { "{}" })
                val result = ActionResult()
                result.statusCode = StatusCode.SUCCESS
                when (operationValue) {
                    FIND_DOCUMENTS -> {
                        val found = documents.find(wanted).limit(DOCUMENTS_READ).map { JsonApi.mapper.readTree(it.toJson()) }.toList()
                        result.output = JsonApi.mapper.writeValueAsString(found)
                        result.outputVariables["documentCount"] = found.size
                    }
                    ADD_DOCUMENT -> {
                        val added = documents.insertOne(Document.parse(documentValue))
                        result.output = "Added a document to $collectionValue."
                        result.outputVariables["documentCount"] = 1
                        result.outputVariables["documentId"] = added.insertedId?.asObjectId()?.value?.toHexString() ?: ""
                    }
                    UPDATE_DOCUMENTS -> {
                        val updated = documents.updateMany(wanted, Document.parse(changesValue))
                        result.output = "Updated ${updated.modifiedCount} of ${updated.matchedCount} documents that matched."
                        result.outputVariables["documentCount"] = updated.modifiedCount
                    }
                    DELETE_DOCUMENTS -> {
                        val deleted = documents.deleteMany(wanted)
                        result.output = "Deleted ${deleted.deletedCount} documents."
                        result.outputVariables["documentCount"] = deleted.deletedCount
                    }
                    else -> return ActionResult.failure("MongoDB has no operation called \"$operationValue\".")
                }
                result
            }
        } catch (e: MongoException) {
            ActionResult.failure("MongoDB answered: ${e.message}")
        } catch (e: JsonParseException) {
            ActionResult.failure("MongoDB could not read the JSON: ${e.message}")
        } catch (e: IllegalArgumentException) {
            ActionResult.failure("MongoDB could not use this: ${e.message}")
        }
    }

    override val outputs: Map<String, String>
        get() = mapOf("documentCount" to "Number of documents", "documentId" to "Document id")

    override val description: String
        get() = "Finds, adds, updates or deletes MongoDB documents"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForFindDocumentsUpdateDocumentsDeleteDocuments = mapOf("operation" to arrayOf(FIND_DOCUMENTS, UPDATE_DOCUMENTS, DELETE_DOCUMENTS))
            val showForAddDocument = mapOf("operation" to arrayOf(ADD_DOCUMENT))
            val showForUpdateDocuments = mapOf("operation" to arrayOf(UPDATE_DOCUMENTS))
            actionInfo.add(PropertyInformation("connectionString", "Connection string", PropertyInformationType.STRING, "", "Such as mongodb+srv://user:password@cluster.example.net.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, FIND_DOCUMENTS, "", listOf(
                PropertyInformation(FIND_DOCUMENTS, "Find documents"),
                PropertyInformation(ADD_DOCUMENT, "Add a document"),
                PropertyInformation(UPDATE_DOCUMENTS, "Update documents"),
                PropertyInformation(DELETE_DOCUMENTS, "Delete documents")
            )))
            actionInfo.add(PropertyInformation("database", "Database", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("collection", "Collection", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("filter", "Filter", PropertyInformationType.MULTILINE, "", "Which documents, as JSON, such as {\"status\": \"open\"}.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForFindDocumentsUpdateDocumentsDeleteDocuments))
            actionInfo.add(PropertyInformation("document", "Document", PropertyInformationType.MULTILINE, "", "As JSON.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddDocument))
            actionInfo.add(PropertyInformation("changes", "Changes", PropertyInformationType.MULTILINE, "", "As JSON, such as {\"\$set\": {\"status\": \"done\"}}.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForUpdateDocuments))
            return actionInfo
        }

    private companion object {
        const val FIND_DOCUMENTS = "findDocuments"
        const val ADD_DOCUMENT = "addDocument"
        const val UPDATE_DOCUMENTS = "updateDocuments"
        const val DELETE_DOCUMENTS = "deleteDocuments"
        const val SECONDS_TO_CONNECT = 10L
        const val DOCUMENTS_READ = 100
    }
}
