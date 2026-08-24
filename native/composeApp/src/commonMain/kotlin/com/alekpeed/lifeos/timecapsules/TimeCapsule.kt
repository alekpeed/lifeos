package com.alekpeed.lifeos.timecapsules

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.archive.Moment
import com.alekpeed.lifeos.archive.MomentKind
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
    override val id: Long,
    override val title: String,
    val body: String,
    val sealedUntil: String = "",
    val createdAt: String = "",
    override val photoBlob: String = "",      // blob-store id of an attached photo, if any
    // The day the body was actually revealed (§5.4). Without it there is no way to tell
    // an unopened capsule from one you read last year, and both surfacing mechanisms
    // would nag forever instead of resolving.
    val readAt: String = "",
) : Moment {
    // The shared Archive shape (§12.1.4). A capsule's moment is the day it opens —
    // that is the day it is about — and its note is the body it was written with.
    // Getters, not fields: nothing here changes how a capsule is stored.
    override val date: String get() = sealedUntil
    override val note: String get() = body
    override val kind: MomentKind get() = MomentKind.OPENS
}

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
