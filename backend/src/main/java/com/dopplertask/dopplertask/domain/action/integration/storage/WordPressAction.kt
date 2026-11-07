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
import java.util.Base64

@Entity
@Table(name = "WordPressAction")
@DiscriminatorValue("wordpress_action")
class WordPressAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var username: String? = null

    @Transient
    var applicationPassword: String? = null

    @Column
    var operation: String? = "createPost"

    @Column
    var postId: String? = null

    @Column
    var title: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var postContent: String? = null

    @Column
    var postStatus: String? = "draft"

    @Column
    var searchText: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = read(siteUrl).trim().trimEnd('/')
        val userValue = read(username).trim()
        val passwordValue = read(applicationPassword)
        val operationValue = read(operation)
        val idValue = read(postId).trim()
        val titleValue = read(title).trim()
        val contentValue = read(postContent)
        if (site.isBlank()) return ActionResult.failure("WordPress needs the address of the site, such as https://blog.example.com.")
        if (userValue.isBlank() || passwordValue.isBlank()) return ActionResult.failure("WordPress needs a username and an application password, which is under Users, Profile.")
        if ((operationValue == READ_POST || operationValue == UPDATE_POST) && idValue.toLongOrNull() == null) return ActionResult.failure("WordPress needs the post ID as a number, and got \"$idValue\".")
        if (operationValue == CREATE_POST && titleValue.isBlank()) return ActionResult.failure("WordPress needs a title for the post.")

        val headers = mapOf("Authorization" to "Basic " + Base64.getEncoder().encodeToString("$userValue:$passwordValue".toByteArray()))
        val posts = "$site/wp-json/wp/v2/posts"
        fun written() = mapOf("title" to titleValue, "content" to contentValue, "status" to read(postStatus).trim().ifEmpty { "draft" }).filterValues { it.isNotEmpty() }
        val result = when (operationValue) {
            CREATE_POST -> JsonApi.call("WordPress", "POST", posts, headers, written())
            READ_POST -> JsonApi.call("WordPress", "GET", "$posts/$idValue?context=edit", headers, null)
            UPDATE_POST -> JsonApi.call("WordPress", "POST", "$posts/$idValue", headers, written())
            LIST_POSTS -> JsonApi.call("WordPress", "GET", "$posts?per_page=$POSTS_READ&status=any&search=${JsonApi.encoded(read(searchText).trim())}", headers, null)
            else -> return ActionResult.failure("WordPress has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            if (operationValue == LIST_POSTS) result.outputVariables["postCount"] = answer.size()
            else {
                result.outputVariables["postId"] = answer.path("id").asText()
                result.outputVariables["postStatus"] = answer.path("status").asText()
                result.outputVariables["url"] = answer.path("link").asText()
            }
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("postId" to "Post", "postStatus" to "Status", "url" to "Address", "postCount" to "Number of posts")

    override val description: String
        get() = "Writes, reads, changes or lists WordPress posts"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForReadPostUpdatePost = mapOf("operation" to arrayOf(READ_POST, UPDATE_POST))
            val showForCreatePostUpdatePost = mapOf("operation" to arrayOf(CREATE_POST, UPDATE_POST))
            val showForListPosts = mapOf("operation" to arrayOf(LIST_POSTS))
            actionInfo.add(PropertyInformation("username", "Username", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("applicationPassword", "Application password", PropertyInformationType.STRING, "", "Under Users, Profile, Application Passwords.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_POST, "", listOf(
                PropertyInformation(CREATE_POST, "Write a post"),
                PropertyInformation(READ_POST, "Read a post"),
                PropertyInformation(UPDATE_POST, "Change a post"),
                PropertyInformation(LIST_POSTS, "List posts")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "Site", PropertyInformationType.STRING, "", "Such as https://blog.example.com."))
            actionInfo.add(PropertyInformation("postId", "Post ID", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadPostUpdatePost))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePostUpdatePost))
            actionInfo.add(PropertyInformation("postContent", "Content", PropertyInformationType.MULTILINE, "", "HTML is allowed.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePostUpdatePost))
            actionInfo.add(PropertyInformation("postStatus", "Status", PropertyInformationType.STRING, "draft", "draft, pending or publish. A draft stays hidden until someone publishes it.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePostUpdatePost))
            actionInfo.add(PropertyInformation("searchText", "Search for", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForListPosts))
            return actionInfo
        }

    private companion object {
        const val CREATE_POST = "createPost"
        const val READ_POST = "readPost"
        const val UPDATE_POST = "updatePost"
        const val LIST_POSTS = "listPosts"
        const val POSTS_READ = 20
    }
}
