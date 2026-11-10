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
@Table(name = "StripeAction")
@DiscriminatorValue("stripe_action")
class StripeAction : Action() {
    override val runsInPreview get() = true

    @Column
    var baseUrl: String? = "https://api.stripe.com"

    @Transient
    var secretKey: String? = null

    @Column
    var operation: String? = "createCustomer"

    @Column
    var email: String? = null

    @Column
    var customerName: String? = null

    @Column
    var priceId: String? = null

    @Column
    var quantity: String? = "1"

    @Column
    var paymentId: String? = null

    @Column
    var amount: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val keyValue = read(secretKey).trim()
        val operationValue = read(operation)
        val emailValue = read(email).trim()
        val priceValue = read(priceId).trim()
        val paymentValue = read(paymentId).trim()
        val amountValue = read(amount).trim()
        if (keyValue.isBlank()) return ActionResult.failure("Stripe needs a secret key, which is under Developers, API keys.")
        if (operationValue == CREATE_CUSTOMER && emailValue.isBlank()) return ActionResult.failure("Stripe needs the email of the customer.")
        if (operationValue == CREATE_PAYMENT_LINK && priceValue.isBlank()) return ActionResult.failure("Stripe needs the price ID, such as price_1Mo.")
        if ((operationValue == READ_PAYMENT || operationValue == REFUND_PAYMENT) && paymentValue.isBlank()) return ActionResult.failure("Stripe needs the payment ID, such as pi_3Mt.")
        if (operationValue == REFUND_PAYMENT && amountValue.isNotEmpty() && amountValue.toLongOrNull() == null) return ActionResult.failure("Stripe needs the amount in the smallest unit, such as cents, and got \"$amountValue\".")

        val base = JsonApi.baseAddress(read(baseUrl))
        val headers = mapOf("Authorization" to "Bearer $keyValue")
        return when (operationValue) {
            CREATE_CUSTOMER -> {
                val result = JsonApi.callForm("Stripe", "$base/v1/customers", headers, mapOf("email" to emailValue, "name" to read(customerName).trim()).filterValues { it.isNotEmpty() })
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["customerId"] = JsonApi.parsed(result).path("id").asText()
                result
            }
            CREATE_PAYMENT_LINK -> {
                val result = JsonApi.callForm("Stripe", "$base/v1/payment_links", headers, mapOf("line_items[0][price]" to priceValue, "line_items[0][quantity]" to read(quantity).trim().ifEmpty { "1" }))
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["url"] = JsonApi.parsed(result).path("url").asText()
                result
            }
            READ_PAYMENT -> {
                val result = JsonApi.call("Stripe", "GET", "$base/v1/payment_intents/${JsonApi.encoded(paymentValue)}", headers, null)
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["paymentStatus"] = JsonApi.parsed(result).path("status").asText()
                result
            }
            REFUND_PAYMENT -> {
                val refund = mutableMapOf("payment_intent" to paymentValue)
                if (amountValue.isNotEmpty()) refund["amount"] = amountValue
                val result = JsonApi.callForm("Stripe", "$base/v1/refunds", headers, refund)
                if (result.statusCode == StatusCode.SUCCESS) {
                    result.outputVariables["refundId"] = JsonApi.parsed(result).path("id").asText()
                    result.outputVariables["paymentStatus"] = JsonApi.parsed(result).path("status").asText()
                }
                result
            }
            else -> ActionResult.failure("Stripe has no operation called \"$operationValue\".")
        }
    }

    override val outputs: Map<String, String>
        get() = mapOf("customerId" to "Customer id", "url" to "Payment link", "paymentStatus" to "Status", "refundId" to "Refund id")

    override val description: String
        get() = "Creates a Stripe customer or payment link, reads a payment or refunds it"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForCreateCustomer = mapOf("operation" to arrayOf(CREATE_CUSTOMER))
            val showForCreatePaymentLink = mapOf("operation" to arrayOf(CREATE_PAYMENT_LINK))
            val showForReadPaymentRefundPayment = mapOf("operation" to arrayOf(READ_PAYMENT, REFUND_PAYMENT))
            val showForRefundPayment = mapOf("operation" to arrayOf(REFUND_PAYMENT))
            actionInfo.add(PropertyInformation("secretKey", "Secret key", PropertyInformationType.STRING, "", "Under Developers, API keys. A restricted key limits what this step can do.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_CUSTOMER, "", listOf(
                PropertyInformation(CREATE_CUSTOMER, "Create a customer"),
                PropertyInformation(CREATE_PAYMENT_LINK, "Create a payment link"),
                PropertyInformation(READ_PAYMENT, "Read a payment"),
                PropertyInformation(REFUND_PAYMENT, "Refund a payment")
            )))
            actionInfo.add(PropertyInformation("email", "Email", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateCustomer))
            actionInfo.add(PropertyInformation("customerName", "Name", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateCustomer))
            actionInfo.add(PropertyInformation("priceId", "Price ID", PropertyInformationType.STRING, "", "Such as price_1Mo. It is shown on the product.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePaymentLink))
            actionInfo.add(PropertyInformation("quantity", "Quantity", PropertyInformationType.STRING, "1", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreatePaymentLink))
            actionInfo.add(PropertyInformation("paymentId", "Payment ID", PropertyInformationType.STRING, "", "Such as pi_3Mt.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadPaymentRefundPayment))
            actionInfo.add(PropertyInformation("amount", "Amount", PropertyInformationType.STRING, "", "In the smallest unit, such as cents. Leave it empty to refund all of it.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForRefundPayment))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://api.stripe.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val CREATE_CUSTOMER = "createCustomer"
        const val CREATE_PAYMENT_LINK = "createPaymentLink"
        const val READ_PAYMENT = "readPayment"
        const val REFUND_PAYMENT = "refundPayment"
    }
}
