package com.alekpeed.lifeos.mood

import com.alekpeed.lifeos.Storage
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// A mood check-in: a 1..10 score stamped with the moment it was logged. The hub's
// feeling slider writes one of these every time it clicks into a new stop, so the
// journal is a simple time series of how the day (and week) actually felt.
data class MoodEntry(val score: Int, val at: Long) {
    val instant: Instant get() = Instant.fromEpochMilliseconds(at)
}

// Stored as one "epochMillis\tscore" line per entry under the "Mood" key. Plain
// text keeps it human-readable and append-cheap; newest last.
private const val MOOD_KEY = "Mood"

fun loadMood(): List<MoodEntry> =
    Storage.read(MOOD_KEY)
        ?.lines()
        ?.mapNotNull { line ->
            val p = line.split("\t")
            val at = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val score = p.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            MoodEntry(score.coerceIn(1, 10), at)
        }
        ?: emptyList()

// Append a check-in. Returns the full journal after the write.
fun appendMood(score: Int): List<MoodEntry> {
    val entry = MoodEntry(score.coerceIn(1, 10), Clock.System.now().toEpochMilliseconds())
    val all = loadMood() + entry
    Storage.write(MOOD_KEY, all.joinToString("\n") { "${it.at}\t${it.score}" })
    return all
}

// The most recent check-in, if any — used to seed the slider on open.
fun latestMood(): MoodEntry? = loadMood().maxByOrNull { it.at }

// A short "Jul 16 · 9:41 PM"-style label for a journal row.
fun MoodEntry.label(): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val mon = months.getOrElse(dt.monthNumber - 1) { "?" }
    val h12 = when (val h = dt.hour % 12) { 0 -> 12; else -> h }
    val ampm = if (dt.hour < 12) "AM" else "PM"
    val min = dt.minute.toString().padStart(2, '0')
    return "$mon ${dt.dayOfMonth} · $h12:$min $ampm"
}
