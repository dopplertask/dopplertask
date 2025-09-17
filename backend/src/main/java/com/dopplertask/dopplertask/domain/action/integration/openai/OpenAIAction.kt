package com.dopplertask.dopplertask.domain.action.integration.openai

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.connection.HttpAction
import com.dopplertask.dopplertask.domain.action.connection.HttpHeader
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.ColumnEncryptor
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table

@Entity
@Table(name = "OpenAIAction")
@DiscriminatorValue("openai_action")
class OpenAIAction : Action() {
    @Lob
    @Column(columnDefinition = "TEXT")
    var baseUrl: String? = "https://api.openai.com/v1"

    @Column
    @Convert(converter = ColumnEncryptor::class)
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
        val baseUrlVar = variableExtractorUtil.extract(baseUrl, execution, scriptLanguage).trimEnd('/')
        val apiKeyVar = variableExtractorUtil.extract(apiKey, execution, scriptLanguage)
        val modelVar = variableExtractorUtil.extract(model, execution, scriptLanguage)
        val userInputVar = variableExtractorUtil.extract(userInput, execution, scriptLanguage)

        val request = HttpAction()
        request.url = concatUrl(baseUrlVar, "/chat/completions")
        request.method = "POST"

        // Minimal JSON body using model + user input
        val jsonBody = "{" +
                "\"model\":\"" + modelVar + "\"," +
                "\"messages\":[{\"role\":\"user\",\"content\":\"" + userInputVar.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}]}"
        request.body = jsonBody

        // Headers
        val headers = ArrayList<HttpHeader>()
        if (!apiKeyVar.isNullOrBlank()) {
            headers.add(createHeader("Authorization", "Bearer ${apiKeyVar}"))
        }
        headers.add(createHeader("Content-Type", "application/json"))
        headers.add(createHeader("Accept", "application/json"))
        request.setHeaders(headers)

        return request.run(taskService, execution, variableExtractorUtil, broadcastListener)
    }

    private fun concatUrl(base: String, path: String): String {
        if (path.isBlank()) return base
        val cleanBase = base.trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return cleanBase + cleanPath
    }

    private fun createHeader(name: String, value: String): HttpHeader {
        val h = HttpHeader()
        h.headerName = name
        h.headerValue = value
        return h
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(
                PropertyInformation(
                    "baseUrl",
                    "Base URL",
                    PropertyInformationType.STRING,
                    "https://api.openai.com/v1",
                    "API base URL",
                    mutableListOf(),
                    PropertyInformationCategory.PROPERTY
                )
            )

            // Credentials
            actionInfo.add(
                PropertyInformation(
                    "apiKey",
                    "API Key",
                    PropertyInformationType.STRING,
                    "",
                    "OpenAI API Key (kept encrypted)",
                    mutableListOf(),
                    PropertyInformationCategory.CREDENTIAL
                )
            )
            
            // Model
            actionInfo.add(
                PropertyInformation(
                    "model",
                    "Model",
                    PropertyInformationType.STRING,
                    "",
                    "Model id (free text)",
                    mutableListOf(),
                    PropertyInformationCategory.PROPERTY
                )
            )

            // User input
            actionInfo.add(
                PropertyInformation(
                    "userInput",
                    "User input",
                    PropertyInformationType.MULTILINE,
                    "",
                    "Free text that will be sent as the user message",
                    mutableListOf(),
                    PropertyInformationCategory.PROPERTY
                )
            )

            return actionInfo
        }

    override val description: String
        get() = "Call OpenAI API directly or via a custom server"
}


