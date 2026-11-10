package com.dopplertask.dopplertask.domain.action.common

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table

/**
 * Runs the steps on its first output once for every item of a list, then goes on along its second output with what
 * every pass gave. A pass ends where its steps end, or where they lead back here.
 */
@Entity
@Table(name = "LoopAction")
@DiscriminatorValue("loop_action")
class LoopAction : Action() {
    override val runsInPreview get() = true


    /** The list: JSON, or text with one item per line. */
    @Lob
    @Column(columnDefinition = "TEXT")
    var items: String? = PREVIOUS_OUTPUT

    /** Where the list is inside JSON, such as data.orders. */
    @Column
    var path: String? = null

    @Column
    var batchSize: String? = "1"

    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val key = ports?.firstOrNull()?.externalId
            ?: return ActionResult.failure("The loop has no input port id, so it cannot keep its place. Save the task again from the editor.")
        var state = execution.loops[key]
        if (state == null) {
            val list = try {
                itemsOf(variableExtractorUtil.extract(items?.takeUnless { it.isBlank() } ?: PREVIOUS_OUTPUT, execution, scriptLanguage), path)
            } catch (e: IllegalArgumentException) {
                execution.currentAction = null
                return ActionResult.failure(e.message ?: "The list could not be read.")
            }
            val size = batchSize?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 1
            state = LoopState(key, actionName ?: "Loop", list, size)
            execution.startLoop(state)
        } else {
            // A pass ended here, so a loop inside it, which a wire may have left, is done as well.
            execution.returnToLoop(key)
            state.results.add(valueOf(execution.logs.lastOrNull()?.output ?: ""))
        }

        if (state.hasMore()) {
            state.next()
            execution.currentAction = outputPorts.getOrNull(0)?.connectionSource?.target?.action
            val result = ActionResult(StatusCode.SUCCESS, "Item ${state.number} of ${state.count}", "")
            result.outputVariables["index"] = state.index
            result.outputVariables["count"] = state.count
            result.outputVariables["item"] = state.itemJson()
            return result
        }

        execution.endLoop(key)
        execution.currentAction = outputPorts.getOrNull(1)?.connectionSource?.target?.action
        val result = ActionResult(StatusCode.SUCCESS, "Done: ${JsonApi.mapper.writeValueAsString(state.results)}", "")
        result.outputVariables["results"] = JsonApi.mapper.writeValueAsString(state.results)
        result.outputVariables["count"] = state.count
        return result
    }

    override val description: String
        get() = "Runs the next steps once for every item of a list, then carries on with all they gave"

    override val outputs: Map<String, String>
        get() = mapOf(
            "item" to "On Each item: the current item, as JSON",
            "index" to "On Each item: where the item is in the list, from 0",
            "count" to "How many items the list has",
            "results" to "On Done: what every pass gave, as a JSON list"
        )

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val info = super.actionInfo
            info.add(PropertyInformation("items", "List", PropertyInformationType.STRING, "\$lastLog.output",
                "A JSON list, a JSON object with the list inside, or text with one item per line. Empty uses what the previous step gave."))
            info.add(PropertyInformation("path", "Field with the list", PropertyInformationType.STRING, "",
                "Where the list is inside JSON, such as data.orders. Empty uses the JSON itself, or its only list."))
            info.add(PropertyInformation("batchSize", "Items per pass", PropertyInformationType.NUMBER, "1",
                "More than one gives each pass a batch, as \$loop.batch."))
            return info
        }

    companion object {
        private const val PREVIOUS_OUTPUT = "\$lastLog.output"

        /** A JSON value as plain Java values, so that \$loop.item.name works. Text that is not JSON stays text. */
        @JvmStatic
        fun valueOf(text: String): Any? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return ""
            return try {
                JsonApi.mapper.readValue(trimmed, Any::class.java)
            } catch (e: Exception) {
                text
            }
        }

        @JvmStatic
        fun itemsOf(text: String, path: String?): List<Any?> {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return emptyList()
            val parsed = if (trimmed.startsWith("[") || trimmed.startsWith("{")) valueOf(trimmed) else null
            if (parsed == null || parsed is String) {
                if (!path.isNullOrBlank()) throw IllegalArgumentException("The list is not JSON, so it has no field ${path.trim()}.")
                return trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
            }
            var value: Any? = parsed
            if (!path.isNullOrBlank()) {
                for (part in path.trim().removePrefix("$.").split('.')) {
                    value = when (value) {
                        is Map<*, *> -> if (value.containsKey(part)) value[part] else throw IllegalArgumentException("There is no field $part in the JSON. It has ${value.keys.joinToString(", ")}.")
                        is List<*> -> part.toIntOrNull()?.let { value.getOrNull(it) } ?: throw IllegalArgumentException("$part is not a position in the list.")
                        else -> throw IllegalArgumentException("The JSON has no field $part, since what is before it is not an object.")
                    }
                }
            }
            return when (value) {
                is List<*> -> value
                is Map<*, *> -> {
                    val lists = value.values.filterIsInstance<List<*>>()
                    if (path.isNullOrBlank() && lists.size == 1) lists[0] else listOf(value)
                }
                null -> emptyList()
                else -> listOf(value)
            }
        }
    }
}

/**
 * Where a loop is in its list. It is kept with the run, so that a run resumed inside a pass carries on with the rest of
 * the items. A loop is known by the port id of its step, which stays the same across versions of the task.
 */
class LoopState(
    var step: String = "",
    var name: String = "Loop",
    var items: List<Any?> = emptyList(),
    var batchSize: Int = 1,
    var nextStart: Int = 0,
    var index: Int = -1,
    var results: MutableList<Any?> = mutableListOf()
) {
    @get:JsonIgnore
    val count: Int
        get() = items.size

    /** From 1, as a person counts. */
    @get:JsonIgnore
    val number: Int
        get() = index + 1

    @get:JsonIgnore
    val batch: List<Any?>
        get() = if (index < 0) emptyList() else items.subList(index, minOf(index + batchSize, items.size))

    fun hasMore() = nextStart < items.size

    fun next() {
        index = nextStart
        nextStart += batchSize
    }

    fun itemJson(): String = JsonApi.mapper.writeValueAsString(batch.firstOrNull())

    /** What steps read as \$loop. */
    fun variables(): Map<String, Any?> = mapOf(
        "item" to batch.firstOrNull(),
        "itemJson" to itemJson(),
        "batch" to batch,
        "index" to index,
        "number" to number,
        "count" to count,
        "isFirst" to (index == 0),
        "isLast" to !hasMore(),
        "results" to results,
        "name" to name
    )
}
