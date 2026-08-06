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
// (Tasks' due date, Habits' streak, Recall's schedule, Today/Briefing). Dates are
// stored as ISO strings ("2026-07-16") so a saved line is still human-readable.
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun LocalDate.toStored(): String = toString()

fun parseDateOrNull(s: String): LocalDate? = try {
    if (s.isBlank()) null else LocalDate.parse(s)
} catch (e: Exception) {
    null
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

fun LocalDate.plusDays(n: Int): LocalDate = this.plus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Int): LocalDate = this.minus(n, DateTimeUnit.DAY)

// Epoch-millis helpers backing scheduled reminders (Notifications' quick-pick
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
