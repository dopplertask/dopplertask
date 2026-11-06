package com.dopplertask.dopplertask.domain.action.integration

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.action.connection.HttpAction
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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

    private fun send(service: String, method: String, url: String, headers: Map<String, String>, body: String, timeout: Duration): ActionResult {
        val request = HttpAction()
        request.method = method
        val result = request.send(url, body, headers + mapOf("Accept" to "application/json"), timeout = timeout)
        if (result.statusCode == StatusCode.FAILURE && result.outputVariables.containsKey("statusCode")) {
            val reason = reasonIn(result.output)
            result.errorMsg = "$service answered with status ${result.outputVariables["statusCode"]}" + (if (reason.isEmpty()) "." else ": ${reason.trimEnd('.')}.")
        }
        return result
    }

    // Some services take their secret in the address, and the messages about a wrong address repeat the address.
    fun withoutSecret(result: ActionResult, secret: String): ActionResult {
        result.errorMsg = result.errorMsg?.replace(secret, "(hidden)")
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
        listOf("message", "description", "error_description", "error").forEach { field ->
            if (answer.path(field).isTextual) return answer.path(field).asText()
        }
        if (answer.path("error").path("message").isTextual) return answer.path("error").path("message").asText()
        // Jira names a wrong field under errors, which is the only reason it gives, and GraphQL lists objects there.
        val firstError = answer.path("errors").elements().asSequence().firstOrNull()
        return answer.path("errorMessages").path(0).asText("").ifEmpty { firstError?.path("message")?.asText(firstError.asText("")) ?: "" }
    }
}
