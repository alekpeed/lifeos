package com.alekpeed.lifeos.rabbitholes

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Rabbit Holes — ported from the web app's Rabbit Hole Journal: track research
// tangents with freeform notes, a running list of links, and an active/resolved
// status. Persists as one JSON blob under "Rabbit Holes"; old plain-line stubs
// migrate to active holes.

@Serializable
data class HoleLink(val id: Long, val url: String, val title: String = "")

@Serializable
data class RabbitHole(
    val id: Long,
    val topic: String,
    val notes: String = "",
    val links: List<HoleLink> = emptyList(),
    val status: String = "active",   // active | resolved
    val startedDate: String = "",
    val photoBlob: String = "",      // blob-store id of an attached photo, if any
)

@Serializable
data class RabbitHolesData(val holes: List<RabbitHole> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadHoles(): RabbitHolesData {
    val raw = Storage.read("Rabbit Holes")
    if (raw.isNullOrBlank()) return RabbitHolesData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<RabbitHolesData>(raw) }.getOrElse { RabbitHolesData() }
    }
    val holes = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        RabbitHole(id = i + 1L, topic = line.trim())
    }
    return RabbitHolesData(holes)
}

fun saveHoles(data: RabbitHolesData) {
    Storage.write("Rabbit Holes", json.encodeToString(data))
}
