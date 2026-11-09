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
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException

@Entity
@Table(name = "CloudflareAction")
@DiscriminatorValue("cloudflare_action")
class CloudflareAction : Action() {
    @Column
    var baseUrl: String? = "https://api.cloudflare.com"

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "purgeAddresses"

    @Column
    var zoneId: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var addresses: String? = null

    @Column
    var recordType: String? = "A"

    @Column
    var recordName: String? = null

    @Column
    var recordContent: String? = null

    @Column
    var recordId: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val tokenValue = read(token).trim()
        val zoneValue = read(zoneId).trim()
        val operationValue = read(operation)
        val addressList = read(addresses).lines().map { it.trim() }.filter { it.isNotEmpty() }
        val nameValue = read(recordName).trim()
        val contentValue = read(recordContent).trim()
        val recordValue = read(recordId).trim()
        if (tokenValue.isBlank()) return ActionResult.failure("Cloudflare needs an API token.")
        if (zoneValue.isBlank()) return ActionResult.failure("Cloudflare needs the zone ID, which is on the overview page of the domain.")
        if (operationValue == PURGE_ADDRESSES && addressList.isEmpty()) return ActionResult.failure("Cloudflare needs at least one address to purge.")
        if (operationValue == ADD_DNS_RECORD && (nameValue.isBlank() || contentValue.isBlank())) return ActionResult.failure("Cloudflare needs the name and the content of the record.")
        if (operationValue == DELETE_DNS_RECORD && recordValue.isBlank()) return ActionResult.failure("Cloudflare needs the ID of the record.")

        val headers = mapOf("Authorization" to "Bearer $tokenValue")
        val zone = JsonApi.baseAddress(read(baseUrl)) + "/client/v4/zones/${JsonApi.encoded(zoneValue)}"
        val result = when (operationValue) {
            PURGE_ADDRESSES -> JsonApi.call("Cloudflare", "POST", "$zone/purge_cache", headers, mapOf("files" to addressList))
            PURGE_EVERYTHING -> JsonApi.call("Cloudflare", "POST", "$zone/purge_cache", headers, mapOf("purge_everything" to true))
            ADD_DNS_RECORD -> JsonApi.call("Cloudflare", "POST", "$zone/dns_records", headers, mapOf("type" to read(recordType).trim().ifEmpty { "A" }, "name" to nameValue, "content" to contentValue, "ttl" to 1))
            DELETE_DNS_RECORD -> JsonApi.call("Cloudflare", "DELETE", "$zone/dns_records/${JsonApi.encoded(recordValue)}", headers, null)
            else -> return ActionResult.failure("Cloudflare has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS && operationValue == ADD_DNS_RECORD) result.outputVariables["recordId"] = JsonApi.parsed(result).path("result").path("id").asText()
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("recordId" to "DNS record")

    override val description: String
        get() = "Purges the Cloudflare cache or changes DNS records"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForPurgeAddresses = mapOf("operation" to arrayOf(PURGE_ADDRESSES))
            val showForAddDnsRecord = mapOf("operation" to arrayOf(ADD_DNS_RECORD))
            val showForDeleteDnsRecord = mapOf("operation" to arrayOf(DELETE_DNS_RECORD))
            actionInfo.add(PropertyInformation("token", "API token", PropertyInformationType.STRING, "", "Under My Profile, API Tokens.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, PURGE_ADDRESSES, "", listOf(
                PropertyInformation(PURGE_ADDRESSES, "Purge these addresses from the cache"),
                PropertyInformation(PURGE_EVERYTHING, "Purge the whole cache"),
                PropertyInformation(ADD_DNS_RECORD, "Add a DNS record"),
                PropertyInformation(DELETE_DNS_RECORD, "Delete a DNS record")
            )))
            actionInfo.add(PropertyInformation("zoneId", "Zone ID", PropertyInformationType.STRING, "", "On the overview page of the domain."))
            actionInfo.add(PropertyInformation("addresses", "Addresses", PropertyInformationType.MULTILINE, "", "One on each line.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForPurgeAddresses))
            actionInfo.add(PropertyInformation("recordType", "Type", PropertyInformationType.STRING, "A", "Such as A, AAAA, CNAME or TXT.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddDnsRecord))
            actionInfo.add(PropertyInformation("recordName", "Name", PropertyInformationType.STRING, "", "Such as www.example.com.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddDnsRecord))
            actionInfo.add(PropertyInformation("recordContent", "Content", PropertyInformationType.STRING, "", "Such as 192.0.2.1.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddDnsRecord))
            actionInfo.add(PropertyInformation("recordId", "Record ID", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForDeleteDnsRecord))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.cloudflare.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val PURGE_ADDRESSES = "purgeAddresses"
        const val PURGE_EVERYTHING = "purgeEverything"
        const val ADD_DNS_RECORD = "addDnsRecord"
        const val DELETE_DNS_RECORD = "deleteDnsRecord"
    }
}
