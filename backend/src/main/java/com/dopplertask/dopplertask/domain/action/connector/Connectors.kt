package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import org.slf4j.LoggerFactory
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.net.URI

/**
 * The connectors that come with DopplerTask, read once from the connector files. A file with a problem is left out and
 * logged, so that a broken connector is never offered and the others still are. The build test refuses such a file.
 */
object Connectors {

    private const val FILES = "classpath*:connectors/*.json"
    private val NAME = Regex("[a-z0-9][a-z0-9-]*")
    private val ACTION_NAME = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")
    private val LOG = LoggerFactory.getLogger(Connectors::class.java)
    const val CREDENTIAL = "credential."
    private val PLACEHOLDER = Regex("\\{\\{\\s*([\\w.]+)\\s*}}")

    private val byName: Map<String, Connector> by lazy {
        PathMatchingResourcePatternResolver().getResources(FILES).mapNotNull { file ->
            val connector = JsonApi.mapper.readValue(file.inputStream, Connector::class.java)
            val problems = problemsOf(connector)
            if (problems.isNotEmpty()) LOG.error("The connector file {} is left out: {}", file.filename, problems.joinToString(" "))
            connector.takeIf { problems.isEmpty() }?.let { it.name to it }
        }.toMap()
    }

    /** Where requests go instead of the connector's hosts, after the host is checked. Only tests set it. */
    @Volatile
    var localServerForTests: String? = null

    fun all(): Collection<Connector> = byName.values

    fun find(name: String?): Connector? = byName[name]

    /** A host as a person may type it into a credential, with https:// and a trailing slash left off. */
    fun hostOf(value: String) = value.trim().removePrefix("https://").trimEnd('/')

    /** The placeholders in a template, such as channel in {{channel}}. */
    fun placeholdersIn(template: String): List<String> = PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toList()

    /** A request's address as written in the file: a full address, or a path after the connector's baseUrl, such as /items. */
    fun urlOf(connector: Connector, template: String) = if (template.startsWith("/")) connector.baseUrl.trimEnd('/') + template else template

    /** The inputs a list is read with, such as base for the tables of a base. */
    fun inputsOf(list: ConnectorList): List<String> = templatesOf(list.request).flatMap(::placeholdersIn).filter { !it.startsWith(CREDENTIAL) }.distinct()

    /** The credential fields a request needs, such as user and password for {{credential.user}} and {{credential.password}}. */
    fun credentialFieldsOf(request: ConnectorRequest) = templatesOf(request).flatMap(::placeholdersIn).filter { it.startsWith(CREDENTIAL) }.map { it.removePrefix(CREDENTIAL) }

    fun templatesOf(request: ConnectorRequest) = listOf(request.url) + request.headers.values + request.query.values + listOfNotNull(request.body?.toString())

    fun fill(template: String, value: (String) -> String): String = PLACEHOLDER.replace(template) { value(it.groupValues[1]) }

    /**
     * Whether a host is one of the connector's hosts. *.example.com stands for any subdomain of it, and a host may name a
     * credential value, such as {{credential.subdomain}}.zendesk.com, which only the person who made the credential sets.
     */
    fun allows(connector: Connector, host: String?, credential: Map<String, String> = emptyMap()): Boolean = host != null && connector.hosts.any { template ->
        val allowed = fill(template) { hostOf(credential[it.removePrefix(CREDENTIAL)].orEmpty()) }
        if (allowed.startsWith("*.")) host.endsWith(allowed.substring(1)) && host.length > allowed.length - 1 else host.equals(allowed, ignoreCase = true)
    }

    /** What is wrong with a connector file, in sentences, or nothing when it may be offered. */
    fun problemsOf(connector: Connector): List<String> {
        val problems = mutableListOf<String>()
        if (!NAME.matches(connector.name)) problems += "The name \"${connector.name}\" should be lowercase letters, numbers and dashes."
        if (connector.label.isBlank()) problems += "It needs a label."
        if (connector.hosts.isEmpty()) problems += "It needs the hosts its requests may go to."
        connector.hosts.filter { it.contains("/") || it.contains(":") }.forEach { problems += "The host \"$it\" should be a name only, such as api.example.com." }
        val credential = connector.credential
        val fields = credential?.fields?.keys.orEmpty()
        val sample = fields.associateWith { "example" }
        val signIn = listOfNotNull(credential?.basic?.username, credential?.basic?.password) + credential?.headers?.values.orEmpty() + credential?.query?.values.orEmpty() +
            credential?.body?.values.orEmpty() + connector.hosts + connector.baseUrl
        signIn.flatMap(::placeholdersIn).filter { !it.startsWith(CREDENTIAL) || it.removePrefix(CREDENTIAL) !in fields }
            .forEach { problems += "The credential part refers to {{$it}}, which is not one of the credential's fields." }
        if (connector.baseUrl.isNotEmpty()) problems += addressProblems("The base address", connector, connector.baseUrl, sample)
        credential?.session?.let { session ->
            problems += addressProblems("The credential's session", connector, session.request.url, sample)
            val needs = templatesOf(session.request).flatMap(::placeholdersIn)
            credentialFieldsOf(session.request).filter { it !in fields }.forEach { problems += "The credential's session refers to {{$CREDENTIAL$it}}, which is not one of the credential's fields." }
            (session.headers.values + session.query.values).flatMap(::placeholdersIn).filter { it != ConnectorRequests.SESSION && !it.startsWith(CREDENTIAL) }
                .forEach { problems += "The credential's session sends {{$it}}; only {{session}} and credential values can go there." }
            val inputs = needs.filterNot { it.startsWith(CREDENTIAL) }
            (connector.actions.map { "The action ${it.key}" to it.value.inputs.keys } + connector.triggers.map { "The trigger ${it.key}" to it.value.inputs.keys }).forEach { (where, names) ->
                inputs.filter { it !in names }.forEach { problems += "$where has no input {{$it}}, which the credential's session needs." }
            }
        }
        if (connector.actions.isEmpty()) problems += "It needs at least one action."
        connector.lists.forEach { (name, list) -> problems += addressProblems("The list $name", connector, list.request.url, sample) }
        val signatures = listOfNotNull(connector.events?.signature?.let { "The app's events" to it }) +
            connector.triggers.mapNotNull { (key, poll) -> poll.webhook?.signature?.let { "The trigger $key" to it } }
        signatures.forEach { (where, signature) ->
            if (signature.header.isBlank() || !signature.key.contains("{{$CREDENTIAL")) problems += "$where should be signed with a header and a key from the credential."
            if (runCatching { javax.crypto.Mac.getInstance(signature.algorithm) }.isFailure) problems += "$where is signed with ${signature.algorithm}, which is not a known keyed hash."
        }
        val inputsWithLists = connector.actions.map { (key, operation) -> "The action $key" to operation.inputs } +
            connector.triggers.map { (key, poll) -> "The trigger $key" to poll.inputs }
        inputsWithLists.forEach { (where, inputs) ->
            inputs.filter { it.value.list.isNotEmpty() }.forEach { (name, input) ->
                val list = connector.lists[input.list]
                if (list == null) problems += "$where chooses $name from the list ${input.list}, which the connector does not have."
                else inputsOf(list).filter { it !in inputs.keys }.forEach { problems += "$where chooses $name from the list ${input.list}, which needs {{$it}}, not one of its inputs." }
            }
        }
        connector.actions.forEach { (key, operation) ->
            val where = "The action $key"
            if (!ACTION_NAME.matches(key)) problems += "$where should be named with letters, numbers, dashes and underscores, as the original names it."
            if (operation.label.isBlank()) problems += "$where needs a label."
            val request = operation.request
            problems += addressProblems(where, connector, request.url, sample)
            val known = operation.inputs.keys + fields.map { CREDENTIAL + it } + listOfNotNull(ConnectorAction.RESUME_URL.takeIf { operation.resume != null })
            val templates = templatesOf(request)
            templates.flatMap(::placeholdersIn).filter { it !in known }
                .forEach { problems += "$where refers to {{$it}}, which is not one of its inputs." }
            templates.drop(1).flatMap(::placeholdersIn).filter { it.startsWith(CREDENTIAL) }
                .forEach { problems += "$where puts {{$it}} in the request. Credential values belong in the credential's headers or query." }
            if (operation.result.isNotEmpty() && !operation.result.startsWith("$")) problems += "$where's result ${operation.result} should start with $."
            operation.outputs.filter { !it.value.startsWith("$") }.forEach { problems += "$where gives ${it.key} from ${it.value}, which should start with $." }
        }
        connector.triggers.forEach { (key, poll) ->
            val where = "The trigger $key"
            if (poll.label.isBlank()) problems += "$where needs a label."
            val webhook = poll.webhook
            val requests = if (webhook == null) listOf(poll.request) else listOfNotNull(webhook.subscribe, webhook.unsubscribe)
            if (webhook != null && webhook.subscribe == null && webhook.unsubscribe != null) problems += "$where unsubscribes, but has no subscribe request: the person sets it up in the service."
            if (webhook != null && webhook.subscribe == null && webhook.renewEvery > 0) problems += "$where renews, but has no subscribe request to renew."
            if (webhook?.app == true && connector.events == null) problems += "$where takes the app's events, but the connector has no events."
            if (webhook?.app == true && (webhook.reply != null || webhook.signature != null)) problems += "$where takes the app's events, whose reply and signature are the connector's."
            requests.forEach { problems += addressProblems(where, connector, it.url, sample) }
            val known = poll.inputs.keys + fields.map { CREDENTIAL + it } + setOf("webhookUrl", "webhookId", ConnectorTrigger.LAST_POLL_TIME, ConnectorTrigger.LAST_ITEM)
            requests.flatMap(::templatesOf).flatMap(::placeholdersIn)
                .filter { it !in known }.forEach { problems += "$where refers to {{$it}}, which is not one of its inputs." }
            if (webhook == null && poll.newBy !in setOf("time", "lastItem", "unseen")) problems += "$where should find new items by time, lastItem or unseen, not ${poll.newBy}."
            listOf(poll.items, poll.key, poll.emit).filter { !it.startsWith("$") }.forEach { problems += "$where reads $it, which should start with $." }
        }
        return problems
    }

    private fun addressProblems(where: String, connector: Connector, written: String, sample: Map<String, String>): List<String> {
        val template = urlOf(connector, written)
        val host = template.substringAfter("://").substringBefore('/')
        // An input may never decide the host, since the credential goes wherever the request goes.
        if (placeholdersIn(host).any { !it.startsWith(CREDENTIAL) }) return listOf("$where has an input in its host.")
        val address = try {
            URI.create(fill(template) { if (it.startsWith(CREDENTIAL)) sample[it.removePrefix(CREDENTIAL)].orEmpty() else "x" })
        } catch (e: IllegalArgumentException) {
            return listOf("$where has an address that is not valid: $template.")
        }
        return when {
            address.scheme != "https" -> listOf("$where should use https.")
            !allows(connector, address.host, sample) -> listOf("$where goes to ${address.host}, which is not in the connector's hosts.")
            else -> emptyList()
        }
    }
}
