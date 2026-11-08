package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.net.URI

/**
 * The connectors that come with DopplerTask, read once from the connector files. A file with a problem stops the
 * server from starting and says which file and why, so that a broken connector is never offered to a person.
 */
object Connectors {

    private const val FILES = "classpath*:connectors/*.json"
    private val NAME = Regex("[a-z0-9][a-z0-9-]*")
    private val PLACEHOLDER = Regex("\\{\\{\\s*([\\w.]+)\\s*}}")

    private val byName: Map<String, Connector> by lazy {
        PathMatchingResourcePatternResolver().getResources(FILES).associate { file ->
            val connector = JsonApi.mapper.readValue(file.inputStream, Connector::class.java)
            val problems = problemsOf(connector)
            check(problems.isEmpty()) { "The connector file ${file.filename} has problems: ${problems.joinToString(" ")}" }
            connector.name to connector
        }
    }

    /** Where requests go instead of the connector's hosts, after the host is checked. Only tests set it. */
    @Volatile
    var localServerForTests: String? = null

    fun all(): Collection<Connector> = byName.values

    fun find(name: String?): Connector? = byName[name]

    /** The placeholders in a template, such as channel in {{channel}}. */
    fun placeholdersIn(template: String): List<String> = PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toList()

    fun fill(template: String, value: (String) -> String): String = PLACEHOLDER.replace(template) { value(it.groupValues[1]) }

    /** Whether a host is one of the connector's hosts, where *.example.com stands for any subdomain of it. */
    fun allows(connector: Connector, host: String?): Boolean = host != null && connector.hosts.any { allowed ->
        if (allowed.startsWith("*.")) host.endsWith(allowed.substring(1)) && host.length > allowed.length - 1 else host.equals(allowed, ignoreCase = true)
    }

    /** What is wrong with a connector file, in sentences, or nothing when it may be offered. */
    fun problemsOf(connector: Connector): List<String> {
        val problems = mutableListOf<String>()
        if (!NAME.matches(connector.name)) problems += "The name \"${connector.name}\" should be lowercase letters, numbers and dashes."
        if (connector.label.isBlank()) problems += "It needs a label."
        if (connector.hosts.isEmpty()) problems += "It needs the hosts its requests may go to."
        connector.hosts.filter { it.contains("/") || it.contains(":") }.forEach { problems += "The host \"$it\" should be a name only, such as api.example.com." }
        val credentialFields = connector.credential?.fields?.keys.orEmpty()
        connector.credential?.let { credential ->
            (credential.headers.values + credential.query.values).flatMap(::placeholdersIn)
                .filter { it.removePrefix("credential.") !in credentialFields || !it.startsWith("credential.") }
                .forEach { problems += "The credential refers to {{$it}}, which is not one of its fields." }
        }
        if (connector.actions.isEmpty()) problems += "It needs at least one action."
        connector.actions.forEach { (key, operation) ->
            val where = "The action $key"
            if (!NAME.matches(key.replace('_', '-'))) problems += "$where should be named with lowercase letters, numbers and underscores."
            if (operation.label.isBlank()) problems += "$where needs a label."
            val request = operation.request
            val address = try {
                URI.create(fill(request.url) { "x" })
            } catch (e: IllegalArgumentException) {
                null
            }
            when {
                address == null -> problems += "$where has an address that is not valid: ${request.url}."
                address.scheme != "https" -> problems += "$where should use https."
                // An input may never decide the host, since the credential goes wherever the request goes.
                request.url.substringAfter("://").substringBefore('/').contains("{{") -> problems += "$where has a placeholder in its host."
                !allows(connector, address.host) -> problems += "$where goes to ${address.host}, which is not in the connector's hosts."
            }
            val known = operation.inputs.keys
            val templates = listOf(request.url) + request.headers.values + request.query.values + listOfNotNull(request.body?.toString())
            templates.flatMap(::placeholdersIn).filter { it !in known }
                .forEach { problems += "$where refers to {{$it}}, which is not one of its inputs." }
            operation.outputs.filter { !it.value.startsWith("$") }.forEach { problems += "$where gives ${it.key} from ${it.value}, which should start with $." }
        }
        return problems
    }
}
