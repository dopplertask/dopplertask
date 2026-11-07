package com.dopplertask.dopplertask.domain.action.connection

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.ColumnEncryptor
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import tools.jackson.databind.ObjectMapper
import com.mysql.cj.jdbc.MysqlDataSource
import java.io.IOException
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient

@Entity
@Table(name = "MySQLAction")
@DiscriminatorValue("mysql_action")
class MySQLAction : Action() {
    @Column
    @Convert(converter = ColumnEncryptor::class)
    var hostname: String? = null

    @Transient
    var username: String? = null

    @Transient
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
                var hasMoreResults = stmt?.execute(localCommand)

                var mysqlResultList = mutableListOf<MySQLResult>()


                while (hasMoreResults!! || stmt?.updateCount != -1 ) {
                    if(hasMoreResults) {
                        var mysqlResult = MySQLResult()
                        rs = stmt?.resultSet;
                        val rsmd = rs?.metaData

                        mysqlResult.query = localCommand
                        mysqlResult.rows = mutableListOf<MySQLResult.MySQLRow>()

                        val columnsNumber = rsmd?.columnCount
                        while (rs!!.next()) {
                            val mysqlColumnList = mutableListOf<MySQLResult.MySQLColumn>()
                            for (i in 1..columnsNumber!!) {
                                val columnValue = rs?.getString(i)
                                val columnName = rsmd.getColumnName(i)
                                // Create MySQLColumn object
                                mysqlColumnList.add(MySQLResult.MySQLColumn(columnName, columnValue))

                            }
                            mysqlResult.rows.add(MySQLResult.MySQLRow(mysqlColumnList))
                        }
                        mysqlResultList.add(mysqlResult)
                    }
                    else {
                        val queryResult = stmt?.getUpdateCount()
                        if (queryResult == -1) { // no more queries processed
                            break;
                        }
                    }
                    hasMoreResults = stmt?.moreResults;

                }
                // Create JSON from the result using Jackson
                val mapper = ObjectMapper()
                val json = mapper.writeValueAsString(mysqlResultList)
                actionResult.output = json
                actionResult.statusCode = StatusCode.SUCCESS
                return actionResult
            }
        } catch (e: SQLException) {
            actionResult.errorMsg = if (e.sqlState?.startsWith("08") == true) "Could not connect to the database on $localHostname. Check the address, and that the database is running." else e.toString()
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
                    PropertyInformation.PropertyInformationCategory.PROPERTY
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
                    PropertyInformation.PropertyInformationCategory.SECRET
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "database", "Database", PropertyInformationType.STRING, "", "Database name", emptyList(),
                    PropertyInformation.PropertyInformationCategory.PROPERTY
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "port", "Port", PropertyInformationType.STRING, "3306", "Default is 3306", emptyList(),
                    PropertyInformation.PropertyInformationCategory.PROPERTY
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
                    PropertyInformation.PropertyInformationCategory.PROPERTY
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "command",
                    "MySQL Statement",
                    PropertyInformationType.MULTILINE,
                    "",
                    "The SQL to run, such as SELECT * FROM orders."
                )
            )
            return actionInfo
        }

    override val description: String
        get() = "Creates a MySQL database connection and runs a command"
}