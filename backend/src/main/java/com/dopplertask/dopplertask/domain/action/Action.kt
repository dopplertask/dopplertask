package com.dopplertask.dopplertask.domain.action

import com.dopplertask.dopplertask.domain.ActionPort
import com.dopplertask.dopplertask.domain.ActionPortType
import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.Task
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.common.*
import com.dopplertask.dopplertask.domain.action.connection.HttpAction
import com.dopplertask.dopplertask.domain.action.connection.MySQLAction
import com.dopplertask.dopplertask.domain.action.connection.PostgresAction
import com.dopplertask.dopplertask.domain.action.connection.RedisAction
import com.dopplertask.dopplertask.domain.action.connection.SSHAction
import com.dopplertask.dopplertask.domain.action.connection.SecureCopyAction
import com.dopplertask.dopplertask.domain.action.integration.anthropic.ClaudeAction
import com.dopplertask.dopplertask.domain.action.integration.chat.DiscordAction
import com.dopplertask.dopplertask.domain.action.integration.chat.MattermostAction
import com.dopplertask.dopplertask.domain.action.integration.chat.SlackAction
import com.dopplertask.dopplertask.domain.action.integration.chat.TeamsAction
import com.dopplertask.dopplertask.domain.action.integration.chat.TelegramAction
import com.dopplertask.dopplertask.domain.action.integration.chat.TwilioAction
import com.dopplertask.dopplertask.domain.action.integration.chat.WhatsAppAction
import com.dopplertask.dopplertask.domain.action.integration.code.AsanaAction
import com.dopplertask.dopplertask.domain.action.integration.code.BitbucketAction
import com.dopplertask.dopplertask.domain.action.integration.code.CircleCIAction
import com.dopplertask.dopplertask.domain.action.integration.code.ClickUpAction
import com.dopplertask.dopplertask.domain.action.integration.code.FreshdeskAction
import com.dopplertask.dopplertask.domain.action.integration.code.GitHubAction
import com.dopplertask.dopplertask.domain.action.integration.code.GitLabAction
import com.dopplertask.dopplertask.domain.action.integration.code.GiteaAction
import com.dopplertask.dopplertask.domain.action.integration.code.IntercomAction
import com.dopplertask.dopplertask.domain.action.integration.code.JiraAction
import com.dopplertask.dopplertask.domain.action.integration.code.LinearAction
import com.dopplertask.dopplertask.domain.action.integration.code.MondayAction
import com.dopplertask.dopplertask.domain.action.integration.code.TrelloAction
import com.dopplertask.dopplertask.domain.action.integration.code.ZendeskAction
import com.dopplertask.dopplertask.domain.action.integration.commerce.PayPalAction
import com.dopplertask.dopplertask.domain.action.integration.commerce.ShopifyAction
import com.dopplertask.dopplertask.domain.action.integration.commerce.StripeAction
import com.dopplertask.dopplertask.domain.action.integration.commerce.WooCommerceAction
import com.dopplertask.dopplertask.domain.action.integration.connection.ElasticsearchAction
import com.dopplertask.dopplertask.domain.action.integration.connection.KafkaAction
import com.dopplertask.dopplertask.domain.action.integration.connection.MongoDBAction
import com.dopplertask.dopplertask.domain.action.integration.connection.MqttAction
import com.dopplertask.dopplertask.domain.action.integration.connection.RabbitMQAction
import com.dopplertask.dopplertask.domain.action.integration.incident.DatadogAction
import com.dopplertask.dopplertask.domain.action.integration.incident.GrafanaAction
import com.dopplertask.dopplertask.domain.action.integration.incident.PagerDutyAction
import com.dopplertask.dopplertask.domain.action.integration.incident.PrometheusAction
import com.dopplertask.dopplertask.domain.action.integration.incident.SentryAction
import com.dopplertask.dopplertask.domain.action.integration.infrastructure.CloudflareAction
import com.dopplertask.dopplertask.domain.action.integration.infrastructure.DigitalOceanAction
import com.dopplertask.dopplertask.domain.action.integration.infrastructure.OktaAction
import com.dopplertask.dopplertask.domain.action.integration.jenkins.JenkinsAction
import com.dopplertask.dopplertask.domain.action.integration.mail.EmailAction
import com.dopplertask.dopplertask.domain.action.integration.mail.OutlookAction
import com.dopplertask.dopplertask.domain.action.integration.mail.ReadEmailAction
import com.dopplertask.dopplertask.domain.action.integration.mail.SendGridAction
import com.dopplertask.dopplertask.domain.action.integration.openai.OpenAIAction
import com.dopplertask.dopplertask.domain.action.integration.sales.HubSpotAction
import com.dopplertask.dopplertask.domain.action.integration.sales.MailchimpAction
import com.dopplertask.dopplertask.domain.action.integration.sales.PipedriveAction
import com.dopplertask.dopplertask.domain.action.integration.sales.SalesforceAction
import com.dopplertask.dopplertask.domain.action.integration.storage.AirtableAction
import com.dopplertask.dopplertask.domain.action.integration.storage.BoxAction
import com.dopplertask.dopplertask.domain.action.integration.storage.ConfluenceAction
import com.dopplertask.dopplertask.domain.action.integration.storage.GoogleCalendarAction
import com.dopplertask.dopplertask.domain.action.integration.storage.GoogleDriveAction
import com.dopplertask.dopplertask.domain.action.integration.storage.GoogleSheetsAction
import com.dopplertask.dopplertask.domain.action.integration.storage.NotionAction
import com.dopplertask.dopplertask.domain.action.integration.storage.OneDriveAction
import com.dopplertask.dopplertask.domain.action.integration.storage.S3Action
import com.dopplertask.dopplertask.domain.action.integration.storage.SupabaseAction
import com.dopplertask.dopplertask.domain.action.integration.storage.WordPressAction
import com.dopplertask.dopplertask.domain.action.io.ReadFileAction
import com.dopplertask.dopplertask.domain.action.io.WriteFileAction
import com.dopplertask.dopplertask.domain.action.trigger.ActiveMQTrigger
import com.dopplertask.dopplertask.domain.action.trigger.IntervalTrigger
import com.dopplertask.dopplertask.domain.action.trigger.ScheduleTrigger
import com.dopplertask.dopplertask.domain.action.trigger.Webhook
import com.dopplertask.dopplertask.domain.action.ui.BrowseWebAction
import com.dopplertask.dopplertask.domain.action.ui.MouseAction
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import org.springframework.beans.BeanWrapperImpl
import tools.jackson.databind.annotation.JsonTypeIdResolver
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Transient
import java.io.IOException
import java.util.stream.Collectors
import org.hibernate.annotations.DiscriminatorOptions
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "action_type", length = 255)
// Otherwise the table only accepts the steps that existed when it was created, and refuses every step added later.
@DiscriminatorOptions(force = true)
@DiscriminatorValue("noop")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.PROPERTY, visible = true)
@JsonTypeIdResolver(StepTypeIdResolver::class)
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
    JsonSubTypes.Type(value = LoopAction::class, name = "LoopAction"),
    JsonSubTypes.Type(value = MouseAction::class, name = "MouseAction"),
    JsonSubTypes.Type(value = StartAction::class, name = "StartAction"),
    JsonSubTypes.Type(value = WriteFileAction::class, name = "WriteFileAction"),
    JsonSubTypes.Type(value = SwitchAction::class, name = "SwitchAction"),
    JsonSubTypes.Type(value = WebhookReplyAction::class, name = "WebhookReplyAction"),
    JsonSubTypes.Type(value = XMLAction::class, name = "XMLAction"),
    JsonSubTypes.Type(value = Webhook::class, name = "Webhook"),
    JsonSubTypes.Type(value = ActiveMQTrigger::class, name = "ActiveMQTrigger"),
    JsonSubTypes.Type(value = IntervalTrigger::class, name = "IntervalTrigger"),
    JsonSubTypes.Type(value = ScheduleTrigger::class, name = "ScheduleTrigger"),
    JsonSubTypes.Type(value = JenkinsAction::class, name = "JenkinsAction"),
    JsonSubTypes.Type(value = RegexAction::class, name = "RegexAction"),
    JsonSubTypes.Type(value = OpenAIAction::class, name = "OpenAIAction"),
    JsonSubTypes.Type(value = TelegramAction::class, name = "TelegramAction"),
    JsonSubTypes.Type(value = DiscordAction::class, name = "DiscordAction"),
    JsonSubTypes.Type(value = PagerDutyAction::class, name = "PagerDutyAction"),
    JsonSubTypes.Type(value = GitHubAction::class, name = "GitHubAction"),
    JsonSubTypes.Type(value = JiraAction::class, name = "JiraAction"),
    JsonSubTypes.Type(value = RedisAction::class, name = "RedisAction"),
    JsonSubTypes.Type(value = PostgresAction::class, name = "PostgresAction"),
    JsonSubTypes.Type(value = EmailAction::class, name = "EmailAction"),
    JsonSubTypes.Type(value = S3Action::class, name = "S3Action"),
    JsonSubTypes.Type(value = TeamsAction::class, name = "TeamsAction"),
    JsonSubTypes.Type(value = MattermostAction::class, name = "MattermostAction"),
    JsonSubTypes.Type(value = TwilioAction::class, name = "TwilioAction"),
    JsonSubTypes.Type(value = GitLabAction::class, name = "GitLabAction"),
    JsonSubTypes.Type(value = LinearAction::class, name = "LinearAction"),
    JsonSubTypes.Type(value = ZendeskAction::class, name = "ZendeskAction"),
    JsonSubTypes.Type(value = NotionAction::class, name = "NotionAction"),
    JsonSubTypes.Type(value = GoogleSheetsAction::class, name = "GoogleSheetsAction"),
    JsonSubTypes.Type(value = ClaudeAction::class, name = "ClaudeAction"),
    JsonSubTypes.Type(value = ReadEmailAction::class, name = "ReadEmailAction"),
    JsonSubTypes.Type(value = SlackAction::class, name = "SlackAction"),
    JsonSubTypes.Type(value = HubSpotAction::class, name = "HubSpotAction"),
    JsonSubTypes.Type(value = SalesforceAction::class, name = "SalesforceAction"),
    JsonSubTypes.Type(value = PipedriveAction::class, name = "PipedriveAction"),
    JsonSubTypes.Type(value = MailchimpAction::class, name = "MailchimpAction"),
    JsonSubTypes.Type(value = StripeAction::class, name = "StripeAction"),
    JsonSubTypes.Type(value = ShopifyAction::class, name = "ShopifyAction"),
    JsonSubTypes.Type(value = WooCommerceAction::class, name = "WooCommerceAction"),
    JsonSubTypes.Type(value = IntercomAction::class, name = "IntercomAction"),
    JsonSubTypes.Type(value = FreshdeskAction::class, name = "FreshdeskAction"),
    JsonSubTypes.Type(value = AsanaAction::class, name = "AsanaAction"),
    JsonSubTypes.Type(value = TrelloAction::class, name = "TrelloAction"),
    JsonSubTypes.Type(value = ClickUpAction::class, name = "ClickUpAction"),
    JsonSubTypes.Type(value = MondayAction::class, name = "MondayAction"),
    JsonSubTypes.Type(value = WhatsAppAction::class, name = "WhatsAppAction"),
    JsonSubTypes.Type(value = AirtableAction::class, name = "AirtableAction"),
    JsonSubTypes.Type(value = SupabaseAction::class, name = "SupabaseAction"),
    JsonSubTypes.Type(value = GoogleDriveAction::class, name = "GoogleDriveAction"),
    JsonSubTypes.Type(value = GoogleCalendarAction::class, name = "GoogleCalendarAction"),
    JsonSubTypes.Type(value = ConfluenceAction::class, name = "ConfluenceAction"),
    JsonSubTypes.Type(value = MongoDBAction::class, name = "MongoDBAction"),
    JsonSubTypes.Type(value = KafkaAction::class, name = "KafkaAction"),
    JsonSubTypes.Type(value = RabbitMQAction::class, name = "RabbitMQAction"),
    JsonSubTypes.Type(value = MqttAction::class, name = "MqttAction"),
    JsonSubTypes.Type(value = ElasticsearchAction::class, name = "ElasticsearchAction"),
    JsonSubTypes.Type(value = PrometheusAction::class, name = "PrometheusAction"),
    JsonSubTypes.Type(value = GrafanaAction::class, name = "GrafanaAction"),
    JsonSubTypes.Type(value = DatadogAction::class, name = "DatadogAction"),
    JsonSubTypes.Type(value = SentryAction::class, name = "SentryAction"),
    JsonSubTypes.Type(value = CloudflareAction::class, name = "CloudflareAction"),
    JsonSubTypes.Type(value = DigitalOceanAction::class, name = "DigitalOceanAction"),
    JsonSubTypes.Type(value = OktaAction::class, name = "OktaAction"),
    JsonSubTypes.Type(value = GiteaAction::class, name = "GiteaAction"),
    JsonSubTypes.Type(value = BitbucketAction::class, name = "BitbucketAction"),
    JsonSubTypes.Type(value = CircleCIAction::class, name = "CircleCIAction"),
    JsonSubTypes.Type(value = WordPressAction::class, name = "WordPressAction"),
    JsonSubTypes.Type(value = BoxAction::class, name = "BoxAction"),
    JsonSubTypes.Type(value = OneDriveAction::class, name = "OneDriveAction"),
    JsonSubTypes.Type(value = OutlookAction::class, name = "OutlookAction"),
    JsonSubTypes.Type(value = SendGridAction::class, name = "SendGridAction"),
    JsonSubTypes.Type(value = PayPalAction::class, name = "PayPalAction")
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

    @Column(nullable = false)
    open var isContinueOnFailure = false

    @Column(length = 4096)
    open var failOn: String? = null
    open var retries = 0

    @Column(length = 4096)
    open var retryWait: String? = null
    open var guiXPos: Int? = null
    open var guiYPos: Int? = null

    open var actionName: String? = this.javaClass.simpleName

    /**
     * The name of the credential this step signs in with. It never holds the values.
     */
    @Column
    open var credential: String? = null

    /*
     * Signing in with an account (OAuth 2). Only a credential fills these in, and only while the step runs, for the
     * steps whose credential offers it.
     */
    @Transient @JsonInclude(JsonInclude.Include.NON_NULL) open var oauthProvider: String? = null
    @Transient @JsonInclude(JsonInclude.Include.NON_NULL) open var oauthGrant: String? = null
    @Transient @JsonInclude(JsonInclude.Include.NON_NULL) open var oauthClientId: String? = null
    @Transient @JsonInclude(JsonInclude.Include.NON_NULL) open var oauthClientSecret: String? = null
    @Transient @JsonInclude(JsonInclude.Include.NON_NULL) open var oauthScope: String? = null
    @Transient @JsonInclude(JsonInclude.Include.NON_NULL) open var oauthAuthUrl: String? = null
    @Transient @JsonInclude(JsonInclude.Include.NON_NULL) open var oauthTokenUrl: String? = null
    @Transient @JsonInclude(JsonInclude.Include.NON_NULL) open var oauthAuthParameters: String? = null
    @Transient @JsonInclude(JsonInclude.Include.NON_NULL) open var oauthClientInHeader: String? = null
    @Transient @JsonInclude(JsonInclude.Include.NON_NULL) open var oauthRefreshToken: String? = null
    @Transient @JsonInclude(JsonInclude.Include.NON_NULL) open var oauthAccessToken: String? = null

    /**
     * Which credentials fit this step. Steps that sign in to the same service share a type, so one account is entered once.
     */
    @get:JsonIgnore
    open val credentialType: String
        get() = javaClass.simpleName

    /** How the editor shows a kind that it does not know by name, such as a connector. */
    @get:JsonIgnore
    open val stepLabel: String?
        get() = null

    @get:JsonIgnore
    open val stepIcon: String?
        get() = null

    /** The kind of step, by the name that tasks, the editor and agents use. */
    @get:JsonIgnore
    open val stepTypeName: String
        get() = javaClass.simpleName

    /** Takes what the step's credential holds, only while it runs. A step reads them as its own fields. */
    open fun useCredentialValues(values: Map<String, String>) {
        BeanWrapperImpl(this).setPropertyValues(values)
    }

    /** Credential values that were written into the step itself, which saving moves into a credential. */
    open fun inlineCredentialValues(fields: Set<String>): Map<String, String> {
        val step = BeanWrapperImpl(this)
        return fields.mapNotNull { field -> step.getPropertyValue(field)?.toString()?.takeIf { it.isNotBlank() }?.let { field to it } }.toMap()
    }

    open fun forgetCredentialValues(fields: Set<String>) {
        val step = BeanWrapperImpl(this)
        fields.forEach { step.setPropertyValue(it, null) }
    }

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
                "actionName",
                "Action name",
                PropertyInformationType.STRING,
                "",
                "Custom description of this action.",
                mutableListOf(),
                PropertyInformation.PropertyInformationCategory.SETTING
            ),
            PropertyInformation(
                "continueOnFailure",
                "Continue on failure",
                PropertyInformationType.BOOLEAN,
                "false",
                "The task carries on with the next step when this step fails. Retries are skipped.",
                mutableListOf(),
                PropertyInformation.PropertyInformationCategory.SETTING
            ),
            PropertyInformation(
                "scriptLanguage",
                "Script Language",
                PropertyInformationType.DROPDOWN,
                "VELOCITY",
                "The language of values such as \$parameters in this step.",
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
                "How many more times to try after the first attempt.",
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
                "The step fails if this gives any text, for example #if(\$result.output.contains(\"error\"))Found an error#end",
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

    /**
     * What a later step can read from \$lastLog.outputVariables, by name, with what it is.
     */
    @get:JsonIgnore
    open val outputs: Map<String, String>
        get() = mapOf()

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

        /** Whether the step cannot run without it. */
        var required: Boolean = false


        @JvmOverloads
        constructor(
            name: String,
            displayName: String,
            type: PropertyInformationType = PropertyInformationType.STRING,
            defaultValue: String = "",
            description: String = "",
            options: List<PropertyInformation> = mutableListOf(),
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
            STRING, MULTILINE, BOOLEAN, NUMBER, DROPDOWN, MAP,

            /** Text, with choices that the server fetches from the step's service when a person asks for them. */
            CHOICES,

            /** A form whose fields the server fetches from the step's service, kept as one JSON object. */
            FIELDS,

            /** The name of a file the run keeps, such as one Read file read, with the run's files as suggestions. */
            FILE
        }

        enum class PropertyInformationCategory {
            PROPERTY, CREDENTIAL, SECRET, SETTING
        }

    }
}