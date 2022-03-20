package com.dopplertask.dopplertask.domain.action.connection

import com.dopplertask.dopplertask.domain.*
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.ColumnEncryptor
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import javax.persistence.*

@Entity
@Table(name = "SSHAction")
@DiscriminatorValue("ssh_action")
class SSHAction : Action() {
    @Column
    var hostname: String? = null

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var username: String? = null

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var password: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var command: String? = null

    @Throws(IOException::class)
    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?): ActionResult {
        val connectionIP = variableExtractorUtil.extract(hostname, execution, scriptLanguage)
        val userName = variableExtractorUtil.extract(username, execution, scriptLanguage)
        val password = variableExtractorUtil.extract(password, execution, scriptLanguage)
        val command = variableExtractorUtil.extract(command, execution, scriptLanguage)
        val instance = SSHManager(userName, password, connectionIP, "")
        val errorMessage = instance.connect()
        if (errorMessage != null) {
            val actionResult = ActionResult()
            actionResult.errorMsg = errorMessage
            actionResult.statusCode = StatusCode.FAILURE
            return actionResult
        }
        // call sendCommand for each command and the output
//(without prompts) is returned
// Manual broadcast for multiple messages.
        val result = instance.sendCommand(command) { msg: String? -> broadcastListener!!.run(msg, OutputType.STRING) }
        // close only after all commands are sent
        instance.close()
        val actionResult = ActionResult()
        actionResult.output = result
        actionResult.isBroadcastMessage = false
        actionResult.statusCode = StatusCode.SUCCESS
        return actionResult
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("hostname", "Hostname", PropertyInformationType.STRING, "", "Hostname or IP"))
            actionInfo.add(PropertyInformation("username", "Username", PropertyInformationType.STRING, "", "Username"))
            actionInfo.add(PropertyInformation("password", "Password", PropertyInformationType.STRING, "", "Password"))
            actionInfo.add(PropertyInformation("command", "Command", PropertyInformationType.MULTILINE, "", "Eg. echo \"Hello world\""))
            return actionInfo
        }

    override val description: String
        get() = "Connect to a remote machine using SSH"
}