package com.alekpeed.lifeos.ideas

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// A free-form capture note, now with tags and an archive flag. Persists as one
// JSON blob under "Ideas"; the old one-idea-per-line format migrates on load.
@Serializable
data class Idea(
    val id: Long,
    val text: String,
    val tags: List<String> = emptyList(),
    val archived: Boolean = false,
    val created: String = "",   // ISO date, best-effort
)

@Serializable
data class IdeasData(val ideas: List<Idea> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadIdeas(): IdeasData {
    val raw = Storage.read("Ideas")
    if (raw.isNullOrBlank()) {
        return IdeasData(listOf(Idea(1, "Ideas land here"), Idea(2, "Jot anything, tidy it later")))
    }
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<IdeasData>(raw) }.getOrElse { IdeasData() }
    }
    // Migrate the old one-idea-per-line text format.
    val ideas = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line -> Idea(i + 1L, line.trim()) }
    return IdeasData(ideas)
}

fun saveIdeas(data: IdeasData) {
    Storage.write("Ideas", json.encodeToString(data))
}

// Append a captured note (from the share sheet / NFC / clipboard) without
// clobbering the JSON blob.
fun appendIdea(text: String) {
    val clean = text.trim().replace("\n", " ")
    if (clean.isEmpty()) return
    val data = loadIdeas()
    val nextId = (data.ideas.maxOfOrNull { it.id } ?: 0L) + 1
    saveIdeas(data.copy(ideas = data.ideas + Idea(nextId, clean)))
}
