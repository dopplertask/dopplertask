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

    /** What starts a task: the service is asked every few minutes for what is new. */
    var triggers: LinkedHashMap<String, ConnectorPoll> = linkedMapOf()

    /** Where the file was copied from, so that a later copy can be compared with it. */
    var upstream: Map<String, String> = emptyMap()
}

/** What a credential for the connector holds, and where each value goes in a request. */
class ConnectorCredential {
    var fields: LinkedHashMap<String, ConnectorInput> = linkedMapOf()
    var headers: Map<String, String> = emptyMap()
    var query: Map<String, String> = emptyMap()

    /** Fields added to a JSON body, for services that take the key in the body. */
    var body: Map<String, String> = emptyMap()
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

    /** What the action works on, such as Contacts, so that a long list of actions reads by group. */
    var group: String = ""

    /** Who the original made the action for: both, a person, or an agent. Kept so that an importer can map it. */
    var audience: String = "both"
    var inputs: LinkedHashMap<String, ConnectorInput> = linkedMapOf()
    var request: ConnectorRequest = ConnectorRequest()

    /** What a later step reads, by name, as a path in the answer such as $.channel.id. */
    var outputs: LinkedHashMap<String, String> = linkedMapOf()

    /** For an answer that is a file: the name the run keeps it by, such as {{fileName}} or invoice.pdf. */
    var download: String? = null

    /** The part of the answer the step gives, such as $.data. Empty gives the whole answer. */
    var result: String = ""

    /** JavaScript that gets inputs and request, and returns the request to send, such as with a field left out. */
    var beforeRequest: String? = null

    /**
     * JavaScript that gets inputs, request and response, and returns what the step gives, such as one field of the answer.
     * Both scripts can send more requests with call({method, url, query, headers, body}), such as for the next page.
     */
    var afterResponse: String? = null
}

/**
 * A trigger that starts the task once for each new item. It asks the service for its newest items every few minutes,
 * or, with webhook, has the service send each event to this server.
 */
class ConnectorPoll {
    var label: String = ""
    var description: String = ""
    var inputs: LinkedHashMap<String, ConnectorInput> = linkedMapOf()
    var request: ConnectorRequest = ConnectorRequest()

    /** Where the list of items is in the answer. */
    var items: String = "$"

    /**
     * What makes an item new. "time": its time at key is later than the last check. "lastItem": it comes before the
     * newest item already seen, by its id at key, in a list that starts with the newest.
     */
    var newBy: String = "time"
    var key: String = "$.id"

    /** Scripts as on an action, such as afterResponse asking for every page with call(...) and returning the items. */
    var beforeRequest: String? = null
    var afterResponse: String? = null

    /** Instead of asking: the service sends events to {{webhookUrl}}, and items and afterResponse read each event. */
    var webhook: ConnectorWebhook? = null
}

/** How a trigger subscribes to a service's events when its task is turned on, and unsubscribes when it is turned off. */
class ConnectorWebhook {
    /** The request that subscribes, with {{webhookUrl}} where the service should send events. */
    var subscribe: ConnectorRequest = ConnectorRequest()

    /** Where the subscription's id is in the answer, which unsubscribe can name as {{webhookId}}. */
    var id: String = "$.id"
    var unsubscribe: ConnectorRequest? = null

    /** Scripts for the subscribe request, such as asking for the account first; afterResponse returns the id. */
    var beforeRequest: String? = null
    var afterResponse: String? = null
}

class ConnectorInput {
    var label: String = ""
    var description: String = ""

    /** string, multiline, number, boolean, select, json, or file: the name of a file the run keeps, sent as base64 or as a file part. */
    var type: String = "string"
    var required: Boolean = false
    var secret: Boolean = false
    var defaultValue: String = ""
    var options: List<ConnectorOption> = emptyList()

    /** Where a list of choices comes from, such as the audiences of the account. The step itself sends the value. */
    var optionsFrom: ConnectorOptionsSource? = null

    /**
     * Where the fields of a form come from, such as the columns of a table. The person fills in each one, and the input
     * holds them as one JSON object.
     */
    var fieldsFrom: ConnectorFieldsSource? = null
}

class ConnectorFieldsSource {
    var request: ConnectorRequest = ConnectorRequest()

    /** Where the list of fields is in the answer, and each field's key, label and type in it. */
    var list: String = "$"
    var key: String = "$.id"
    var label: String = "$.name"
    var type: String = ""

    /** The service's type names as ours: string, multiline, number, boolean or json. Others are text. */
    var types: Map<String, String> = emptyMap()
}

/** A field of a form that follows the service. */
class ConnectorField {
    var key: String = ""
    var label: String = ""
    var type: String = "string"
}

class ConnectorOptionsSource {
    var request: ConnectorRequest = ConnectorRequest()

    /** Where the list is in the answer, and the label and value of each item in it. */
    var list: String = "$"
    var label: String = "$.name"
    var value: String = "$.id"
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
