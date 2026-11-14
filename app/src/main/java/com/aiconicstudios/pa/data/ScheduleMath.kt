package com.aiconicstudios.pa.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Pure scheduling helpers — kept free of Android types so they are unit-testable. */
object ScheduleMath {

    const val MINUTES_PER_DAY = 24 * 60

    val dayShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dayLong = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /**
     * The next moment strictly after [after] at which the reminder for [activity] should ring
     * (start time minus lead time), across all of its weekdays. Null when it has no days.
     */
    fun nextTrigger(activity: Activity, after: LocalDateTime): LocalDateTime? {
        if (activity.days.none { it in 1..7 }) return null
        // A lead time can push the ring onto the previous calendar day, so start one day back.
        val firstDate = after.toLocalDate().minusDays(1)
        return (0L..8L).asSequence()
            .map { firstDate.plusDays(it) }
            .filter { it.dayOfWeek.value in activity.days }
            .map {
                it.atStartOfDay()
                    .plusMinutes(activity.startMinute.toLong())
                    .minusMinutes(activity.leadMinutes.toLong())
            }
            .firstOrNull { it.isAfter(after) }
    }

    /** True when the two activities share a weekday and their time ranges intersect. */
    fun overlaps(a: Activity, b: Activity): Boolean {
        if (a.id == b.id) return false
        if (a.days.intersect(b.days).isEmpty()) return false
        return a.startMinute < b.endMinute && b.startMinute < a.endMinute
    }

    fun conflictsFor(candidate: Activity, all: List<Activity>): List<Activity> =
        all.filter { overlaps(candidate, it) }

    fun formatTime(minuteOfDay: Int, use24h: Boolean): String {
        val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val h = m / 60
        val min = m % 60
        if (minuteOfDay == MINUTES_PER_DAY) return if (use24h) "24:00" else "12:00 am"
        return if (use24h) "%02d:%02d".format(h, min)
        else {
            val h12 = if (h % 12 == 0) 12 else h % 12
            "%d:%02d %s".format(h12, min, if (h < 12) "am" else "pm")
        }
    }

    fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    fun formatRange(a: Activity, use24h: Boolean) =
        "${formatTime(a.startMinute, use24h)}–${formatTime(a.endMinute, use24h)}"

    fun daysLabel(days: Set<Int>): String {
        val sorted = days.sorted()
        return when (sorted) {
            listOf(1, 2, 3, 4, 5, 6, 7) -> "Every day"
            listOf(1, 2, 3, 4, 5) -> "Weekdays"
            listOf(6, 7) -> "Weekends"
            else -> sorted.joinToString(", ") { dayShort[it - 1] }
        }
    }

    /** The activity running at [now], if any. */
    fun current(all: List<Activity>, now: LocalDateTime): Activity? {
        val day = now.dayOfWeek.value
        val minute = now.toLocalTime().toSecondOfDay() / 60
        return all.firstOrNull { day in it.days && minute >= it.startMinute && minute < it.endMinute }
    }

    /** Next activity to start after [now] (within the coming week) and when it starts. */
    fun upcoming(all: List<Activity>, now: LocalDateTime): Pair<Activity, LocalDateTime>? =
        all.mapNotNull { a -> nextTrigger(a.copy(leadMinutes = 0), now)?.let { a to it } }
            .minByOrNull { it.second }

    /**
     * Streak = consecutive scheduled days (walking back from today) that were checked in.
     * Today does not break the streak if it hasn't been checked in yet.
     */
    fun streak(days: Set<Int>, checkIns: Collection<String>, today: LocalDate): Int {
        if (days.isEmpty()) return 0
        val done = checkIns.toSet()
        var date = today
        // A scheduled day that's still in progress doesn't break the streak.
        if (today.dayOfWeek.value in days && today.toString() !in done) date = today.minusDays(1)
        var count = 0
        repeat(400) {
            if (date.dayOfWeek.value in days) {
                if (date.toString() in done) count++ else return count
            }
            date = date.minusDays(1)
        }
        return count
    }

    /** Parses markdown checklist lines ("- [ ] task" / "- [x] task") into tasks. */
    fun parseChecklist(text: String): List<PlanTask> =
        text.lineSequence()
            .map { it.trim() }
            .mapNotNull { line ->
                CHECKLIST.matchEntire(line)?.let { m ->
                    val body = m.groupValues[2].replace("**", "").trim()
                    if (body.isEmpty()) null
                    else PlanTask(text = body, done = m.groupValues[1].equals("x", ignoreCase = true))
                }
            }
            .toList()

    private val CHECKLIST = Regex("""^[-*•]\s*\[([ xX])]\s*(.+)$""")

    /** Minute-of-day for a LocalTime, handy for "now" lines. */
    fun minuteOf(time: LocalTime) = time.hour * 60 + time.minute
}
