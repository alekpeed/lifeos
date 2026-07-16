package com.alekpeed.lifeos.habits

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.minusDays
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.today
import kotlinx.datetime.LocalDate

// A habit with real check-in history: `checkins` are the calendar days it was
// checked in (deduped), and `streak` is derived from that history so tapping
// "check in" twice in a day can't inflate it, and missing a day genuinely
// resets it — the two behaviors the earlier version got wrong.
data class Habit(val name: String, val checkins: Set<LocalDate>) {
    val lastCheckIn: LocalDate? get() = checkins.maxOrNull()
    val checkedInToday: Boolean get() = lastCheckIn == today()

    val streak: Int
        get() {
            if (checkins.isEmpty()) return 0
            var day = if (checkedInToday) today() else today().minusDays(1)
            if (day !in checkins) return 0
            var count = 0
            while (day in checkins) {
                count++
                day = day.minusDays(1)
            }
            return count
        }
}

// name|epochDay1,epochDay2,... (sorted, capped to the most recent 60 so the file
// doesn't grow unbounded)
private fun Habit.toLine(): String =
    "$name|${checkins.sorted().takeLast(60).joinToString(",") { it.toString() }}"

private fun parseLine(line: String): Habit {
    val parts = line.split("|")
    val name = parts.getOrElse(0) { line }
    val dates = parts.getOrElse(1) { "" }
        .split(",")
        .mapNotNull { parseDateOrNull(it) }
        .toSet()
    return Habit(name, dates)
}

fun loadHabits(): List<Habit> =
    Storage.read("Habits")?.lines()?.filter { it.isNotBlank() }?.map { parseLine(it) }
        ?: listOf(Habit("Drink water", emptySet()), Habit("Move 30 min", emptySet()))

fun saveHabits(habits: List<Habit>) {
    Storage.write("Habits", habits.joinToString("\n") { it.toLine() })
}
