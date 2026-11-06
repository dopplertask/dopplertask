package com.dopplertask.dopplertask.domain.action.integration.anthropic

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.ColumnEncryptor
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.time.Duration
import java.io.IOException
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table

@Entity
@Table(name = "ClaudeAction")
@DiscriminatorValue("claude_action")
class ClaudeAction : Action() {
    @Column
    var baseUrl: String? = "https://api.anthropic.com"

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var apiKey: String? = null

    @Column
    var model: String? = "claude-opus-5-5"

    @Lob
    @Column(columnDefinition = "TEXT")
    var instructions: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var userInput: String? = null

    @Column
    var effort: String? = "medium"

    @Column
    var maxTokens: String? = "16000"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val keyValue = read(apiKey)
        val modelValue = read(model).trim().ifEmpty { "claude-opus-5-5" }
        val inputValue = read(userInput)
        val instructionsValue = read(instructions)
        val effortValue = read(effort)
        val limit = read(maxTokens).trim().ifEmpty { "16000" }.toIntOrNull()
        if (keyValue.isBlank()) return ActionResult.failure("Claude needs an API key, which the Claude Console gives.")
        if (inputValue.isBlank()) return ActionResult.failure("Claude needs a prompt.")
        if (limit == null || limit <= 0) return ActionResult.failure("Claude needs the longest answer as a number of tokens above 0.")

        val request = mutableMapOf<String, Any>("model" to modelValue, "max_tokens" to limit, "messages" to listOf(mapOf("role" to "user", "content" to inputValue)))
        if (instructionsValue.isNotBlank()) request["system"] = instructionsValue
        if (effortValue.isNotBlank()) request["output_config"] = mapOf("effort" to effortValue)
        val headers = mutableMapOf("x-api-key" to keyValue, "anthropic-version" to API_VERSION)
        // A model that declines can hand over to another one, which only these models offer.
        if (modelValue in MODELS_WITH_FALLBACK) {
            request["fallbacks"] = "default"
            headers["anthropic-beta"] = FALLBACK_BETA
        }

        val base = read(baseUrl).trim().trimEnd('/')
        val result = JsonApi.call("Claude", "POST", "$base/v1/messages", headers, request, Duration.ofMinutes(ANSWER_MINUTES))
        if (result.statusCode != StatusCode.SUCCESS) return result

        val answer = JsonApi.parsed(result)
        val stopReason = answer.path("stop_reason").asText()
        result.output = answer.path("content").filter { it.path("type").asText() == "text" }.joinToString("") { it.path("text").asText() }
        result.outputVariables["stopReason"] = stopReason
        result.outputVariables["inputTokens"] = answer.path("usage").path("input_tokens").asLong()
        result.outputVariables["outputTokens"] = answer.path("usage").path("output_tokens").asLong()
        // Both cases look like an answer, and a task should not carry on as if it had a whole one.
        if (stopReason == "refusal") {
            result.statusCode = StatusCode.FAILURE
            result.errorMsg = "Claude declined to answer" + (answer.path("stop_details").path("explanation").asText("").let { if (it.isEmpty()) "." else ": ${it.trimEnd('.')}." })
        }
        if (stopReason == "max_tokens") {
            result.statusCode = StatusCode.FAILURE
            result.errorMsg = "The answer from Claude was cut off at $limit tokens. Raise the longest answer."
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("stopReason" to "Why the answer ended", "inputTokens" to "Tokens read", "outputTokens" to "Tokens written")

    override val description: String
        get() = "Asks Claude and gives its answer as text"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("apiKey", "API key", PropertyInformationType.STRING, "", "The Claude Console gives it.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("model", "Model", PropertyInformationType.STRING, "claude-opus-5-5", ""))
            actionInfo.add(PropertyInformation("instructions", "Instructions", PropertyInformationType.MULTILINE, "", "How Claude should work, apart from what it is asked."))
            actionInfo.add(PropertyInformation("userInput", "Prompt", PropertyInformationType.MULTILINE, "", "What Claude is asked."))
            actionInfo.add(PropertyInformation("effort", "Effort", PropertyInformationType.DROPDOWN, "medium", "More effort gives more thought and costs more.", listOf(
                PropertyInformation("low", "Low"),
                PropertyInformation("medium", "Medium"),
                PropertyInformation("high", "High")
            )))
            actionInfo.add(PropertyInformation("maxTokens", "Longest answer", PropertyInformationType.NUMBER, "16000", "In tokens. An answer that reaches this fails the step, since it is cut off."))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.anthropic.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val API_VERSION = "2026-10-21"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        const val ANSWER_MINUTES = 10L
        val MODELS_WITH_FALLBACK = setOf("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")
    }
}
