package com.dopplertask.dopplertask.domain.action.connection

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import tools.jackson.databind.ObjectMapper
import org.postgresql.ds.PGSimpleDataSource
import java.io.IOException
import java.sql.ResultSet
import java.sql.SQLException
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient

@Entity
@Table(name = "PostgresAction")
@DiscriminatorValue("postgres_action")
class PostgresAction : Action() {
    @Column
    var hostname: String? = null

    @Column
    var port: String? = "5432"

    @Column
    var database: String? = null

    @Transient
    var username: String? = null

    @Transient
    var password: String? = null

    @Column
    var useTls: String? = "false"

    @Lob
    @Column(columnDefinition = "TEXT")
    var command: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var queryValues: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val hostValue = variableExtractorUtil.extract(hostname, execution, scriptLanguage).trim()
        val portValue = variableExtractorUtil.extract(port, execution, scriptLanguage).trim().ifEmpty { "5432" }.toIntOrNull()
        val commandValue = variableExtractorUtil.extract(command, execution, scriptLanguage)
        if (hostValue.isBlank()) return ActionResult.failure("Postgres needs a hostname.")
        if (portValue == null) return ActionResult.failure("Postgres needs the port as a number, such as 5432.")
        if (commandValue.isBlank()) return ActionResult.failure("Postgres needs a query to run.")

        val dataSource = PGSimpleDataSource()
        dataSource.serverNames = arrayOf(hostValue)
        dataSource.portNumbers = intArrayOf(portValue)
        dataSource.databaseName = variableExtractorUtil.extract(database, execution, scriptLanguage).trim()
        dataSource.user = variableExtractorUtil.extract(username, execution, scriptLanguage).trim()
        dataSource.password = variableExtractorUtil.extract(password, execution, scriptLanguage)
        dataSource.connectTimeout = CONNECT_TIMEOUT_SECONDS
        // The server decides the type of each value, so that a value can go into any column.
        dataSource.stringType = "unspecified"
        if (variableExtractorUtil.extract(useTls, execution, scriptLanguage) == "true") dataSource.sslMode = "require"

        return try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(commandValue).use { statement ->
                    // Each value is sent apart from the query, so that text from an earlier step cannot change the query.
                    valuesOf(queryValues, execution, variableExtractorUtil).forEachIndexed { index, value -> statement.setString(index + 1, value) }
                    val result = ActionResult()
                    if (statement.execute()) {
                        val rows = rowsIn(statement.resultSet)
                        result.output = ObjectMapper().writeValueAsString(rows)
                        result.outputVariables["rowCount"] = rows.size
                    } else {
                        result.output = "${statement.updateCount} rows changed."
                        result.outputVariables["rowCount"] = statement.updateCount
                    }
                    result
                }
            }
        } catch (e: SQLException) {
            // Class 08 is what Postgres calls a connection that failed.
            if (e.sqlState?.startsWith("08") == true) ActionResult.failure("Could not connect to Postgres at $hostValue:$portValue. Check the address, and that the service is running.")
            else ActionResult.failure("Postgres answered: ${e.message?.lineSequence()?.first()}")
        }
    }

    private fun valuesOf(text: String?, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil): List<String> {
        if (text.isNullOrBlank()) return listOf()
        return text.lines().map { line -> variableExtractorUtil.extract(line, execution, scriptLanguage) }
    }

    private fun rowsIn(resultSet: ResultSet): List<Map<String, Any?>> {
        val columns = (1..resultSet.metaData.columnCount).map { resultSet.metaData.getColumnLabel(it) }
        val rows = mutableListOf<Map<String, Any?>>()
        while (resultSet.next()) {
            rows.add(columns.associateWith { column ->
                // Numbers stay numbers for the next step, and dates and such are given as Postgres writes them.
                when (val value = resultSet.getObject(column)) {
                    null -> null
                    is Number, is Boolean -> value
                    else -> value.toString()
                }
            })
        }
        return rows
    }

    override val outputs: Map<String, String>
        get() = mapOf("rowCount" to "Number of rows")

    override val description: String
        get() = "Runs a query on a PostgreSQL database"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("hostname", "Hostname", PropertyInformationType.STRING, "", "Such as db.example.com."))
            actionInfo.add(PropertyInformation("port", "Port", PropertyInformationType.NUMBER, "5432", ""))
            actionInfo.add(PropertyInformation("database", "Database", PropertyInformationType.STRING, "", ""))
            actionInfo.add(PropertyInformation("username", "Username", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.CREDENTIAL))
            actionInfo.add(PropertyInformation("password", "Password", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.add(PropertyInformation("useTls", "Use TLS", PropertyInformationType.BOOLEAN, "false", "Requires an encrypted connection. The certificate of the server is not verified."))
            actionInfo.add(PropertyInformation("command", "Query", PropertyInformationType.MULTILINE, "", "Write ? where a value goes, such as SELECT * FROM orders WHERE id = ?"))
            actionInfo.add(PropertyInformation("queryValues", "Values", PropertyInformationType.MULTILINE, "", "One value per line, for each ? in the query. Use this for text from earlier steps, since it cannot change the query."))
            return actionInfo
        }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10
    }
}
