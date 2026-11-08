package com.dopplertask.dopplertask.domain.action.connector

import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.domain.action.integration.OAuthSignIn

/** The settings of a connector step or trigger, kept as one JSON object since every connector asks for different ones. */
object ConnectorSettings {

    private val TYPES = mapOf(
        "string" to PropertyInformationType.STRING, "multiline" to PropertyInformationType.MULTILINE, "json" to PropertyInformationType.MULTILINE,
        "number" to PropertyInformationType.NUMBER, "boolean" to PropertyInformationType.BOOLEAN, "select" to PropertyInformationType.DROPDOWN,
        "file" to PropertyInformationType.FILE
    )

    // The connector's file names the service's sign-in addresses, so a person only gives the client.
    private val FIXED_BY_FILE = setOf("oauthProvider", "oauthAuthUrl", "oauthTokenUrl", "oauthAuthParameters")

    /** An input as the editor and agents see it, shown only for the choices it belongs to. */
    fun propertyOf(name: String, input: ConnectorInput, shownFor: Map<String, Array<String>>, category: PropertyInformationCategory = PropertyInformationCategory.PROPERTY) =
        PropertyInformation(name, input.label, when {
            input.fieldsFrom != null -> PropertyInformationType.FIELDS
            input.optionsFrom != null -> PropertyInformationType.CHOICES
            else -> TYPES[input.type] ?: PropertyInformationType.STRING
        },
            input.defaultValue, input.description, input.options.map { PropertyInformation(it.value, it.label) }, category, shownFor)
            .also { it.required = input.required }

    /** What a credential for the connector holds, the same for its actions and its triggers. */
    fun credentialProperties(connector: Connector): List<PropertyInformation> =
        connector.credential?.fields.orEmpty().map { (name, field) ->
            propertyOf(name, field, mapOf(), if (field.secret) PropertyInformationCategory.SECRET else PropertyInformationCategory.CREDENTIAL)
        } + connector.credential?.oauth2?.let { oauth -> OAuthSignIn.fields(connector.name, oauth.scope).filter { it.name !in FIXED_BY_FILE } }.orEmpty()

    fun read(settings: String): Map<String, String> =
        JsonApi.mapper.readValue(settings, LinkedHashMap::class.java).entries.associate { it.key.toString() to it.value.toString() }

    fun without(settings: String, names: Set<String>): String = JsonApi.mapper.writeValueAsString(read(settings) - names)

    fun with(settings: String, name: String, value: Any?): String {
        val text = if (value is String) value else JsonApi.mapper.writeValueAsString(value)
        return JsonApi.mapper.writeValueAsString(read(settings) + (name to text))
    }
}
