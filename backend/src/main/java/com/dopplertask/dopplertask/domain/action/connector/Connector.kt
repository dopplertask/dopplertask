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

    /**
     * Lists of choices that inputs share by name, such as the bases of the account and the tables of a base. A list's
     * request can use the inputs of the action it is chosen in, and is read again when one of them changes.
     */
    var lists: LinkedHashMap<String, ConnectorList> = linkedMapOf()
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
     * newest item already seen, by its id at key, in a list that starts with the newest. "unseen": its key at key was
     * not in the list before, for a list in no order.
     */
    var newBy: String = "time"
    var key: String = "$.id"

    /**
     * Scripts as on an action, such as afterResponse asking for every page with call(...) and returning the items. They
     * also have memory, an object kept from one check or event to the next, such as the cursor a service gives.
     */
    var beforeRequest: String? = null
    var afterResponse: String? = null

    /** Instead of asking: the service sends events to {{webhookUrl}}, and items and afterResponse read each event. */
    var webhook: ConnectorWebhook? = null
}

/** How a trigger subscribes to a service's events when its task is turned on, and unsubscribes when it is turned off. */
class ConnectorWebhook {
    /**
     * The request that subscribes, with {{webhookUrl}} where the service should send events. Without one, the person
     * copies the trigger's address into the service.
     */
    var subscribe: ConnectorRequest? = null

    /** Where the subscription's id is in the answer, which unsubscribe can name as {{webhookId}}. */
    var id: String = "$.id"
    var unsubscribe: ConnectorRequest? = null

    /** Scripts for the subscribe request, such as asking for the account first; afterResponse returns the id. */
    var beforeRequest: String? = null
    var afterResponse: String? = null

    /**
     * What the address answers to a delivery, such as the challenge a service sends to check the address, which it
     * expects back. The script reads the event as response and returns the body of the answer, or null for none.
     */
    var reply: String? = null
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

    /** The name of one of the connector's lists to choose the value from. The step itself sends the value. */
    var list: String = ""

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

class ConnectorList {
    var request: ConnectorRequest = ConnectorRequest()

    /** Where the items are in the answer, and the label and value of each item. */
    var items: String = "$"
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
