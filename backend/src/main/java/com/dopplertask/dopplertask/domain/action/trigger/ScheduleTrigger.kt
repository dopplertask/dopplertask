package com.dopplertask.dopplertask.domain.action.trigger

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import org.springframework.scheduling.support.CronExpression
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Starts the task on the clock: every so many seconds up to months, at a time of day, on chosen weekdays, or by cron.
 * Times follow the clock, so every 15 minutes runs at :00, :15, :30 and :45, whenever the task was turned on.
 */
@Entity
class ScheduleTrigger : Trigger() {

    @Column
    var rule: String? = "MINUTES"

    @Column
    var every: String? = "1"

    @Column
    var atHour: String? = "0"

    @Column
    var atMinute: String? = "0"

    /** Weekdays for a weekly rule, such as MON,WED,FRI. */
    @Column
    var onDays: String? = "MON"

    @Column
    var onDayOfMonth: String? = "1"

    @Column
    var weekdaysOnly = false

    /** One or more cron lines. Each one starts the task, so several lines combine like several rules. */
    @Lob
    @Column(columnDefinition = "TEXT")
    var cronExpression: String? = null

    @Column
    var timezone: String? = null

    override fun trigger(): TriggerResult {
        val zone = zone()
        val schedule = Schedule.of(this)
        val next = schedule.nextAfter(ZonedDateTime.now(zone))
            ?: throw IllegalArgumentException("The schedule never comes round. Check the cron line.")
        Thread.sleep(Duration.between(ZonedDateTime.now(zone), next).toMillis().coerceAtLeast(0))
        return TriggerResult(momentOf(next))
    }

    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val result = ActionResult()
        // A run started by hand has no tick, so it gets the moment it ran.
        val moment = OUTPUT_NAMES.associateWith { name -> execution.parameters["TRIGGER_$name"]?.toString() }
        val values = if (moment.values.any { it == null }) momentOf(ZonedDateTime.now(zone())) else moment.mapValues { it.value!! }
        result.outputVariables.putAll(values)
        result.output = "Started by the schedule at ${values["timestamp"]}"
        return result
    }

    private fun zone(): ZoneId = if (timezone.isNullOrBlank()) ZoneId.systemDefault() else ZoneId.of(timezone!!.trim())

    override val description: String
        get() = "Starts the task on a schedule: every so often, at set times, or by cron"

    override val outputs: Map<String, String>
        get() = mapOf(
            "timestamp" to "When it started, as an ISO date and time with its offset",
            "readableDate" to "The date and time in words, such as November 8th 2026, 9:00:00 am",
            "readableTime" to "The time, such as 9:00:00 am",
            "dayOfWeek" to "The weekday, such as Monday",
            "year" to "The year",
            "month" to "The month, such as November",
            "dayOfMonth" to "The day of the month, as two digits",
            "hour" to "The hour, from 00 to 23",
            "minute" to "The minute",
            "second" to "The second",
            "timezone" to "The time zone, such as Europe/Stockholm (UTC+01:00)"
        )

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val info = super.actionInfo
            val clock = arrayOf("DAYS", "WEEKS", "MONTHS")
            info.add(PropertyInformation("rule", "Run", PropertyInformationType.DROPDOWN, "MINUTES", "How often the task starts.", listOf(
                PropertyInformation("SECONDS", "Every few seconds"),
                PropertyInformation("MINUTES", "Every few minutes"),
                PropertyInformation("HOURS", "Every few hours"),
                PropertyInformation("DAYS", "Every few days"),
                PropertyInformation("WEEKS", "Every few weeks"),
                PropertyInformation("MONTHS", "Every few months"),
                PropertyInformation("CRON", "By cron")
            )))
            info.add(PropertyInformation("every", "Every", PropertyInformationType.NUMBER, "1", "How many of the chosen unit lie between two starts.", mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY,
                mapOf("rule" to arrayOf("SECONDS", "MINUTES", "HOURS", "DAYS", "WEEKS", "MONTHS"))))
            info.add(PropertyInformation("onDays", "On", PropertyInformationType.STRING, "MON", "Weekdays, such as MON,WED,FRI.", mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY,
                mapOf("rule" to arrayOf("WEEKS"))))
            info.add(PropertyInformation("onDayOfMonth", "On day", PropertyInformationType.NUMBER, "1", "Day of the month, from 1 to 31. 31 means the last day of every month.", mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY,
                mapOf("rule" to arrayOf("MONTHS"))))
            info.add(PropertyInformation("atHour", "At hour", PropertyInformationType.NUMBER, "0", "From 0 to 23.", mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY,
                mapOf("rule" to clock)))
            info.add(PropertyInformation("atMinute", "At minute", PropertyInformationType.NUMBER, "0", "From 0 to 59.", mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY,
                mapOf("rule" to arrayOf("HOURS", *clock))))
            info.add(PropertyInformation("weekdaysOnly", "Weekdays only", PropertyInformationType.BOOLEAN, "false", "Skips Saturdays and Sundays.", mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY,
                mapOf("rule" to arrayOf("SECONDS", "MINUTES", "HOURS", "DAYS"))))
            info.add(PropertyInformation("cronExpression", "Cron", PropertyInformationType.MULTILINE, "0 9 * * MON-FRI",
                "One schedule per line, with 5 fields (minute hour day month weekday) or 6 with seconds first. @hourly, @daily, @weekly and @monthly work too.",
                mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY, mapOf("rule" to arrayOf("CRON"))))
            info.add(PropertyInformation("timezone", "Time zone", PropertyInformationType.STRING, "", "Such as Europe/Stockholm. Empty uses the server's time zone."))
            return info
        }

    /** When the rules start the task. Every rule becomes cron lines, and a rule every few weeks also keeps to its weeks. */
    class Schedule private constructor(private val lines: List<CronExpression>, private val everyWeeks: Long) {

        fun nextAfter(after: ZonedDateTime): ZonedDateTime? {
            var from = after
            // Every few weeks: a tick in a week that is not one of them is passed over.
            repeat(MAX_SKIPPED_TICKS) {
                val next = lines.mapNotNull { it.next(from) }.minOrNull() ?: return null
                if (everyWeeks <= 1 || weeksSinceFirstMonday(next) % everyWeeks == 0L) return next
                from = next
            }
            return null
        }

        companion object {
            private const val MAX_SKIPPED_TICKS = 10_000
            private val FIRST_MONDAY: LocalDate = LocalDate.of(1970, 1, 5)
            private val DAY_NAMES = DayOfWeek.entries.associateBy { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase() } +
                DayOfWeek.entries.associateBy { it.name }

            private fun weeksSinceFirstMonday(moment: ZonedDateTime) = ChronoUnit.WEEKS.between(FIRST_MONDAY, moment.toLocalDate())

            fun of(trigger: ScheduleTrigger): Schedule {
                val every = number(trigger.every, "Every", 1, Int.MAX_VALUE, 1)
                val minute = number(trigger.atMinute, "At minute", 0, 59, 0)
                val hour = number(trigger.atHour, "At hour", 0, 23, 0)
                val weekdays = if (trigger.weekdaysOnly) "MON-FRI" else "*"
                val lines = when (trigger.rule ?: "MINUTES") {
                    "SECONDS" -> listOf("*/$every * * * * $weekdays")
                    "MINUTES" -> listOf("0 */$every * * * $weekdays")
                    "HOURS" -> listOf("0 $minute */$every * * $weekdays")
                    "DAYS" -> listOf("0 $minute $hour */$every * $weekdays")
                    "WEEKS" -> listOf("0 $minute $hour * * ${days(trigger.onDays)}")
                    "MONTHS" -> listOf(monthly(number(trigger.onDayOfMonth, "On day", 1, 31, 1), hour, minute, every))
                    "CRON" -> (trigger.cronExpression ?: "").lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map(::withSeconds)
                    else -> throw IllegalArgumentException("The schedule has an unknown rule: ${trigger.rule}.")
                }
                if (lines.isEmpty()) throw IllegalArgumentException("Add a cron line, such as 0 9 * * MON-FRI for nine on weekdays.")
                val parsed = lines.map { line ->
                    try {
                        CronExpression.parse(line)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("The cron line \"$line\" is not valid: ${e.message}")
                    }
                }
                return Schedule(parsed, if (trigger.rule == "WEEKS") every.toLong() else 1)
            }

            /** The 31st stands for the last day, so a month-end task also runs in shorter months. */
            private fun monthly(day: Int, hour: Int, minute: Int, every: Int) =
                "0 $minute $hour ${if (day == 31) "L" else day.toString()} */$every *"

            private fun withSeconds(line: String) =
                if (line.startsWith("@") || line.split(Regex("\\s+")).size != 5) line else "0 $line"

            private fun days(text: String?): String {
                val names = (text ?: "").split(',', ' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                if (names.isEmpty()) throw IllegalArgumentException("Choose at least one weekday, such as MON.")
                return names.joinToString(",") { name ->
                    (DAY_NAMES[name] ?: DAY_NAMES[name.take(3)] ?: throw IllegalArgumentException("\"$name\" is not a weekday. Use MON, TUE, WED, THU, FRI, SAT or SUN."))
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
                }
            }

            private fun number(text: String?, label: String, lowest: Int, highest: Int, empty: Int): Int {
                if (text.isNullOrBlank()) return empty
                val value = text.trim().toIntOrNull() ?: throw IllegalArgumentException("$label should be a whole number, not \"$text\".")
                if (value < lowest || value > highest) throw IllegalArgumentException("$label should be from $lowest to $highest, not $value.")
                return value
            }
        }
    }

    companion object {
        private fun String.lowercaseMeridiem() = replace(" AM", " am").replace(" PM", " pm")

        private val OUTPUT_NAMES = listOf("timestamp", "readableDate", "readableTime", "dayOfWeek", "year", "month", "dayOfMonth", "hour", "minute", "second", "timezone")

        fun momentOf(moment: ZonedDateTime): MutableMap<String, String> {
            val day = moment.dayOfMonth
            val suffix = if (day in 11..13) "th" else when (day % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
            return mutableMapOf(
                "timestamp" to moment.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "readableDate" to moment.format(DateTimeFormatter.ofPattern("MMMM d'$suffix' yyyy, h:mm:ss a", Locale.ENGLISH)).lowercaseMeridiem(),
                "readableTime" to moment.format(DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)).lowercaseMeridiem(),
                "dayOfWeek" to moment.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)),
                "year" to moment.format(DateTimeFormatter.ofPattern("yyyy")),
                "month" to moment.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)),
                "dayOfMonth" to moment.format(DateTimeFormatter.ofPattern("dd")),
                "hour" to moment.format(DateTimeFormatter.ofPattern("HH")),
                "minute" to moment.format(DateTimeFormatter.ofPattern("mm")),
                "second" to moment.format(DateTimeFormatter.ofPattern("ss")),
                "timezone" to "${moment.zone.id} (UTC${moment.format(DateTimeFormatter.ofPattern("xxx"))})"
            )
        }
    }
}
