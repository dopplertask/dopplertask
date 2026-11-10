package com.dopplertask.dopplertask.domain.action.common

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import java.util.function.Consumer
import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "SwitchAction")
@DiscriminatorValue("switch_action")
class SwitchAction : Action() {
    override val runsInPreview get() = true

    var value: String? = null

    @OneToMany(mappedBy = "switchAction", cascade = [CascadeType.ALL])
    private var switchCases: List<SwitchCase> = ArrayList()

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val actionResult = ActionResult()
        if (value.isNullOrEmpty()) {
            actionResult.statusCode = StatusCode.FAILURE
            actionResult.errorMsg = "Please enter a condition."
            return actionResult
        }

        val evaluatedValue = evaluate(value, execution, variableExtractorUtil)
        val cases = switchCases.map { evaluate(it.currentCase, execution, variableExtractorUtil) }
        val matchedCase = cases.indexOf(evaluatedValue)

        // A port after the last case is the default path.
        val portNr = if (matchedCase >= 0) matchedCase else cases.size
        if (portNr < outputPorts.size) {
            actionResult.output = "Switch evaluated to port nr: $portNr"
            execution.currentAction = outputPorts[portNr].connectionSource?.target?.action
        } else {
            actionResult.statusCode = StatusCode.FAILURE
            actionResult.errorMsg = "No case or output port matched the value [$evaluatedValue]. Add one more output port to use as the default path."
        }
        return actionResult
    }

    private fun evaluate(text: String?, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil): String =
        when (scriptLanguage) {
            ScriptLanguage.VELOCITY -> variableExtractorUtil.extract(text, execution, scriptLanguage)
            ScriptLanguage.JAVASCRIPT -> variableExtractorUtil.extract("\"$text\"", execution, scriptLanguage)
        }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(
                PropertyInformation(
                    "value",
                    "Value",
                    PropertyInformationType.STRING,
                    "",
                    "Value to compare. The first output ports match the cases in order, and one more output port is the default path."
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "switchCases", "Cases", PropertyInformationType.MAP, "", "Cases to match the value", mutableListOf(
                        PropertyInformation("currentCase", "Case")
                    )
                )
            )
            return actionInfo
        }

    override val description: String
        get() = "Evaluate a condition and route to the desired path"

    fun getSwitchCases(): List<SwitchCase> {
        return switchCases
    }

    fun setSwitchCases(switchCases: List<SwitchCase>) {
        switchCases.forEach(Consumer { switchCase: SwitchCase -> switchCase.switchAction = this })
        this.switchCases = switchCases
    }
}