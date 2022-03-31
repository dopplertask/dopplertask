package com.dopplertask.dopplertask.domain.action

import com.dopplertask.dopplertask.domain.*
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.common.*
import com.dopplertask.dopplertask.domain.action.connection.HttpAction
import com.dopplertask.dopplertask.domain.action.connection.MySQLAction
import com.dopplertask.dopplertask.domain.action.connection.SSHAction
import com.dopplertask.dopplertask.domain.action.connection.SecureCopyAction
import com.dopplertask.dopplertask.domain.action.integration.jenkins.JenkinsAction
import com.dopplertask.dopplertask.domain.action.integration.jira.JiraAction
import com.dopplertask.dopplertask.domain.action.integration.rockmelon.RockmelonAction
import com.dopplertask.dopplertask.domain.action.io.ReadFileAction
import com.dopplertask.dopplertask.domain.action.io.WriteFileAction
import com.dopplertask.dopplertask.domain.action.trigger.ActiveMQTrigger
import com.dopplertask.dopplertask.domain.action.trigger.IntervalTrigger
import com.dopplertask.dopplertask.domain.action.trigger.Webhook
import com.dopplertask.dopplertask.domain.action.ui.BrowseWebAction
import com.dopplertask.dopplertask.domain.action.ui.MouseAction
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.io.IOException
import java.util.*
import java.util.stream.Collectors
import javax.persistence.*

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "action_type", length = 255)
@DiscriminatorValue("noop")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.PROPERTY)
@JsonSubTypes(
    JsonSubTypes.Type(value = BrowseWebAction::class, name = "BrowseWebAction"),
    JsonSubTypes.Type(value = HttpAction::class, name = "HttpAction"),
    JsonSubTypes.Type(value = LinkedTaskAction::class, name = "LinkedTaskAction"),
    JsonSubTypes.Type(value = MySQLAction::class, name = "MySQLAction"),
    JsonSubTypes.Type(value = PrintAction::class, name = "PrintAction"),
    JsonSubTypes.Type(value = ReadFileAction::class, name = "ReadFileAction"),
    JsonSubTypes.Type(value = SecureCopyAction::class, name = "SecureCopyAction"),
    JsonSubTypes.Type(value = SSHAction::class, name = "SSHAction"),
    JsonSubTypes.Type(value = TimedWait::class, name = "TimedWait"),
    JsonSubTypes.Type(value = ExecuteCommandAction::class, name = "ExecuteCommandAction"),
    JsonSubTypes.Type(value = SetVariableAction::class, name = "SetVariableAction"),
    JsonSubTypes.Type(value = ScriptAction::class, name = "ScriptAction"),
    JsonSubTypes.Type(value = IfAction::class, name = "IfAction"),
    JsonSubTypes.Type(value = MouseAction::class, name = "MouseAction"),
    JsonSubTypes.Type(value = StartAction::class, name = "StartAction"),
    JsonSubTypes.Type(value = WriteFileAction::class, name = "WriteFileAction"),
    JsonSubTypes.Type(value = SwitchAction::class, name = "SwitchAction"),
    JsonSubTypes.Type(value = XMLAction::class, name = "XMLAction"),
    JsonSubTypes.Type(value = Webhook::class, name = "Webhook"),
    JsonSubTypes.Type(value = ActiveMQTrigger::class, name = "ActiveMQTrigger"),
    JsonSubTypes.Type(value = IntervalTrigger::class, name = "IntervalTrigger"),
    JsonSubTypes.Type(value = JenkinsAction::class, name = "JenkinsAction"),
    JsonSubTypes.Type(value = RockmelonAction::class, name = "RockmelonAction"),
    JsonSubTypes.Type(value = JiraAction::class, name = "JiraAction")
)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class Action {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @JsonIgnore
    open var id: Long? = null

    @ManyToOne
    @JoinColumn
    @JsonIgnore
    open var task: Task? = null

    @Column
    @JsonIgnore
    open var orderPosition: Int? = null

    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    open var isContinueOnFailure = false

    @Column(length = 4096)
    open var failOn: String? = null
    open var retries = 0

    @Column(length = 4096)
    open var retryWait: String? = null
    open var guiXPos: Int? = null
    open var guiYPos: Int? = null

    /**
     * All action values are evaluated with VELOCITY as standard, but can be changed to other languages.
     */
    @Enumerated(EnumType.STRING)
    @Column
    open var scriptLanguage = ScriptLanguage.VELOCITY

    @OneToMany(mappedBy = "action", cascade = [CascadeType.ALL])
    @Fetch(value = FetchMode.JOIN)
    open var ports: MutableList<ActionPort>? = ArrayList()

    @get:JsonIgnore
    val outputPorts: List<ActionPort>
        get() = if (ports != null) {
            ports!!.stream().filter { actionPort: ActionPort -> actionPort.portType == ActionPortType.OUTPUT }
                .collect(Collectors.toList())
        } else emptyList()

    @get:JsonIgnore
    val inputPorts: List<ActionPort>
        get() = if (ports != null) {
            ports!!.stream().filter { actionPort: ActionPort -> actionPort.portType == ActionPortType.INPUT }
                .collect(Collectors.toList())
        } else emptyList()

    @Throws(IOException::class)
    fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil
    ): ActionResult {
        return run(taskService, execution, variableExtractorUtil, null)
    }

    /**
     * Executes an action.
     *
     * @param taskService           which handles task execution.
     * @param execution             of the current task.
     * @param variableExtractorUtil utility to evaluate velocity code.
     * @param broadcastListener     custom broadcast message from within the action.
     * @return an action result which represents the outcome of the executed action.
     */
    @Throws(IOException::class)
    open fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        return ActionResult()
    }

    @get:JsonIgnore
    open val actionInfo: MutableList<PropertyInformation>
        get() = mutableListOf(
            PropertyInformation(
                "continueOnFailure",
                "Continue on failure",
                PropertyInformationType.BOOLEAN,
                "false",
                "true or false. Lets the action continue on failure, ignoring any retry.",
                mutableListOf(),
                PropertyInformation.PropertyInformationCategory.SETTING
            ),
            PropertyInformation(
                "scriptLanguage",
                "Script Language",
                PropertyInformationType.DROPDOWN,
                "VELOCITY",
                "VELOCITY (default), JAVASCRIPT.",
                mutableListOf(
                    PropertyInformation("VELOCITY", "Velocity"), PropertyInformation(
                        "JAVASCRIPT",
                        "Javascript"
                    )
                ),
                PropertyInformation.PropertyInformationCategory.SETTING
            ),
            PropertyInformation(
                "retries",
                "Max. Retries",
                PropertyInformationType.NUMBER,
                "0",
                "Amount of retries.",
                mutableListOf(),
                PropertyInformation.PropertyInformationCategory.SETTING
            ),
            PropertyInformation(
                "retryWait",
                "Wait between retries",
                PropertyInformationType.STRING,
                "1000",
                "Milliseconds to wait before next retry.",
                mutableListOf(),
                PropertyInformation.PropertyInformationCategory.SETTING
            ),
            PropertyInformation(
                "failOn",
                "Fail on",
                PropertyInformationType.STRING,
                "",
                "The current action will fail if this evaluates to anything.",
                mutableListOf(),
                PropertyInformation.PropertyInformationCategory.SETTING
            )
        )

    /**
     * Describes the action in a short text.
     *
     * @return action description.
     */
    @get:JsonIgnore
    abstract val description: String

    class PropertyInformation
    /**
     * Initialize a property info with name and displayName. Type is set to a String.
     *
     * @param name
     * @param displayName
     */
    {

        var name: String
        var displayName: String
        var type: PropertyInformationType = PropertyInformationType.STRING
        var defaultValue: String = ""
        var description: String = ""
        var options: List<PropertyInformation>
        var category: PropertyInformationCategory = PropertyInformationCategory.PROPERTY
        var displayOptions: Map<String, Array<String>> = mutableMapOf()


        @JvmOverloads
        constructor(
            name: String,
            displayName: String,
            type: PropertyInformationType = PropertyInformationType.STRING,
            defaultValue: String = "",
            description: String = "",
            options: List<PropertyInformation> = java.util.List.of(),
            category: PropertyInformationCategory = PropertyInformationCategory.PROPERTY,
            displayOptions: Map<String, Array<String>> = mutableMapOf()
        ) {
            this.name = name
            this.displayName = displayName
            this.type = type
            this.defaultValue = defaultValue
            this.description = description
            this.options = options
            this.category = category
            this.displayOptions = displayOptions
        }

        constructor(
            name: String,
            displayName: String,
            options: List<PropertyInformation>,
        ) {
            this.name = name
            this.displayName = displayName
            this.options = options
        }

        constructor(
            name: String,
            displayName: String,
            options: List<PropertyInformation>,
            displayOptions: Map<String, Array<String>> = mutableMapOf()
        ) {
            this.name = name
            this.displayName = displayName
            this.options = options
            this.displayOptions = displayOptions
        }

        enum class PropertyInformationType {
            STRING, MULTILINE, BOOLEAN, NUMBER, DROPDOWN, MAP
        }

        enum class PropertyInformationCategory {
            PROPERTY, CREDENTIAL, SETTING
        }

    }
}