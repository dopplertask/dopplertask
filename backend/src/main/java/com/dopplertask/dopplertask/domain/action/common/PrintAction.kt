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
import javax.persistence.*

@Entity
@Table(name = "PrintAction")
@DiscriminatorValue("print_action")
class PrintAction : Action {
    @Lob
    @Column(columnDefinition = "TEXT")
    var message: String? = null

    constructor() {}
    constructor(message: String?) {
        this.message = message
    }

    @Throws(IOException::class)
    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?): ActionResult {
        val messageVariable = variableExtractorUtil.extract(message, execution, scriptLanguage)
        val actionResult = ActionResult()
        actionResult.isBroadcastMessage = true
        if (messageVariable.isNotEmpty()) {
            actionResult.output = messageVariable
            actionResult.statusCode = StatusCode.SUCCESS
        } else {
            actionResult.errorMsg = "No output"
            actionResult.statusCode = StatusCode.FAILURE
        }
        return actionResult
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("message", "Message", PropertyInformationType.MULTILINE))
            return actionInfo
        }

    override val description: String = "Print a console message"
}