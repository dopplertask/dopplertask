package com.dopplertask.dopplertask.domain.action.integration.storage

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.domain.action.integration.code.ATLASSIAN_ACCOUNT
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
@Table(name = "ConfluenceAction")
@DiscriminatorValue("confluence_action")
class ConfluenceAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var email: String? = null

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "createPage"

    @Column
    var spaceKey: String? = null

    @Column
    var pageId: String? = null

    @Column
    var parentPageId: String? = null

    @Column(length = 1024)
    var title: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var pageContent: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = read(siteUrl).trim().trimEnd('/')
        val emailValue = read(email).trim()
        val tokenValue = read(token)
        val operationValue = read(operation)
        val spaceValue = read(spaceKey).trim()
        val pageValue = read(pageId).trim()
        val titleValue = read(title).trim()
        val contentValue = read(pageContent)
        if (site.isBlank()) return ActionResult.failure("Confluence needs the address of the site, such as https://acme.atlassian.net.")
        if (emailValue.isBlank() || tokenValue.isBlank()) return ActionResult.failure("Confluence needs the email of an Atlassian account and an API token.")
        if (operationValue == CREATE_PAGE && (spaceValue.isBlank() || titleValue.isBlank())) return ActionResult.failure("Confluence needs the key of the space and a title.")
        if (operationValue != CREATE_PAGE && pageValue.isBlank()) return ActionResult.failure("Confluence needs the page, which is the number in its address.")
        if (operationValue != READ_PAGE && contentValue.isBlank()) return ActionResult.failure("Confluence needs the content of the page.")

        val headers = mapOf("Authorization" to "Basic " + Base64.getEncoder().encodeToString("$emailValue:$tokenValue".toByteArray()))
        val api = "$site/wiki/api/v2"
        val page = "$api/pages/${JsonApi.encoded(pageValue)}"
        fun storage() = mapOf("representation" to "storage", "value" to contentValue)
        fun withAddress(result: ActionResult, created: Boolean): ActionResult {
            if (result.statusCode == StatusCode.SUCCESS) {
                val answer = JsonApi.parsed(result)
                result.outputVariables["pageId"] = if (created) answer.path("id").asText() else pageValue
                result.outputVariables["url"] = site + "/wiki" + answer.path("_links").path("webui").asText()
            }
            return result
        }
        return when (operationValue) {
            CREATE_PAGE -> {
                // The API wants the number of the space, while a person knows its short key.
                val spaces = JsonApi.call("Confluence", "GET", "$api/spaces?keys=${JsonApi.encoded(spaceValue)}", headers, null)
                if (spaces.statusCode == StatusCode.FAILURE) return spaces
                val spaceId = JsonApi.parsed(spaces).path("results").path(0).path("id").asText("")
                if (spaceId.isEmpty()) return ActionResult.failure("Confluence has no space with the key \"$spaceValue\".")
                val created = mutableMapOf("spaceId" to spaceId, "status" to "current", "title" to titleValue, "body" to storage())
                read(parentPageId).trim().takeIf { it.isNotEmpty() }?.let { created["parentId"] = it }
                withAddress(JsonApi.call("Confluence", "POST", "$api/pages", headers, created), true)
            }
            READ_PAGE -> withAddress(JsonApi.call("Confluence", "GET", "$page?body-format=storage", headers, null), false)
            UPDATE_PAGE -> {
                // Confluence refuses an update unless it is told the next version, which keeps the history of the page.
                val current = JsonApi.call("Confluence", "GET", page, headers, null)
                if (current.statusCode == StatusCode.FAILURE) return current
                val answer = JsonApi.parsed(current)
                val updated = mapOf("id" to pageValue, "status" to "current", "title" to titleValue.ifEmpty { answer.path("title").asText() }, "body" to storage(), "version" to mapOf("number" to answer.path("version").path("number").asInt() + 1))
                withAddress(JsonApi.call("Confluence", "PUT", page, headers, updated), false)
            }
            else -> ActionResult.failure("Confluence has no operation called \"$operationValue\".")
        }
    }

    override val credentialType: String
        get() = ATLASSIAN_ACCOUNT

    override val outputs: Map<String, String>
        get() = mapOf("pageId" to "Page", "url" to "Address")

    override val description: String
        get() = "Creates, reads or updates a Confluence page"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForCreatePage = mapOf("operation" to arrayOf(CREATE_PAGE))
            val showForReadPageUpdatePage = mapOf("operation" to arrayOf(READ_PAGE, UPDATE_PAGE))
            val showForCreatePageUpdatePage = mapOf("operation" to arrayOf(CREATE_PAGE, UPDATE_PAGE))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_PAGE, "", listOf(
                PropertyInformation(CREATE_PAGE, "Create a page"),
                PropertyInformation(READ_PAGE, "Read a page"),
                PropertyInformation(UPDATE_PAGE, "Update a page")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "Site", PropertyInformationType.STRING, "", "Such as https://acme.atlassian.net."))
            actionInfo.add(PropertyInformation("email", "Email", PropertyInformationType.STRING, "", "The email of the Atlassian account.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("token", "API token", PropertyInformationType.STRING, "", "Made under the security settings of the Atlassian account.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("spaceKey", "Space", PropertyInformationType.STRING, "", "The short key in the address of the space, such as ENG.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePage))
            actionInfo.add(PropertyInformation("pageId", "Page", PropertyInformationType.STRING, "", "The number in the address of the page.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadPageUpdatePage))
            actionInfo.add(PropertyInformation("parentPageId", "Under the page", PropertyInformationType.STRING, "", "The number in the address of a page. Otherwise it goes directly in the space.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePage))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "When updating, leave it empty to keep it.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePageUpdatePage))
            actionInfo.add(PropertyInformation("pageContent", "Content", PropertyInformationType.MULTILINE, "", "In the storage format of Confluence, such as <p>Hello</p>.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePageUpdatePage))
            return actionInfo
        }

    private companion object {
        const val CREATE_PAGE = "createPage"
        const val READ_PAGE = "readPage"
        const val UPDATE_PAGE = "updatePage"
    }
}
