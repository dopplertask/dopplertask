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
import java.util.Base64

@Entity
@Table(name = "PayPalAction")
@DiscriminatorValue("paypal_action")
class PayPalAction : Action() {
    override val runsInPreview get() = true

    @Column
    var baseUrl: String? = "https://api-m.paypal.com"

    @Transient
    var clientId: String? = null

    @Transient
    var clientSecret: String? = null

    @Column
    var operation: String? = "createOrder"

    @Column
    var amount: String? = null

    @Column
    var currency: String? = "EUR"

    @Column
    var orderDescription: String? = null

    @Column
    var orderId: String? = null

    @Column
    var captureId: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val clientValue = read(clientId).trim()
        val secretValue = read(clientSecret)
        val operationValue = read(operation)
        val amountValue = read(amount).trim()
        val currencyValue = read(currency).trim().ifEmpty { "EUR" }
        val orderValue = read(orderId).trim()
        val captureValue = read(captureId).trim()
        if (clientValue.isBlank() || secretValue.isBlank()) return ActionResult.failure("PayPal needs the client ID and the secret of an app.")
        if (operationValue == CREATE_ORDER && amountValue.toBigDecimalOrNull() == null) return ActionResult.failure("PayPal needs the amount as a number, such as 19.90, and got \"$amountValue\".")
        if ((operationValue == READ_ORDER || operationValue == CAPTURE_ORDER) && orderValue.isBlank()) return ActionResult.failure("PayPal needs the ID of the order.")
        if (operationValue == REFUND_PAYMENT && captureValue.isBlank()) return ActionResult.failure("PayPal needs the capture ID of the payment.")

        val base = JsonApi.baseAddress(read(baseUrl))
        val basic = "Basic " + Base64.getEncoder().encodeToString("$clientValue:$secretValue".toByteArray())
        val signIn = JsonApi.callForm("PayPal", "$base/v1/oauth2/token", mapOf("Authorization" to basic), mapOf("grant_type" to "client_credentials"))
        if (signIn.statusCode == StatusCode.FAILURE) return signIn
        val headers = mapOf("Authorization" to "Bearer " + JsonApi.parsed(signIn).path("access_token").asText())
        val money = mapOf("currency_code" to currencyValue, "value" to amountValue)
        val orders = "$base/v2/checkout/orders"
        val result = when (operationValue) {
            CREATE_ORDER -> {
                val unit = mutableMapOf<String, Any>("amount" to money)
                read(orderDescription).trim().takeIf { it.isNotEmpty() }?.let { unit["description"] = it }
                JsonApi.call("PayPal", "POST", orders, headers, mapOf("intent" to "CAPTURE", "purchase_units" to listOf(unit)))
            }
            READ_ORDER -> JsonApi.call("PayPal", "GET", "$orders/${JsonApi.encoded(orderValue)}", headers, null)
            CAPTURE_ORDER -> JsonApi.call("PayPal", "POST", "$orders/${JsonApi.encoded(orderValue)}/capture", headers, mapOf<String, String>())
            REFUND_PAYMENT -> JsonApi.call("PayPal", "POST", "$base/v2/payments/captures/${JsonApi.encoded(captureValue)}/refund", headers, if (amountValue.isEmpty()) mapOf<String, Any>() else mapOf("amount" to money))
            else -> return ActionResult.failure("PayPal has no operation called \"$operationValue\".")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            val answer = JsonApi.parsed(result)
            if (operationValue == REFUND_PAYMENT) result.outputVariables["refundId"] = answer.path("id").asText()
            else {
                result.outputVariables["orderId"] = answer.path("id").asText()
                result.outputVariables["orderStatus"] = answer.path("status").asText()
                answer.path("links").firstOrNull { it.path("rel").asText() == "approve" || it.path("rel").asText() == "payer-action" }?.let { result.outputVariables["approvalUrl"] = it.path("href").asText() }
            }
            if (operationValue == CAPTURE_ORDER) result.outputVariables["captureId"] = answer.path("purchase_units").path(0).path("payments").path("captures").path(0).path("id").asText()
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("orderId" to "Order", "orderStatus" to "Status", "approvalUrl" to "Where the buyer approves", "captureId" to "Capture", "refundId" to "Refund")

    override val description: String
        get() = "Creates, reads or captures a PayPal order, or refunds a payment"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForCreateOrderRefundPayment = mapOf("operation" to arrayOf(CREATE_ORDER, REFUND_PAYMENT))
            val showForCreateOrder = mapOf("operation" to arrayOf(CREATE_ORDER))
            val showForReadOrderCaptureOrder = mapOf("operation" to arrayOf(READ_ORDER, CAPTURE_ORDER))
            val showForRefundPayment = mapOf("operation" to arrayOf(REFUND_PAYMENT))
            actionInfo.add(PropertyInformation("clientId", "Client ID", PropertyInformationType.STRING, "", "From an app under Apps and Credentials.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("clientSecret", "Secret", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_ORDER, "", listOf(
                PropertyInformation(CREATE_ORDER, "Create an order"),
                PropertyInformation(READ_ORDER, "Read an order"),
                PropertyInformation(CAPTURE_ORDER, "Take the payment of an approved order"),
                PropertyInformation(REFUND_PAYMENT, "Refund a payment")
            )))
            actionInfo.add(PropertyInformation("amount", "Amount", PropertyInformationType.STRING, "", "Such as 19.90. When refunding, empty means all of it.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateOrderRefundPayment))
            actionInfo.add(PropertyInformation("currency", "Currency", PropertyInformationType.STRING, "EUR", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateOrderRefundPayment))
            actionInfo.add(PropertyInformation("orderDescription", "Description", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateOrder))
            actionInfo.add(PropertyInformation("orderId", "Order ID", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadOrderCaptureOrder))
            actionInfo.add(PropertyInformation("captureId", "Capture ID", PropertyInformationType.STRING, "", "The step that took the payment gives this.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForRefundPayment))
            actionInfo.add(PropertyInformation("baseUrl", "PayPal address", PropertyInformationType.STRING, "https://api-m.paypal.com", "https://api-m.sandbox.paypal.com to try it without real money."))
            return actionInfo
        }

    private companion object {
        const val CREATE_ORDER = "createOrder"
        const val READ_ORDER = "readOrder"
        const val CAPTURE_ORDER = "captureOrder"
        const val REFUND_PAYMENT = "refundPayment"
    }
}
