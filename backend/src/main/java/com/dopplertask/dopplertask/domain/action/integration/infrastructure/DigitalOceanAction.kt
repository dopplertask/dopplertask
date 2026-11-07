package com.dopplertask.dopplertask.domain.action.integration.infrastructure

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
@Table(name = "DigitalOceanAction")
@DiscriminatorValue("digitalocean_action")
class DigitalOceanAction : Action() {
    @Column
    var baseUrl: String? = "https://api.digitalocean.com"

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "listDroplets"

    @Column
    var dropletId: String? = null

    @Column
    var tagName: String? = null

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
        val dropletValue = read(dropletId).trim()
        val tagValue = read(tagName).trim()
        if (tokenValue.isBlank()) return ActionResult.failure("DigitalOcean needs a personal access token.")
        if (operationValue != LIST_DROPLETS && dropletValue.toLongOrNull() == null) return ActionResult.failure("DigitalOcean needs the droplet ID as a number, and got \"$dropletValue\".")

        val headers = mapOf("Authorization" to "Bearer $tokenValue")
        val droplets = read(baseUrl).trim().trimEnd('/') + "/v2/droplets"
        val act = { kind: String -> JsonApi.call("DigitalOcean", "POST", "$droplets/$dropletValue/actions", headers, mapOf("type" to kind)) }
        val result = when (operationValue) {
            LIST_DROPLETS -> JsonApi.call("DigitalOcean", "GET", "$droplets?per_page=$SERVERS_READ" + (if (tagValue.isEmpty()) "" else "&tag_name=${JsonApi.encoded(tagValue)}"), headers, null)
            READ_DROPLET -> JsonApi.call("DigitalOcean", "GET", "$droplets/$dropletValue", headers, null)
            REBOOT_DROPLET -> act("reboot")
            POWER_OFF_DROPLET -> act("power_off")
            POWER_ON_DROPLET -> act("power_on")
            else -> return ActionResult.failure("DigitalOcean has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            when (operationValue) {
                LIST_DROPLETS -> result.outputVariables["dropletCount"] = answer.path("droplets").size()
                READ_DROPLET -> result.outputVariables["dropletStatus"] = answer.path("droplet").path("status").asText()
                else -> result.outputVariables["actionId"] = answer.path("action").path("id").asText()
            }
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("dropletCount" to "Number of droplets", "dropletStatus" to "Status", "actionId" to "Action")

    override val description: String
        get() = "Lists, reads, restarts or powers DigitalOcean servers on and off"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForListDroplets = mapOf("operation" to arrayOf(LIST_DROPLETS))
            val showForReadDropletRebootDropletPowerOffDropletPowerOnDroplet = mapOf("operation" to arrayOf(READ_DROPLET, REBOOT_DROPLET, POWER_OFF_DROPLET, POWER_ON_DROPLET))
            actionInfo.add(PropertyInformation("token", "Personal access token", PropertyInformationType.STRING, "", "Under API, Tokens.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, LIST_DROPLETS, "", listOf(
                PropertyInformation(LIST_DROPLETS, "List droplets"),
                PropertyInformation(READ_DROPLET, "Read a droplet"),
                PropertyInformation(REBOOT_DROPLET, "Restart a droplet"),
                PropertyInformation(POWER_OFF_DROPLET, "Power off a droplet"),
                PropertyInformation(POWER_ON_DROPLET, "Power on a droplet")
            )))
            actionInfo.add(PropertyInformation("tagName", "Tag", PropertyInformationType.STRING, "", "Leave empty for all droplets.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForListDroplets))
            actionInfo.add(PropertyInformation("dropletId", "Droplet ID", PropertyInformationType.STRING, "", "The number in the address of the droplet.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadDropletRebootDropletPowerOffDropletPowerOnDroplet))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.digitalocean.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val LIST_DROPLETS = "listDroplets"
        const val READ_DROPLET = "readDroplet"
        const val REBOOT_DROPLET = "rebootDroplet"
        const val POWER_OFF_DROPLET = "powerOffDroplet"
        const val POWER_ON_DROPLET = "powerOnDroplet"
        const val SERVERS_READ = 200
    }
}
