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
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Starts the task on the clock: every so many seconds up to months, at a time of day, on chosen weekdays, or by cron.
 * Times follow the clock, so every 15 minutes runs at :00, :15, :30 and :45, whenever the task was turned on, and every
 * 90 minutes keeps 90 minutes apart, also across midnight.
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
        val schedule = Schedule.of(this)
        val next = schedule.nextAfter(ZonedDateTime.now(schedule.zone))
            ?: throw IllegalArgumentException(NEVER)
        Thread.sleep(Duration.between(ZonedDateTime.now(schedule.zone), next).toMillis().coerceAtLeast(0))
        return TriggerResult(momentOf(next))
    }

    override fun problem(): String? = try {
        if (Schedule.of(this).nextAfter(ZonedDateTime.now()) == null) NEVER else null
    } catch (e: IllegalArgumentException) {
        e.message
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
        val values = if (moment.values.any { it == null }) momentOf(ZonedDateTime.now(Schedule.zoneOf(timezone))) else moment.mapValues { it.value!! }
        result.outputVariables.putAll(values)
        result.output = "Started by the schedule at ${values["timestamp"]}"
        return result
    }

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
            info.add(PropertyInformation("every", "Every", PropertyInformationType.NUMBER, "1", "How many of the chosen unit lie between two starts, up to ${Schedule.MAX_EVERY}.", mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY,
                mapOf("rule" to arrayOf("SECONDS", "MINUTES", "HOURS", "DAYS", "WEEKS", "MONTHS"))))
            info.add(PropertyInformation("onDays", "On", PropertyInformationType.STRING, "MON", "Weekdays, such as MON,WED,FRI.", mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY,
                mapOf("rule" to arrayOf("WEEKS"))))
            info.add(PropertyInformation("onDayOfMonth", "On day", PropertyInformationType.NUMBER, "1", "Day of the month, from 1 to 31. A day a month does not have means its last day.", mutableListOf(), PropertyInformation.PropertyInformationCategory.PROPERTY,
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

    /**
     * When the rules start the task. A rule gives the moments it may start on, such as every minute, and every few of
     * them keeps every so many, counted from a Monday in 1970. So every 90 minutes is exact, every second day goes on
     * across the end of a month, and nothing has to be remembered between starts.
     */
    class Schedule private constructor(
        val zone: ZoneId,
        private val momentAfter: (ZonedDateTime) -> ZonedDateTime?,
        private val unit: ChronoUnit,
        private val every: Long
    ) {

        fun nextAfter(after: ZonedDateTime): ZonedDateTime? {
            var from = after.withZoneSameInstant(zone)
            repeat(MAX_SKIPPED_MOMENTS) {
                val next = momentAfter(from) ?: return null
                if (unitsSinceStart(next) % every == 0L) return next
                from = next
            }
            return null
        }

        private fun unitsSinceStart(moment: ZonedDateTime): Long = if (unit == ChronoUnit.MONTHS) {
            ChronoUnit.MONTHS.between(YearMonth.from(START), YearMonth.from(moment))
        } else {
            unit.between(START, moment.toLocalDateTime())
        }

        companion object {
            const val MAX_EVERY = 10_000
            private const val MAX_SKIPPED_MOMENTS = 100_000
            private val START: LocalDateTime = LocalDateTime.of(1970, 1, 5, 0, 0)
            private val DAY_NAMES = DayOfWeek.entries.associateBy { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase() } +
                DayOfWeek.entries.associateBy { it.name }

            /** The schedule of a trigger, or why its settings do not make one. */
            fun of(trigger: ScheduleTrigger): Schedule {
                val zone = zoneOf(trigger.timezone)
                val every = number(trigger.every, "Every", 1, MAX_EVERY, 1).toLong()
                val minute = number(trigger.atMinute, "At minute", 0, 59, 0)
                val hour = number(trigger.atHour, "At hour", 0, 23, 0)
                val weekdays = if (trigger.weekdaysOnly) "MON-FRI" else "*"
                return when (trigger.rule ?: "MINUTES") {
                    "SECONDS" -> Schedule(zone, cron("* * * * * $weekdays"), ChronoUnit.SECONDS, every)
                    "MINUTES" -> Schedule(zone, cron("0 * * * * $weekdays"), ChronoUnit.MINUTES, every)
                    "HOURS" -> Schedule(zone, cron("0 $minute * * * $weekdays"), ChronoUnit.HOURS, every)
                    "DAYS" -> Schedule(zone, cron("0 $minute $hour * * $weekdays"), ChronoUnit.DAYS, every)
                    "WEEKS" -> Schedule(zone, cron("0 $minute $hour * * ${days(trigger.onDays)}"), ChronoUnit.WEEKS, every)
                    "MONTHS" -> Schedule(zone, monthly(number(trigger.onDayOfMonth, "On day", 1, 31, 1), hour, minute), ChronoUnit.MONTHS, every)
                    "CRON" -> Schedule(zone, cron(*cronLines(trigger.cronExpression)), ChronoUnit.SECONDS, 1)
                    else -> throw IllegalArgumentException("The schedule has an unknown rule: ${trigger.rule}.")
                }
            }

            fun zoneOf(timezone: String?): ZoneId = try {
                if (timezone.isNullOrBlank()) ZoneId.systemDefault() else ZoneId.of(timezone.trim())
            } catch (e: DateTimeException) {
                throw IllegalArgumentException("\"$timezone\" is not a time zone. Use a name such as Europe/Stockholm, or leave it empty.")
            }

            /** The soonest moment of any of the lines. */
            private fun cron(vararg lines: String): (ZonedDateTime) -> ZonedDateTime? {
                val parsed = lines.map { line ->
                    try {
                        CronExpression.parse(line)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("The cron line \"$line\" is not valid: ${e.message}")
                    }
                }
                return { after -> parsed.mapNotNull { it.next(after) }.minOrNull() }
            }

            private fun cronLines(text: String?): Array<String> {
                val lines = (text ?: "").lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                if (lines.isEmpty()) throw IllegalArgumentException("Add a cron line, such as 0 9 * * MON-FRI for nine on weekdays.")
                return lines.map(::withSeconds).toTypedArray()
            }

            /** A day the month does not have means its last day, so the 31st also runs in shorter months. */
            private fun monthly(day: Int, hour: Int, minute: Int): (ZonedDateTime) -> ZonedDateTime? = { after ->
                generateSequence(YearMonth.from(after)) { it.plusMonths(1) }.take(2)
                    .map { month -> month.atDay(minOf(day, month.lengthOfMonth())).atTime(hour, minute).atZone(after.zone) }
                    .firstOrNull { it.isAfter(after) }
            }

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
        private const val NEVER = "The schedule never comes round. Check the cron line."

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
