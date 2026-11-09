package com.dopplertask.dopplertask.domain.action

import com.dopplertask.dopplertask.domain.action.connector.ConnectorAction
import com.dopplertask.dopplertask.domain.action.connector.ConnectorTrigger
import com.dopplertask.dopplertask.domain.action.connector.Connectors
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import tools.jackson.databind.DatabindContext
import tools.jackson.databind.JavaType
import tools.jackson.databind.jsontype.impl.TypeIdResolverBase

/**
 * Every kind of step, by the name that tasks, the editor and agents use. A class registered on Action gives a kind, and
 * so does each connector file, so that both are listed, saved and signed in the same way.
 */
object StepTypes {

    /** The kinds that a class gives. Their tables are named after these classes. */
    val classes: Map<String, Class<out Action>> = Action::class.java.getAnnotation(JsonSubTypes::class.java).value
        .associate { it.name to it.value.java.asSubclass(Action::class.java) }

    val names: Set<String>
        get() = classes.keys + Connectors.all().map { it.name } + Connectors.all().filter { it.triggers.isNotEmpty() }.map { it.name + ConnectorTrigger.KIND_SUFFIX }

    /** A new, empty step of a kind, which describes the kind. */
    fun newStep(name: String?): Action? = classes[name]?.getDeclaredConstructor()?.newInstance()
        ?: Connectors.find(name)?.let { ConnectorAction.of(it.name) }
        ?: Connectors.find(name?.removeSuffix(ConnectorTrigger.KIND_SUFFIX))?.takeIf { name!!.endsWith(ConnectorTrigger.KIND_SUFFIX) }?.let { ConnectorTrigger.of(it.name) }

    fun all(): List<Action> = names.mapNotNull(::newStep)
}

/** Reads and writes a step's @type as the name of its kind, which for a connector is the connector, not the class. */
class StepTypeIdResolver : TypeIdResolverBase() {
    override fun idFromValue(context: DatabindContext, value: Any): String = (value as Action).stepTypeName

    override fun idFromValueAndType(context: DatabindContext, value: Any?, suggestedType: Class<*>): String =
        (value as? Action)?.stepTypeName ?: suggestedType.simpleName

    override fun typeFromId(context: DatabindContext, id: String): JavaType? {
        return StepTypes.newStep(id)?.let { context.constructType(it.javaClass) }
    }

    override fun getMechanism(): JsonTypeInfo.Id = JsonTypeInfo.Id.NAME

    override fun getDescForKnownTypeIds(): String = StepTypes.names.joinToString(", ")
}
