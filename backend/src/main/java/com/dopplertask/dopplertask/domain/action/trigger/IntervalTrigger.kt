package com.dopplertask.dopplertask.domain.action.trigger

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.OutputType
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.TriggerException
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import javax.persistence.Column
import javax.persistence.Entity

@Entity
class IntervalTrigger : Trigger() {
    @Column
    var time: String? = null

    // Seconds, Minutes, Hours, Days
    @Column
    var timeUnit: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val result = ActionResult()
        result.output = "Interval triggered"
        result.statusCode = StatusCode.SUCCESS
        result.outputType = OutputType.STRING
        return result
    }

    override val description: String
        get() = "Starts the workflow at an interval"

    override fun trigger(): TriggerResult {
        var millis = 1000
        when (timeUnit) {
            "SECONDS" -> millis = 1000 * Integer.parseInt(time)
            "MINUTES" -> millis = 1000 * 60 * Integer.parseInt(time)
            "HOURS" -> millis = 1000 * 60 * 60 * Integer.parseInt(time)
            "DAYS" -> millis = 1000 * 60 * 60 * 24 * Integer.parseInt(time)
        }
        try {
            Thread.sleep(millis.toLong())
            return TriggerResult(mutableMapOf())
        } catch (e: InterruptedException) {
            e.printStackTrace()
            throw TriggerException(e)
        }
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(
                PropertyInformation(
                    "time",
                    "Interval",
                    PropertyInformation.PropertyInformationType.NUMBER
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "timeUnit",
                    "Time unit",
                    PropertyInformation.PropertyInformationType.DROPDOWN,
                    "SECONDS",
                    "",
                    java.util.List.of(
                        PropertyInformation("SECONDS", "Seconds"),
                        PropertyInformation("MINUTES", "Minutes"),
                        PropertyInformation("HOURS", "Hours"),
                        PropertyInformation("DAYS", "Days")
                    )
                )
            )
            return actionInfo
        }
}