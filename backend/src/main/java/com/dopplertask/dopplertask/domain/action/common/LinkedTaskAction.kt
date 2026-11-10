package com.dopplertask.dopplertask.domain.action.common

import com.dopplertask.dopplertask.domain.Preview
import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.ExecutionParameter
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.TaskExecutionStatus
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskRequest
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.util.function.Consumer
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "LinkedTaskAction")
@DiscriminatorValue("linkedtask_action")
class LinkedTaskAction : Action() {
    override val runsInPreview get() = true

    @Column
    var name: String? = null

    @Column
    @OneToMany(mappedBy = "linkedTaskAction", cascade = [CascadeType.ALL])
    private var parameters: List<LinkedTaskParameter> = ArrayList()

    override fun run(
            taskService: TaskService,
            execution: TaskExecution,
            variableExtractorUtil: VariableExtractorUtil,
            broadcastListener: BroadcastListener?
    ): ActionResult {
        if (execution.depth >= MAX_LINKED_TASK_DEPTH) {
            return ActionResult.failure("Tasks run each other more than $MAX_LINKED_TASK_DEPTH levels deep.")
        }
        val taskRequest = TaskRequest()
        taskRequest.taskName = name
        taskRequest.depth = execution.depth + 1
        // The task this step runs sends nothing either, when this run sends nothing.
        if (Preview.isHolding()) taskRequest.preview = Preview()
        taskRequest.parameters = parameters.filter { !it.parameterName.isNullOrEmpty() }.associateTo(mutableMapOf()) { given ->
            val value = variableExtractorUtil.extract(given.parameterValue, execution, scriptLanguage)
            given.parameterName!! to ExecutionParameter(given.parameterName, value.toByteArray(), false)
        }

        val linkedRun = taskService.runRequest(taskRequest) ?: return ActionResult.failure("The task $name was not found.")
        if (linkedRun.status == TaskExecutionStatus.WAITING) {
            // Its answer would otherwise continue it alone, long after this run went on.
            val reason = "The task $name waits for an answer. A task that another task runs has to finish in one go."
            taskService.failRun(linkedRun.id, reason)
            return ActionResult.failure(reason)
        }
        if (!linkedRun.isSuccess) {
            return ActionResult.failure("The task $name failed: ${linkedRun.failureReason?.ifBlank { null } ?: "it was stopped"}")
        }
        // The answer of this step is the answer of the last step of the task, so later steps read it as their own.
        return ActionResult().apply { output = linkedRun.steps.lastOrNull()?.output.orEmpty() }
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("name", "Task name"))
            actionInfo.add(
                    PropertyInformation(
                            "parameters",
                            "Parameters",
                            PropertyInformation.PropertyInformationType.MAP,
                            "",
                            "Parameters for the linked task",
                            listOf(
                                    PropertyInformation("parameterName", "Name"),
                                    PropertyInformation("parameterValue", "Value ")
                            )
                    )
            )
            return actionInfo
        }

    override val description: String
        get() = "Runs another task and gives the answer of its last step"

    companion object {
        private const val MAX_LINKED_TASK_DEPTH = 100
    }

    fun getParameters(): List<LinkedTaskParameter> {
        return parameters;
    }

    fun setParameters(parameters: List<LinkedTaskParameter>) {
        parameters.forEach(Consumer { parameter: LinkedTaskParameter -> parameter.linkedTaskAction = this })
        this.parameters = parameters
    }
}