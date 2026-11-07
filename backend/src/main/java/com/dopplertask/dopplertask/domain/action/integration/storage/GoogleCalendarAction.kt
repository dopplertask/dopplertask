package com.dopplertask.dopplertask.domain.action.integration.storage

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import com.dopplertask.dopplertask.domain.action.integration.OAuthSignIn
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.io.IOException
import java.time.OffsetDateTime

@Entity
@Table(name = "GoogleCalendarAction")
@DiscriminatorValue("google_calendar_action")
class GoogleCalendarAction : Action() {
    @Column
    var baseUrl: String? = "https://www.googleapis.com"

    @Transient
    var serviceAccountKey: String? = null

    @Column
    var calendar: String? = null

    @Column
    var operation: String? = "createEvent"

    @Column(length = 1024)
    var title: String? = null

    @Lob
    @Column(columnDefinition = "TEXT")
    var eventDescription: String? = null

    @Column
    var startTime: String? = null

    @Column
    var endTime: String? = null

    @Column
    var eventId: String? = null

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val read = { text: String? -> variableExtractorUtil.extract(text, execution, scriptLanguage) }
        val keyText = read(serviceAccountKey)
        val calendarValue = read(calendar).trim()
        val operationValue = read(operation)
        val titleValue = read(title).trim()
        val startValue = read(startTime).trim()
        val endValue = read(endTime).trim()
        val eventValue = read(eventId).trim()
        if (keyText.isBlank() && !OAuthSignIn.isUsedBy(this)) return ActionResult.failure("Google Calendar needs the key file of a service account, or a connected Google account.")
        if (calendarValue.isBlank()) return ActionResult.failure("Google Calendar needs the ID of the calendar, which is under its settings.")
        if (operationValue == CREATE_EVENT && titleValue.isBlank()) return ActionResult.failure("Google Calendar needs a title for the event.")
        if (operationValue != DELETE_EVENT && (startValue.isBlank() || endValue.isBlank())) return ActionResult.failure("Google Calendar needs a start and an end, such as 2026-12-01T09:00:00+01:00.")
        if (operationValue != DELETE_EVENT && listOf(startValue, endValue).any { runCatching { OffsetDateTime.parse(it) }.isFailure }) return ActionResult.failure("Google Calendar needs times with their time zone, such as 2026-12-01T09:00:00+01:00, and got \"$startValue\" and \"$endValue\".")
        if (operationValue == DELETE_EVENT && eventValue.isBlank()) return ActionResult.failure("Google Calendar needs the ID of the event.")

        val signIn = GoogleAccount.signIn("Google Calendar", this, keyText, SCOPE)
        if (signIn.failure != null) return signIn.failure
        val events = read(baseUrl).trim().trimEnd('/') + "/calendar/v3/calendars/${JsonApi.encoded(calendarValue)}/events"
        return when (operationValue) {
            CREATE_EVENT -> {
                val event = mapOf("summary" to titleValue, "description" to read(eventDescription), "start" to mapOf("dateTime" to startValue), "end" to mapOf("dateTime" to endValue))
                val result = JsonApi.call("Google Calendar", "POST", events, signIn.headers, event)
                if (result.statusCode == StatusCode.SUCCESS) {
                    result.outputVariables["eventId"] = JsonApi.parsed(result).path("id").asText()
                    result.outputVariables["url"] = JsonApi.parsed(result).path("htmlLink").asText()
                }
                result
            }
            LIST_EVENTS -> {
                val result = JsonApi.call("Google Calendar", "GET", "$events?timeMin=${JsonApi.encoded(startValue)}&timeMax=${JsonApi.encoded(endValue)}&singleEvents=true&orderBy=startTime", signIn.headers, null)
                if (result.statusCode == StatusCode.SUCCESS) result.outputVariables["eventCount"] = JsonApi.parsed(result).path("items").size()
                result
            }
            DELETE_EVENT -> JsonApi.call("Google Calendar", "DELETE", "$events/${JsonApi.encoded(eventValue)}", signIn.headers, null)
            else -> ActionResult.failure("Google Calendar has no operation called \"$operationValue\".")
        }
    }

    override val credentialType: String
        get() = GOOGLE_SERVICE_ACCOUNT

    override val outputs: Map<String, String>
        get() = mapOf("eventId" to "Event ID", "url" to "Address", "eventCount" to "Number of events")

    override val description: String
        get() = "Creates, lists or deletes Google Calendar events"

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            val showForCreateEvent = mapOf("operation" to arrayOf(CREATE_EVENT))
            val showForCreateEventListEvents = mapOf("operation" to arrayOf(CREATE_EVENT, LIST_EVENTS))
            val showForDeleteEvent = mapOf("operation" to arrayOf(DELETE_EVENT))
            actionInfo.add(PropertyInformation("serviceAccountKey", "Key file of the service account", PropertyInformationType.MULTILINE, "", "The JSON file. Share the calendar with the email of the account.", mutableListOf(), PropertyInformationCategory.SECRET))
            actionInfo.addAll(GoogleAccount.accountFields())
            actionInfo.add(PropertyInformation("operation", "Action", PropertyInformationType.DROPDOWN, CREATE_EVENT, "", listOf(
                PropertyInformation(CREATE_EVENT, "Create an event"),
                PropertyInformation(LIST_EVENTS, "List events"),
                PropertyInformation(DELETE_EVENT, "Delete an event")
            )))
            actionInfo.add(PropertyInformation("calendar", "Calendar ID", PropertyInformationType.STRING, "", "Under the settings of the calendar, such as team@example.com."))
            actionInfo.add(PropertyInformation("title", "Title", PropertyInformationType.STRING, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateEvent))
            actionInfo.add(PropertyInformation("eventDescription", "Description", PropertyInformationType.MULTILINE, "", "", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateEvent))
            actionInfo.add(PropertyInformation("startTime", "Start", PropertyInformationType.STRING, "", "With its time zone, such as 2026-12-01T09:00:00+01:00.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateEventListEvents))
            actionInfo.add(PropertyInformation("endTime", "End", PropertyInformationType.STRING, "", "With its time zone, such as 2026-12-01T10:00:00+01:00.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForCreateEventListEvents))
            actionInfo.add(PropertyInformation("eventId", "Event ID", PropertyInformationType.STRING, "", "A step that creates an event gives it as \$lastLog.outputVariables.eventId.", mutableListOf(), PropertyInformationCategory.PROPERTY, showForDeleteEvent))
            actionInfo.add(PropertyInformation("baseUrl", "Base URL", PropertyInformationType.STRING, "https://www.googleapis.com", "Only change this to go through a proxy."))
            return actionInfo
        }

    private companion object {
        const val CREATE_EVENT = "createEvent"
        const val LIST_EVENTS = "listEvents"
        const val DELETE_EVENT = "deleteEvent"
        const val SCOPE = "https://www.googleapis.com/auth/calendar"
    }
}
