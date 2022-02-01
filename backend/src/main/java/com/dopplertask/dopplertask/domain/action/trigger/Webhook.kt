package com.dopplertask.dopplertask.domain.action.trigger

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.OutputType
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import javax.persistence.Column
import javax.persistence.Entity

@Entity
class Webhook : Trigger() {

    @Column
    var path = ""

    @Column
    var method = "GET"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val result = ActionResult()
        result.output = "Webhook triggered"
        result.statusCode = StatusCode.SUCCESS
        result.outputType = OutputType.STRING
        return result
    }

    override fun trigger(): TriggerResult {
        // Do nothing.
        return TriggerResult(mutableMapOf())
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(
                PropertyInformation(
                    "method", "Method", PropertyInformation.PropertyInformationType.DROPDOWN, "GET", "HTTP Method",
                    mutableListOf(
                        PropertyInformation("GET", "GET"),
                        PropertyInformation("POST", "POST"),
                        PropertyInformation("PUT", "PUT"),
                        PropertyInformation("PATCH", "PATCH"),
                        PropertyInformation("DELETE", "DELETE")
                    )
                )
            )
            actionInfo.add(PropertyInformation("path", "Path", PropertyInformation.PropertyInformationType.STRING))
            return actionInfo
        }

    override val description: String
        get() = "Starts the workflow when the webhook URL is called."
}