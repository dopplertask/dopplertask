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
import jakarta.persistence.Table
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads a file on this machine and keeps it in the run, so that a later step can send it, such as a connector that
 * uploads it, or write it elsewhere. A text file also gives its text.
 */
@Entity
@Table(name = "ReadFileAction")
@DiscriminatorValue("readfile_action")
class ReadFileAction : Action() {
    override val runsInPreview get() = true


    @Column
    var filename: String? = null

    /** The name the run keeps the file by. Empty keeps it by its own name. */
    @Column
    var keepAs: String? = null

    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?): ActionResult {
        val path = Path.of(homeOf(variableExtractorUtil.extract(filename, execution, scriptLanguage)))
        StepRules.active().refusalOfFile(path)?.let { return ActionResult.failure(it) }
        val bytes = try {
            Files.readAllBytes(path)
        } catch (e: IOException) {
            return ActionResult.failure("$path could not be read. Check that it exists and that DopplerTask may read it.")
        }
        val name = variableExtractorUtil.extract(keepAs, execution, scriptLanguage).ifBlank { path.fileName.toString() }
        RunFiles.keep(execution, name, bytes)
        val about = RunFiles.describe(name, bytes, path.fileName.toString())
        val text = if (RunFiles.isText(about["mimeType"].toString())) String(bytes, StandardCharsets.UTF_8) else null
        return ActionResult(StatusCode.SUCCESS, text ?: "Read $name (${bytes.size} bytes)", "").also { result ->
            result.outputVariables.putAll(about + ("path" to path.toString()))
        }
    }

    override val description: String
        get() = "Reads a file on this machine, for later steps to send or write"

    override val outputs: Map<String, String>
        get() = mapOf("file" to "The name the run keeps the file by", "size" to "Its size in bytes", "mimeType" to "Its type, such as application/pdf", "path" to "Where it was read")

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val info = super.actionInfo
            info.add(PropertyInformation("filename", "Path", PropertyInformationType.STRING, "", "Such as ~/reports/june.pdf.").also { it.required = true })
            info.add(PropertyInformation("keepAs", "Keep as", PropertyInformationType.STRING, "", "The name later steps choose the file by. Empty uses its own name, such as june.pdf."))
            return info
        }

    companion object {
        /** A path that starts with ~/ is in the home folder, as in a shell. */
        fun homeOf(path: String) = if (path.startsWith("~/")) System.getProperty("user.home") + path.substring(1) else path
    }
}
