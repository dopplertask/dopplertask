package com.dopplertask.dopplertask.domain.action.connection

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException
import redis.clients.jedis.exceptions.JedisDataException
import java.io.IOException
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table
import javax.persistence.Transient

@Entity
@Table(name = "RedisAction")
@DiscriminatorValue("redis_action")
class RedisAction : Action() {
    @Column
    var hostname: String? = null

    @Column
    var port: String? = "6379"

    @Transient
    var password: String? = null

    @Column
    var useTls: String? = "false"

    @Column
    var operation: String? = GET

    @Column(name = "redisKey", length = 1024)
    var key: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var value: String? = null

    @Column
    var expirySeconds: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val hostValue = variableExtractorUtil.extract(hostname, execution, scriptLanguage).trim()
        val portValue = variableExtractorUtil.extract(port, execution, scriptLanguage).trim().ifEmpty { "6379" }.toIntOrNull()
        val passwordValue = variableExtractorUtil.extract(password, execution, scriptLanguage)
        val operationValue = variableExtractorUtil.extract(operation, execution, scriptLanguage)
        val keyValue = variableExtractorUtil.extract(key, execution, scriptLanguage)
        val valueValue = variableExtractorUtil.extract(value, execution, scriptLanguage)
        val expiryText = variableExtractorUtil.extract(expirySeconds, execution, scriptLanguage).trim()
        val expiry = expiryText.toLongOrNull()
        if (hostValue.isBlank()) return ActionResult.failure("Redis needs a hostname.")
        if (portValue == null) return ActionResult.failure("Redis needs the port as a number, such as 6379.")
        if (keyValue.isBlank()) return ActionResult.failure("Redis needs a key.")
        if (expiryText.isNotEmpty() && (expiry == null || expiry <= 0)) return ActionResult.failure("Redis needs the expiry as a number of seconds above 0, and got \"$expiryText\".")

        val result = ActionResult()
        try {
            Jedis(hostValue, portValue, TIMEOUT_MILLISECONDS, variableExtractorUtil.extract(useTls, execution, scriptLanguage) == "true").use { redis ->
                if (passwordValue.isNotEmpty()) redis.auth(passwordValue)
                when (operationValue) {
                    GET -> {
                        val found = redis.get(keyValue)
                        // A missing key is an answer and not a failure, since a task can branch on it.
                        result.output = found ?: ""
                        result.outputVariables["found"] = found != null
                    }
                    SET -> {
                        if (expiry != null) redis.setex(keyValue, expiry, valueValue) else redis.set(keyValue, valueValue)
                        result.output = valueValue
                    }
                    INCREMENT -> result.output = redis.incr(keyValue).toString()
                    DELETE -> result.outputVariables["deleted"] = redis.del(keyValue) > 0
                    else -> return ActionResult.failure("Redis has no operation called \"$operationValue\".")
                }
            }
            result.statusCode = StatusCode.SUCCESS
        } catch (e: JedisConnectionException) {
            return ActionResult.failure("Could not connect to Redis at $hostValue:$portValue. Check the address, and that the service is running.")
        } catch (e: JedisDataException) {
            return ActionResult.failure("Redis refused: ${e.message}")
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("found" to "Whether the key exists", "deleted" to "Whether a key was deleted")

    override val description: String
        get() = "Reads and writes a value in Redis"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("hostname", "Hostname", PropertyInformationType.STRING, "", "Such as redis.example.com."))
            actionInfo.add(PropertyInformation("port", "Port", PropertyInformationType.NUMBER, "6379", ""))
            actionInfo.add(PropertyInformation("password", "Password", PropertyInformationType.STRING, "", "Leave it empty if the server has none.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("useTls", "Use TLS", PropertyInformationType.BOOLEAN, "false", "Most hosted Redis services require it."))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, GET, "", listOf(
                PropertyInformation(GET, "Read a value"),
                PropertyInformation(SET, "Write a value"),
                PropertyInformation(INCREMENT, "Increment a counter"),
                PropertyInformation(DELETE, "Delete a key")
            )))
            actionInfo.add(PropertyInformation("key", "Key", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("value", "Value", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(SET))))
            actionInfo.add(PropertyInformation("expirySeconds", "Expires after seconds", PropertyInformationType.NUMBER, "", "The key is removed after this time.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(SET))))
            return actionInfo
        }

    private companion object {
        const val GET = "get"
        const val SET = "set"
        const val INCREMENT = "increment"
        const val DELETE = "delete"
        const val TIMEOUT_MILLISECONDS = 10000
    }
}
