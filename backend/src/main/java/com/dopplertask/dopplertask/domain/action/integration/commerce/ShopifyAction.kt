package com.dopplertask.dopplertask.domain.action.integration.commerce

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
@Table(name = "ShopifyAction")
@DiscriminatorValue("shopify_action")
class ShopifyAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var clientId: String? = null

    @Transient
    var clientSecret: String? = null

    @Transient
    var token: String? = null

    @Column
    var operation: String? = "readOrder"

    @Column
    var orderNumber: String? = null

    @Column
    var email: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = read(siteUrl).trim().trimEnd('/')
        val clientIdValue = read(clientId).trim()
        val clientSecretValue = read(clientSecret)
        val storedToken = read(token)
        val operationValue = read(operation)
        val numberValue = read(orderNumber).trim().trimStart('#')
        val emailValue = read(email).trim()
        if (site.isBlank()) return ActionResult.failure("Shopify needs the address of the store, such as https://acme.myshopify.com.")
        if (storedToken.isBlank() && (clientIdValue.isBlank() || clientSecretValue.isBlank())) return ActionResult.failure("Shopify needs the client ID and secret of an app, or the Admin API access token of a custom app.")
        if ((operationValue == READ_ORDER || operationValue == CANCEL_ORDER) && numberValue.isBlank()) return ActionResult.failure("Shopify needs the number of the order, such as 1001.")
        if (operationValue == FIND_CUSTOMER && emailValue.isBlank()) return ActionResult.failure("Shopify needs the email of the customer.")

        // Tokens of newer apps only last a day, so one is asked for each time.
        val tokenValue = storedToken.ifBlank {
            val signIn = JsonApi.callForm("Shopify", "$site/admin/oauth/access_token", mapOf(), mapOf("grant_type" to "client_credentials", "client_id" to clientIdValue, "client_secret" to clientSecretValue))
            if (signIn.statusCode == StatusCode.FAILURE) return signIn
            JsonApi.parsed(signIn).path("access_token").asText()
        }
        val headers = mapOf("X-Shopify-Access-Token" to tokenValue)
        val admin = "$site/admin/api/$API_VERSION"
        return when (operationValue) {
            READ_ORDER, CANCEL_ORDER -> {
                // The API wants its own long id, while a person knows the number shown on the order.
                val found = JsonApi.call("Shopify", "GET", "$admin/orders.json?status=any&name=${JsonApi.encoded(numberValue)}", headers, null)
                if (found.statusCode == StatusCode.FAILURE) return found
                val order = JsonApi.parsed(found).path("orders").path(0)
                if (order.isMissingNode) return ActionResult.failure("Shopify has no order with the number \"$numberValue\".")
                val orderId = order.path("id").asText()
                val result = if (operationValue == READ_ORDER) found else JsonApi.call("Shopify", "POST", "$admin/orders/$orderId/cancel.json", headers, mapOf<String, String>())
                if (result.statusCode == StatusCode.SUCCESS) {
                    result.outputVariables["orderId"] = orderId
                    result.outputVariables["url"] = "$site/admin/orders/$orderId"
                }
                result
            }
            LIST_OPEN_ORDERS -> {
                val result = JsonApi.call("Shopify", "GET", "$admin/orders.json?status=open&limit=$ORDERS_LISTED", headers, null)
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["orderCount"] = JsonApi.parsed(result).path("orders").size()
                result
            }
            FIND_CUSTOMER -> {
                val result = JsonApi.call("Shopify", "GET", "$admin/customers/search.json?query=${JsonApi.encoded("email:$emailValue")}", headers, null)
                if (result.statusCode == StatusCode.SUCCESS) {
                    val customerId = JsonApi.parsed(result).path("customers").path(0).path("id").asText("")
                    result.outputVariables["customerId"] = customerId
                    result.outputVariables["found"] = customerId.isNotEmpty()
                }
                result
            }
            else -> ActionResult.failure("Shopify has no operation called \"$operationValue\".")
        }
    }

    override val outputs: Map<String, String>
        get() = mapOf("orderId" to "Order id", "url" to "Address", "orderCount" to "Number of orders", "customerId" to "Customer id", "found" to "Whether a customer was found")

    override val description: String
        get() = "Reads, lists or cancels Shopify orders, or finds a customer"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForReadOrderCancelOrder = mapOf("operation" to arrayOf(READ_ORDER, CANCEL_ORDER))
            val showForFindCustomer = mapOf("operation" to arrayOf(FIND_CUSTOMER))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, READ_ORDER, "", listOf(
                PropertyInformation(READ_ORDER, "Read an order"),
                PropertyInformation(LIST_OPEN_ORDERS, "List open orders"),
                PropertyInformation(CANCEL_ORDER, "Cancel an order"),
                PropertyInformation(FIND_CUSTOMER, "Find a customer")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "Store", PropertyInformationType.STRING, "", "Such as https://acme.myshopify.com."))
            actionInfo.add(PropertyInformation("clientId", "Client ID", PropertyInformationType.STRING, "", "From an app in the Dev Dashboard, under Settings.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("clientSecret", "Client secret", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("token", "Admin API access token", PropertyInformationType.STRING, "", "Instead of the client ID and secret, for a custom app that was made in the admin.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("orderNumber", "Order number", PropertyInformationType.STRING, "", "The number shown on the order, such as 1001.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadOrderCancelOrder))
            actionInfo.add(PropertyInformation("email", "Email", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForFindCustomer))
            return actionInfo
        }

    private companion object {
        const val READ_ORDER = "readOrder"
        const val LIST_OPEN_ORDERS = "listOpenOrders"
        const val CANCEL_ORDER = "cancelOrder"
        const val FIND_CUSTOMER = "findCustomer"
        const val API_VERSION = "2026-10"
        const val ORDERS_LISTED = 50
    }
}
