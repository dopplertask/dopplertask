package com.dopplertask.dopplertask.domain.action.integration

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.action.connection.HttpAction
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * What the steps for outside services have in common. It goes through the HTTP step, verifies certificates since a
 * token is sent along, and says what the service answered instead of only a status.
 */
object JsonApi {
    private const val LONGEST_SENTENCE = 200

    val mapper = ObjectMapper()

    fun call(service: String, method: String, url: String, headers: Map<String, String>, body: Any?, timeout: Duration = Duration.ofMinutes(1)): ActionResult =
        send(service, method, url, headers + mapOf("Content-Type" to "application/json"), if (body == null) "" else mapper.writeValueAsString(body), timeout)

    fun callForm(service: String, url: String, headers: Map<String, String>, fields: Map<String, String>): ActionResult {
        val form = fields.entries.joinToString("&") { "${URLEncoder.encode(it.key, StandardCharsets.UTF_8)}=${URLEncoder.encode(it.value, StandardCharsets.UTF_8)}" }
        return send(service, "POST", url, headers + mapOf("Content-Type" to "application/x-www-form-urlencoded"), form, Duration.ofMinutes(1))
    }

    fun callText(service: String, method: String, url: String, headers: Map<String, String>, text: String, contentType: String): ActionResult =
        send(service, method, url, headers + mapOf("Content-Type" to contentType), text, Duration.ofMinutes(1))

    /** Where a service starts, once the step has read it, without spaces or a slash at the end. */
    fun baseAddress(read: String?): String = read.orEmpty().trim().trimEnd('/')

    /** A value that a person typed, made safe to put in the address of a request. */
    fun encoded(text: String): String = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")

    /** What a person typed as a JSON object, or null when it is not one. */
    @Suppress("UNCHECKED_CAST")
    fun objectFrom(text: String): Map<String, Any?>? = try {
        mapper.readValue(text, Map::class.java) as Map<String, Any?>?
    } catch (e: Exception) {
        null
    }

    fun send(service: String, method: String, url: String, headers: Map<String, String>, body: String, timeout: Duration = Duration.ofMinutes(1)): ActionResult =
        send(service, method, url, headers, body.toByteArray(), timeout) {}

    /** The same with a body of bytes, such as a form with a file, and the answer's bytes handed to [answer]. */
    fun send(service: String, method: String, url: String, headers: Map<String, String>, body: ByteArray, timeout: Duration = Duration.ofMinutes(1), answer: (ByteArray) -> Unit): ActionResult {
        val request = HttpAction()
        request.method = method
        val result = request.send(url, body, mapOf("Accept" to "application/json") + headers, timeout = timeout, answer = answer)
        if (result.statusCode == StatusCode.FAILURE && result.outputVariables.containsKey("statusCode")) {
            val reason = reasonIn(result.output)
            result.errorMsg = "$service answered with status ${result.outputVariables["statusCode"]}" + (if (reason.isEmpty()) "." else ": ${reason.trimEnd('.')}.")
        }
        return result
    }

    fun parsed(result: ActionResult): JsonNode = try {
        mapper.readTree(result.output)
    } catch (e: Exception) {
        mapper.createObjectNode()
    }

    // Services name the reason differently, so these are the names they use.
    private fun reasonIn(body: String?): String {
        val answer = try {
            mapper.readTree(body ?: "")
        } catch (e: Exception) {
            // Some services answer with a plain sentence, and an error page is not worth repeating.
            val sentence = body.orEmpty().trim()
            return if (sentence.length <= LONGEST_SENTENCE && !sentence.startsWith("<")) sentence else ""
        }
        listOf("message", "description", "error_description", "error", "detail", "err").forEach { field ->
            if (answer.path(field).isTextual) return answer.path(field).asText()
        }
        if (answer.path("error").path("message").isTextual) return answer.path("error").path("message").asText()
        // Elasticsearch explains under error, and the first root cause says more than "all shards failed".
        val rootCause = answer.path("error").path("root_cause").path(0).path("reason")
        if (rootCause.isTextual) return rootCause.asText()
        if (answer.path("error").path("reason").isTextual) return answer.path("error").path("reason").asText()
        if (answer.path("errors").isTextual) return answer.path("errors").asText()
        // Salesforce answers with a list of errors.
        if (answer.path(0).path("message").isTextual) return answer.path(0).path("message").asText()
        // Jira names a wrong field under errors, which is the only reason it gives, and GraphQL lists objects there.
        val firstError = answer.path("errors").values().firstOrNull()
        return answer.path("errorMessages").path(0).asText("").ifEmpty { firstError?.path("message")?.asText(firstError.path("title").asText(firstError.asText(""))) ?: "" }
    }
}
