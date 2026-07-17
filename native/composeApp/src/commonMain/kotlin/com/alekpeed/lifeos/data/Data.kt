package com.alekpeed.lifeos.data

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// A record of every module that persists a list under a Storage key, so the
// aggregate screens (Today, Briefing, The Almanac) and the search screens (Ask,
// Search) can read across modules without each hard-coding the list. `key` is the
// exact Storage key the module's screen reads/writes; `label` is how it's shown.
data class DataSource(val label: String, val key: String)

val DATA_SOURCES: List<DataSource> = listOf(
    DataSource("Tasks", "Tasks"),
    DataSource("Ideas", "Ideas"),
    DataSource("Places", "Places"),
    DataSource("Links", "Links"),
    DataSource("Contacts", "Contacts"),
    DataSource("Recipes", "Recipes"),
    DataSource("Documents", "Documents"),
    DataSource("Packing", "Packing"),
    DataSource("Books", "Books"),
    DataSource("Milestones", "Milestones"),
    DataSource("Time Capsules", "Time Capsules"),
    DataSource("Collections", "Collections"),
    DataSource("Rabbit Holes", "Rabbit Holes"),
    DataSource("Habits", "Habits"),
    DataSource("Finance", "Finance"),
    DataSource("Education", "Education"),
    DataSource("Daily Paper", "Daily Paper"),
    DataSource("Orrery", "Orrery"),
    DataSource("Quartermaster", "Quartermaster"),
    DataSource("Sharebox", "Sharebox"),
    DataSource("Photos", "Photos"),
    DataSource("Museum", "Museum"),
    DataSource("Ghost Days", "Ghost Days"),
    DataSource("Health", "Health"),
    DataSource("Skill Trees", "Skill Trees"),
    DataSource("Recall", "Recall"),
    DataSource("Notifications", "Notifications"),
    DataSource("Entropy", "Entropy"),
    DataSource("Time Machine", "Time Machine"),
    DataSource("Knowledge Graph", "Knowledge Graph"),
    DataSource("Theme from Photo", "Theme from Photo"),
    DataSource("Tools", "Tools"),
)

private val jsonReader = Json { ignoreUnknownKeys = true }

// The object fields most worth showing for a record, in priority order — the
// first non-blank one becomes an item's display label in cross-module views.
private val PREFERRED_FIELDS = listOf(
    "title", "name", "topic", "text", "body", "label", "caption",
    "url", "phrase", "word", "question", "front", "notes",
)

private fun itemLabel(e: JsonElement): String? = when (e) {
    is JsonObject -> {
        val byPref = PREFERRED_FIELDS.firstNotNullOfOrNull { k ->
            (e[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifBlank { null }
        }
        byPref ?: e.values.filterIsInstance<JsonPrimitive>()
            .firstOrNull { it.isString && it.content.isNotBlank() }?.content?.trim()
    }
    is JsonPrimitive -> if (e.isString) e.content.trim().ifBlank { null } else null
    else -> null
}

// Display labels for every record a JSON-stored module holds. Handles both a
// top-level array and an object whose array fields hold the records (combining
// all of them, e.g. Education's semesters + courses + assignments).
private fun jsonItems(raw: String): List<String> {
    val root = jsonReader.parseToJsonElement(raw)
    val arrays = when (root) {
        is JsonArray -> listOf(root)
        is JsonObject -> root.values.filterIsInstance<JsonArray>()
        else -> emptyList()
    }
    return arrays.flatMap { arr -> arr.mapNotNull { itemLabel(it) } }
}

// The display items stored under a key, dropping blanks. Handles both the old
// newline-per-item format (tab/pipe-delimited fields, stripped to their label)
// and the newer JSON-blob modules (records extracted to a display label each),
// so all the cross-module readers — Search, Ask/Assistant grounding, the
// Almanac/Briefing counts, Entropy's has-data filter — stay correct after a
// module moves from a text list to a structured JSON store.
fun linesOf(key: String): List<String> {
    val raw = Storage.read(key) ?: return emptyList()
    val trimmed = raw.trimStart()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        return runCatching { jsonItems(raw) }.getOrElse { emptyList() }
    }
    return raw.lines().map { displayOf(it) }.filter { it.isNotBlank() }
}

fun countOf(key: String): Int = linesOf(key).size

// The user-facing text of a stored line, stripping the delimiter-encoded fields
// (tab for note/status screens, pipe for habits) down to the leading label.
// Idempotent on the already-clean labels linesOf now returns.
fun displayOf(line: String): String =
    line.substringBefore("\t").substringBefore("|").trim()

data class SearchHit(val source: String, val text: String)

fun searchAll(query: String): List<SearchHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val hits = mutableListOf<SearchHit>()
    for (src in DATA_SOURCES) {
        for (line in linesOf(src.key)) {
            if (line.contains(q, ignoreCase = true)) {
                hits.add(SearchHit(src.label, displayOf(line)))
            }
        }
    }
    return hits
}

// A compact snapshot of the user's data for grounding an AI answer: lines that
// match the query first, then a per-module count summary — bounded so the prompt
// stays small and cheap.
fun aiContext(query: String, maxMatches: Int = 40): String {
    val matches = searchAll(query).take(maxMatches)
    val counts = DATA_SOURCES.map { it.label to countOf(it.key) }.filter { it.second > 0 }
    return buildString {
        if (matches.isNotEmpty()) {
            append("Relevant saved items:\n")
            matches.forEach { append("- [${it.source}] ${it.text}\n") }
            append("\n")
        }
        if (counts.isNotEmpty()) {
            append("Module totals: ")
            append(counts.joinToString(", ") { "${it.first} ${it.second}" })
        }
        if (matches.isEmpty() && counts.isEmpty()) append("(No data saved yet.)")
    }.trim()
}
