package com.dopplertask.dopplertask.domain.action.common

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import java.util.function.Consumer
import javax.persistence.*

@Entity
@Table(name = "SetVariableAction")
@DiscriminatorValue("setvariable_action")
class SetVariableAction : Action() {
    @OneToMany(mappedBy = "setVariableAction", cascade = [CascadeType.ALL])
    private var setVariableList: List<SetVariable>? = null

    @Throws(IOException::class)
    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?): ActionResult {
        val actionResult = ActionResult()
        val builder = StringBuilder()
        for (setVariable in setVariableList!!) {
            if (setVariable.value != null) {
                val evaluatedValue = variableExtractorUtil.extract(setVariable.value, execution, scriptLanguage)
                execution.parameters[setVariable.name] = evaluatedValue
                builder.append("Setting variable [key=" + setVariable.name + ", value=" + evaluatedValue + "]\n")
            }
        }
        actionResult.output = builder.toString()
        return actionResult
    }

    fun getSetVariableList(): List<SetVariable>? {
        return setVariableList
    }

    fun setSetVariableList(setVariableList: List<SetVariable>?) {
        this.setVariableList = setVariableList
        this.setVariableList!!.forEach(Consumer { setVariable: SetVariable -> setVariable.setVariableAction = this })
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("setVariableList", "Variables", PropertyInformationType.MAP, "", "", java.util.List.of(
                    PropertyInformation("name", "Name"),
                    PropertyInformation("value", "Value")
            )))
            return actionInfo
        }

    override val description: String
        get() = "Set or modify a variable"
}