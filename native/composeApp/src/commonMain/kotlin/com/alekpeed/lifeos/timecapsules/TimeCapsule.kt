package com.alekpeed.lifeos.timecapsules

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.today
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Time Capsules — ported from the web app: write a sealed note to your future
// self, hidden until a date you choose. Honor-system, not cryptographic — the
// UI simply won't show the body until sealedUntil has passed. Persists as one
// JSON blob under "Time Capsules"; old note stubs migrate.

@Serializable
data class TimeCapsule(
    val id: Long,
    val title: String,
    val body: String,
    val sealedUntil: String = "",
    val createdAt: String = "",
)

@Serializable
data class TimeCapsulesData(val capsules: List<TimeCapsule> = emptyList())

fun isSealed(c: TimeCapsule): Boolean = c.sealedUntil.isNotBlank() && c.sealedUntil > today().toString()

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadCapsules(): TimeCapsulesData {
    val raw = Storage.read("Time Capsules")
    if (raw.isNullOrBlank()) return TimeCapsulesData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<TimeCapsulesData>(raw) }.getOrElse { TimeCapsulesData() }
    }
    // Old NoteListScreen stub ("<message>\t<open on>").
    val caps = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val parts = line.split("\t", limit = 2)
        val whenStr = parts.getOrElse(1) { "" }.trim()
        TimeCapsule(
            id = i + 1L, title = "", body = parts[0].trim(),
            sealedUntil = if (parseDateOrNull(whenStr) != null) whenStr else "",
            createdAt = today().toString(),
        )
    }
    return TimeCapsulesData(caps)
}

fun saveCapsules(data: TimeCapsulesData) {
    Storage.write("Time Capsules", json.encodeToString(data))
}
