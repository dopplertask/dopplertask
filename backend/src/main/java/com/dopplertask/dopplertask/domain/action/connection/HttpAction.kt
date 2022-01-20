package com.dopplertask.dopplertask.domain.action.connection

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.function.Consumer
import javax.persistence.*

@Entity
@Table(name = "HttpAction")
@DiscriminatorValue("http_action")
class HttpAction : Action() {
    @Column
    var url: String? = null

    @OneToMany(mappedBy = "httpAction", cascade = [CascadeType.ALL])
    private var headers: List<HttpHeader> = ArrayList()

    @Column
    var method: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var body: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult { // Extract variables
        val urlVariable = variableExtractorUtil.extract(url, execution, scriptLanguage)
        val bodyVariable = variableExtractorUtil.extract(body, execution, scriptLanguage)
        val actionResult = ActionResult()
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(urlVariable))
            .timeout(Duration.ofMinutes(1))
        when (method) {
            "POST" -> builder = builder.POST(HttpRequest.BodyPublishers.ofString(bodyVariable))
            "PUT" -> builder = builder.PUT(HttpRequest.BodyPublishers.ofString(bodyVariable))
            "DELETE" -> builder = builder.DELETE()
            "GET" -> builder = builder.GET()
            "PATCH" -> builder = builder.method("PATCH", HttpRequest.BodyPublishers.ofString(bodyVariable))
            "HEAD" -> builder = builder.method("HEAD", HttpRequest.BodyPublishers.ofString(bodyVariable))
            "OPTIONS" -> builder = builder.method("OPTIONS", HttpRequest.BodyPublishers.ofString(bodyVariable))
            else -> {
                actionResult.errorMsg = "HTTP method is not supported! [method=$method]"
                actionResult.statusCode = StatusCode.FAILURE
            }
        }
        for (entry in headers) {
            builder = builder.header(
                variableExtractorUtil.extract(entry.headerName, execution, scriptLanguage),
                variableExtractorUtil.extract(entry.headerValue, execution, scriptLanguage)
            )
        }
        val client = HttpClient.newHttpClient()
        val request = builder.build()
        var response: HttpResponse<String>?
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString())
            actionResult.output = response.body()
            actionResult.statusCode = StatusCode.SUCCESS
        } catch (e: IOException) {
            actionResult.errorMsg = "Exception when sending http request: $e"
            actionResult.statusCode = StatusCode.FAILURE
        } catch (e: InterruptedException) {
            actionResult.errorMsg = "Exception when sending http request: $e"
            actionResult.statusCode = StatusCode.FAILURE
        }
        return actionResult
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("url", "URL", PropertyInformationType.STRING, "", "Hostname or IP"))
            actionInfo.add(
                PropertyInformation(
                    "method", "Method", PropertyInformationType.DROPDOWN, "GET", "HTTP Method",
                    mutableListOf(
                        PropertyInformation("GET", "GET"),
                        PropertyInformation(
                            "POST", "POST", mutableListOf(
                                PropertyInformation(
                                    "body",
                                    "Body",
                                    PropertyInformationType.MULTILINE,
                                    "",
                                    "Contents to send"
                                )
                            )
                        ),
                        PropertyInformation(
                            "PUT", "PUT", mutableListOf(
                                PropertyInformation(
                                    "body",
                                    "Body",
                                    PropertyInformationType.MULTILINE,
                                    "",
                                    "Contents to send"
                                )
                            )
                        ),
                        PropertyInformation(
                            "PATCH", "PATCH", mutableListOf(
                                PropertyInformation(
                                    "body",
                                    "Body",
                                    PropertyInformationType.MULTILINE,
                                    "",
                                    "Contents to send"
                                )
                            )
                        ),
                        PropertyInformation("DELETE", "DELETE"),
                        PropertyInformation("OPTIONS", "OPTIONS"),
                        PropertyInformation("HEAD", "HEAD")
                    )
                )
            )

            actionInfo.add(
                PropertyInformation(
                    "headers", "Headers", PropertyInformationType.MAP, "", "Headers",
                    java.util.List.of(
                        PropertyInformation("headerName", "Key"),
                        PropertyInformation("headerValue", "Value")
                    )
                )
            )
            return actionInfo
        }

    override val description: String
        get() = "Makes an HTTP request and returns the result"

    fun getHeaders(): List<HttpHeader> {
        return headers
    }

    fun setHeaders(headers: List<HttpHeader>) {
        headers.forEach(Consumer { header: HttpHeader -> header.httpAction = this })
        this.headers = headers
    }

}