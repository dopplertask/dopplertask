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
import javax.persistence.*

@Entity
@Table(name = "WriteFileAction")
@DiscriminatorValue("writefile_action")
class WriteFileAction : Action() {
    @Column
    var filename: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var contents: String? = null

    @Throws(IOException::class)
    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?): ActionResult {
        var filenameVariable = variableExtractorUtil.extract(filename, execution, scriptLanguage)
        val contentsVariable = variableExtractorUtil.extract(contents, execution, scriptLanguage)
        return try { // Support shell ~ for home directory
            if (filenameVariable.contains("~/") && filenameVariable.startsWith("~")) {
                filenameVariable = filenameVariable.replace("~/", System.getProperty("user.home") + "/")
            }
            Files.writeString(Paths.get(filenameVariable), contentsVariable, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
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
            actionInfo.add(PropertyInformation("filename", "File location", PropertyInformationType.STRING, "", "File path. eg. /home/user/file.txt"))
            actionInfo.add(PropertyInformation("contents", "Contents", PropertyInformationType.MULTILINE, "", "Contents of the file"))
            return actionInfo
        }

    override val description: String
        get() = "Writes a file to disk"

}