package com.dopplertask.dopplertask.domain.action.integration.connection

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
import com.rabbitmq.client.AlreadyClosedException
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.MessageProperties
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.TimeoutException

@Entity
@Table(name = "RabbitMQAction")
@DiscriminatorValue("rabbitmq_action")
class RabbitMQAction : Action() {
    @Column
    var hostname: String? = null

    @Column
    var port: String? = "5672"

    @Column
    var virtualHost: String? = "/"

    @Transient
    var username: String? = null

    @Transient
    var password: String? = null

    @Column
    var useTls: String? = "false"

    @Column
    var operation: String? = "sendMessage"

    @Column
    var exchange: String? = null

    @Column
    var routingKey: String? = null

    @Column
    var queueName: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var messageBody: String? = null

    @Column
    var messageLimit: String? = "10"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val hostValue = read(hostname).trim()
        val portValue = read(port).trim().ifEmpty { "5672" }.toIntOrNull()
        val operationValue = read(operation)
        val routingValue = read(routingKey).trim()
        val queueValue = read(queueName).trim()
        val messageValue = read(messageBody)
        val limitText = read(messageLimit).trim()
        val limit = if (limitText.isEmpty()) DEFAULT_READ else limitText.toIntOrNull()
        if (hostValue.isBlank()) return ActionResult.failure("RabbitMQ needs a hostname.")
        if (portValue == null) return ActionResult.failure("RabbitMQ needs the port as a number, such as 5672.")
        if (operationValue == SEND_MESSAGE && routingValue.isBlank()) return ActionResult.failure("RabbitMQ needs a routing key, which is the name of the queue when there is no exchange.")
        if (operationValue == SEND_MESSAGE && messageValue.isBlank()) return ActionResult.failure("RabbitMQ needs the message to send.")
        if (operationValue == READ_MESSAGES && queueValue.isBlank()) return ActionResult.failure("RabbitMQ needs the name of the queue.")
        if (operationValue == READ_MESSAGES && (limit == null || limit !in 1..LARGEST_READ)) return ActionResult.failure("RabbitMQ needs the number of messages to read between 1 and $LARGEST_READ, and got \"$limitText\".")

        val factory = ConnectionFactory()
        factory.host = hostValue
        factory.port = portValue
        factory.virtualHost = read(virtualHost).trim().ifEmpty { "/" }
        read(username).trim().takeIf { it.isNotEmpty() }?.let { factory.username = it }
        read(password).takeIf { it.isNotEmpty() }?.let { factory.password = it }
        factory.connectionTimeout = MILLISECONDS_TO_WAIT
        val result = ActionResult()
        result.statusCode = StatusCode.SUCCESS
        try {
            if (read(useTls) == "true") factory.useSslProtocol()
            factory.newConnection().use { connection ->
                connection.createChannel().use { channel ->
                    when (operationValue) {
                        SEND_MESSAGE -> {
                            // Waits for the server to confirm, so a step only succeeds once the message is really taken.
                            channel.confirmSelect()
                            channel.basicPublish(read(exchange).trim(), routingValue, MessageProperties.PERSISTENT_TEXT_PLAIN, messageValue.toByteArray())
                            channel.waitForConfirmsOrDie(MILLISECONDS_TO_WAIT.toLong())
                            result.output = "Sent a message with the routing key $routingValue."
                            result.outputVariables["messageCount"] = 1
                        }
                        READ_MESSAGES -> {
                            val messages = generateSequence { channel.basicGet(queueValue, true) }.take(limit!!).map { String(it.body) }.toList()
                            result.output = JsonApi.mapper.writeValueAsString(messages)
                            result.outputVariables["messageCount"] = messages.size
                        }
                        else -> return ActionResult.failure("RabbitMQ has no operation called \"$operationValue\".")
                    }
                }
            }
        } catch (e: IOException) {
            val detail = e.cause?.message ?: e.message.orEmpty()
            // The server's sentence comes wrapped in the details of the protocol.
            return ActionResult.failure("RabbitMQ answered: ${Regex("reply-text=([^,]+)").find(detail)?.groupValues?.get(1) ?: detail}")
        } catch (e: TimeoutException) {
            return ActionResult.failure("RabbitMQ did not answer in time. Check the hostname and the port.")
        } catch (e: AlreadyClosedException) {
            return ActionResult.failure("RabbitMQ answered: ${e.message}")
        } catch (e: GeneralSecurityException) {
            return ActionResult.failure("RabbitMQ could not set up TLS: ${e.message}")
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("messageCount" to "Number of messages")

    override val description: String
        get() = "Sends a message through RabbitMQ or reads messages from a queue"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForSendMessage = mapOf("operation" to arrayOf(SEND_MESSAGE))
            val showForReadMessages = mapOf("operation" to arrayOf(READ_MESSAGES))
            actionInfo.add(PropertyInformation("username", "Username", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("password", "Password", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, SEND_MESSAGE, "", listOf(
                PropertyInformation(SEND_MESSAGE, "Send a message"),
                PropertyInformation(READ_MESSAGES, "Read messages from a queue")
            )))
            actionInfo.add(PropertyInformation("hostname", "Hostname", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("port", "Port", PropertyInformationType.STRING, "5672", ""))
            actionInfo.add(PropertyInformation("virtualHost", "Virtual host", PropertyInformationType.STRING, "/", ""))
            actionInfo.add(PropertyInformation("useTls", "Use TLS", PropertyInformationType.BOOLEAN, "false", ""))
            actionInfo.add(PropertyInformation("exchange", "Exchange", PropertyInformationType.STRING, "", "Leave empty to send straight to the queue named as routing key.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSendMessage))
            actionInfo.add(PropertyInformation("routingKey", "Routing key", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSendMessage))
            actionInfo.add(PropertyInformation("messageBody", "Message", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSendMessage))
            actionInfo.add(PropertyInformation("queueName", "Queue", PropertyInformationType.STRING, "", "A message that is read is taken off the queue.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadMessages))
            actionInfo.add(PropertyInformation("messageLimit", "Most messages to read", PropertyInformationType.STRING, "10", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadMessages))
            return actionInfo
        }

    private companion object {
        const val SEND_MESSAGE = "sendMessage"
        const val READ_MESSAGES = "readMessages"
        const val MILLISECONDS_TO_WAIT = 10000
        const val LARGEST_READ = 100
        const val DEFAULT_READ = 10
    }
}
