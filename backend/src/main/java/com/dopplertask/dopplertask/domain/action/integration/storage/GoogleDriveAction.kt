package com.dopplertask.dopplertask.domain.action.integration.storage

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.domain.action.integration.OAuthSignIn
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
@Table(name = "GoogleDriveAction")
@DiscriminatorValue("google_drive_action")
class GoogleDriveAction : Action() {
    @Column
    var baseUrl: String? = "https://www.googleapis.com"

    @Transient
    var serviceAccountKey: String? = null

    @Column
    var operation: String? = "findFiles"

    @Column
    var fileName: String? = null

    @Column
    var fileId: String? = null

    @Column
    var folderId: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var fileContent: String? = null

    @Column
    var email: String? = null

    @Column
    var role: String? = "reader"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val keyText = read(serviceAccountKey)
        val operationValue = read(operation)
        val nameValue = read(fileName).trim()
        val fileValue = read(fileId).trim()
        val folderValue = read(folderId).trim()
        val emailValue = read(email).trim()
        if (keyText.isBlank() && !OAuthSignIn.isUsedBy(this)) return ActionResult.failure("Google Drive needs the key file of a service account, or a connected Google account.")
        if (operationValue == CREATE_TEXT_FILE && (nameValue.isBlank() || folderValue.isBlank())) return ActionResult.failure("Google Drive needs a name for the file and a folder in a shared drive.")
        if ((operationValue == READ_TEXT_FILE || operationValue == SHARE_FILE) && fileValue.isBlank()) return ActionResult.failure("Google Drive needs the file, which is the long code in its address.")
        if (operationValue == SHARE_FILE && emailValue.isBlank()) return ActionResult.failure("Google Drive needs the email to share with.")

        val signIn = GoogleAccount.signIn("Google Drive", this, keyText, SCOPE)
        if (signIn.failure != null) return signIn.failure
        val base = read(baseUrl).trim().trimEnd('/')
        val files = "$base/drive/v3/files"
        val file = "$files/${JsonApi.encoded(fileValue)}"
        return when (operationValue) {
            FIND_FILES -> {
                val wanted = "trashed = false" + if (nameValue.isEmpty()) "" else " and name contains '${nameValue.replace("\\", "\\\\").replace("'", "\\'")}'"
                val fields = JsonApi.encoded("files(id,name,webViewLink)")
                val result = JsonApi.call("Google Drive", "GET", "$files?q=${JsonApi.encoded(wanted)}&fields=$fields&$ALL_DRIVES&includeItemsFromAllDrives=true", signIn.headers, null)
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["fileCount"] = JsonApi.parsed(result).path("files").size()
                result
            }
            READ_TEXT_FILE -> JsonApi.call("Google Drive", "GET", "$file?alt=media&$ALL_DRIVES", signIn.headers, null)
            CREATE_TEXT_FILE -> {
                val description = JsonApi.mapper.writeValueAsString(mapOf("name" to nameValue, "parents" to listOf(folderValue)))
                val body = "--$PART_BOUNDARY\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$description\r\n--$PART_BOUNDARY\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n${read(fileContent)}\r\n--$PART_BOUNDARY--"
                val result = JsonApi.callText("Google Drive", "POST", "$base/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink&$ALL_DRIVES", signIn.headers, body, "multipart/related; boundary=$PART_BOUNDARY")
                if (result.statusCode == StatusCode.SUCCESS) {
                    result.outputVariables["fileId"] = JsonApi.parsed(result).path("id").asText()
                    result.outputVariables["url"] = JsonApi.parsed(result).path("webViewLink").asText()
                }
                result
            }
            SHARE_FILE -> JsonApi.call("Google Drive", "POST", "$file/permissions?$ALL_DRIVES", signIn.headers, mapOf("type" to "user", "role" to read(role).trim().ifEmpty { "reader" }, "emailAddress" to emailValue))
            else -> ActionResult.failure("Google Drive has no operation called \"$operationValue\".")
        }
    }

    override val credentialType: String
        get() = GOOGLE_SERVICE_ACCOUNT

    override val outputs: Map<String, String>
        get() = mapOf("fileCount" to "Number of files", "fileId" to "File", "url" to "Address")

    override val description: String
        get() = "Finds, reads, creates or shares a Google Drive file"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForFindFilesCreateTextFile = mapOf("operation" to arrayOf(FIND_FILES, CREATE_TEXT_FILE))
            val showForReadTextFileShareFile = mapOf("operation" to arrayOf(READ_TEXT_FILE, SHARE_FILE))
            val showForCreateTextFile = mapOf("operation" to arrayOf(CREATE_TEXT_FILE))
            val showForShareFile = mapOf("operation" to arrayOf(SHARE_FILE))
            actionInfo.add(PropertyInformation("serviceAccountKey", "Key file of the service account", PropertyInformationType.MULTILINE, "", "The JSON file. Share a folder or a shared drive with the email of the account.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.addAll(GoogleAccount.accountFields())
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, FIND_FILES, "", listOf(
                PropertyInformation(FIND_FILES, "Find files"),
                PropertyInformation(READ_TEXT_FILE, "Read a text file"),
                PropertyInformation(CREATE_TEXT_FILE, "Create a text file"),
                PropertyInformation(SHARE_FILE, "Share a file")
            )))
            actionInfo.add(PropertyInformation("fileName", "File name", PropertyInformationType.STRING, "", "For finding, a part of the name is enough.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForFindFilesCreateTextFile))
            actionInfo.add(PropertyInformation("fileId", "File", PropertyInformationType.STRING, "", "The long code in the address of the file. Reading is for text files, and not Google Docs.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadTextFileShareFile))
            actionInfo.add(PropertyInformation("folderId", "Folder", PropertyInformationType.STRING, "", "The long code in the address of a folder in a shared drive, as a service account has no storage of its own.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateTextFile))
            actionInfo.add(PropertyInformation("fileContent", "Content", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateTextFile))
            actionInfo.add(PropertyInformation("email", "Share with", PropertyInformationType.STRING, "", "An email address.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForShareFile))
            actionInfo.add(PropertyInformation("role", "Access", PropertyInformationType.STRING, "reader", "reader, commenter or writer.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForShareFile))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://www.googleapis.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val FIND_FILES = "findFiles"
        const val READ_TEXT_FILE = "readTextFile"
        const val CREATE_TEXT_FILE = "createTextFile"
        const val SHARE_FILE = "shareFile"
        const val SCOPE = "https://www.googleapis.com/auth/drive"
        const val ALL_DRIVES = "supportsAllDrives=true"
        const val PART_BOUNDARY = "dopplertask-file-part"
    }
}
