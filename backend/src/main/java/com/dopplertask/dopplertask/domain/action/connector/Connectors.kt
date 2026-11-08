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
    private const val CREDENTIAL = "credential."
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
        if (connector.actions.isEmpty()) problems += "It needs at least one action."
        connector.actions.forEach { (key, operation) ->
            val where = "The action $key"
            if (!ACTION_NAME.matches(key)) problems += "$where should be named with letters, numbers, dashes and underscores, as the original names it."
            if (operation.label.isBlank()) problems += "$where needs a label."
            val request = operation.request
            problems += addressProblems(where, connector, request.url, sample)
            val known = operation.inputs.keys + fields.map { CREDENTIAL + it }
            val templates = listOf(request.url) + request.headers.values + request.query.values + listOfNotNull(request.body?.toString())
            templates.flatMap(::placeholdersIn).filter { it !in known }
                .forEach { problems += "$where refers to {{$it}}, which is not one of its inputs." }
            templates.drop(1).flatMap(::placeholdersIn).filter { it.startsWith(CREDENTIAL) }
                .forEach { problems += "$where puts {{$it}} in the request. Credential values belong in the credential's headers or query." }
            operation.inputs.values.mapNotNull { it.optionsFrom?.request?.url }.forEach { problems += addressProblems("$where's choices", connector, it, sample) }
            if (operation.result.isNotEmpty() && !operation.result.startsWith("$")) problems += "$where's result ${operation.result} should start with $."
            operation.outputs.filter { !it.value.startsWith("$") }.forEach { problems += "$where gives ${it.key} from ${it.value}, which should start with $." }
        }
        connector.triggers.forEach { (key, poll) ->
            val where = "The trigger $key"
            if (poll.label.isBlank()) problems += "$where needs a label."
            val webhook = poll.webhook
            val requests = if (webhook == null) listOf(poll.request) else listOfNotNull(webhook.subscribe, webhook.unsubscribe)
            requests.forEach { problems += addressProblems(where, connector, it.url, sample) }
            val known = poll.inputs.keys + fields.map { CREDENTIAL + it } + setOf("webhookUrl", "webhookId")
            requests.flatMap { listOf(it.url) + it.headers.values + it.query.values + listOfNotNull(it.body?.toString()) }.flatMap(::placeholdersIn)
                .filter { it !in known }.forEach { problems += "$where refers to {{$it}}, which is not one of its inputs." }
            if (webhook == null && poll.newBy !in setOf("time", "lastItem")) problems += "$where should find new items by time or lastItem, not ${poll.newBy}."
            listOf(poll.items, poll.key).filter { !it.startsWith("$") }.forEach { problems += "$where reads $it, which should start with $." }
        }
        return problems
    }

    private fun addressProblems(where: String, connector: Connector, template: String, sample: Map<String, String>): List<String> {
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
