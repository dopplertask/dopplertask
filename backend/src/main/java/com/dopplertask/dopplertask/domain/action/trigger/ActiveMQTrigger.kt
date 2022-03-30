package com.dopplertask.dopplertask.domain.action.trigger

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.OutputType
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.TriggerException
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import org.apache.activemq.ActiveMQConnectionFactory
import java.io.IOException
import javax.jms.Connection
import javax.jms.Destination
import javax.jms.Message
import javax.jms.MessageConsumer
import javax.jms.Session
import javax.jms.TextMessage
import javax.persistence.Column
import javax.persistence.Entity

@Entity
class ActiveMQTrigger : Trigger() {

    @Column
    var url: String? = null

    @Column
    var queue: String? = null


    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("url", "Url", PropertyInformation.PropertyInformationType.STRING))
            actionInfo.add(
                PropertyInformation(
                    "queue",
                    "Queue/Topic",
                    PropertyInformation.PropertyInformationType.STRING
                )
            )
            return actionInfo
        }

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val result = ActionResult()
        result.output = "ActiveMQ triggered"
        result.statusCode = StatusCode.SUCCESS
        result.outputType = OutputType.STRING
        return result
    }

    override fun trigger(): TriggerResult {
        try {

            // Create a ConnectionFactory
            val connectionFactory = ActiveMQConnectionFactory(url)

            // Create a Connection
            val connection: Connection = connectionFactory.createConnection()
            connection.start()

            // Create a Session
            val session: Session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

            // Create the destination (Topic or Queue)
            val destination: Destination = session.createQueue(queue)

            // Create a MessageConsumer from the Session to the Topic or Queue
            val consumer: MessageConsumer = session.createConsumer(destination)

            // Wait for a message
            val message: Message = consumer.receive()
            if (message is TextMessage) {
                val textMessage: TextMessage = message
                val text: String = textMessage.text

                closeAllConnections(consumer, session, connection)
                return TriggerResult(mutableMapOf(Pair("Message", text)))
            } else {

                closeAllConnections(consumer, session, connection)
                return TriggerResult(mutableMapOf(Pair("Message", "Message is an object.")))
            }


        } catch (e: Exception) {
            throw TriggerException();
        }

    }

    private fun closeAllConnections(
        consumer: MessageConsumer,
        session: Session,
        connection: Connection
    ) {
        consumer.close()
        session.close()
        connection.close()
    }

    override val description: String
        get() = "Starts the workflow when the a message is received."
}