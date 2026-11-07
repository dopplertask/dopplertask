package com.dopplertask.dopplertask.domain.action.integration.storage

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.action.integration.JsonApi

const val MICROSOFT_ACCOUNT = "Microsoft account"

/**
 * How the steps for Microsoft 365 sign in. An app registration proves who it is with its secret, and what it may do is
 * what an administrator consented to for that app.
 */
object MicrosoftAccount {
    private const val GRAPH_SCOPE = "https://graph.microsoft.com/.default"

    class SignIn(val headers: Map<String, String>, val failure: ActionResult?)

    fun signIn(service: String, signInUrl: String, tenantId: String, clientId: String, clientSecret: String): SignIn {
        if (tenantId.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
            return SignIn(mapOf(), ActionResult.failure("$service needs the directory ID, application ID and client secret of an app registration."))
        }
        val tokenUrl = "${signInUrl.trimEnd('/')}/${JsonApi.encoded(tenantId)}/oauth2/v2.0/token"
        val tokens = JsonApi.callForm(service, tokenUrl, mapOf(), mapOf("grant_type" to "client_credentials", "client_id" to clientId, "client_secret" to clientSecret, "scope" to GRAPH_SCOPE))
        if (tokens.statusCode == StatusCode.FAILURE) return SignIn(mapOf(), tokens)
        return SignIn(mapOf("Authorization" to "Bearer " + JsonApi.parsed(tokens).path("access_token").asText()), null)
    }
}
