package com.dopplertask.dopplertask.domain.action.integration.code

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException

@Entity
@Table(name = "CircleCIAction")
@DiscriminatorValue("circleci_action")
class CircleCIAction : Action() {
    override val runsInPreview get() = true

    @Column
    var baseUrl: String? = "https://circleci.com"

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "startPipeline"

    @Column
    var projectSlug: String? = null

    @Column
    var branch: String? = null

    @Column
    var pipelineId: String? = null

    @Column
    var workflowId: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val tokenValue = read(token).trim()
        val operationValue = read(operation)
        val projectValue = read(projectSlug).trim()
        val pipelineValue = read(pipelineId).trim()
        val workflowValue = read(workflowId).trim()
        if (tokenValue.isBlank()) return ActionResult.failure("CircleCI needs a personal API token.")
        if (operationValue == START_PIPELINE && projectValue.isBlank()) return ActionResult.failure("CircleCI needs the project slug, such as gh/acme/shop.")
        if (operationValue == READ_PIPELINE && pipelineValue.isBlank()) return ActionResult.failure("CircleCI needs the ID of the pipeline.")
        if ((operationValue == RERUN_WORKFLOW || operationValue == CANCEL_WORKFLOW) && workflowValue.isBlank()) return ActionResult.failure("CircleCI needs the ID of the workflow.")

        val headers = mapOf("Circle-Token" to tokenValue)
        val api = JsonApi.baseAddress(read(baseUrl)) + "/api/v2"
        val workflow = "$api/workflow/${JsonApi.encoded(workflowValue)}"
        val result = when (operationValue) {
            START_PIPELINE -> JsonApi.call("CircleCI", "POST", "$api/project/${projectValue.split("/").joinToString("/") { JsonApi.encoded(it) }}/pipeline", headers, read(branch).trim().takeIf { it.isNotEmpty() }?.let { mapOf("branch" to it) } ?: mapOf<String, String>())
            READ_PIPELINE -> JsonApi.call("CircleCI", "GET", "$api/pipeline/${JsonApi.encoded(pipelineValue)}", headers, null)
            RERUN_WORKFLOW -> JsonApi.call("CircleCI", "POST", "$workflow/rerun", headers, mapOf<String, String>())
            CANCEL_WORKFLOW -> JsonApi.call("CircleCI", "POST", "$workflow/cancel", headers, null)
            else -> return ActionResult.failure("CircleCI has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS && (operationValue == START_PIPELINE || operationValue == READ_PIPELINE)) {
            val answer = JsonApi.parsed(result)
            result.outputVariables["pipelineId"] = answer.path("id").asText()
            result.outputVariables["pipelineNumber"] = answer.path("number").asText()
            result.outputVariables["pipelineState"] = answer.path("state").asText()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("pipelineId" to "Pipeline", "pipelineNumber" to "Pipeline number", "pipelineState" to "State")

    override val description: String
        get() = "Starts or reads a CircleCI pipeline, or reruns or cancels a workflow"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForStartPipeline = mapOf("operation" to arrayOf(START_PIPELINE))
            val showForReadPipeline = mapOf("operation" to arrayOf(READ_PIPELINE))
            val showForRerunWorkflowCancelWorkflow = mapOf("operation" to arrayOf(RERUN_WORKFLOW, CANCEL_WORKFLOW))
            actionInfo.add(PropertyInformation("token", "Personal API token", PropertyInformationType.STRING, "", "Under User settings, Personal API Tokens.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, START_PIPELINE, "", listOf(
                PropertyInformation(START_PIPELINE, "Start a pipeline"),
                PropertyInformation(READ_PIPELINE, "Read a pipeline"),
                PropertyInformation(RERUN_WORKFLOW, "Rerun a workflow"),
                PropertyInformation(CANCEL_WORKFLOW, "Cancel a workflow")
            )))
            actionInfo.add(PropertyInformation("projectSlug", "Project slug", PropertyInformationType.STRING, "", "Such as gh/acme/shop, under Project settings, Overview.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForStartPipeline))
            actionInfo.add(PropertyInformation("branch", "Branch", PropertyInformationType.STRING, "", "Leave empty for the default branch.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForStartPipeline))
            actionInfo.add(PropertyInformation("pipelineId", "Pipeline ID", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadPipeline))
            actionInfo.add(PropertyInformation("workflowId", "Workflow ID", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForRerunWorkflowCancelWorkflow))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://circleci.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val START_PIPELINE = "startPipeline"
        const val READ_PIPELINE = "readPipeline"
        const val RERUN_WORKFLOW = "rerunWorkflow"
        const val CANCEL_WORKFLOW = "cancelWorkflow"
    }
}
