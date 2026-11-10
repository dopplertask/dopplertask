package com.dopplertask.dopplertask.domain.action.integration.sales

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
import java.security.MessageDigest
import java.util.Base64

@Entity
@Table(name = "MailchimpAction")
@DiscriminatorValue("mailchimp_action")
class MailchimpAction : Action() {
    override val runsInPreview get() = true

    @Column
    var baseUrl: String? = null

    @Transient
    var apiKey: String? = null

    @Column
    var operation: String? = "saveContact"

    @Column
    var audienceId: String? = null

    @Column
    var email: String? = null

    @Column
    var firstName: String? = null

    @Column
    var lastName: String? = null

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
        val keyValue = read(apiKey).trim()
        val operationValue = read(operation)
        val audienceValue = read(audienceId).trim()
        val emailValue = read(email).trim().lowercase()
        val tagValue = read(tagName).trim()
        val dataCenter = keyValue.substringAfterLast('-', "")
        if (keyValue.isBlank()) return ActionResult.failure("Mailchimp needs an API key, which is under Account, Extras, API keys.")
        if (audienceValue.isBlank() || emailValue.isBlank()) return ActionResult.failure("Mailchimp needs the audience ID and the email of the contact.")
        if (operationValue == TAG_CONTACT && tagValue.isBlank()) return ActionResult.failure("Mailchimp needs the name of the tag.")

        val base = JsonApi.baseAddress(read(baseUrl)).ifEmpty { if (dataCenter.isEmpty()) "https://api.mailchimp.com" else "https://$dataCenter.api.mailchimp.com" }
        val headers = mapOf("Authorization" to "Basic " + Base64.getEncoder().encodeToString("dopplertask:$keyValue".toByteArray()))
        // Mailchimp finds a contact by this hash of the email.
        val hash = MessageDigest.getInstance("MD5").digest(emailValue.toByteArray()).joinToString("") { "%02x".format(it) }
        val member = "$base/3.0/lists/${JsonApi.encoded(audienceValue)}/members/$hash"
        val result = when (operationValue) {
            SAVE_CONTACT -> {
                val names = mapOf("FNAME" to read(firstName).trim(), "LNAME" to read(lastName).trim()).filterValues { it.isNotEmpty() }
                // Someone who has unsubscribed stays unsubscribed, since the status only applies to a new contact.
                JsonApi.call("Mailchimp", "PUT", member, headers, mapOf("email_address" to emailValue, "status_if_new" to "subscribed", "merge_fields" to names))
            }
            TAG_CONTACT -> JsonApi.call("Mailchimp", "POST", "$member/tags", headers, mapOf("tags" to listOf(mapOf("name" to tagValue, "status" to "active"))))
            UNSUBSCRIBE_CONTACT -> JsonApi.call("Mailchimp", "PATCH", member, headers, mapOf("status" to "unsubscribed"))
            else -> return ActionResult.failure("Mailchimp has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["contactId"] = hash
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("contactId" to "Contact id")

    override val description: String
        get() = "Adds, tags or unsubscribes a Mailchimp contact"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForSaveContact = mapOf("operation" to arrayOf(SAVE_CONTACT))
            val showForTagContact = mapOf("operation" to arrayOf(TAG_CONTACT))
            actionInfo.add(PropertyInformation("apiKey", "API key", PropertyInformationType.STRING, "", "Under Account, Extras, API keys. It ends with where your account is, such as -us21.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, SAVE_CONTACT, "", listOf(
                PropertyInformation(SAVE_CONTACT, "Add or update a contact"),
                PropertyInformation(TAG_CONTACT, "Tag a contact"),
                PropertyInformation(UNSUBSCRIBE_CONTACT, "Unsubscribe a contact")
            )))
            actionInfo.add(PropertyInformation("audienceId", "Audience ID", PropertyInformationType.STRING, "", "Under Audience, Settings."))
            actionInfo.add(PropertyInformation("email", "Email", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("firstName", "First name", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSaveContact))
            actionInfo.add(PropertyInformation("lastName", "Last name", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSaveContact))
            actionInfo.add(PropertyInformation("tagName", "Tag", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForTagContact))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "", "Only change this to go through a proxy. It is otherwise worked out from the API key."))
            return actionInfo
        }

    private companion object {
        const val SAVE_CONTACT = "saveContact"
        const val TAG_CONTACT = "tagContact"
        const val UNSUBSCRIBE_CONTACT = "unsubscribeContact"
    }
}
