package com.dopplertask.dopplertask.domain.action.trigger

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.OutputType
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity

@Entity
class Webhook : Trigger() {

    @Column
    var path = ""

    @Column
    var method = "GET"

    @Column
    var authentication: String? = "none"

    @Column
    var username: String? = ""

    @Column
    var password: String? = ""

    @Column
    var token: String? = ""

    @Column
    var headerName: String? = "";

    @Column
    var headerValue: String? = "";

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

            return if (authentication != null && usernameVariable.equals(usernameDecoded) && passwordVariable.equals(
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
            }

        } else if (authentication == "header") {
            val headerNameVariable = variableExtractorUtil.extract(headerName, execution, scriptLanguage)
            val headerValueVariable = variableExtractorUtil.extract(headerValue, execution, scriptLanguage)

            val authentication = execution.parameters["TRIGGER_$headerNameVariable"]

            return respond(headerValueVariable, authentication.toString())
        } else if (authentication == "bearer") {
            val tokenVariable = variableExtractorUtil.extract(token, execution, scriptLanguage)

            val authentication =
                (execution.parameters["TRIGGER_authorization"] ?: execution.parameters["TRIGGER_Authorization"])

            // Remove Basic and decode the base64 encoded string containing the username and password.
            val bearerAuth = authentication.toString().replace("Bearer ", "")

            return respond(tokenVariable, bearerAuth)
        }

        val result = ActionResult()
        result.output = "Webhook triggered"
        result.statusCode = StatusCode.SUCCESS
        result.outputType = OutputType.STRING
        return result
    }

    private fun respond(
        webhookValue: String,
        userInput: String
    ): ActionResult {
        if (webhookValue == userInput) {
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
                    PropertyInformation.PropertyInformationCategory.PROPERTY,
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
                    PropertyInformation.PropertyInformationCategory.PROPERTY,
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
                    PropertyInformation.PropertyInformationCategory.PROPERTY,
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
                    PropertyInformation.PropertyInformationCategory.PROPERTY,
                    mutableMapOf(Pair("authentication", arrayOf("header")))
                )
            )

            actionInfo.add(PropertyInformation("path", "Path", PropertyInformation.PropertyInformationType.STRING))
            return actionInfo
        }

    override val description: String
        get() = "Starts the workflow when the webhook URL is called."
}