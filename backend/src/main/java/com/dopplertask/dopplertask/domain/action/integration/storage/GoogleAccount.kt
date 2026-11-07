package com.dopplertask.dopplertask.domain.action.integration.storage

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

const val GOOGLE_SERVICE_ACCOUNT = "Google service account"

/**
 * How the steps for Google services sign in. A service account proves who it is with its key file, and gets a token
 * that is only good for what the step asks for.
 */
object GoogleAccount {
    private const val TOKEN_SECONDS = 3600L

    class SignIn(val headers: Map<String, String>, val failure: ActionResult?)

    fun signIn(service: String, keyText: String, scope: String): SignIn {
        val account = try {
            JsonApi.mapper.readTree(keyText)
        } catch (e: Exception) {
            return SignIn(mapOf(), ActionResult.failure("$service could not read the key file, which should be the JSON file of the service account."))
        }
        val tokenUrl = account.path("token_uri").asText("https://oauth2.googleapis.com/token")
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .issuer(account.path("client_email").asText())
            .audience(tokenUrl)
            .claim("scope", scope)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(TOKEN_SECONDS)))
            .build()
        val assertion = SignedJWT(JWSHeader(JWSAlgorithm.RS256), claims)
        try {
            val keyBytes = Base64.getMimeDecoder().decode(account.path("private_key").asText().replace(Regex("-----[A-Z ]+-----"), ""))
            assertion.sign(RSASSASigner(KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))))
        } catch (e: Exception) {
            return SignIn(mapOf(), ActionResult.failure("$service could not read the private key in the key file."))
        }
        val tokens = JsonApi.callForm(service, tokenUrl, mapOf(), mapOf("grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion" to assertion.serialize()))
        if (tokens.statusCode == StatusCode.FAILURE) return SignIn(mapOf(), tokens)
        return SignIn(mapOf("Authorization" to "Bearer " + JsonApi.parsed(tokens).path("access_token").asText()), null)
    }
}
