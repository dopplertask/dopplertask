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
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException

@Entity
@Table(name = "BoxAction")
@DiscriminatorValue("box_action")
class BoxAction : Action() {
    override val runsInPreview get() = true

    @Column
    var baseUrl: String? = "https://api.box.com"

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "findFiles"

    @Column
    var searchText: String? = null

    @Column
    var fileId: String? = null

    @Column
    var folderName: String? = null

    @Column
    var parentFolderId: String? = "0"

    @Column
    var sharedAccess: String? = "company"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val tokenValue = read(token).trim()
        val operationValue = read(operation)
        val searchValue = read(searchText).trim()
        val fileValue = read(fileId).trim()
        val folderValue = read(folderName).trim()
        if (tokenValue.isBlank()) return ActionResult.failure("Box needs an access token.")
        if (operationValue == FIND_FILES && searchValue.isBlank()) return ActionResult.failure("Box needs something to search for.")
        if ((operationValue == READ_TEXT_FILE || operationValue == SHARE_FILE) && fileValue.isBlank()) return ActionResult.failure("Box needs the ID of the file.")
        if (operationValue == CREATE_FOLDER && folderValue.isBlank()) return ActionResult.failure("Box needs a name for the folder.")

        val headers = mapOf("Authorization" to "Bearer $tokenValue")
        val api = JsonApi.baseAddress(read(baseUrl)) + "/2.0"
        val file = "$api/files/${JsonApi.encoded(fileValue)}"
        val result = when (operationValue) {
            FIND_FILES -> JsonApi.call("Box", "GET", "$api/search?type=file&limit=$FILES_READ&query=${JsonApi.encoded(searchValue)}", headers, null)
            READ_TEXT_FILE -> JsonApi.call("Box", "GET", "$file/content", headers, null)
            CREATE_FOLDER -> JsonApi.call("Box", "POST", "$api/folders", headers, mapOf("name" to folderValue, "parent" to mapOf("id" to read(parentFolderId).trim().ifEmpty { "0" })))
            SHARE_FILE -> JsonApi.call("Box", "PUT", "$file?fields=shared_link", headers, mapOf("shared_link" to mapOf("access" to read(sharedAccess).trim().ifEmpty { "company" })))
            else -> return ActionResult.failure("Box has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            when (operationValue) {
                FIND_FILES -> result.outputVariables["fileCount"] = answer.path("entries").size()
                CREATE_FOLDER -> result.outputVariables["folderId"] = answer.path("id").asText()
                SHARE_FILE -> result.outputVariables["url"] = answer.path("shared_link").path("url").asText()
            }
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("fileCount" to "Number of files", "folderId" to "Folder", "url" to "Shared link")

    override val description: String
        get() = "Finds and reads Box files, creates folders or shares a file"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForFindFiles = mapOf("operation" to arrayOf(FIND_FILES))
            val showForReadTextFileShareFile = mapOf("operation" to arrayOf(READ_TEXT_FILE, SHARE_FILE))
            val showForCreateFolder = mapOf("operation" to arrayOf(CREATE_FOLDER))
            val showForShareFile = mapOf("operation" to arrayOf(SHARE_FILE))
            actionInfo.add(PropertyInformation("token", "Access token", PropertyInformationType.STRING, "", "From an app in the Box Developer Console.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, FIND_FILES, "", listOf(
                PropertyInformation(FIND_FILES, "Find files"),
                PropertyInformation(READ_TEXT_FILE, "Read a text file"),
                PropertyInformation(CREATE_FOLDER, "Create a folder"),
                PropertyInformation(SHARE_FILE, "Share a file")
            )))
            actionInfo.add(PropertyInformation("searchText", "Search for", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForFindFiles))
            actionInfo.add(PropertyInformation("fileId", "File ID", PropertyInformationType.STRING, "", "The number in the address of the file.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadTextFileShareFile))
            actionInfo.add(PropertyInformation("folderName", "Folder name", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateFolder))
            actionInfo.add(PropertyInformation("parentFolderId", "Inside folder ID", PropertyInformationType.STRING, "0", "0 is the top folder.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateFolder))
            actionInfo.add(PropertyInformation("sharedAccess", "Who can open the link", PropertyInformationType.STRING, "company", "open, company or collaborators.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForShareFile))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.box.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val FIND_FILES = "findFiles"
        const val READ_TEXT_FILE = "readTextFile"
        const val CREATE_FOLDER = "createFolder"
        const val SHARE_FILE = "shareFile"
        const val FILES_READ = 50
    }
}
