package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.domain.action.integration.OAuthSignIn
import tools.jackson.databind.JsonNode
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.UUID

/**
 * Sends a connector's requests for a step or trigger: signed with its credential, only to the connector's hosts, and
 * with a fresh access token when its accounts sign in. A script's own requests go out the same way.
 */
class ConnectorSender(private val step: Action, private val connector: Connector, private val credential: Map<String, String>) {

    /** The token of the credential's session, once [openSession] signed in, which the step's requests carry. */
    var session: String? = null
        private set

    /**
     * Signs in with the credential's session, when it has one and the credential has every value it needs, such as a
     * user name and password, or the account's token to ask for a page's own with the step's {{page}}.
     */
    fun openSession(values: Map<String, String>) {
        val opening = connector.credential?.session ?: return
        val needed = Connectors.templatesOf(opening.request).flatMap(Connectors::placeholdersIn).filter { it.startsWith(CREDENTIAL) }.map { it.removePrefix(CREDENTIAL) }
        if (needed.any { credential[it].isNullOrBlank() }) return
        val signIn = ConnectorOperation().also { it.request = opening.request }
        val answer = send(ConnectorRequests.build(connector, signIn, values + credential.mapKeys { CREDENTIAL + it.key }, credential))
        require(answer.statusCode == StatusCode.SUCCESS) { "${connector.label} did not sign in: ${answer.errorMsg}" }
        session = JsonPaths.read(answerOf(answer), opening.token)?.asText()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("${connector.label} signed in, but gave no token at ${opening.token}.")
    }

    /** Sends a request. A download gets the answer's bytes through [answer]. */
    fun send(request: ConnectorRequests.Built, answer: (ByteArray) -> Unit = {}): ActionResult {
        require(Connectors.allows(connector, URI.create(request.url).host, credential)) { "${connector.label} may only send its credential to ${connector.hosts.joinToString(", ")}." }
        var headers = request.headers
        if (connector.credential?.oauth2 != null && !request.bySession) {
            val signIn = OAuthSignIn.headersFor(step, connector.label)
            signIn.failure?.let { return it }
            headers = headers + signIn.headers
        }
        val address = Connectors.localServerForTests?.let { request.url.replaceFirst(Regex("^https://[^/]+"), it) } ?: request.url
        // JSON, as most services and the originals send, unless the connector names another type, such as a form or JSON Patch.
        val named = headers.entries.firstOrNull { it.key.equals(CONTENT_TYPE, ignoreCase = true) }?.value ?: "application/json"
        val boundary = "dopplertask-" + UUID.randomUUID()
        val whole = request.files[ConnectorRequests.WHOLE_BODY]
        val (contentType, body) = when {
            whole != null -> (headers.entries.firstOrNull { it.key.equals(CONTENT_TYPE, ignoreCase = true) }?.value ?: whole.mimeType) to whole.bytes
            named.startsWith(MULTIPART) -> "${named.substringBefore(';')}; boundary=$boundary" to multipartOf(request, boundary, named.startsWith(FORM_DATA))
            named.startsWith(FORM) -> named to request.body?.let(::formOf).orEmpty().toByteArray()
            // A body in another type, such as CSV, is sent as the text it is.
            "json" !in named && request.body?.isTextual == true -> named to request.body.asText().toByteArray()
            else -> named to request.body?.toString().orEmpty().toByteArray()
        }
        val typed = headers.filterKeys { !it.equals(CONTENT_TYPE, ignoreCase = true) } + (CONTENT_TYPE to contentType)
        return JsonApi.send(connector.label, request.method, address, typed, body, answer = answer)
    }

    /**
     * Each field of the body as a part, in the order of the body, and each file as a part with its name and type; a
     * file that the body does not name comes last. A form names its parts; other multipart bodies, such as
     * multipart/related, give each part its type instead.
     */
    private fun multipartOf(request: ConnectorRequests.Built, boundary: String, isForm: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        fun line(text: String) = out.write("$text\r\n".toByteArray())
        val fields = request.body?.properties()?.associate { it.key to it.value }.orEmpty()
        val parts = fields + (request.files.keys - fields.keys).associateWith { JsonApi.mapper.nullNode() }
        parts.forEach { (name, value) ->
            val file = request.files[name]
            line("--$boundary")
            if (isForm) line("Content-Disposition: form-data; name=\"$name\"" + (file?.let { "; filename=\"${it.name}\"" } ?: ""))
            when {
                file != null -> line("Content-Type: ${file.mimeType}")
                !isForm -> line("Content-Type: " + if (value.isValueNode) "text/plain; charset=UTF-8" else "application/json; charset=UTF-8")
            }
            line("")
            if (file != null) out.write(file.bytes) else out.write((if (value.isValueNode) value.asText() else value.toString()).toByteArray())
            line("")
        }
        line("--$boundary--")
        return out.toByteArray()
    }

    private fun formOf(body: JsonNode): String = body.properties().joinToString("&") { (key, value) ->
        "${JsonApi.encoded(key)}=${JsonApi.encoded(if (value.isValueNode) value.asText() else value.toString())}"
    }

    companion object {
        private const val CONTENT_TYPE = "Content-Type"
        private const val CREDENTIAL = "credential."
        private const val FORM = "application/x-www-form-urlencoded"
        private const val MULTIPART = "multipart/"
        private const val FORM_DATA = "multipart/form-data"

        /** An answer as JSON, as its text when it is not JSON, such as CSV, or null when it is empty. */
        fun answerOf(result: ActionResult): JsonNode = when {
            result.output.isNullOrBlank() -> JsonApi.mapper.nullNode()
            else -> runCatching { JsonApi.mapper.readTree(result.output) }.getOrElse { JsonApi.mapper.getNodeFactory().textNode(result.output) }
        }

        /** What came with an answer, for scripts: its status, its headers, each with its first value, and its text. */
        fun receivedOf(result: ActionResult): JsonNode = JsonApi.mapper.valueToTree(mapOf(
            "status" to result.outputVariables["statusCode"],
            "headers" to result.outputVariables.filterKeys { it != "statusCode" }.mapValues { (it.value as? List<*>)?.firstOrNull() ?: it.value },
            "text" to result.output.orEmpty()))

        /** What a step signing in with an account reads from its own fields, as every step that offers it does. */
        fun accountFields(connector: Connector, values: Map<String, String>): Map<String, String> =
            if (connector.credential?.oauth2 == null) emptyMap() else values.filterKeys { it.startsWith("oauth") } + ("oauthProvider" to connector.name)
    }

    /** What a script's request answered. A failed one stops the script with what the service said. */
    fun answerFor(spec: JsonNode): JsonNode {
        val answer = send(ConnectorRequests.sign(connector, spec, credential, session = session))
        require(answer.statusCode == StatusCode.SUCCESS) { answer.errorMsg }
        return answerOf(answer)
    }
}
