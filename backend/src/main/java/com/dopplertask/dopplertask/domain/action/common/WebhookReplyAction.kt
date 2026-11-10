package com.dopplertask.dopplertask.domain.action.common

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import com.dopplertask.dopplertask.service.WebhookReply
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table

/**
 * Answers the caller of the webhook that started the task, then lets the task carry on. The webhook has to be set to
 * reply with what this step sends, and the step fails when it is not, since the answer would otherwise be lost.
 */
@Entity
@Table(name = "WebhookReplyAction")
@DiscriminatorValue("webhook_reply_action")
class WebhookReplyAction : Action() {
    override val runsInPreview get() = true


    /** TEXT, JSON, LAST_STEP, NO_DATA or REDIRECT. */
    @Column
    var replyWith: String? = "JSON"

    @Lob
    @Column(columnDefinition = "TEXT")
    var body: String? = null

    @Column(length = 4096)
    var redirectUrl: String? = null

    @Column
    var statusCode: String? = null

    /** One header per line, as Name: value. */
    @Lob
    @Column(columnDefinition = "TEXT")
    var headers: String? = null

    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val reply = execution.webhookReply
            ?: return ActionResult(StatusCode.SUCCESS, "Nothing to reply to, since no webhook call started this run.", "")
        if (reply.mode != WebhookReply.REPLY_STEP) {
            return ActionResult.failure("The webhook's Reply is \"${WebhookReply.LABELS[reply.mode] ?: reply.mode}\", so it does not wait for this step. "
                + "Set it to \"${WebhookReply.LABELS[WebhookReply.REPLY_STEP]}\", or remove this step.")
        }
        val kind = replyWith ?: "JSON"
        val previous = execution.logs.lastOrNull()?.output ?: ""
        val text = when (kind) {
            "LAST_STEP" -> previous
            "NO_DATA", "REDIRECT" -> ""
            else -> variableExtractorUtil.extract(body, execution, scriptLanguage)
        }
        if (kind == "JSON" && text.isNotBlank() && !isJson(text)) {
            return ActionResult.failure("The answer is not valid JSON. Check the body, or reply with text instead.")
        }
        val answerHeaders = linkedMapOf<String, String>()
        when (kind) {
            "JSON" -> answerHeaders["Content-Type"] = "application/json"
            "TEXT" -> answerHeaders["Content-Type"] = "text/plain; charset=utf-8"
            "LAST_STEP" -> answerHeaders["Content-Type"] = contentTypeOf(text)
            "REDIRECT" -> {
                val location = variableExtractorUtil.extract(redirectUrl, execution, scriptLanguage)
                if (location.isBlank()) return ActionResult.failure("Add the address to redirect the caller to.")
                answerHeaders["Location"] = location
            }
        }
        variableExtractorUtil.extract(headers, execution, scriptLanguage).lines().forEach { line ->
            val colon = line.indexOf(':')
            if (colon > 0) answerHeaders[line.substring(0, colon).trim()] = line.substring(colon + 1).trim()
        }
        val status = statusOf(variableExtractorUtil.extract(statusCode, execution, scriptLanguage), if (kind == "REDIRECT") 307 else if (kind == "NO_DATA") 204 else 200)
        if (!reply.give(status, answerHeaders, text.ifEmpty { null })) {
            return ActionResult(StatusCode.SUCCESS, "The caller already has its answer, so this reply was left out.", "")
        }
        val result = ActionResult(StatusCode.SUCCESS, text, "")
        result.outputVariables["statusCode"] = status
        return result
    }

    private fun isJson(text: String) = try {
        JsonApi.mapper.readTree(text)
        true
    } catch (e: Exception) {
        false
    }

    override val description: String
        get() = "Answers the webhook call that started the task, then carries on"

    override val outputs: Map<String, String>
        get() = mapOf("statusCode" to "The HTTP status the caller got")

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val info = super.actionInfo
            info.add(PropertyInformation("replyWith", "Reply with", PropertyInformationType.DROPDOWN, "JSON", "What the caller gets.", listOf(
                PropertyInformation("JSON", "JSON"),
                PropertyInformation("TEXT", "Text"),
                PropertyInformation("LAST_STEP", "What the last step gave"),
                PropertyInformation("NO_DATA", "No content"),
                PropertyInformation("REDIRECT", "A redirect")
            )))
            info.add(PropertyInformation("body", "Body", PropertyInformationType.MULTILINE, "", "Such as {\"received\": true}. Values like \$lastLog.output work here.",
                mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY, mapOf("replyWith" to arrayOf("JSON", "TEXT"))))
            info.add(PropertyInformation("redirectUrl", "Redirect to", PropertyInformationType.STRING, "", "The address the caller goes to.",
                mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY, mapOf("replyWith" to arrayOf("REDIRECT"))))
            info.add(PropertyInformation("statusCode", "Status code", PropertyInformationType.NUMBER, "", "Empty gives 200, 204 for no content and 307 for a redirect."))
            info.add(PropertyInformation("headers", "Headers", PropertyInformationType.MULTILINE, "", "One per line, such as Cache-Control: no-store."))
            return info
        }

    companion object {
        @JvmStatic
        fun statusOf(text: String?, empty: Int): Int {
            val status = text?.trim()?.toIntOrNull() ?: return empty
            return if (status in 100..599) status else empty
        }

        @JvmStatic
        fun contentTypeOf(text: String): String {
            val start = text.trimStart().firstOrNull()
            return if ((start == '{' || start == '[') && try { JsonApi.mapper.readTree(text); true } catch (e: Exception) { false }) "application/json" else "text/plain; charset=utf-8"
        }
    }
}
