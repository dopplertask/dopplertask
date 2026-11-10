package com.dopplertask.dopplertask.domain.action.integration

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.Preview
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.connector.Connectors
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Signing in to a service with a person's own account (OAuth 2). A person connects the account once from the credential;
 * after that, steps get a fresh access token for every run. The fields match the usual OAuth 2 settings, so that a
 * credential from another tool can be filled in the same way.
 */
object OAuthSignIn {

    const val AUTHORIZATION_CODE = "authorizationCode"
    const val CLIENT_CREDENTIALS = "clientCredentials"

    /** Where a known service signs people in, and what it needs to give a token that lasts. */
    class Provider(val label: String, val authUrl: String, val tokenUrl: String, val authParameters: String, val scope: String)

    val PROVIDERS = linkedMapOf(
        "GOOGLE" to Provider("Google", "https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token", "access_type=offline&prompt=consent",
            "https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/calendar"),
        // offline_access is what makes Microsoft give a refresh token.
        "MICROSOFT" to Provider("Microsoft", "https://login.microsoftonline.com/common/oauth2/v2.0/authorize", "https://login.microsoftonline.com/common/oauth2/v2.0/token", "", "offline_access User.Read"),
        "GITHUB" to Provider("GitHub", "https://github.com/login/oauth/authorize", "https://github.com/login/oauth/access_token", "", "repo"),
        "CUSTOM" to Provider("Another service", "", "", "", "")
    )

    /** The scope a person typed, or what the known service needs when they left it empty. */
    fun scopeOf(values: Map<String, String?>): String = values["oauthScope"].orEmpty().ifBlank { providerOf(values)?.scope.orEmpty() }

    /** A service known here, or a connector whose file says how its accounts sign in. */
    fun providerOf(values: Map<String, String?>): Provider? = values["oauthProvider"].let { name ->
        PROVIDERS[name] ?: Connectors.find(name)?.let { connector ->
            connector.credential?.oauth2?.let { Provider(connector.label, it.authUrl, it.tokenUrl, it.authParameters, it.scope) }
        }
    }

    class SignIn(val headers: Map<String, String>, val failure: ActionResult?)

    /** What a credential holds to sign in this way. Every step that offers it adds these fields. */
    fun fields(provider: String, scope: String): List<PropertyInformation> = listOf(
        PropertyInformation("oauthProvider", "Account of", PropertyInformationType.DROPDOWN, provider, "Sign in with an account instead. Fill in the client from the service's developer settings, save, then connect the account.",
            PROVIDERS.map { (key, known) -> PropertyInformation(key, known.label) }, PropertyInformationCategory.CREDENTIAL),
        PropertyInformation("oauthGrant", "How it signs in", PropertyInformationType.DROPDOWN, AUTHORIZATION_CODE, "",
            listOf(PropertyInformation(AUTHORIZATION_CODE, "A person connects their account"), PropertyInformation(CLIENT_CREDENTIALS, "The client signs in as itself")),
            PropertyInformationCategory.CREDENTIAL),
        PropertyInformation("oauthClientId", "Client id", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.CREDENTIAL),
        PropertyInformation("oauthClientSecret", "Client secret", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.SECRET),
        PropertyInformation("oauthScope", "Scope", PropertyInformationType.STRING, scope, "What the account lets steps do, separated by spaces. Empty asks a known service for what its steps need.", mutableListOf(), PropertyInformationCategory.CREDENTIAL),
        PropertyInformation("oauthAuthUrl", "Sign-in address", PropertyInformationType.STRING, "", "Only for another service. Known services fill it in.", mutableListOf(), PropertyInformationCategory.CREDENTIAL),
        PropertyInformation("oauthTokenUrl", "Token address", PropertyInformationType.STRING, "", "Only for another service. Known services fill it in.", mutableListOf(), PropertyInformationCategory.CREDENTIAL),
        PropertyInformation("oauthAuthParameters", "Extra sign-in values", PropertyInformationType.STRING, "", "Added to the sign-in address, such as audience=api.example.com.", mutableListOf(), PropertyInformationCategory.CREDENTIAL),
        PropertyInformation("oauthClientInHeader", "Client in the header", PropertyInformationType.BOOLEAN, "false", "Sends the client id and secret as Basic sign-in instead of in the form.", mutableListOf(), PropertyInformationCategory.CREDENTIAL),
        PropertyInformation("oauthRefreshToken", "Connected account", PropertyInformationType.STRING, "", "Filled in when the account is connected.", mutableListOf(), PropertyInformationCategory.SECRET),
        PropertyInformation("oauthAccessToken", "Access token", PropertyInformationType.STRING, "", "Filled in for services whose tokens do not expire.", mutableListOf(), PropertyInformationCategory.SECRET)
    )

    fun authUrlOf(values: Map<String, String?>): String = values["oauthAuthUrl"].orEmpty().ifBlank { providerOf(values)?.authUrl.orEmpty() }

    fun tokenUrlOf(values: Map<String, String?>): String = values["oauthTokenUrl"].orEmpty().ifBlank { providerOf(values)?.tokenUrl.orEmpty() }

    /** Whether the step's credential signs in with an account, rather than in the step's own way. */
    fun isUsedBy(step: Action): Boolean =
        !step.oauthRefreshToken.isNullOrBlank() || !step.oauthAccessToken.isNullOrBlank() || (step.oauthGrant == CLIENT_CREDENTIALS && !step.oauthClientId.isNullOrBlank())

    /** Where connected accounts are kept. Set by the credential store. */
    interface Accounts {
        /** What keeps the same-named credentials of different customers apart. */
        fun keyOf(credential: String): String = credential

        /** Saves the refresh token a service gave in place of the used one, unless the credential holds another one by now. */
        fun keepRefreshToken(credential: String, usedToken: String, newToken: String) {}
    }

    @Volatile
    var accounts: Accounts = object : Accounts {}

    /**
     * The latest access token of an account, and the refresh token to use next. A run that started before a refresh still
     * holds a refresh token that was replaced, which some services no longer accept, so that run gets the new one from here.
     */
    private class Account(val accessToken: String, val expires: Instant, val refreshToken: String?, val replaced: List<String>) {
        fun follows(refreshToken: String?) = refreshToken == this.refreshToken || refreshToken in replaced
    }

    /** Enough for runs that last through several refreshes, without keeping every old token for good. */
    private const val REPLACED_TOKENS_KEPT = 5

    private val signedIn = ConcurrentHashMap<String, Account>()
    private val refreshing = ConcurrentHashMap<String, ReentrantLock>()

    /** The header that signs a request in as the connected account. */
    fun headersFor(step: Action, service: String): SignIn {
        val values = valuesOf(step)
        val tokenUrl = tokenUrlOf(values)
        if (step.oauthRefreshToken.isNullOrBlank() && !step.oauthAccessToken.isNullOrBlank()) {
            return bearer(step.oauthAccessToken!!)
        }
        if (tokenUrl.isBlank()) return SignIn(mapOf(), ActionResult.failure("$service needs the token address of the account's service. Choose the service, or fill in the token address."))
        val clientCredentials = step.oauthGrant == CLIENT_CREDENTIALS
        if (!clientCredentials && step.oauthRefreshToken.isNullOrBlank()) {
            return SignIn(mapOf(), ActionResult.failure("$service has no connected account yet. Open the credential and connect the account."))
        }
        val credential = step.credential
        val key = digest(listOf(tokenUrl, step.oauthClientId, step.oauthScope, credential?.let { accounts.keyOf(it) } ?: step.oauthRefreshToken).joinToString("|"))
        // One refresh at a time for an account, so that runs at the same moment do not each use the same refresh token.
        refreshing.computeIfAbsent(key) { ReentrantLock() }.withLock {
            val known = signedIn[key]?.takeIf { clientCredentials || it.follows(step.oauthRefreshToken) }
            if (known != null && known.expires.isAfter(Instant.now().plusSeconds(60))) return bearer(known.accessToken)

            val refreshToken = known?.refreshToken ?: step.oauthRefreshToken
            val form = if (clientCredentials) mutableMapOf("grant_type" to "client_credentials").also { if (!step.oauthScope.isNullOrBlank()) it["scope"] = step.oauthScope!! }
            else mutableMapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken!!)
            val answer = requestToken(service, tokenUrl, values, form)
            if (answer.statusCode == StatusCode.FAILURE) return SignIn(mapOf(), answer)
            val json = JsonApi.parsed(answer)
            val accessToken = json.path("access_token").asText("")
            if (accessToken.isBlank()) {
                return SignIn(mapOf(), ActionResult.failure("$service did not give an access token. It said: ${json.path("error_description").asText(json.path("error").asText("nothing"))}. Connect the account again."))
            }
            val newRefreshToken = json.path("refresh_token").asText("").ifBlank { null }?.takeIf { it != refreshToken }
            val replaced = (known?.replaced.orEmpty() + listOfNotNull(refreshToken.takeIf { newRefreshToken != null })).takeLast(REPLACED_TOKENS_KEPT)
            signedIn[key] = Account(accessToken, Instant.now().plusSeconds(json.path("expires_in").asLong(3600)), newRefreshToken ?: refreshToken, replaced)
            if (newRefreshToken != null && credential != null) {
                accounts.keepRefreshToken(credential, refreshToken!!, newRefreshToken)
            }
            return bearer(accessToken)
        }
    }

    private fun bearer(accessToken: String) = SignIn(mapOf("Authorization" to "Bearer $accessToken"), null)

    /** Asks the token address for tokens, with the client in the form or as Basic sign-in. */
    fun requestToken(service: String, tokenUrl: String, values: Map<String, String?>, form: MutableMap<String, String>): ActionResult {
        val clientId = values["oauthClientId"].orEmpty()
        val clientSecret = values["oauthClientSecret"].orEmpty()
        val headers = mutableMapOf<String, String>()
        if (values["oauthClientInHeader"] == "true") {
            headers["Authorization"] = "Basic " + Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
        } else {
            form["client_id"] = clientId
            if (clientSecret.isNotBlank()) form["client_secret"] = clientSecret
        }
        return Preview.sendingForReal { JsonApi.callForm(service, tokenUrl, headers, form) }
    }

    fun valuesOf(step: Action): Map<String, String?> = mapOf(
        "oauthProvider" to step.oauthProvider, "oauthGrant" to step.oauthGrant, "oauthClientId" to step.oauthClientId,
        "oauthClientSecret" to step.oauthClientSecret, "oauthScope" to step.oauthScope, "oauthAuthUrl" to step.oauthAuthUrl,
        "oauthTokenUrl" to step.oauthTokenUrl, "oauthAuthParameters" to step.oauthAuthParameters, "oauthClientInHeader" to step.oauthClientInHeader
    )

    private fun digest(text: String): String = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))
}
