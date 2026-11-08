package com.dopplertask.dopplertask.domain.action.trigger

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.OutputType
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import com.dopplertask.dopplertask.service.WebhookReply
import com.dopplertask.dopplertask.domain.action.common.WebhookReplyAction
import jakarta.persistence.Lob
import java.io.IOException
import java.util.*
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Transient

@Entity
class Webhook : Trigger() {

    @Column
    var path = ""

    @Column
    var method = "GET"

    @Column
    var authentication: String? = "none"

    @Transient
    var username: String? = ""

    @Transient
    var password: String? = ""

    @Transient
    var token: String? = ""

    @Column
    var headerName: String? = "";

    @Transient
    var headerValue: String? = "";

    /** How the caller is answered. Empty answers with the run log, as webhooks always have. */
    @Column
    var responseMode: String? = WebhookReply.RUN_LOG

    @Column
    var responseCode: String? = "200"

    /** What the caller gets when it is answered at once. Empty gives the run id. */
    @Lob
    @Column(columnDefinition = "TEXT")
    var responseBody: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {

        // Check the authentication type. If it is Basic, then read the username and password. if it is Header, then read the header name and value.
        if (authentication == "basic") {
            val usernameVariable = variableExtractorUtil.extract(username, execution, scriptLanguage)
            val passwordVariable = variableExtractorUtil.extract(password, execution, scriptLanguage)

            // Read authentication either from TRIGGER_authentication or TRIGGER_Authentication
            val authentication =
                execution.parameters["TRIGGER_authorization"] ?: execution.parameters["TRIGGER_Authorization"]

            // Remove Basic and decode the base64 encoded string containing the username and password.
            val basicAuth = authentication.toString().replace("Basic ", "")
            val decodedAuth = String(Base64.getDecoder().decode(basicAuth))
            val parts = decodedAuth.split(":")
            var usernameDecoded = ""
            var passwordDecoded = ""
            if (parts.size == 2) {
                usernameDecoded = parts[0]
                passwordDecoded = parts[1]
            }

            return answered(if (authentication != null && passwordVariable.isNotEmpty() && usernameVariable.equals(usernameDecoded) && passwordVariable.equals(
                    passwordDecoded
                )
            ) {
                val result = ActionResult()
                result.output = "Webhook triggered and authenticated successfully."
                result.statusCode = StatusCode.SUCCESS
                result.outputType = OutputType.STRING
                result
            } else {
                authenticationFailed()
            }, execution, variableExtractorUtil)

        } else if (authentication == "header") {
            val headerNameVariable = variableExtractorUtil.extract(headerName, execution, scriptLanguage)
            val headerValueVariable = variableExtractorUtil.extract(headerValue, execution, scriptLanguage)

            val authentication = execution.parameters["TRIGGER_$headerNameVariable"]

            return answered(respond(headerValueVariable, authentication.toString()), execution, variableExtractorUtil)
        } else if (authentication == "bearer") {
            val tokenVariable = variableExtractorUtil.extract(token, execution, scriptLanguage)

            val authentication =
                (execution.parameters["TRIGGER_authorization"] ?: execution.parameters["TRIGGER_Authorization"])

            // Remove Basic and decode the base64 encoded string containing the username and password.
            val bearerAuth = authentication.toString().replace("Bearer ", "")

            return answered(respond(tokenVariable, bearerAuth), execution, variableExtractorUtil)
        }

        val result = ActionResult()
        result.output = "Webhook triggered"
        result.statusCode = StatusCode.SUCCESS
        result.outputType = OutputType.STRING
        return answered(result, execution, variableExtractorUtil)
    }

    /** Answers a caller who should not wait, once it is known that the call may start the task. */
    private fun answered(result: ActionResult, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil): ActionResult {
        val reply = execution.webhookReply ?: return result
        if (result.statusCode == StatusCode.FAILURE) {
            reply.give(401, mapOf("Content-Type" to "text/plain"), "The webhook could not check who is calling.")
        } else if (reply.mode == WebhookReply.AT_ONCE) {
            val body = variableExtractorUtil.extract(responseBody, execution, scriptLanguage)
            val answer = body.ifBlank { "{\"executionId\":${execution.id},\"status\":\"started\"}" }
            reply.give(WebhookReplyAction.statusOf(variableExtractorUtil.extract(responseCode, execution, scriptLanguage), 200), mapOf("Content-Type" to WebhookReplyAction.contentTypeOf(answer)), answer)
        }
        return result
    }

    private fun respond(
        webhookValue: String,
        userInput: String
    ): ActionResult {
        // Without a value to compare with, nothing can pass, so that a missing credential does not open the webhook.
        if (webhookValue.isNotEmpty() && webhookValue == userInput) {
            val result = ActionResult()
            result.output = "Webhook triggered and authenticated successfully."
            result.statusCode = StatusCode.SUCCESS
            result.outputType = OutputType.STRING
            return result
        } else {
            return authenticationFailed()
        }
    }

    private fun authenticationFailed(): ActionResult {
        val result = ActionResult()
        result.output = "Webhook triggered but authentication failed."
        result.statusCode = StatusCode.FAILURE
        result.outputType = OutputType.STRING
        return result
    }

    override fun trigger(): TriggerResult {
        // Do nothing.
        return TriggerResult(mutableMapOf())
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(
                PropertyInformation(
                    "triggerSuffix", "Webhook URL suffix", PropertyInformation.PropertyInformationType.STRING, "", "Part of the webhook URL that makes it unique."
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "method", "Method", PropertyInformation.PropertyInformationType.DROPDOWN, "GET", "HTTP Method",
                    mutableListOf(
                        PropertyInformation("GET", "GET"),
                        PropertyInformation("POST", "POST"),
                        PropertyInformation("PUT", "PUT"),
                        PropertyInformation("PATCH", "PATCH"),
                        PropertyInformation("DELETE", "DELETE")
                    )
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "authentication",
                    "Authentication",
                    PropertyInformation.PropertyInformationType.DROPDOWN,
                    "none",
                    "Authentication method",
                    mutableListOf(
                        PropertyInformation("none", "None"),
                        PropertyInformation("basic", "Basic Authentication"),
                        PropertyInformation("bearer", "Bearer Authentication"),
                        PropertyInformation("header", "Header Authentication")
                    )
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "username",
                    "Username",
                    PropertyInformation.PropertyInformationType.STRING,
                    "",
                    "Username",
                    mutableListOf(),
                    PropertyInformation.PropertyInformationCategory.CREDENTIAL,
                    mutableMapOf(Pair("authentication", arrayOf("basic")))
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "password",
                    "Password",
                    PropertyInformation.PropertyInformationType.STRING,
                    "",
                    "Username",
                    mutableListOf(),
                    PropertyInformation.PropertyInformationCategory.SECRET,
                    mutableMapOf(Pair("authentication", arrayOf("basic")))
                )
            )

            actionInfo.add(
                PropertyInformation(
                    "token",
                    "Token",
                    PropertyInformation.PropertyInformationType.STRING,
                    "",
                    "Bearer token",
                    mutableListOf(),
                    PropertyInformation.PropertyInformationCategory.SECRET,
                    mutableMapOf(Pair("authentication", arrayOf("bearer")))
                )
            )

            actionInfo.add(
                PropertyInformation(
                    "headerName",
                    "Header name",
                    PropertyInformation.PropertyInformationType.STRING,
                    "",
                    "Header name",
                    mutableListOf(),
                    PropertyInformation.PropertyInformationCategory.PROPERTY,
                    mutableMapOf(Pair("authentication", arrayOf("header")))
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "headerValue",
                    "Header value",
                    PropertyInformation.PropertyInformationType.STRING,
                    "",
                    "Header value",
                    mutableListOf(),
                    PropertyInformation.PropertyInformationCategory.SECRET,
                    mutableMapOf(Pair("authentication", arrayOf("header")))
                )
            )

            actionInfo.add(PropertyInformation("path", "Path", PropertyInformation.PropertyInformationType.STRING))
            actionInfo.add(
                PropertyInformation(
                    "responseMode", "Reply", PropertyInformation.PropertyInformationType.DROPDOWN, WebhookReply.RUN_LOG, "What the caller gets back, and when.",
                    WebhookReply.LABELS.map { (mode, label) -> PropertyInformation(mode, label) }
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "responseCode", "Status code", PropertyInformation.PropertyInformationType.NUMBER, "200", "The HTTP status of the answer, such as 200 or 202.",
                    mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY,
                    mutableMapOf(Pair("responseMode", arrayOf(WebhookReply.AT_ONCE, WebhookReply.LAST_STEP)))
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "responseBody", "Answer", PropertyInformation.PropertyInformationType.MULTILINE, "", "Text or JSON for the caller. Empty gives the run id.",
                    mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY,
                    mutableMapOf(Pair("responseMode", arrayOf(WebhookReply.AT_ONCE)))
                )
            )
            return actionInfo
        }

    override val description: String
        get() = "Starts the workflow when the webhook URL is called."
}