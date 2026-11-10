package com.dopplertask.dopplertask.domain.action.common

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table(name = "ScriptAction")
@DiscriminatorValue("script_action")
class ScriptAction : Action() {
    override val runsInPreview get() = true

    @Lob
    @Column(columnDefinition = "TEXT")
    var script: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val actionResult = ActionResult()
        actionResult.output = variableExtractorUtil.extract(script, execution, scriptLanguage)
        return actionResult
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(
                PropertyInformation(
                    "script",
                    "Script",
                    PropertyInformationType.MULTILINE,
                    "",
                    "The value of the last line is the result."
                )
            )
            return actionInfo
        }

    override val description: String
        get() = "Run a script written in Javascript or Velocity"
}