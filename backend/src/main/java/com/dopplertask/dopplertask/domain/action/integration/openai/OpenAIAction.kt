package com.dopplertask.dopplertask.domain.action.integration.openai

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import java.time.Duration
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient

@Entity
@Table(name = "OpenAIAction")
@DiscriminatorValue("openai_action")
class OpenAIAction : Action() {
    override val runsInPreview get() = true

    @Lob
    @Column(columnDefinition = "TEXT")
    var baseUrl: String? = "https://api.openai.com/v1"

    @Transient
    var apiKey: String? = null

    @Column
    var model: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var userInput: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val modelValue = read(model).trim()
        val inputValue = read(userInput)
        if (modelValue.isBlank()) return ActionResult.failure("OpenAI needs a model, such as gpt-5.")
        if (inputValue.isBlank()) return ActionResult.failure("OpenAI needs a prompt.")

        // A key is optional, since a server of your own may not ask for one.
        val keyValue = read(apiKey)
        val headers = if (keyValue.isBlank()) mapOf() else mapOf("Authorization" to "Bearer $keyValue")
        val url = JsonApi.baseAddress(read(baseUrl)) + "/chat/completions"
        val request = mapOf("model" to modelValue, "messages" to listOf(mapOf("role" to "user", "content" to inputValue)))
        return JsonApi.call("OpenAI", "POST", url, headers, request, Duration.ofMinutes(ANSWER_MINUTES))
    }

    override val description: String
        get() = "Asks OpenAI or a compatible server"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("apiKey", "API key", PropertyInformationType.STRING, "", "Leave it empty if your own server does not ask for one.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("model", "Model", PropertyInformationType.STRING, "", "Such as gpt-5."))
            actionInfo.add(PropertyInformation("userInput", "Prompt", PropertyInformationType.MULTILINE, "", "What the model is asked."))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.openai.com/v1", "Only change this for a server of your own. Its certificate has to be valid."))
            return actionInfo
        }

    private companion object {
        const val ANSWER_MINUTES = 10L
    }
}
