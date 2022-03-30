package com.dopplertask.dopplertask.domain.action

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.OutputType
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Table

/**
 * This action defines the start of a task.
 */
@Entity
@Table(name = "StartAction")
@DiscriminatorValue("start_action")
class StartAction : Action() {
    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val result = ActionResult()
        result.output = "--- Task execution started ---"
        result.statusCode = StatusCode.SUCCESS
        result.outputType = OutputType.STRING
        return result
    }

    override val description: String
        get() = "This is the start of every task."
}