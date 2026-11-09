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
@Table(name = "OktaAction")
@DiscriminatorValue("okta_action")
class OktaAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "findUser"

    @Column
    var userLogin: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = JsonApi.baseAddress(read(siteUrl))
        val tokenValue = read(token).trim()
        val operationValue = read(operation)
        val loginValue = read(userLogin).trim()
        if (site.isBlank()) return ActionResult.failure("Okta needs its address, such as https://acme.okta.com.")
        if (tokenValue.isBlank()) return ActionResult.failure("Okta needs an API token.")
        if (loginValue.isBlank()) return ActionResult.failure("Okta needs the login or ID of the user.")

        val headers = mapOf("Authorization" to "SSWS $tokenValue")
        val user = "$site/api/v1/users/${JsonApi.encoded(loginValue)}"
        val result = when (operationValue) {
            FIND_USER -> JsonApi.call("Okta", "GET", user, headers, null)
            SUSPEND_USER -> JsonApi.call("Okta", "POST", "$user/lifecycle/suspend", headers, null)
            RESTORE_USER -> JsonApi.call("Okta", "POST", "$user/lifecycle/unsuspend", headers, null)
            DEACTIVATE_USER -> JsonApi.call("Okta", "POST", "$user/lifecycle/deactivate", headers, null)
            else -> return ActionResult.failure("Okta has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS && operationValue == FIND_USER) {
            val answer = JsonApi.parsed(result)
            result.outputVariables["userId"] = answer.path("id").asText()
            result.outputVariables["userStatus"] = answer.path("status").asText()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("userId" to "User", "userStatus" to "Status")

    override val description: String
        get() = "Finds an Okta user, or suspends, restores or deactivates one"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("token", "API token", PropertyInformationType.STRING, "", "Under Security, API, Tokens.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, FIND_USER, "", listOf(
                PropertyInformation(FIND_USER, "Find a user"),
                PropertyInformation(SUSPEND_USER, "Suspend a user"),
                PropertyInformation(RESTORE_USER, "Restore a suspended user"),
                PropertyInformation(DEACTIVATE_USER, "Deactivate a user")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "Okta address", PropertyInformationType.STRING, "", "Such as https://acme.okta.com."))
            actionInfo.add(PropertyInformation("userLogin", "Login or user ID", PropertyInformationType.STRING, "", "Such as ada@example.com."))
            return actionInfo
        }

    private companion object {
        const val FIND_USER = "findUser"
        const val SUSPEND_USER = "suspendUser"
        const val RESTORE_USER = "restoreUser"
        const val DEACTIVATE_USER = "deactivateUser"
    }
}
