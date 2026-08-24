package com.alekpeed.lifeos.rabbitholes

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.StaleRule
import com.alekpeed.lifeos.data.daysSinceDate
import com.alekpeed.lifeos.data.today
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
    // Last time you actually did something to this hole. startedDate alone can't
    // answer "have I abandoned this?" — a hole opened in January and edited
    // yesterday looks identical to one opened in January and never touched again.
    // Blank on records written before this field existed; they migrate to their
    // start date, which is the most honest guess available.
    val touchedDate: String = "",
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

// Stamp a hole as touched right now. Every edit in the journal goes through this,
// so "untouched for N days" means what it says.
fun touched(h: RabbitHole): RabbitHole = h.copy(touchedDate = today().toString())

// How long a hole has been sitting, in days — null when there's no date to go on.
// One line over the shared staleness utility (§12.1.2): this module keeps its own
// vocabulary, because "cold" is the right word for an abandoned thread and "neglected"
// is not, but the arithmetic behind it is the arithmetic Entropy uses.
fun daysCold(h: RabbitHole): Int? = daysSinceDate(h.touchedDate.ifBlank { h.startedDate })

// The point at which an open thread reads as abandoned rather than in progress.
const val COLD_AFTER_DAYS = 21

// Three weeks cold, six weeks abandoned. The thresholds are this module's; the
// computation is not.
val COLD_RULE = StaleRule(staleAfter = COLD_AFTER_DAYS, neglectedAfter = COLD_AFTER_DAYS * 2)
