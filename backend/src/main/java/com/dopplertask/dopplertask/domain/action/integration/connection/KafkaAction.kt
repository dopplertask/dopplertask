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
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException
import java.time.Duration
import java.util.Properties
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.TimeoutException as KafkaTimeout
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer

@Entity
@Table(name = "KafkaAction")
@DiscriminatorValue("kafka_action")
class KafkaAction : Action() {
    @Column
    var servers: String? = null

    @Transient
    var username: String? = null

    @Transient
    var password: String? = null

    @Column
    var authMechanism: String? = "PLAIN"

    @Column
    var useTls: String? = "false"

    @Column
    var operation: String? = "sendMessage"

    @Column
    var topic: String? = null

    @Column
    var messageKey: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var messageBody: String? = null

    @Column
    var consumerGroup: String? = null

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
        val serversValue = read(servers).trim()
        val topicValue = read(topic).trim()
        val operationValue = read(operation)
        val messageValue = read(messageBody)
        val groupValue = read(consumerGroup).trim()
        val limitText = read(messageLimit).trim()
        val limit = if (limitText.isEmpty()) DEFAULT_READ else limitText.toIntOrNull()
        if (serversValue.isBlank()) return ActionResult.failure("Kafka needs the servers, such as broker1.example.com:9092.")
        if (topicValue.isBlank()) return ActionResult.failure("Kafka needs a topic.")
        if (operationValue == SEND_MESSAGE && messageValue.isBlank()) return ActionResult.failure("Kafka needs the message to send.")
        if (operationValue == READ_MESSAGES && groupValue.isBlank()) return ActionResult.failure("Kafka needs a consumer group to read messages.")
        if (operationValue == READ_MESSAGES && (limit == null || limit !in 1..LARGEST_READ)) return ActionResult.failure("Kafka needs the number of messages to read between 1 and $LARGEST_READ, and got \"$limitText\".")

        val connection = settings(serversValue, read(username).trim(), read(password), read(authMechanism).trim(), read(useTls) == "true")
        val result = ActionResult()
        result.statusCode = StatusCode.SUCCESS
        try {
            when (operationValue) {
                SEND_MESSAGE -> {
                    connection["max.block.ms"] = TimeUnit.SECONDS.toMillis(SECONDS_TO_WAIT)
                    connection["delivery.timeout.ms"] = TimeUnit.SECONDS.toMillis(SECONDS_TO_WAIT * 2).toInt()
                    KafkaProducer(connection, StringSerializer(), StringSerializer()).use { producer ->
                        val sent = producer.send(ProducerRecord(topicValue, read(messageKey).trim().ifEmpty { null }, messageValue)).get(SECONDS_TO_WAIT * 2, TimeUnit.SECONDS)
                        result.output = "Sent a message to $topicValue."
                        result.outputVariables["partition"] = sent.partition()
                        result.outputVariables["offset"] = sent.offset()
                    }
                }
                READ_MESSAGES -> {
                    connection["group.id"] = groupValue
                    connection["auto.offset.reset"] = "earliest"
                    connection["enable.auto.commit"] = false
                    connection["max.poll.records"] = limit!!
                    KafkaConsumer(connection, StringDeserializer(), StringDeserializer()).use { consumer ->
                        consumer.subscribe(listOf(topicValue))
                        val messages = mutableListOf<Map<String, Any?>>()
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SECONDS_TO_READ)
                        while (messages.size < limit && System.nanoTime() < deadline) {
                            consumer.poll(Duration.ofMillis(500)).forEach { record -> messages.add(mapOf("key" to record.key(), "message" to record.value(), "partition" to record.partition(), "offset" to record.offset())) }
                        }
                        consumer.commitSync()
                        result.output = JsonApi.mapper.writeValueAsString(messages)
                        result.outputVariables["messageCount"] = messages.size
                    }
                }
                else -> return ActionResult.failure("Kafka has no operation called \"$operationValue\".")
            }
        } catch (e: KafkaTimeout) {
            return ActionResult.failure("Kafka did not answer in time. Check the servers and that the topic exists.")
        } catch (e: KafkaException) {
            return ActionResult.failure("Kafka answered: ${e.message}")
        } catch (e: ExecutionException) {
            if (e.cause is KafkaTimeout) return ActionResult.failure("Kafka did not answer in time. Check the servers and that the topic exists.")
            return ActionResult.failure("Kafka answered: ${e.cause?.message}")
        } catch (e: TimeoutException) {
            return ActionResult.failure("Kafka did not answer in time. Check the servers and the sign-in.")
        }
        return result
    }

    private fun settings(servers: String, userValue: String, passwordValue: String, mechanismValue: String, secure: Boolean) = Properties().apply {
        this["bootstrap.servers"] = servers
        this["request.timeout.ms"] = TimeUnit.SECONDS.toMillis(SECONDS_TO_WAIT).toInt()
        this["default.api.timeout.ms"] = TimeUnit.SECONDS.toMillis(SECONDS_TO_WAIT).toInt()
        val signsIn = userValue.isNotEmpty()
        this["security.protocol"] = (if (signsIn) "SASL_" else "") + (if (secure) "SSL" else "PLAINTEXT")
        if (signsIn) {
            val mechanism = mechanismValue.ifEmpty { "PLAIN" }
            val module = if (mechanism == "PLAIN") "org.apache.kafka.common.security.plain.PlainLoginModule" else "org.apache.kafka.common.security.scram.ScramLoginModule"
            val quoted = { text: String -> text.replace("\\", "\\\\").replace("\"", "\\\"") }
            this["sasl.mechanism"] = mechanism
            this["sasl.jaas.config"] = "$module required username=\"${quoted(userValue)}\" password=\"${quoted(passwordValue)}\";"
        }
    }

    override val outputs: Map<String, String>
        get() = mapOf("messageCount" to "Number of messages", "partition" to "Partition", "offset" to "Offset")

    override val description: String
        get() = "Sends a message to a Kafka topic or reads messages from one"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForSendMessage = mapOf("operation" to arrayOf(SEND_MESSAGE))
            val showForReadMessages = mapOf("operation" to arrayOf(READ_MESSAGES))
            actionInfo.add(PropertyInformation("username", "Username", PropertyInformationType.STRING, "", "Leave empty when the cluster has no sign-in.", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("password", "Password", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, SEND_MESSAGE, "", listOf(
                PropertyInformation(SEND_MESSAGE, "Send a message"),
                PropertyInformation(READ_MESSAGES, "Read messages")
            )))
            actionInfo.add(PropertyInformation("servers", "Servers", PropertyInformationType.STRING, "", "Such as broker1.example.com:9092, broker2.example.com:9092."))
            actionInfo.add(PropertyInformation("authMechanism", "Sign-in method", PropertyInformationType.STRING, "PLAIN", "PLAIN, SCRAM-SHA-256 or SCRAM-SHA-512."))
            actionInfo.add(PropertyInformation("useTls", "Use TLS", PropertyInformationType.BOOLEAN, "false", ""))
            actionInfo.add(PropertyInformation("topic", "Topic", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("messageKey", "Key", PropertyInformationType.STRING, "", "Messages with the same key stay in order.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSendMessage))
            actionInfo.add(PropertyInformation("messageBody", "Message", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForSendMessage))
            actionInfo.add(PropertyInformation("consumerGroup", "Consumer group", PropertyInformationType.STRING, "", "Kafka remembers per group what has been read.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadMessages))
            actionInfo.add(PropertyInformation("messageLimit", "Most messages to read", PropertyInformationType.STRING, "10", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForReadMessages))
            return actionInfo
        }

    private companion object {
        const val SEND_MESSAGE = "sendMessage"
        const val READ_MESSAGES = "readMessages"
        const val SECONDS_TO_WAIT = 10L
        const val SECONDS_TO_READ = 5L
        const val LARGEST_READ = 100
        const val DEFAULT_READ = 10
    }
}
