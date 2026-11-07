package com.dopplertask.dopplertask.domain.action.integration.connection

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

@Entity
@Table(name = "MqttAction")
@DiscriminatorValue("mqtt_action")
class MqttAction : Action() {
    @Column
    var brokerUrl: String? = null

    @Transient
    var username: String? = null

    @Transient
    var password: String? = null

    @Column
    var operation: String? = "publishMessage"

    @Column
    var topic: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var messageBody: String? = null

    @Column
    var quality: String? = "1"

    @Column
    var retain: String? = "false"

    @Column
    var secondsToWait: String? = "5"

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val brokerValue = read(brokerUrl).trim()
        val topicValue = read(topic).trim()
        val operationValue = read(operation)
        val messageValue = read(messageBody)
        val qualityValue = read(quality).trim().ifEmpty { "1" }.toIntOrNull()
        val waitValue = read(secondsToWait).trim().ifEmpty { DEFAULT_WAIT_SECONDS.toString() }.toLongOrNull()
        if (brokerValue.isBlank()) return ActionResult.failure("MQTT needs the address of the broker, such as tcp://broker.example.com:1883.")
        if (topicValue.isBlank()) return ActionResult.failure("MQTT needs a topic.")
        if (qualityValue == null || qualityValue !in 0..LARGEST_QUALITY) return ActionResult.failure("MQTT needs the quality of service as 0, 1 or 2.")
        if (operationValue == PUBLISH_MESSAGE && messageValue.isBlank()) return ActionResult.failure("MQTT needs the message to publish.")
        if (operationValue == WAIT_FOR_MESSAGE && (waitValue == null || waitValue <= 0)) return ActionResult.failure("MQTT needs the seconds to wait as a number above 0.")

        val options = MqttConnectOptions()
        options.connectionTimeout = SECONDS_TO_CONNECT
        options.isCleanSession = true
        read(username).trim().takeIf { it.isNotEmpty() }?.let { options.userName = it }
        read(password).takeIf { it.isNotEmpty() }?.let { options.password = it.toCharArray() }
        val result = ActionResult()
        result.statusCode = StatusCode.SUCCESS
        try {
            val client = MqttClient(brokerValue, MqttClient.generateClientId(), MemoryPersistence())
            try {
                client.connect(options)
                when (operationValue) {
                    PUBLISH_MESSAGE -> {
                        client.publish(topicValue, messageValue.toByteArray(), qualityValue, read(retain) == "true")
                        result.output = "Published a message to $topicValue."
                    }
                    WAIT_FOR_MESSAGE -> {
                        val received = LinkedBlockingQueue<String>()
                        client.subscribe(topicValue, qualityValue) { _, message -> received.add(String(message.payload)) }
                        val message = received.poll(waitValue!!, TimeUnit.SECONDS)
                        result.output = message ?: ""
                        result.outputVariables["received"] = message != null
                    }
                    else -> return ActionResult.failure("MQTT has no operation called \"$operationValue\".")
                }
            } finally {
                if (client.isConnected) client.disconnect()
                client.close()
            }
        } catch (e: MqttException) {
            return ActionResult.failure("MQTT answered: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return ActionResult.failure("MQTT could not use this: ${e.message}")
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("received" to "Whether a message arrived")

    override val description: String
        get() = "Publishes an MQTT message or waits for one on a topic"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForPublishMessage = mapOf("operation" to arrayOf(PUBLISH_MESSAGE))
            val showForWaitForMessage = mapOf("operation" to arrayOf(WAIT_FOR_MESSAGE))
            actionInfo.add(PropertyInformation("username", "Username", PropertyInformationType.STRING, "", "Leave empty when the broker has no sign-in.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("password", "Password", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, PUBLISH_MESSAGE, "", listOf(
                PropertyInformation(PUBLISH_MESSAGE, "Publish a message"),
                PropertyInformation(WAIT_FOR_MESSAGE, "Wait for a message")
            )))
            actionInfo.add(PropertyInformation("brokerUrl", "Broker address", PropertyInformationType.STRING, "", "Such as tcp://broker.example.com:1883, or ssl:// for TLS."))
            actionInfo.add(PropertyInformation("topic", "Topic", PropertyInformationType.STRING, "", "Such as sensors/hall/temperature."))
            actionInfo.add(PropertyInformation("messageBody", "Message", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForPublishMessage))
            actionInfo.add(PropertyInformation("quality", "Quality of service", PropertyInformationType.STRING, "1", "0 is at most once, 1 at least once and 2 exactly once."))
            actionInfo.add(PropertyInformation("retain", "Keep for new subscribers", PropertyInformationType.BOOLEAN, "false", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForPublishMessage))
            actionInfo.add(PropertyInformation("secondsToWait", "Seconds to wait", PropertyInformationType.STRING, "5", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForWaitForMessage))
            return actionInfo
        }

    private companion object {
        const val PUBLISH_MESSAGE = "publishMessage"
        const val WAIT_FOR_MESSAGE = "waitForMessage"
        const val SECONDS_TO_CONNECT = 10
        const val DEFAULT_WAIT_SECONDS = 5L
        const val LARGEST_QUALITY = 2
    }
}
