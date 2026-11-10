package com.dopplertask.dopplertask.domain.action.common

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.xml.XmlMapper
import java.io.IOException
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table(name = "XMLAction")
@DiscriminatorValue("xml_action")
class XMLAction : Action() {
    override val runsInPreview get() = true

    @Lob
    @Column(columnDefinition = "TEXT")
    var content: String? = null

    @Column
    var type = XMLActionType.JSON_TO_XML

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val contentVariable = variableExtractorUtil.extract(content, execution, scriptLanguage)
        val actionResult = ActionResult()
        if (contentVariable != null && !contentVariable.isEmpty()) {
            if (type == XMLActionType.XML_TO_JSON) {
                val xmlMapper = XmlMapper()
                val node = xmlMapper.readTree(contentVariable.toByteArray())
                val jsonMapper = ObjectMapper()
                actionResult.output = jsonMapper.writeValueAsString(node)
            } else if (type == XMLActionType.JSON_TO_XML) {
                val objectMapper = ObjectMapper()
                val xmlMapper = XmlMapper()
                val tree = objectMapper.readTree(content)
                actionResult.output = xmlMapper.writer().withRootName("xml").writeValueAsString(tree)
            }
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
            actionInfo.add(PropertyInformation("content", "Content", PropertyInformationType.MULTILINE))
            actionInfo.add(
                PropertyInformation(
                    "type", "Mode", PropertyInformationType.DROPDOWN, "JSON_TO_XML", "Which way to convert.", listOf(
                        PropertyInformation("JSON_TO_XML", "JSON to XML"),
                        PropertyInformation("XML_TO_JSON", "XML to JSON")
                    )
                )
            )
            return actionInfo
        }

    override val description: String
        get() = "Converts from/to XML"

}