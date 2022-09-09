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
import java.lang.Boolean
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.function.Consumer
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.OneToMany
import javax.persistence.Table
import kotlin.Array
import kotlin.String
import kotlin.Throws
import kotlin.arrayOf


@Entity
@Table(name = "HttpAction")
@DiscriminatorValue("http_action")
class HttpAction : Action() {
    @Lob
    @Column(columnDefinition = "TEXT")
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

        val client = HttpClient.newBuilder().sslContext(insecureContext()).build();
        val request = builder.build()

        var response: HttpResponse<String>?
        try {

            response = client.send(request, HttpResponse.BodyHandlers.ofString())
            actionResult.output = response.body()
            response.headers().map().forEach { (key, value) ->
                actionResult.outputVariables[key] = value
            }

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

    private fun insecureContext(): SSLContext? {
        val noopTrustManager = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(xcs: Array<X509Certificate?>?, string: String?) {}
                override fun checkServerTrusted(xcs: Array<X509Certificate?>?, string: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate>? {
                    return null
                }
            }
        )
        try {
            val sc = SSLContext.getInstance("ssl")
            sc.init(null, noopTrustManager, null)
            return sc
        } catch (ex: KeyManagementException) {
        } catch (ex: NoSuchAlgorithmException) {
        }
        return null;
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
                    mutableListOf(
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