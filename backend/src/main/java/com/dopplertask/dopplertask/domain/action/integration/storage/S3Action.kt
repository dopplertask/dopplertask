package com.dopplertask.dopplertask.domain.action.integration.storage

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.connection.HttpAction
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table
import javax.persistence.Transient

@Entity
@Table(name = "S3Action")
@DiscriminatorValue("s3_action")
class S3Action : Action() {
    @Column
    var baseUrl: String? = null

    @Column
    var region: String? = "us-east-1"

    @Transient
    var accessKeyId: String? = null

    @Transient
    var secretAccessKey: String? = null

    @Column
    var operation: String? = READ

    @Column
    var bucket: String? = null

    @Column(name = "objectKey", length = 1024)
    var key: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var content: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val regionValue = variableExtractorUtil.extract(region, execution, scriptLanguage).trim().ifEmpty { "us-east-1" }
        val accessKeyValue = variableExtractorUtil.extract(accessKeyId, execution, scriptLanguage).trim()
        val secretValue = variableExtractorUtil.extract(secretAccessKey, execution, scriptLanguage)
        val operationValue = variableExtractorUtil.extract(operation, execution, scriptLanguage)
        val bucketValue = variableExtractorUtil.extract(bucket, execution, scriptLanguage).trim()
        val keyValue = variableExtractorUtil.extract(key, execution, scriptLanguage).trim().trimStart('/')
        val contentValue = variableExtractorUtil.extract(content, execution, scriptLanguage)
        if (accessKeyValue.isBlank() || secretValue.isBlank()) return ActionResult.failure("S3 needs an access key id and a secret access key.")
        if (bucketValue.isBlank()) return ActionResult.failure("S3 needs a bucket.")
        if (keyValue.isBlank()) return ActionResult.failure("S3 needs a key, which is the path of the file in the bucket.")
        val method = when (operationValue) {
            READ -> "GET"
            WRITE -> "PUT"
            DELETE -> "DELETE"
            else -> return ActionResult.failure("S3 has no operation called \"$operationValue\".")
        }

        // The bucket goes in the path, since this is what S3 compatible services such as MinIO take.
        val base = variableExtractorUtil.extract(baseUrl, execution, scriptLanguage).trim().trimEnd('/').ifEmpty { "https://s3.$regionValue.amazonaws.com" }
        val path = "/$bucketValue/" + keyValue.split("/").joinToString("/") { encoded(it) }
        val body = if (operationValue == WRITE) contentValue else ""
        val now = ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP)
        val headers = mapOf("x-amz-content-sha256" to sha256(body), "x-amz-date" to now)
        val hostHeader = URI.create(base).let { if (it.port == -1) it.host else "${it.host}:${it.port}" }
        val authorization = authorization(method, URI.create(base).path.orEmpty() + path, headers + mapOf("host" to hostHeader), accessKeyValue, secretValue, regionValue, now)

        val request = HttpAction()
        request.method = method
        val result = request.send(base + path, body, headers + mapOf("Authorization" to authorization))
        if (result.statusCode == StatusCode.FAILURE && result.outputVariables.containsKey("statusCode")) {
            val reason = REASON.find(result.output)?.groupValues?.get(1)
            result.errorMsg = "S3 answered with status ${result.outputVariables["statusCode"]}" + (if (reason == null) "." else ": ${reason.trimEnd('.')}.")
        }
        if (result.statusCode == StatusCode.SUCCESS) {
            result.outputVariables["url"] = base + path
            // Only reading answers with something, and a person should still see what happened.
            if (operationValue != READ) result.output = (if (operationValue == WRITE) "Wrote " else "Deleted ") + "$keyValue in $bucketValue."
        }
        return result
    }

    override val outputs: Map<String, String>
        get() = mapOf("url" to "Address")

    override val description: String
        get() = "Reads, writes or deletes a text file in S3"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("accessKeyId", "Access key id", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("secretAccessKey", "Secret access key", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("region", "Region", PropertyInformationType.STRING, "us-east-1", "Such as eu-north-1."))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, READ, "", listOf(
                PropertyInformation(READ, "Read a file"),
                PropertyInformation(WRITE, "Write a file"),
                PropertyInformation(DELETE, "Delete a file")
            )))
            actionInfo.add(PropertyInformation("bucket", "Bucket", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("key", "Key", PropertyInformationType.STRING, "", "The path of the file in the bucket, such as reports/today.txt."))
            actionInfo.add(PropertyInformation("content", "Content", PropertyInformationType.MULTILINE, "", "The text to write.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(WRITE))))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "", "Only set this for a service that works like S3, such as http://minio.example.com:9000."))
            return actionInfo
        }

    companion object {
        private const val READ = "read"
        private const val WRITE = "write"
        private const val DELETE = "delete"
        private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        private val REASON = Regex("<Message>(.*?)</Message>")

        private fun encoded(segment: String) = URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

        private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun hmac(key: ByteArray, text: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(text.toByteArray())
        }

        /** Signature Version 4, as AWS describes it. A whole SDK would be much larger than the rest of this product's integrations. */
        fun authorization(method: String, path: String, headers: Map<String, String>, accessKey: String, secret: String, region: String, timestamp: String): String {
            val sorted = headers.mapKeys { it.key.lowercase() }.toSortedMap()
            val signedHeaders = sorted.keys.joinToString(";")
            val canonicalRequest = listOf(method, path, "", sorted.entries.joinToString("") { "${it.key}:${it.value.trim()}\n" }, signedHeaders, sorted.getValue("x-amz-content-sha256")).joinToString("\n")
            val day = timestamp.take(8)
            val scope = "$day/$region/s3/aws4_request"
            val toSign = listOf("AWS4-HMAC-SHA256", timestamp, scope, sha256(canonicalRequest)).joinToString("\n")
            val signingKey = listOf(day, region, "s3", "aws4_request").fold("AWS4$secret".toByteArray()) { key, part -> hmac(key, part) }
            val signature = hmac(signingKey, toSign).joinToString("") { "%02x".format(it) }
            return "AWS4-HMAC-SHA256 Credential=$accessKey/$scope,SignedHeaders=$signedHeaders,Signature=$signature"
        }
    }
}
