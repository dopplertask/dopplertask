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
@Table(name = "OneDriveAction")
@DiscriminatorValue("onedrive_action")
class OneDriveAction : Action() {
    @Transient
    var tenantId: String? = null

    @Transient
    var clientId: String? = null

    @Transient
    var clientSecret: String? = null

    @Column
    var baseUrl: String? = "https://graph.microsoft.com"

    @Column
    var signInUrl: String? = "https://login.microsoftonline.com"

    @Column
    var operation: String? = "findFiles"

    @Column
    var person: String? = null

    @Column
    var searchText: String? = null

    @Column
    var fileId: String? = null

    @Column
    var filePath: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var fileContent: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val personValue = read(person).trim()
        val operationValue = read(operation)
        val searchValue = read(searchText).trim()
        val fileValue = read(fileId).trim()
        val pathValue = read(filePath).trim().trim('/')
        if (personValue.isBlank()) return ActionResult.failure("OneDrive needs the email of the person whose files these are.")
        if (operationValue == FIND_FILES && searchValue.isBlank()) return ActionResult.failure("OneDrive needs something to search for.")
        if (operationValue == READ_TEXT_FILE && fileValue.isBlank()) return ActionResult.failure("OneDrive needs the ID of the file.")
        if (operationValue == SAVE_TEXT_FILE && pathValue.isBlank()) return ActionResult.failure("OneDrive needs a path, such as reports/week-45.txt.")

        val signIn = MicrosoftAccount.signIn("OneDrive", JsonApi.baseAddress(read(signInUrl)), read(tenantId).trim(), read(clientId).trim(), read(clientSecret))
        signIn.failure?.let { return it }
        val api = JsonApi.baseAddress(read(baseUrl)) + "/v1.0"
        // The API addresses files through a drive, while a person knows whose files they are.
        val drive = JsonApi.call("OneDrive", "GET", "$api/users/${JsonApi.encoded(personValue)}/drive", signIn.headers, null)
        if (drive.statusCode == StatusCode.FAILURE) return drive
        val items = "$api/drives/${JsonApi.encoded(JsonApi.parsed(drive).path("id").asText())}/items"
        val result = when (operationValue) {
            FIND_FILES -> JsonApi.call("OneDrive", "GET", "$items/root/search(q='${JsonApi.encoded(searchValue.replace("'", "''"))}')", signIn.headers, null)
            READ_TEXT_FILE -> JsonApi.call("OneDrive", "GET", "$items/${JsonApi.encoded(fileValue)}/content", signIn.headers, null)
            SAVE_TEXT_FILE -> JsonApi.callText("OneDrive", "PUT", "$items/root:/${pathValue.split("/").joinToString("/") { JsonApi.encoded(it) }}:/content", signIn.headers, read(fileContent), "text/plain")
            else -> return ActionResult.failure("OneDrive has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            when (operationValue) {
                FIND_FILES -> result.outputVariables["fileCount"] = answer.path("value").size()
                SAVE_TEXT_FILE -> {
                    result.outputVariables["fileId"] = answer.path("id").asText()
                    result.outputVariables["url"] = answer.path("webUrl").asText()
                }
            }
        }
        return result
    }

    override val credentialType: String
        get() = MICROSOFT_ACCOUNT

    override val outputs: Map<String, String>
        get() = mapOf("fileCount" to "Number of files", "fileId" to "File", "url" to "Address")

    override val description: String
        get() = "Finds, reads or saves text files in the OneDrive of a person"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForFindFiles = mapOf("operation" to arrayOf(FIND_FILES))
            val showForReadTextFile = mapOf("operation" to arrayOf(READ_TEXT_FILE))
            val showForSaveTextFile = mapOf("operation" to arrayOf(SAVE_TEXT_FILE))
            actionInfo.add(PropertyInformation("tenantId", "Directory (tenant) ID", PropertyInformationType.STRING, "", "On the overview page of the app registration.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("clientId", "Application (client) ID", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("clientSecret", "Client secret", PropertyInformationType.STRING, "", "Under Certificates and secrets.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, FIND_FILES, "", listOf(
                PropertyInformation(FIND_FILES, "Find files"),
                PropertyInformation(READ_TEXT_FILE, "Read a text file"),
                PropertyInformation(SAVE_TEXT_FILE, "Save a text file")
            )))
            actionInfo.add(PropertyInformation("person", "Person", PropertyInformationType.STRING, "", "The email of the person whose OneDrive it is."))
            actionInfo.add(PropertyInformation("searchText", "Search for", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForFindFiles))
            actionInfo.add(PropertyInformation("fileId", "File ID", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadTextFile))
            actionInfo.add(PropertyInformation("filePath", "Path", PropertyInformationType.STRING, "", "Such as reports/week-45.txt. A file that is there already is replaced.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSaveTextFile))
            actionInfo.add(PropertyInformation("fileContent", "Content", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSaveTextFile))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://graph.microsoft.com", "Only change this to go through a proxy."))
            actionInfo.add(PropertyInformation("signInUrl", "Sign-in URL", PropertyInformationType.STRING, "https://login.microsoftonline.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val FIND_FILES = "findFiles"
        const val READ_TEXT_FILE = "readTextFile"
        const val SAVE_TEXT_FILE = "saveTextFile"
    }
}
