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
import java.io.IOException
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient

@Entity
@Table(name = "NotionAction")
@DiscriminatorValue("notion_action")
class NotionAction : Action() {
    @Column
    var baseUrl: String? = "https://api.notion.com"

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "createPage"

    @Column
    var page: String? = null

    @Column(length = 1024)
    var title: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var content: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var addedText: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val tokenValue = read(token)
        val operationValue = read(operation)
        val pageText = read(page).trim()
        val titleValue = read(title)
        val addedValue = read(addedText)
        // A person copies the address of a page, which ends with its id.
        val pageId = PAGE_ID.findAll(pageText.substringBefore('?').replace("-", "")).lastOrNull()?.value
        if (tokenValue.isBlank()) return ActionResult.failure("Notion needs a token, from an integration that the page is shared with.")
        if (pageId == null) return ActionResult.failure("Notion needs the address or the id of a page, and got \"$pageText\".")
        if (operationValue == CREATE_PAGE && titleValue.isBlank()) return ActionResult.failure("Notion needs a title for the page.")
        if (operationValue == ADD_TEXT && addedValue.isBlank()) return ActionResult.failure("Notion needs the text to add.")

        val base = read(baseUrl).trimEnd('/')
        val headers = mapOf("Authorization" to "Bearer $tokenValue", "Notion-Version" to API_VERSION)
        val result = when (operationValue) {
            CREATE_PAGE -> JsonApi.call("Notion", "POST", "$base/v1/pages", headers, mapOf(
                "parent" to mapOf("page_id" to pageId),
                "properties" to mapOf("title" to mapOf("title" to richText(titleValue))),
                "children" to paragraphs(read(content))
            ))
            ADD_TEXT -> JsonApi.call("Notion", "PATCH", "$base/v1/blocks/$pageId/children", headers, mapOf("children" to paragraphs(addedValue)))
            else -> ActionResult.failure("Notion has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            result.outputVariables["pageId"] = if (operationValue == CREATE_PAGE) answer.path("id").asText() else pageId
            if (operationValue == CREATE_PAGE) result.outputVariables["url"] = answer.path("url").asText()
        }
        return result
    }

    private fun richText(text: String) = listOf(mapOf("type" to "text", "text" to mapOf("content" to text)))

    // One paragraph for each line, and Notion allows 2000 characters in each piece of text.
    private fun paragraphs(text: String) = text.lines().filter { it.isNotBlank() }.flatMap { it.chunked(MAX_TEXT_LENGTH) }.map { piece ->
        mapOf("object" to "block", "type" to "paragraph", "paragraph" to mapOf("rich_text" to richText(piece)))
    }

    override val outputs: Map<String, String>
        get() = mapOf("pageId" to "Page id", "url" to "Address")

    override val description: String
        get() = "Creates a Notion page, or adds text to one"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("token", "Token", PropertyInformationType.STRING, "", "From an integration in Notion. The page has to be shared with it.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_PAGE, "", listOf(
                PropertyInformation(CREATE_PAGE, "Create a page"),
                PropertyInformation(ADD_TEXT, "Add text to a page")
            )))
            actionInfo.add(PropertyInformation("page", "Page", PropertyInformationType.STRING, "", "The address or the id. A new page is placed under this one."))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(CREATE_PAGE))))
            actionInfo.add(PropertyInformation("content", "Text", PropertyInformationType.MULTILINE, "", "Each line becomes a paragraph.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(CREATE_PAGE))))
            actionInfo.add(PropertyInformation("addedText", "Text to add", PropertyInformationType.MULTILINE, "", "Each line becomes a paragraph.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(ADD_TEXT))))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.notion.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val CREATE_PAGE = "createPage"
        const val ADD_TEXT = "addText"
        const val API_VERSION = "2022-06-28"
        const val MAX_TEXT_LENGTH = 2000
        val PAGE_ID = Regex("[0-9a-fA-F]{32}")
    }
}
