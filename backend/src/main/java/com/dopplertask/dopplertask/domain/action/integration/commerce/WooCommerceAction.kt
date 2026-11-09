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
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException
import java.util.Base64

@Entity
@Table(name = "WooCommerceAction")
@DiscriminatorValue("woocommerce_action")
class WooCommerceAction : Action() {
    @Column
    var siteUrl: String? = null

    @Transient
    var consumerKey: String? = null

    @Transient
    var consumerSecret: String? = null

    @Column
    var operation: String? = "readOrder"

    @Column
    var orderNumber: String? = null

    @Column
    var orderStatus: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var note: String? = null

    @Column
    var visibleToCustomer: String? = "false"

    @Column
    var productId: String? = null

    @Column
    var stockQuantity: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val site = JsonApi.baseAddress(siteUrl)
        val keyValue = read(consumerKey).trim()
        val secretValue = read(consumerSecret)
        val operationValue = read(operation)
        val numberValue = read(orderNumber).trim().trimStart('#')
        val statusValue = read(orderStatus).trim()
        val noteValue = read(note)
        val productValue = read(productId).trim()
        val stockValue = read(stockQuantity).trim()
        if (site.isBlank()) return ActionResult.failure("WooCommerce needs the address of the site, such as https://shop.example.com.")
        if (keyValue.isBlank() || secretValue.isBlank()) return ActionResult.failure("WooCommerce needs a consumer key and secret, which are under WooCommerce, Settings, Advanced, REST API.")
        if (operationValue != SET_STOCK && numberValue.toLongOrNull() == null) return ActionResult.failure("WooCommerce needs the number of the order, and got \"$numberValue\".")
        if (operationValue == SET_ORDER_STATUS && statusValue.isBlank()) return ActionResult.failure("WooCommerce needs the new status, such as completed.")
        if (operationValue == ADD_ORDER_NOTE && noteValue.isBlank()) return ActionResult.failure("WooCommerce needs the text of the note.")
        if (operationValue == SET_STOCK && (productValue.toLongOrNull() == null || stockValue.toLongOrNull() == null)) return ActionResult.failure("WooCommerce needs the product ID and the stock as numbers.")

        val headers = mapOf("Authorization" to "Basic " + Base64.getEncoder().encodeToString("$keyValue:$secretValue".toByteArray()))
        val orders = "$site/wp-json/wc/v3/orders/$numberValue"
        val result = when (operationValue) {
            READ_ORDER -> JsonApi.call("WooCommerce", "GET", orders, headers, null)
            SET_ORDER_STATUS -> JsonApi.call("WooCommerce", "PUT", orders, headers, mapOf("status" to statusValue))
            // A note reaches the customer by email when it is visible, so that only happens when asked for.
            ADD_ORDER_NOTE -> JsonApi.call("WooCommerce", "POST", "$orders/notes", headers, mapOf("note" to noteValue, "customer_note" to (read(visibleToCustomer) == "true")))
            SET_STOCK -> JsonApi.call("WooCommerce", "PUT", "$site/wp-json/wc/v3/products/$productValue", headers, mapOf("manage_stock" to true, "stock_quantity" to stockValue.toLong()))
            else -> return ActionResult.failure("WooCommerce has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS && operationValue != SET_STOCK) {
            if (operationValue != ADD_ORDER_NOTE) result.outputVariables["orderStatus"] = JsonApi.parsed(result).path("status").asText()
            result.outputVariables["url"] = "$site/wp-admin/post.php?post=$numberValue&action=edit"
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("orderStatus" to "Status", "url" to "Address")

    override val description: String
        get() = "Reads or updates a WooCommerce order, or sets the stock of a product"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForReadOrderSetOrderStatusAddOrderNote = mapOf("operation" to arrayOf(READ_ORDER, SET_ORDER_STATUS, ADD_ORDER_NOTE))
            val showForSetOrderStatus = mapOf("operation" to arrayOf(SET_ORDER_STATUS))
            val showForAddOrderNote = mapOf("operation" to arrayOf(ADD_ORDER_NOTE))
            val showForSetStock = mapOf("operation" to arrayOf(SET_STOCK))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, READ_ORDER, "", listOf(
                PropertyInformation(READ_ORDER, "Read an order"),
                PropertyInformation(SET_ORDER_STATUS, "Change the status of an order"),
                PropertyInformation(ADD_ORDER_NOTE, "Add a note to an order"),
                PropertyInformation(SET_STOCK, "Set the stock of a product")
            )))
            actionInfo.add(PropertyInformation("siteUrl", "Site", PropertyInformationType.STRING, "", "Such as https://shop.example.com."))
            actionInfo.add(PropertyInformation("consumerKey", "Consumer key", PropertyInformationType.STRING, "", "Under WooCommerce, Settings, Advanced, REST API.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("consumerSecret", "Consumer secret", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("orderNumber", "Order number", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadOrderSetOrderStatusAddOrderNote))
            actionInfo.add(PropertyInformation("orderStatus", "Status", PropertyInformationType.STRING, "", "Such as processing, completed or cancelled.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSetOrderStatus))
            actionInfo.add(PropertyInformation("note", "Note", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddOrderNote))
            actionInfo.add(PropertyInformation("visibleToCustomer", "Visible to the customer", PropertyInformationType.BOOLEAN, "false", "Otherwise the note is only for the shop.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForAddOrderNote))
            actionInfo.add(PropertyInformation("productId", "Product ID", PropertyInformationType.STRING, "", "Shown in the list of products.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSetStock))
            actionInfo.add(PropertyInformation("stockQuantity", "Stock", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSetStock))
            return actionInfo
        }

    private companion object {
        const val READ_ORDER = "readOrder"
        const val SET_ORDER_STATUS = "setOrderStatus"
        const val ADD_ORDER_NOTE = "addOrderNote"
        const val SET_STOCK = "setStock"
    }
}
