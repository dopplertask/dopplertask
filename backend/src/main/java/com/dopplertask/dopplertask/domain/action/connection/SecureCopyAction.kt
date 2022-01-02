package com.dopplertask.dopplertask.domain.action.connection

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.SSHManager
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.SftpException
import java.io.IOException
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Table

@Entity
@Table(name = "SecureCopyAction")
@DiscriminatorValue("securecopy_action")
class SecureCopyAction : Action() {
    @Column
    var hostname: String? = null

    @Column
    var username: String? = null

    @Column
    var password: String? = null

    @Column
    var sourceFilename: String? = null

    @Column
    var destinationFilename: String? = null

    @Throws(IOException::class)
    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?): ActionResult {
        val connectionIP = variableExtractorUtil.extract(hostname, execution, scriptLanguage)
        val localUsername = variableExtractorUtil.extract(username, execution, scriptLanguage)
        val localPassword = variableExtractorUtil.extract(password, execution, scriptLanguage)
        val localSourceFilename = variableExtractorUtil.extract(sourceFilename, execution, scriptLanguage)
        val localDestinationFilename = variableExtractorUtil.extract(destinationFilename, execution, scriptLanguage)
        val instance = SSHManager(localUsername, localPassword, connectionIP, "")
        val errorMessage = instance.connect()
        if (errorMessage != null) {
            val actionResult = ActionResult()
            actionResult.errorMsg = errorMessage
            actionResult.statusCode = StatusCode.FAILURE
            return actionResult
        }
        return try {
            val sftpChannel = instance.openChannel("sftp") as ChannelSftp
            sftpChannel.connect()
            sftpChannel.put(localSourceFilename, localDestinationFilename)
            sftpChannel.disconnect()
            // close only after all commands are sent
            instance.close()
            val actionResult = ActionResult()
            actionResult.output = "File transfer completed [sourceFilename=$localSourceFilename, destinationFilename=$localDestinationFilename]"
            actionResult.statusCode = StatusCode.SUCCESS
            actionResult
        } catch (e: JSchException) {
            val actionResult = ActionResult()
            actionResult.errorMsg = e.toString()
            actionResult.statusCode = StatusCode.FAILURE
            actionResult
        } catch (e: SftpException) {
            val actionResult = ActionResult()
            actionResult.errorMsg = e.toString()
            actionResult.statusCode = StatusCode.FAILURE
            actionResult
        }
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("hostname", "Hostname", PropertyInformationType.STRING, "", "Hostname or IP"))
            actionInfo.add(PropertyInformation("username", "Username", PropertyInformationType.STRING, "", "Username"))
            actionInfo.add(PropertyInformation("password", "Password", PropertyInformationType.STRING, "", "Password"))
            actionInfo.add(PropertyInformation("sourceFilename", "Source filename", PropertyInformationType.STRING, "", "Eg. /home/user/myfile"))
            actionInfo.add(PropertyInformation("destinationFilename", "Destination filename", PropertyInformationType.STRING, "", "Eg. on the remote server /home/remote/myfile"))
            return actionInfo
        }

    override val description: String
        get() = "Copy a file to a remote machine"
}