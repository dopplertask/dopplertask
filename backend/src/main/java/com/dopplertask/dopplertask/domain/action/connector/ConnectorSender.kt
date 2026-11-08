package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.domain.action.integration.OAuthSignIn
import tools.jackson.databind.JsonNode
import java.net.URI

/**
 * Sends a connector's requests for a step or trigger: signed with its credential, only to the connector's hosts, and
 * with a fresh access token when its accounts sign in. A script's own requests go out the same way.
 */
class ConnectorSender(private val step: Action, private val connector: Connector, private val credential: Map<String, String>) {

    fun send(request: ConnectorRequests.Built): ActionResult {
        require(Connectors.allows(connector, URI.create(request.url).host, credential)) { "${connector.label} may only send its credential to ${connector.hosts.joinToString(", ")}." }
        var headers = request.headers
        if (connector.credential?.oauth2 != null) {
            val signIn = OAuthSignIn.headersFor(step, connector.label)
            signIn.failure?.let { return it }
            headers = headers + signIn.headers
        }
        val address = Connectors.localServerForTests?.let { request.url.replaceFirst(Regex("^https://[^/]+"), it) } ?: request.url
        return JsonApi.call(connector.label, request.method, address, headers, request.body)
    }

    companion object {
        /** What a step signing in with an account reads from its own fields, as every step that offers it does. */
        fun accountFields(connector: Connector, values: Map<String, String>): Map<String, String> =
            if (connector.credential?.oauth2 == null) emptyMap() else values.filterKeys { it.startsWith("oauth") } + ("oauthProvider" to connector.name)
    }

    /** What a script's request answered. A failed one stops the script with what the service said. */
    fun answerFor(spec: JsonNode): JsonNode {
        val answer = send(ConnectorRequests.sign(connector, spec, credential))
        require(answer.statusCode == StatusCode.SUCCESS) { answer.errorMsg }
        return JsonApi.parsed(answer)
    }
}
