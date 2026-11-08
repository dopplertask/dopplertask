package com.dopplertask.dopplertask.service

import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.domain.action.integration.OAuthSignIn
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Connects the account of a credential: a person goes to the service's sign-in page, and the service sends them back
 * here with a code that becomes tokens. The client secret stays on the server, and every sign-in link works once.
 */
@Service
class OAuthConnections(
    private val credentials: CredentialService,
    private val customerScope: ObjectProvider<CustomerScope>,
    @Value("\${dopplertask.accounts.redirectUri:}") private val configuredRedirectUri: String
) {
    private class Pending(val verifier: String, val redirectUri: String, val values: Map<String, String>, val created: Instant, val tokens: AtomicReference<Map<String, String>>, val save: Runnable)

    private val pending = ConcurrentHashMap<String, Pending>()
    private val random = SecureRandom()

    init {
        OAuthSignIn.accounts = object : OAuthSignIn.Accounts {
            override fun keyOf(credential: String) = customerScope.getIfAvailable { CustomerScope.ONE_CUSTOMER }.current() + "/" + credential
            override fun keepRefreshToken(credential: String, usedToken: String, newToken: String) {
                // The step already has its token, so a failed save is only logged; the next refresh tries again from memory.
                try {
                    credentials.keepRefreshToken(credential, usedToken, newToken)
                } catch (e: RuntimeException) {
                    LOG.error("Could not keep the new refresh token of {}", credential, e)
                }
            }
        }
    }

    /** Where the service sends a person back to, which has to be registered with the service. */
    fun redirectUri(serverAddress: String) = configuredRedirectUri.ifBlank { serverAddress + CALLBACK_PATH }

    /** The address of the service's sign-in page for this credential. */
    fun start(name: String, serverAddress: String): String {
        val values = credentials.valuesNamed(name)
        if (values["oauthGrant"] == OAuthSignIn.CLIENT_CREDENTIALS) {
            throw CredentialProblemException("$name signs in as its client, so there is no account to connect. Steps sign in when they run.")
        }
        val clientId = values["oauthClientId"].orEmpty()
        val authUrl = OAuthSignIn.authUrlOf(values)
        if (clientId.isBlank()) throw CredentialProblemException("Add the client id from the service's developer settings, then connect.")
        if (authUrl.isBlank() || OAuthSignIn.tokenUrlOf(values).isBlank()) throw CredentialProblemException("Choose the service, or fill in its sign-in and token addresses, then connect.")

        pending.entries.removeIf { it.value.created.isBefore(Instant.now().minus(LINK_LIFETIME)) }
        val state = randomText()
        val verifier = randomText()
        val redirectUri = redirectUri(serverAddress)
        val tokens = AtomicReference<Map<String, String>>(mapOf())
        val scope = customerScope.getIfAvailable { CustomerScope.ONE_CUSTOMER }
        pending[state] = Pending(verifier, redirectUri, values, Instant.now(), tokens, scope.bound { credentials.update(name, tokens.get()) })

        val parameters = linkedMapOf(
            "response_type" to "code",
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "state" to state,
            "code_challenge" to Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())),
            "code_challenge_method" to "S256"
        )
        OAuthSignIn.scopeOf(values).takeIf { it.isNotBlank() }?.let { parameters["scope"] = it }
        val extra = listOf(OAuthSignIn.PROVIDERS[values["oauthProvider"]]?.authParameters.orEmpty(), values["oauthAuthParameters"].orEmpty())
            .filter { it.isNotBlank() }.joinToString("&")
        val query = parameters.entries.joinToString("&") { "${it.key}=${JsonApi.encoded(it.value)}" } + if (extra.isBlank()) "" else "&$extra"
        return authUrl + (if (authUrl.contains('?')) "&" else "?") + query
    }

    /** Turns the code the service sent back into tokens, and keeps them in the credential. Gives what to tell the person. */
    fun finish(state: String?, code: String?, error: String?): String {
        val waiting = state?.let { pending.remove(it) }
            ?: throw CredentialProblemException("This sign-in link was already used or is too old. Connect the account again from the credential.")
        if (waiting.created.isBefore(Instant.now().minus(LINK_LIFETIME))) {
            throw CredentialProblemException("The sign-in took longer than ten minutes. Connect the account again from the credential.")
        }
        if (!error.isNullOrBlank() || code.isNullOrBlank()) {
            throw CredentialProblemException("The service did not connect the account" + (if (error.isNullOrBlank()) "." else ": $error."))
        }
        val answer = OAuthSignIn.requestToken("The sign-in", OAuthSignIn.tokenUrlOf(waiting.values), waiting.values,
            mutableMapOf("grant_type" to "authorization_code", "code" to code, "redirect_uri" to waiting.redirectUri, "code_verifier" to waiting.verifier))
        if (answer.statusCode == StatusCode.FAILURE) throw CredentialProblemException(answer.errorMsg)
        val json = JsonApi.parsed(answer)
        val refreshToken = json.path("refresh_token").asText("")
        val accessToken = json.path("access_token").asText("")
        if (refreshToken.isBlank() && accessToken.isBlank()) {
            throw CredentialProblemException("The service gave no token. It said: " + json.path("error_description").asText(json.path("error").asText("nothing")) + ".")
        }
        // A service whose tokens do not expire gives no refresh token, so the access token is kept instead.
        waiting.tokens.set(if (refreshToken.isNotBlank()) mapOf("oauthRefreshToken" to refreshToken) else mapOf("oauthAccessToken" to accessToken))
        waiting.save.run()
        return "The account is connected. You can close this window."
    }

    private fun randomText(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(OAuthConnections::class.java)
        const val CALLBACK_PATH = "/oauth/callback"
        private val LINK_LIFETIME: Duration = Duration.ofMinutes(10)
    }
}
