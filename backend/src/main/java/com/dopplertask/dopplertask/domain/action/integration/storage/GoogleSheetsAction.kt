package com.dopplertask.dopplertask.domain.action.integration.storage

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
import com.fasterxml.jackson.databind.JsonNode
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.io.IOException
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table
import javax.persistence.Transient

@Entity
@Table(name = "GoogleSheetsAction")
@DiscriminatorValue("google_sheets_action")
class GoogleSheetsAction : Action() {
    @Column
    var baseUrl: String? = "https://sheets.googleapis.com"

    @Transient
    var serviceAccountKey: String? = null

    @Column
    var spreadsheet: String? = null

    @Column
    var sheet: String? = "Sheet1"

    @Column
    var operation: String? = "readRows"

    @Lob
    @Column(columnDefinition = "TEXT")
    var rowValues: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val keyText = read(serviceAccountKey)
        val spreadsheetText = read(spreadsheet).trim()
        val sheetValue = read(sheet).trim().ifEmpty { "Sheet1" }
        val operationValue = read(operation)
        val spreadsheetId = SPREADSHEET_ID.find(spreadsheetText)?.groupValues?.get(1) ?: spreadsheetText
        if (keyText.isBlank()) return ActionResult.failure("Google Sheets needs the key file of a service account.")
        if (spreadsheetId.isBlank()) return ActionResult.failure("Google Sheets needs the address or the id of a spreadsheet.")
        if (operationValue == ADD_ROW && rowValues.isNullOrBlank()) return ActionResult.failure("Google Sheets needs the values of the row, one per line.")

        val account = try {
            JsonApi.mapper.readTree(keyText)
        } catch (e: Exception) {
            return ActionResult.failure("Google Sheets could not read the key file, which should be the JSON file of the service account.")
        }
        val tokens = accessToken(account) ?: return ActionResult.failure("Google Sheets could not read the private key in the key file.")
        if (tokens.statusCode == StatusCode.FAILURE) return tokens
        val headers = mapOf("Authorization" to "Bearer " + JsonApi.parsed(tokens).path("access_token").asText())

        val base = read(baseUrl).trimEnd('/')
        val range = URLEncoder.encode(sheetValue, StandardCharsets.UTF_8).replace("+", "%20")
        val values = "$base/v4/spreadsheets/$spreadsheetId/values/$range"
        return when (operationValue) {
            READ_ROWS -> {
                val result = JsonApi.call("Google Sheets", "GET", values, headers, null)
                if (result.statusCode == StatusCode.SUCCESS) {
                    val rows = rowsOf(JsonApi.parsed(result).path("values"))
                    result.output = JsonApi.mapper.writeValueAsString(rows)
                    result.outputVariables["rowCount"] = rows.size
                }
                result
            }
            ADD_ROW -> {
                // RAW keeps text as text, so that a value from an earlier step cannot become a formula.
                val row = rowValues!!.lines().map { read(it) }
                val result = JsonApi.call("Google Sheets", "POST", "$values:append?valueInputOption=RAW", headers, mapOf("values" to listOf(row)))
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["rowCount"] = 1
                result
            }
            else -> ActionResult.failure("Google Sheets has no operation called \"$operationValue\".")
        }
    }

    private fun accessToken(account: JsonNode): ActionResult? {
        val tokenUrl = account.path("token_uri").asText("https://oauth2.googleapis.com/token")
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .issuer(account.path("client_email").asText())
            .audience(tokenUrl)
            .claim("scope", "https://www.googleapis.com/auth/spreadsheets")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(TOKEN_SECONDS)))
            .build()
        val assertion = SignedJWT(JWSHeader(JWSAlgorithm.RS256), claims)
        try {
            val keyBytes = Base64.getMimeDecoder().decode(account.path("private_key").asText().replace(Regex("-----[A-Z ]+-----"), ""))
            assertion.sign(RSASSASigner(KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))))
        } catch (e: Exception) {
            return null
        }
        return JsonApi.callForm("Google Sheets", tokenUrl, mapOf(), mapOf("grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion" to assertion.serialize()))
    }

    // The first row names the columns, and a short row leaves the last columns out.
    private fun rowsOf(values: JsonNode): List<Map<String, String>> {
        val names = values.path(0).map { it.asText() }
        return values.drop(1).map { row -> names.zip(row.map { it.asText() }).toMap() }
    }

    override val outputs: Map<String, String>
        get() = mapOf("rowCount" to "Number of rows")

    override val description: String
        get() = "Reads or adds rows in Google Sheets"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("serviceAccountKey", "Service account key", PropertyInformationType.STRING, "", "The JSON file that Google Cloud gives for a service account. Share the spreadsheet with the email of that account.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("spreadsheet", "Spreadsheet", PropertyInformationType.STRING, "", "The address or the id."))
            actionInfo.add(PropertyInformation("sheet", "Sheet", PropertyInformationType.STRING, "Sheet1", "The name of the tab."))
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, READ_ROWS, "", listOf(
                PropertyInformation(READ_ROWS, "Read the rows"),
                PropertyInformation(ADD_ROW, "Add a row")
            )))
            actionInfo.add(PropertyInformation("rowValues", "Values", PropertyInformationType.MULTILINE, "", "One value per line, one for each column.", mutableListOf(), PropertyInformationCategory.PROPERTY, mapOf("operation" to arrayOf(ADD_ROW))))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://sheets.googleapis.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val READ_ROWS = "readRows"
        const val ADD_ROW = "addRow"
        const val TOKEN_SECONDS = 3600L
        val SPREADSHEET_ID = Regex("/spreadsheets/d/([\\w-]+)")
    }
}
