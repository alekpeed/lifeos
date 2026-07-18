package com.alekpeed.lifeos.mood

import com.alekpeed.lifeos.Storage
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// A mood check-in: a 1..10 score stamped with the moment it was logged. The home
// screen's feeling slider writes one of these when it settles on a new value.
data class MoodEntry(val score: Int, val at: Long) {
    val instant: Instant get() = Instant.fromEpochMilliseconds(at)
}

// Stored as one "epochMillis\tscore" line per entry under the "Mood" key.
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

// Append a check-in and return the full journal after the write.
fun appendMood(score: Int): List<MoodEntry> {
    val entry = MoodEntry(score.coerceIn(1, 10), Clock.System.now().toEpochMilliseconds())
    val all = loadMood() + entry
    Storage.write(MOOD_KEY, all.joinToString("\n") { "${it.at}\t${it.score}" })
    return all
}

// The most recent check-in, if any — used to seed the slider on open.
fun latestMood(): MoodEntry? = loadMood().maxByOrNull { it.at }
