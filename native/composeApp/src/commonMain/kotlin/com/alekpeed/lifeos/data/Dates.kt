package com.alekpeed.lifeos.data

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.hours

// A single, real notion of "today" for every module that reasons about dates
// (Tasks' due date, Habits' streak, skill freshness, Today/Briefing). Dates are
// stored as ISO strings ("2026-07-16") so a saved line is still human-readable.
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun LocalDate.toStored(): String = toString()

// A stored value is either "2026-07-16" or "2026-07-16T15:00" (M-01a). Extending the
// existing string rather than adding a field to 37 record types is what keeps this a
// contained change: every value already written stays valid, no data class moves, and
// ISO still sorts lexicographically — which several modules rely on for ordering.
//
// ONE RULE if you enable a time on a field that did not have one. Sorting stays correct,
// and so does `>=` against a bare date, but `<=` does not: "2026-07-16T15:00" is NOT
// <= "2026-07-16", because the timed string is longer. So a same-day item silently drops
// out of a filter written that way. Compare parsed dates instead of raw strings. Two
// places do it the string way today and are only safe because their fields are date-only
// — education/Education.kt's pacing checkpoints and health/HealthScreen's log cutoff.
const val DATE_TIME_SEP = "T"

// Tolerating the time half here is the load-bearing part. Every module reads dates
// through this one function and none parses ISO itself, so a value that gained a time
// keeps working everywhere unchanged — callers that only care about the day simply see
// the day. Without this they would parse a timed value as *no date at all*.
fun parseDateOrNull(s: String): LocalDate? = try {
    if (s.isBlank()) null else LocalDate.parse(s.substringBefore(DATE_TIME_SEP))
} catch (e: Exception) {
    null
}

// The time half, or null when the value is date-only.
fun parseTimeOrNull(s: String): LocalTime? {
    if (!s.contains(DATE_TIME_SEP)) return null
    val raw = s.substringAfter(DATE_TIME_SEP)
    return try {
        if (raw.isBlank()) null else LocalTime.parse(if (raw.length == 5) "$raw:00" else raw)
    } catch (e: Exception) {
        null
    }
}

fun hasTime(s: String): Boolean = parseTimeOrNull(s) != null

// Build a stored value. A null time writes a plain date, so clearing the time returns
// the value to exactly the shape it had before.
fun storedDateTime(date: LocalDate, time: LocalTime?): String =
    if (time == null) date.toString()
    else "$date$DATE_TIME_SEP${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

// Set or clear the time on an existing stored value, keeping its date.
fun withTime(stored: String, time: LocalTime?): String {
    val date = parseDateOrNull(stored) ?: return stored
    return storedDateTime(date, time)
}

// When a stored value actually falls, for scheduling. A date-only value has no time of
// day, so the caller says what to assume — reminders have always used 09:00.
fun epochMillisOf(stored: String, defaultHour: Int = 9, defaultMinute: Int = 0): Long? {
    val date = parseDateOrNull(stored) ?: return null
    val time = parseTimeOrNull(stored)
    return epochMillisAt(date, time?.hour ?: defaultHour, time?.minute ?: defaultMinute)
}

// Short, human labels for due-date badges: "Today", "Tomorrow", "in 3d", "5d overdue".
fun relativeLabel(date: LocalDate, from: LocalDate = today()): String {
    val days = from.daysUntil(date)
    return when {
        days == 0 -> "Today"
        days == 1 -> "Tomorrow"
        days == -1 -> "Yesterday"
        days > 1 -> "in ${days}d"
        else -> "${-days}d overdue"
    }
}

// "3:00 PM" — the app shows 12-hour time everywhere it shows a clock.
fun timeLabel(t: LocalTime): String {
    val h = if (t.hour % 12 == 0) 12 else t.hour % 12
    val ampm = if (t.hour < 12) "AM" else "PM"
    return "$h:${t.minute.toString().padStart(2, '0')} $ampm"
}

// The badge for a stored value, carrying the time only when one was set: "Today",
// "Today 3:00 PM", "2d overdue".
fun relativeLabelOf(stored: String, from: LocalDate = today()): String {
    val date = parseDateOrNull(stored) ?: return ""
    val base = relativeLabel(date, from)
    val time = parseTimeOrNull(stored) ?: return base
    return "$base ${timeLabel(time)}"
}

fun LocalDate.plusDays(n: Int): LocalDate = this.plus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Int): LocalDate = this.minus(n, DateTimeUnit.DAY)

// Epoch-millis helpers backing scheduled reminders (the Calendar's quick-pick
// times and Finance's recurring-bill nudge).
fun epochMillisAt(date: LocalDate, hour: Int, minute: Int): Long =
    LocalDateTime(date, LocalTime(hour, minute)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

fun nowPlusHours(n: Int): Long = (Clock.System.now() + n.hours).toEpochMilliseconds()

// The next occurrence of a clock time: today if it hasn't passed yet, else tomorrow.
fun nextClockTime(hour: Int, minute: Int = 0): Long {
    val now = Clock.System.now().toEpochMilliseconds()
    val todayAt = epochMillisAt(today(), hour, minute)
    return if (todayAt > now) todayAt else epochMillisAt(today().plusDays(1), hour, minute)
}

// "Today 6:00 PM" / "Tomorrow 9:00 AM" — a scheduled reminder's display label.
fun formatEpochMillis(millis: Long): String {
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    val h = if (dt.hour % 12 == 0) 12 else dt.hour % 12
    val ampm = if (dt.hour < 12) "AM" else "PM"
    val minute = dt.minute.toString().padStart(2, '0')
    return "${relativeLabel(dt.date)} $h:$minute $ampm"
}
