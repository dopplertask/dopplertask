package com.dopplertask.dopplertask.domain.action.common

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.ExecutionParameter
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.LinkedTaskNotFoundException
import com.dopplertask.dopplertask.service.TaskRequest
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import com.fasterxml.jackson.annotation.JsonIgnore
import java.io.IOException
import java.util.function.Consumer
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.OneToMany
import javax.persistence.Table

@Entity
@Table(name = "LinkedTaskAction")
@DiscriminatorValue("linkedtask_action")
class LinkedTaskAction : Action() {
    @Column
    var name: String? = null

    @Column
    @JsonIgnore
    var checksum: String? = null

    @Column
    @OneToMany(mappedBy = "linkedTaskAction", cascade = [CascadeType.ALL])
    private var parameters: List<LinkedTaskParameter> = ArrayList()

    override fun run(
            taskService: TaskService,
            execution: TaskExecution,
            variableExtractorUtil: VariableExtractorUtil,
            broadcastListener: BroadcastListener?
    ): ActionResult {
        if (execution.depth < MAX_LINKED_TASK_DEPTH) {
            val taskRequest = TaskRequest()
            taskRequest.taskName = name
            taskRequest.checksum = checksum
            // Increase depth by one
            taskRequest.depth = execution.depth + 1

            val passedLinkedTaskParameters: MutableMap<String, ExecutionParameter> = mutableMapOf()
            parameters.forEach(Consumer { linkedActionParameter: LinkedTaskParameter ->
                try {
                    val parameterName = variableExtractorUtil.extract(
                            linkedActionParameter.parameterName,
                            execution,
                            scriptLanguage
                    );
                    passedLinkedTaskParameters[parameterName] = ExecutionParameter(parameterName, variableExtractorUtil.extract(linkedActionParameter.parameterValue, execution, scriptLanguage).toByteArray(), false)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            })
            taskRequest.parameters = passedLinkedTaskParameters
            try {
                val taskExecution = taskService.runRequest(taskRequest)
                if (taskExecution != null) {
                    var standardOutput = StringBuilder()
                    return if (taskExecution.isSuccess) {
                        val actionResult = ActionResult()
                        actionResult.statusCode = StatusCode.SUCCESS
                        standardOutput = getExecutionLogsAsString(taskExecution)
                        standardOutput.append("Successfully executed linked task [name=$name]")
                        actionResult.output = standardOutput.toString()
                        actionResult
                    } else {
                        val actionResult = ActionResult()
                        actionResult.statusCode = StatusCode.FAILURE
                        standardOutput.append("Linked task execution failed [name=$name]")
                        standardOutput = getExecutionLogsAsString(taskExecution)
                        actionResult.errorMsg = standardOutput.toString()
                        actionResult
                    }
                }
            }
            catch (e: LinkedTaskNotFoundException) {
                val actionResult = ActionResult()
                actionResult.statusCode = StatusCode.FAILURE
                actionResult.errorMsg = e.message.toString()
                return actionResult
            }
        }
        val actionResult = ActionResult()
        actionResult.statusCode = StatusCode.FAILURE
        actionResult.output = "Could not find the requested linked task [name=$name]"
        return actionResult
    }

    /**
     * Loop over all logs and compress into a string with new lines.
     *
     * @param taskExecution to extract logs from.
     * @return a string builder containing all log messages.
     */
    private fun getExecutionLogsAsString(taskExecution: TaskExecution): StringBuilder {
        val standardOutput = StringBuilder()
        val logSize = taskExecution.logs.size;
        for ((i, log) in taskExecution.logs.withIndex()) {
            if (i != 0 && log.isBroadcasted && i != (logSize - 1)) {
                standardOutput.append("[" + name + "] " + log.output + "\n")
            }
        }
        return standardOutput
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
        get() = "Execute another task"

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