package com.dopplertask.dopplertask.domain.action.io

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.OutputType
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table


@Entity
@Table(name = "WriteFileAction")
@DiscriminatorValue("writefile_action")
class WriteFileAction : Action() {
    @Column
    var filename: String? = null

    @Column
    var outputType: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var contents: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        var filenameVariable = variableExtractorUtil.extract(filename, execution, scriptLanguage)
        val contentsVariable = variableExtractorUtil.extract(contents, execution, scriptLanguage)
        return try { // Support shell ~ for home directory
            if (filenameVariable.contains("~/") && filenameVariable.startsWith("~")) {
                filenameVariable = filenameVariable.replace("~/", System.getProperty("user.home") + "/")
            }
            if (outputType.equals("clearText")) {
                Files.writeString(
                    Paths.get(filenameVariable),
                    contentsVariable,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
                )
            } else {
                try {
                    Files.write(
                        Paths.get(filenameVariable),
                        execution.parameters[contentsVariable]?.paramValue,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    )
                } catch (e: NullPointerException) {
                    val actionResult = ActionResult()
                    actionResult.output = "Content variable is not set, does not exist, or is a wrong value"
                    actionResult.outputType = OutputType.STRING
                    actionResult.statusCode = StatusCode.FAILURE
                    return actionResult
                }
            }

            val actionResult = ActionResult()
            actionResult.output = contents!!
            actionResult.outputType = OutputType.STRING
            actionResult.statusCode = StatusCode.SUCCESS
            actionResult
        } catch (e: IOException) {
            val actionResult = ActionResult()
            actionResult.output = "File could not be written [filename=$filenameVariable]"
            actionResult.outputType = OutputType.STRING
            actionResult.statusCode = StatusCode.FAILURE
            actionResult
        }
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(
                PropertyInformation(
                    "filename",
                    "File location",
                    PropertyInformationType.STRING,
                    "",
                    "File path. eg. /home/user/file.txt"
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "outputType",
                    "Output Type",
                    PropertyInformationType.DROPDOWN,
                    "clearText",
                    "Choose binary input or clear text",
                    mutableListOf(
                        PropertyInformation("clearText", "Clear text"),
                        PropertyInformation("binaryVar", "Binary")
                    ),
                    PropertyInformation.PropertyInformationCategory.PROPERTY
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "contents",
                    "Contents",
                    PropertyInformationType.STRING,
                    "",
                    "Property name containing the binary data",
                    mutableListOf(),
                    PropertyInformation.PropertyInformationCategory.PROPERTY,
                    mutableMapOf(
                        Pair("outputType", arrayOf("binaryVar"))
                    )
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "contents",
                    "Contents",
                    PropertyInformationType.MULTILINE,
                    "",
                    "Contents of the file",
                    mutableListOf(),
                    PropertyInformation.PropertyInformationCategory.PROPERTY,
                    mutableMapOf(
                        Pair("outputType", arrayOf("clearText"))
                    )
                )
            )
            return actionInfo
        }

    override val description: String
        get() = "Writes a file to disk"

}