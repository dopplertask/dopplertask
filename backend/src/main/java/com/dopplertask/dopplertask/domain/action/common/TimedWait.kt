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
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Table

@Entity
@Table(name = "TimedWait")
@DiscriminatorValue("timedwait_action")
class TimedWait : Action {
    @Column
    var seconds: Long? = null

    constructor() {}
    constructor(seconds: Long?) {
        this.seconds = seconds
    }

    @Throws(IOException::class)
    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?): ActionResult {
        val amountOfSeconds = variableExtractorUtil.extract("" + seconds, execution, scriptLanguage)
        val actionResult = ActionResult()
        try {
            Thread.sleep(amountOfSeconds.toInt() * 1000L)
            actionResult.output = amountOfSeconds
            actionResult.statusCode = StatusCode.SUCCESS
        } catch (e: InterruptedException) {
            actionResult.errorMsg = "Interrupted wait. Error: " + e.message
            actionResult.statusCode = StatusCode.FAILURE
        } catch (e: NumberFormatException) {
            actionResult.errorMsg = "Wrong input. Error: " + e.message
            actionResult.statusCode = StatusCode.FAILURE
        }
        return actionResult
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("seconds", "Time (Seconds)", PropertyInformationType.NUMBER, "", "Amount of seconds to wait"))
            return actionInfo
        }

    override val description: String
        get() = "Waits an X amount of seconds"
}