package com.dopplertask.dopplertask.domain.action.common

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.OutputType
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.*
import java.util.function.Consumer
import javax.persistence.*

@Entity
@Table(name = "ExecuteCommandAction")
@DiscriminatorValue("executecommand_action")
class ExecuteCommandAction : Action() {
    @Lob
    @Column(columnDefinition = "TEXT")
    var command: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val commandVar = variableExtractorUtil.extract(command, execution, scriptLanguage)
        val isWindows = System.getProperty("os.name")
            .toLowerCase().startsWith("windows")
        val builder = ProcessBuilder()
        if (isWindows) {
            builder.command("cmd.exe", "/c", commandVar)
        } else {
            builder.command("sh", "-c", commandVar)
        }
        builder.directory(File(System.getProperty("user.home")))
        var process: Process?
        val actionResult = ActionResult()
        try {
            process = builder.start()
            val output = StringBuilder()
            val streamGobbler = StreamGobbler(process.inputStream, Consumer { consumer: String ->
                run {
                    output.append(consumer + "\n")
                    broadcastListener?.run(consumer, OutputType.STRING)
                }
            })
            streamGobbler.run()
            val streamGobblerError = StreamGobbler(process.errorStream, Consumer { consumer: String ->
                run {
                    output.append(consumer + "\n")
                    broadcastListener?.run(consumer, OutputType.STRING);
                }
            })
            streamGobblerError.run()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                actionResult.statusCode = StatusCode.SUCCESS
            } else {
                actionResult.statusCode = StatusCode.FAILURE
            }
            actionResult.output = output.toString()
        } catch (e: InterruptedException) {
            actionResult.statusCode = StatusCode.FAILURE
            actionResult.errorMsg = "Could not execute task: $e"
        } catch (e: IOException) {
            actionResult.statusCode = StatusCode.FAILURE
            actionResult.errorMsg = "Could not execute task: $e"
        }
        return actionResult
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(
                PropertyInformation(
                    "command",
                    "Command",
                    PropertyInformationType.MULTILINE,
                    "",
                    "Command to execute."
                )
            )
            return actionInfo
        }

    override val description: String
        get() = "Executes a command on the current machine"

    private class StreamGobbler(private val inputStream: InputStream, private val consumer: Consumer<String>) :
        Runnable {
        override fun run() {
            BufferedReader(InputStreamReader(inputStream)).lines()
                .forEach(consumer)
        }

    }
}