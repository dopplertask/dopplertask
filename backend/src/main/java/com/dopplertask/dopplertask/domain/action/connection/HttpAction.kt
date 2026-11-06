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
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.function.Consumer
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
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


private const val FIRST_ERROR_STATUS = 400

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
        val headerVariables = headers.associate {
            variableExtractorUtil.extract(it.headerName, execution, scriptLanguage) to
                    variableExtractorUtil.extract(it.headerValue, execution, scriptLanguage)
        }
        return send(urlVariable, bodyVariable, headerVariables, insecureContext())
    }

    /**
     * Sends a request with values that are final, so that text coming from a service or a step is never evaluated.
     */
    fun send(urlVariable: String, bodyVariable: String, headerVariables: Map<String, String>, sslContext: SSLContext? = SSLContext.getDefault(), timeout: Duration = Duration.ofMinutes(1)): ActionResult {
        val actionResult = ActionResult()
        var builder = try {
            HttpRequest.newBuilder().uri(URI.create(urlVariable.trim())).timeout(timeout)
        } catch (e: IllegalArgumentException) {
            actionResult.errorMsg = "The address \"$urlVariable\" is not valid. It should start with http:// or https://, such as https://example.com/status."
            actionResult.statusCode = StatusCode.FAILURE
            return actionResult
        }
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
        headerVariables.forEach { (name, value) -> builder = builder.header(name, value) }

        val client = HttpClient.newBuilder().sslContext(sslContext).build();
        val request = builder.build()

        var response: HttpResponse<String>?
        try {

            response = client.send(request, HttpResponse.BodyHandlers.ofString())
            actionResult.output = response.body()
            actionResult.outputVariables["statusCode"] = response.statusCode()
            response.headers().map().forEach { (key, value) ->
                actionResult.outputVariables[key] = value
            }

            if (response.statusCode() >= FIRST_ERROR_STATUS) {
                actionResult.errorMsg = "The server answered with status ${response.statusCode()}."
                actionResult.statusCode = StatusCode.FAILURE
            } else {
                actionResult.statusCode = StatusCode.SUCCESS
            }
        } catch (e: ConnectException) {
            actionResult.errorMsg = "Could not connect to ${request.uri().host}. Check the address, and that the service is running."
            actionResult.statusCode = StatusCode.FAILURE
        } catch (e: HttpTimeoutException) {
            actionResult.errorMsg = "${request.uri().host} did not answer within a minute."
            actionResult.statusCode = StatusCode.FAILURE
        } catch (e: SSLException) {
            actionResult.errorMsg = "The certificate of ${request.uri().host} could not be verified."
            actionResult.statusCode = StatusCode.FAILURE
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
            actionInfo.add(PropertyInformation("url", "URL", PropertyInformationType.STRING, "", "The full address, such as https://example.com/status"))
            actionInfo.add(
                PropertyInformation(
                    "method", "Method", PropertyInformationType.DROPDOWN, "GET", "",
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

    override val outputs: Map<String, String>
        get() = mapOf("statusCode" to "Status of the answer")

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