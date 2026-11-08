package com.dopplertask.dopplertask.domain.action.connector

import tools.jackson.databind.JsonNode

/**
 * A service that steps can work with, described in a file rather than in code: who it signs in as, which hosts it may
 * send that to, and what each of its actions asks for, sends and gives back. One connector is one kind of step, and
 * each action is one choice in it.
 */
class Connector {
    var name: String = ""
    var label: String = ""
    var description: String = ""
    var icon: String = ""
    var category: String = "integration"
    var version: Int = 1

    /** The only hosts that a request with this connector's credential may go to, such as slack.com or *.zendesk.com. */
    var hosts: List<String> = emptyList()

    /** Where the service's API starts, such as https://api.resend.com. With it, every connector can also make any call of that API. */
    var baseUrl: String = ""
    var credential: ConnectorCredential? = null
    var actions: LinkedHashMap<String, ConnectorOperation> = linkedMapOf()

    /** Where the file was copied from, so that a later copy can be compared with it. */
    var upstream: Map<String, String> = emptyMap()
}

/** What a credential for the connector holds, and where each value goes in a request. */
class ConnectorCredential {
    var fields: LinkedHashMap<String, ConnectorInput> = linkedMapOf()
    var headers: Map<String, String> = emptyMap()
    var query: Map<String, String> = emptyMap()
    var basic: ConnectorBasicAuth? = null
    var oauth2: ConnectorOAuth? = null
}

/** A user name and password sent as Basic sign-in, each a template of credential values. */
class ConnectorBasicAuth {
    var username: String = ""
    var password: String = ""
}

/** Signing in with an account, by the service's own addresses. */
class ConnectorOAuth {
    var authUrl: String = ""
    var tokenUrl: String = ""
    var scope: String = ""
    var authParameters: String = ""
}

class ConnectorOperation {
    var label: String = ""
    var description: String = ""

    /** Who the action is for: both, a person in the editor, or an agent. */
    var audience: String = "both"
    var inputs: LinkedHashMap<String, ConnectorInput> = linkedMapOf()
    var request: ConnectorRequest = ConnectorRequest()

    /** What a later step reads, by name, as a path in the answer such as $.channel.id. */
    var outputs: LinkedHashMap<String, String> = linkedMapOf()

    /** JavaScript that gets inputs and request, and returns the request to send, such as with a field left out. */
    var beforeRequest: String? = null

    /** JavaScript that gets inputs, request and response, and returns what the step gives, such as one field of the answer. */
    var afterResponse: String? = null
}

class ConnectorInput {
    var label: String = ""
    var description: String = ""

    /** string, multiline, number, boolean, select or json. */
    var type: String = "string"
    var required: Boolean = false
    var secret: Boolean = false
    var defaultValue: String = ""
    var options: List<ConnectorOption> = emptyList()
}

class ConnectorOption {
    var value: String = ""
    var label: String = ""
}

/** A request with {{placeholders}} for inputs and credential values, which are filled in without being run as code. */
class ConnectorRequest {
    var method: String = "GET"
    var url: String = ""
    var headers: Map<String, String> = emptyMap()
    var query: Map<String, String> = emptyMap()
    var body: JsonNode? = null
}
