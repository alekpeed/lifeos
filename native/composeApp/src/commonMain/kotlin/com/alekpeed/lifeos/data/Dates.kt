package com.alekpeed.lifeos.data

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

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
