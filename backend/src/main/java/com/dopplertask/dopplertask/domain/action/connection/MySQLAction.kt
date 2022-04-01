package com.dopplertask.dopplertask.domain.action.connection

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.ColumnEncryptor
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import com.mysql.cj.jdbc.MysqlDataSource
import java.io.IOException
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table

@Entity
@Table(name = "MySQLAction")
@DiscriminatorValue("mysql_action")
class MySQLAction : Action() {
    @Column
    @Convert(converter = ColumnEncryptor::class)
    var hostname: String? = null

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var username: String? = null

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var password: String? = null

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var port: String? = null

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var database: String? = null

    @Column
    @Convert(converter = ColumnEncryptor::class)
    var timezone: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var command: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val localHostname = variableExtractorUtil.extract(hostname, execution, scriptLanguage)
        val localUsername = variableExtractorUtil.extract(username, execution, scriptLanguage)
        val localPassword = variableExtractorUtil.extract(password, execution, scriptLanguage)
        val localPort = variableExtractorUtil.extract(port, execution, scriptLanguage)
        val localDatabase = variableExtractorUtil.extract(database, execution, scriptLanguage)
        val localCommand = variableExtractorUtil.extract(command, execution, scriptLanguage)
        val localTimezone = variableExtractorUtil.extract(timezone, execution, scriptLanguage)
        val dataSource = MysqlDataSource()
        dataSource.allowMultiQueries = true
        dataSource.user = localUsername
        dataSource.password = localPassword
        dataSource.serverName = localHostname
        dataSource.databaseName = localDatabase
        if (localPort != null && !localPort.isEmpty()) {
            try {
                dataSource.port = localPort.toInt()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
        }
        if (localTimezone.isNotEmpty()) {
            try {
                dataSource.serverTimezone = localTimezone
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
        var stmt: Statement? = null
        var rs: ResultSet? = null
        val actionResult = ActionResult()
        try {
            dataSource.connection.use { conn ->
                stmt = conn.createStatement()
                val builder = StringBuilder()
                var hasMoreResults = stmt?.execute(localCommand)
                while (hasMoreResults!! || stmt?.updateCount != -1 ) {
                    if(hasMoreResults!!) {
                        rs = stmt?.resultSet;
                        val rsmd = rs?.metaData


                        builder.append("Quering: $localCommand\n")
                        builder.append("Result: \n\n")
                        val columnsNumber = rsmd?.columnCount
                        while (rs!!.next()) {
                            for (i in 1..columnsNumber!!) {
                                if (i > 1) {
                                    builder.append(", ")
                                }
                                val columnValue = rs?.getString(i)
                                builder.append(rsmd.getColumnName(i) + ": " + columnValue)
                            }
                            builder.append("\n")
                        }
                    }
                    else {
                        val queryResult = stmt?.getUpdateCount()
                        if (queryResult == -1) { // no more queries processed
                            break;
                        }
                    }
                    hasMoreResults = stmt?.moreResults;

                }
                actionResult.output = builder.toString()
                actionResult.statusCode = StatusCode.SUCCESS
                return actionResult
            }
        } catch (e: SQLException) {
            actionResult.errorMsg = e.toString()
            actionResult.statusCode = StatusCode.FAILURE
            return actionResult
        } finally {
            try {
                if (stmt != null) {
                    stmt!!.close()
                }
            } catch (e: SQLException) {
            }
            try {
                if (rs != null) {
                    rs!!.close()
                }
            } catch (e: SQLException) {
            }
        }
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(
                PropertyInformation(
                    "hostname", "Hostname", PropertyInformationType.STRING, "", "Hostname or IP", emptyList(),
                    PropertyInformation.PropertyInformationCategory.CREDENTIAL
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "username", "Username", PropertyInformationType.STRING, "", "Username",
                    emptyList(),
                    PropertyInformation.PropertyInformationCategory.CREDENTIAL
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "password", "Password", PropertyInformationType.STRING, "", "Password", emptyList(),
                    PropertyInformation.PropertyInformationCategory.CREDENTIAL
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "database", "Database", PropertyInformationType.STRING, "", "Database name", emptyList(),
                    PropertyInformation.PropertyInformationCategory.CREDENTIAL
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "port", "Port", PropertyInformationType.STRING, "3306", "Default is 3306", emptyList(),
                    PropertyInformation.PropertyInformationCategory.CREDENTIAL
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "timezone",
                    "Timezone",
                    PropertyInformationType.STRING,
                    "",
                    "Specify timezone. Example CET",
                    emptyList(),
                    PropertyInformation.PropertyInformationCategory.CREDENTIAL
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "command",
                    "MySQL Statement",
                    PropertyInformationType.MULTILINE,
                    "",
                    "Statement to execute"
                )
            )
            return actionInfo
        }

    override val description: String
        get() = "Creates a MySQL database connection and runs a command"
}