package com.dopplertask.dopplertask.domain.action.io

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.RunFiles
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.StepRules
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Writes text, or a file from earlier in the run, such as one a connector downloaded, to a file on this machine. */
@Entity
@Table(name = "WriteFileAction")
@DiscriminatorValue("writefile_action")
class WriteFileAction : Action() {

    @Column
    var filename: String? = null

    /** TEXT or FILE. */
    @Column
    var write: String? = TEXT

    @Lob
    @Column(columnDefinition = "TEXT")
    var contents: String? = null

    /** The name of a file the run keeps. */
    @Column
    var file: String? = null

    /** REPLACE, APPEND or STOP. */
    @Column
    var ifExists: String? = REPLACE

    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?): ActionResult {
        val path = Path.of(ReadFileAction.homeOf(variableExtractorUtil.extract(filename, execution, scriptLanguage)))
        StepRules.active().refusalOfFile(path)?.let { return ActionResult.failure(it) }
        val bytes = if (write == FILE) {
            val name = variableExtractorUtil.extract(file, execution, scriptLanguage)
            RunFiles.read(execution, name) ?: return ActionResult.failure("The run has no file called \"$name\". Read or download it in an earlier step.")
        } else {
            variableExtractorUtil.extract(contents, execution, scriptLanguage).toByteArray(StandardCharsets.UTF_8)
        }
        if (ifExists == STOP && Files.exists(path)) return ActionResult.failure("$path already exists, and this step is set to stop then.")
        val options = if (ifExists == APPEND) arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        else arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        return try {
            Files.write(path, bytes, *options)
            ActionResult(StatusCode.SUCCESS, "Wrote ${bytes.size} bytes to $path", "").also { it.outputVariables.putAll(mapOf("path" to path.toString(), "size" to bytes.size)) }
        } catch (e: IOException) {
            ActionResult.failure("$path could not be written. Check that its folder exists and that DopplerTask may write there.")
        }
    }

    override val description: String
        get() = "Writes text, or a file from earlier in the run, to a file on this machine"

    override val outputs: Map<String, String>
        get() = mapOf("path" to "Where it was written", "size" to "How many bytes were written")

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val info = super.actionInfo
            info.add(PropertyInformation("filename", "Path", PropertyInformationType.STRING, "", "Such as ~/reports/summary.txt.").also { it.required = true })
            info.add(PropertyInformation("write", "Write", PropertyInformationType.DROPDOWN, TEXT, "", listOf(
                PropertyInformation(TEXT, "Text"), PropertyInformation(FILE, "A file from earlier in the run"))))
            info.add(PropertyInformation("contents", "Text", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY,
                mapOf("write" to arrayOf(TEXT))))
            info.add(PropertyInformation("file", "File", PropertyInformationType.FILE, "", "A file an earlier step read or downloaded.", mutableListOf(),
                PropertyInformation.PropertyInformationCategory.PROPERTY, mapOf("write" to arrayOf(FILE))).also { it.required = true })
            info.add(PropertyInformation("ifExists", "If the file exists", PropertyInformationType.DROPDOWN, REPLACE, "", listOf(
                PropertyInformation(REPLACE, "Replace it"), PropertyInformation(APPEND, "Add to its end"), PropertyInformation(STOP, "Stop with an error"))))
            return info
        }

    companion object {
        const val TEXT = "TEXT"
        const val FILE = "FILE"
        const val REPLACE = "REPLACE"
        const val APPEND = "APPEND"
        const val STOP = "STOP"
    }
}
