package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import com.dopplertask.dopplertask.domain.RunFiles.RunFile
import java.util.Base64

/**
 * Fills in a connector's request with the step's values, runs the action's script on it if it has one, and only then
 * adds the credential, so that a script never sees it. Each value is encoded for where it goes.
 */
object ConnectorRequests {

    /**
     * A request ready to send, with the files of a multipart body by the part they go in, and the request as the
     * connector made it before the credential was added, which is all a script may see of it. One signed with a session
     * carries its token and nothing else of the credential. A body that is only a file is that file's bytes.
     */
    class Built(val method: String, val url: String, val headers: Map<String, String>, val body: JsonNode?, val files: Map<String, RunFile>, val unsigned: JsonNode,
                val bySession: Boolean = false, val bodyFile: RunFile? = null, private val shownAddress: String = "", private val address: String = "") {
        /** How a script's call() sends, so that a call to the address the script was shown gets its credential back. */
        fun calling(send: (JsonNode) -> JsonNode): (JsonNode) -> JsonNode = { spec -> send(withAddress(spec, shownAddress, address)) }
    }

    private val NODES = JsonNodeFactory.instance

    // A credential value that names part of a host, such as a subdomain, may only hold what a host name holds.
    private val HOST_PART = Regex("[A-Za-z0-9.-]+")

    private const val CREDENTIAL = Connectors.CREDENTIAL
    private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")

    /** The placeholder of a session's token in where the credential's session puts it. */
    const val SESSION = "session"

    @JvmOverloads
    fun build(connector: Connector, operation: ConnectorOperation, values: Map<String, String>, credential: Map<String, String>,
              send: (JsonNode) -> JsonNode = { throw IllegalArgumentException("This action cannot send more requests.") },
              files: Map<String, RunFile> = emptyMap(), memory: ObjectNode? = null, session: String? = null): Built {
        val request = operation.request
        val draft = NODES.objectNode()
        draft.put("method", request.method)
        // Scripts see a secret credential value in the address by its name. Only the part of the address they were given
        // gets the value back, so that nothing a script adds, such as an input or a value from an answer, gets one.
        val template = Connectors.urlOf(connector, request.url)
        val shown = address(template, values, credential, Connectors.secretFieldsOf(connector))
        draft.put("url", shown)
        draft.set("query", JsonApi.mapper.valueToTree(request.query.mapValues { Connectors.fill(it.value) { name -> values[name].orEmpty() } }.filterValues { it.isNotEmpty() }))
        draft.set("headers", JsonApi.mapper.valueToTree(request.headers.mapValues { Connectors.fill(it.value) { name -> values[name].orEmpty() } }))
        draft.set("body", request.body?.let { bodyOf(it, operation, values, files) } ?: NODES.nullNode())
        val real = address(template, values, credential)
        val ready = operation.beforeRequest?.let { ConnectorScripts.run(it, values, draft, null, { spec -> send(withAddress(spec, shown, real)) }, memory = memory) } ?: draft
        // In a multipart body a file goes in its own part, so the part is named by the body's field. A body that is
        // only a file is the file's bytes.
        val fileIn = { template: JsonNode -> template.takeIf { it.isTextual }?.let { Connectors.placeholdersIn(it.asText()).singleOrNull() }?.let { files[it] } }
        val bodyFile = request.body?.let(fileIn)
        val parts = if (bodyFile != null) emptyMap() else request.body?.properties()?.mapNotNull { (part, template) -> fileIn(template)?.let { part to it } }?.toMap().orEmpty()
        val signed = sign(connector, withAddress(ready, shown, real), credential, parts, session, bodyFile)
        return Built(signed.method, signed.url, signed.headers, signed.body, signed.files, ready.deepCopy(), signed.bySession, signed.bodyFile, shown, real)
    }

    /** A request whose address starts with the one a script was shown, with the credential value in place of its name. */
    private fun withAddress(spec: JsonNode, shown: String, real: String): JsonNode {
        val written = spec.path("url").asText()
        return if (shown.isEmpty() || shown == real || !written.startsWith(shown)) spec else spec.deepCopy().also { (it as ObjectNode).put("url", real + written.removePrefix(shown)) }
    }

    /**
     * Any call of the service's API, with its credential, for what the connector's actions do not cover. The address
     * starts at the connector's base, so the credential still only goes to the connector's hosts.
     */
    @JvmOverloads
    fun customCall(connector: Connector, values: Map<String, String>, credential: Map<String, String>, session: String? = null): Built {
        val path = values["path"].orEmpty().trim()
        require(!path.contains("://") && !path.startsWith("//")) { "The path should start after the address of ${connector.label}, such as /contacts." }
        val draft = NODES.objectNode()
        draft.put("method", values["method"].orEmpty().ifBlank { "GET" })
        draft.put("url", "/" + path.trimStart('/'))
        draft.set("query", json(values["query"]) ?: NODES.objectNode())
        draft.set("headers", json(values["headers"]) ?: NODES.objectNode())
        draft.set("body", json(values["body"]) ?: NODES.nullNode())
        return sign(connector, draft, credential, session = session)
    }

    /** Adds the credential to a request, whose address may also be a path after the connector's base, such as /items. */
    @JvmOverloads
    fun sign(connector: Connector, draft: JsonNode, credential: Map<String, String>, files: Map<String, RunFile> = emptyMap(), session: String? = null,
             bodyFile: RunFile? = null): Built {
        // A path after the base, as customCall makes and a script may call, gets the base with its credential here.
        val target = draft.path("url").asText()
        val address = if (target.startsWith("/")) address(connector.baseUrl, emptyMap(), credential).trimEnd('/') + target else target
        // A session's token stands in for the rest of the credential.
        val signIn = if (session == null) connector.credential else connector.credential?.session?.let { opened ->
            ConnectorCredential().also { it.headers = opened.headers; it.query = opened.query }
        }
        val credentialValue = { name: String -> if (name == SESSION) session.orEmpty() else credential[name.removePrefix(CREDENTIAL)].orEmpty() }
        val query = textsOf(draft.path("query")) + signIn?.query.orEmpty().mapValues { Connectors.fill(it.value, credentialValue) }
        val url = address + query.entries.joinToString("&", prefix = if (query.isEmpty()) "" else if ('?' in address) "&" else "?") {
            "${JsonApi.encoded(it.key)}=${JsonApi.encoded(it.value)}"
        }
        val headers = textsOf(draft.path("headers")) + signIn?.headers.orEmpty().mapValues { Connectors.fill(it.value, credentialValue) } +
            listOfNotNull(signIn?.basic?.let { basic ->
                val pair = Connectors.fill(basic.username, credentialValue) + ":" + Connectors.fill(basic.password, credentialValue)
                "Authorization" to "Basic " + Base64.getEncoder().encodeToString(pair.toByteArray())
            })
        headers.forEach { (name, value) -> require('\r' !in value && '\n' !in value) { "The header $name may not hold a line break." } }
        val method = draft.path("method").asText("GET")
        val body = draft.path("body").takeUnless { it.isNull || it.isMissingNode }.let { body ->
            val fields = signIn?.body.orEmpty().mapValues { Connectors.fill(it.value, credentialValue) }
            // A credential in the body goes only where a body goes, never into a GET or a DELETE that has none.
            if (fields.isEmpty() || (body == null && method !in METHODS_WITH_BODY)) body else ((body as? ObjectNode)?.deepCopy() ?: NODES.objectNode()).also { filled -> fields.forEach { (key, value) -> filled.put(key, value) } }
        }
        return Built(method, url, headers, body, files, draft.deepCopy(), bySession = session != null, bodyFile = bodyFile)
    }

    /**
     * The address with its host from the credential, checked as a host name, and every other value percent-encoded. A
     * secret credential value named in hidden keeps its name, for the address scripts see.
     */
    private fun address(template: String, values: Map<String, String>, credential: Map<String, String>, hidden: Set<String> = emptySet()): String {
        val hostEnd = template.indexOf('/', template.indexOf("://") + 3).let { if (it < 0) template.length else it }
        val path = Connectors.fill(template.substring(hostEnd)) { name ->
            val field = name.removePrefix(CREDENTIAL)
            when {
                !name.startsWith(CREDENTIAL) -> pathPart(values[name].orEmpty())
                field in hidden -> "{{$name}}"
                else -> pathPart(credential[field].orEmpty())
            }
        }
        return Connectors.fill(template.substring(0, hostEnd)) { hostPart(credential, it) } + path
    }

    /** A value made safe for an address the way browsers and the originals do it, with encodeURIComponent. */
    fun pathPart(value: String) = JsonApi.encoded(value)
        .replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~")

    private fun hostPart(credential: Map<String, String>, name: String): String {
        val value = Connectors.hostOf(credential[name.removePrefix(CREDENTIAL)].orEmpty())
        require(HOST_PART.matches(value)) { "The credential's ${name.removePrefix(CREDENTIAL)} should be a host name, such as example.zendesk.com." }
        return value
    }

    private fun textsOf(node: JsonNode): Map<String, String> =
        node.properties().associate { (key, value) -> key to if (value.isValueNode) value.asText() else value.toString() }

    private fun json(text: String?): JsonNode? = text?.takeIf { it.isNotBlank() }?.let {
        try {
            JsonApi.mapper.readTree(it)
        } catch (e: Exception) {
            throw IllegalArgumentException("\"$it\" is not valid JSON.")
        }
    }

    /**
     * The body with each placeholder replaced. A value that is only a placeholder takes the input's type, such as a
     * number or JSON, and is left out when the person left it empty, as the services expect of optional fields.
     */
    private fun bodyOf(template: JsonNode, operation: ConnectorOperation, values: Map<String, String>, files: Map<String, RunFile>): JsonNode? = when {
        template.isObject -> NODES.objectNode().also { filled ->
            template.properties().forEach { (key, value) -> bodyOf(value, operation, values, files)?.let { filled.set(key, it) } }
        }
        template.isArray -> NODES.arrayNode().also { filled -> template.forEach { item -> bodyOf(item, operation, values, files)?.let(filled::add) } }
        template.isTextual -> {
            val text = template.asText()
            val whole = Connectors.placeholdersIn(text).singleOrNull()?.takeIf { text.trim() == "{{$it}}" }
            if (whole == null) NODES.textNode(Connectors.fill(text) { values[it].orEmpty() })
            else files[whole]?.let { NODES.textNode(Base64.getEncoder().encodeToString(it.bytes)) } ?: typed(values[whole].orEmpty(), operation.inputs[whole]?.type)
        }
        else -> template
    }

    private fun typed(value: String, type: String?): JsonNode? {
        if (value.isEmpty()) return null
        return when (type) {
            "number" -> value.toBigDecimalOrNull()?.let(NODES::numberNode) ?: throw IllegalArgumentException("\"$value\" is not a number.")
            "boolean" -> NODES.booleanNode(value.toBooleanStrictOrNull() ?: throw IllegalArgumentException("\"$value\" is not true or false."))
            "json" -> json(value)
            else -> NODES.textNode(value)
        }
    }
}
