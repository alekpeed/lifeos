package com.alekpeed.lifeos.data

import com.alekpeed.lifeos.Storage

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

// Lines stored under a key, dropping blanks. All module formats are newline-per-item.
fun linesOf(key: String): List<String> =
    Storage.read(key)?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

fun countOf(key: String): Int = linesOf(key).size

// The user-facing text of a stored line, stripping the delimiter-encoded fields
// (tab for note/status screens, pipe for habits) down to the leading label.
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
