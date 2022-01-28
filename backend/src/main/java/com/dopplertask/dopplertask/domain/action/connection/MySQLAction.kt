package com.dopplertask.dopplertask.domain.action.connection

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import com.mysql.cj.jdbc.MysqlDataSource
import java.io.IOException
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import javax.persistence.*

@Entity
@Table(name = "MySQLAction")
@DiscriminatorValue("mysql_action")
class MySQLAction : Action() {
    @Column
    var hostname: String? = null

    @Column
    var username: String? = null

    @Column
    var password: String? = null

    @Column
    var port: String? = null

    @Column
    var database: String? = null

    @Column
    var timezone: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var command: String? = null

    @Throws(IOException::class)
    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?): ActionResult {
        val localHostname = variableExtractorUtil.extract(hostname, execution, scriptLanguage)
        val localUsername = variableExtractorUtil.extract(username, execution, scriptLanguage)
        val localPassword = variableExtractorUtil.extract(password, execution, scriptLanguage)
        val localPort = variableExtractorUtil.extract(port, execution, scriptLanguage)
        val localDatabase = variableExtractorUtil.extract(database, execution, scriptLanguage)
        val localCommand = variableExtractorUtil.extract(command, execution, scriptLanguage)
        val localTimezone = variableExtractorUtil.extract(timezone, execution, scriptLanguage)
        val dataSource = MysqlDataSource()
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
                rs = stmt?.executeQuery(localCommand)
                val rsmd = rs?.getMetaData()
                val builder = StringBuilder()
                builder.append("Quering: $localCommand\n")
                builder.append("Result: \n\n")
                val columnsNumber = rsmd?.columnCount
                while (rs!!.next()) {
                    for (i in 1..columnsNumber!!) {
                        if (i > 1) {
                            builder.append(",  ")
                        }
                        val columnValue = rs?.getString(i)
                        builder.append(columnValue + " " + rsmd.getColumnName(i))
                    }
                    builder.append("\n")
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
            actionInfo.add(PropertyInformation("hostname", "Hostname", PropertyInformationType.STRING, "", "Hostname or IP"))
            actionInfo.add(PropertyInformation("username", "Username", PropertyInformationType.STRING, "", "Username"))
            actionInfo.add(PropertyInformation("password", "Password", PropertyInformationType.STRING, "", "Password"))
            actionInfo.add(PropertyInformation("database", "Database", PropertyInformationType.STRING, "", "Database name"))
            actionInfo.add(PropertyInformation("port", "Port", PropertyInformationType.STRING, "3306", "Default is 3306"))
            actionInfo.add(PropertyInformation("timezone", "Timezone", PropertyInformationType.STRING, "", "Specify timezone. Example CET"))
            actionInfo.add(PropertyInformation("command", "MySQL Statement", PropertyInformationType.STRING, "", "Statement to execute"))
            return actionInfo
        }

    override val description: String
        get() = "Creates a MySQL database connection and runs a command"
}