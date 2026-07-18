package com.alekpeed.lifeos.milestones

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.parseDateOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Milestones — ported from the web app's Milestones view: a life timeline
// grouped by year, plus a yearly recap with real cross-module stats and an
// AI-written narrative grounded in those numbers. Each milestone has a title,
// date, category, notes, and an optional photo. Persists as one JSON blob under
// "Milestones"; old note stubs migrate.

@Serializable
data class Milestone(
    val id: Long,
    val title: String,
    val date: String = "",
    val category: String = "",
    val notes: String = "",
    val photoBlob: String = "",
)

@Serializable
data class MilestonesData(val milestones: List<Milestone> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadMilestones(): MilestonesData {
    val raw = Storage.read("Milestones")
    if (raw.isNullOrBlank()) return MilestonesData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<MilestonesData>(raw) }.getOrElse { MilestonesData() }
    }
    // Old NoteListScreen stub ("<achievement>\t<when>"): when → date if it parses.
    val ms = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val parts = line.split("\t", limit = 2)
        val whenStr = parts.getOrElse(1) { "" }.trim()
        val asDate = parseDateOrNull(whenStr)
        Milestone(
            id = i + 1L, title = parts[0].trim(),
            date = if (asDate != null) whenStr else "",
            notes = if (asDate == null) whenStr else "",
        )
    }
    return MilestonesData(ms)
}

fun saveMilestones(data: MilestonesData) {
    Storage.write("Milestones", json.encodeToString(data))
}
