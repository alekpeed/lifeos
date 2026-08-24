package com.alekpeed.lifeos

// What the home screen decides, separated from how it draws.
//
// The launcher grew from a handful of modules to forty-one across eight domains, and at
// that size the question stopped being "what does this app do" and became "where is the
// one I want". Everything here answers that: a search that reaches every module, pins you
// choose rather than pins somebody hardcoded, and the ones you actually opened.
//
// It is pure and testable on purpose. A launcher that loses your pins, or quietly drops a
// module out of search because of a stray capital, is the kind of fault nobody files a
// bug about — they just stop trusting the screen.

// What the six original pins were. Seeded on first run so an existing install opens onto
// the same six it had, and editable from then on.
val DEFAULT_PINS = listOf("today", "tasks", "briefing", "finance", "calendar", "ask")

// Pins sync: which modules you keep to hand is a preference, and having to set it again
// on the laptop would be an annoyance with no reason behind it.
private const val K_PINS = "HomePins"

// Recents do not: what you opened on this device is a fact about this device, and a
// laptop inheriting a phone's afternoon is noise. Reserved, so it never leaves.
private const val K_RECENT = "__home_recent"

private const val MAX_RECENT = 6

// ---- search -------------------------------------------------------------------------

// Case-insensitive, matches anywhere in the name, and also matches the domain so "arch"
// finds everything in Archive. Not fuzzy: a launcher that guesses is a launcher that
// shows you the wrong thing confidently.
fun moduleMatches(m: Module, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return m.label.lowercase().contains(q) ||
        m.id.lowercase().contains(q) ||
        m.group.lowercase().contains(q)
}

// Best matches first: something that starts with what you typed beats something that
// merely contains it, so "ta" puts Tasks above Time Capsules and Rabbit Holes.
fun searchModules(modules: List<Module>, query: String): List<Module> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return modules.filter { moduleMatches(it, q) }
        .sortedWith(
            compareBy(
                { !it.label.lowercase().startsWith(q) },
                { !it.label.lowercase().contains(q) },
                { it.label.lowercase() },
            ),
        )
}

// ---- pins ---------------------------------------------------------------------------

// Absent means never set, which seeds the defaults. An explicitly emptied list is stored
// as a single blank marker so it stays empty — otherwise unpinning the last one would
// bring all six back on the next read, which reads as the app arguing with you.
private const val EMPTY_MARKER = "-"

fun loadPins(): List<String> {
    val raw = Storage.read(K_PINS) ?: return DEFAULT_PINS
    if (raw.trim() == EMPTY_MARKER) return emptyList()
    val ids = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    return ids.ifEmpty { DEFAULT_PINS }
}

fun savePins(ids: List<String>) {
    Storage.write(K_PINS, if (ids.isEmpty()) EMPTY_MARKER else ids.joinToString("\n"))
}

// Newly pinned goes to the end, so the row you have learned the shape of does not
// reshuffle every time you add one.
fun togglePin(current: List<String>, id: String): List<String> =
    if (id in current) current - id else current + id

// Resolved against the live registry, so a pin for a module that no longer exists
// disappears from the row rather than leaving a gap or crashing.
fun pinnedModules(modules: List<Module>, ids: List<String> = loadPins()): List<Module> =
    ids.mapNotNull { id -> modules.firstOrNull { it.id == id } }

// ---- recents ------------------------------------------------------------------------

fun recentIds(): List<String> =
    Storage.read(K_RECENT)?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

// Most recent first, no duplicates, capped. Opening something you opened an hour ago
// moves it to the front rather than adding a second copy of it.
fun noteOpened(id: String, existing: List<String> = recentIds()): List<String> {
    if (id.isBlank()) return existing
    val next = (listOf(id) + existing.filterNot { it == id }).take(MAX_RECENT)
    Storage.write(K_RECENT, next.joinToString("\n"))
    return next
}

// Recents earn their place by being things you did not pin — a row that repeats the row
// above it is half a screen saying nothing.
fun recentModules(modules: List<Module>, pins: List<String> = loadPins()): List<Module> =
    recentIds().filterNot { it in pins }.mapNotNull { id -> modules.firstOrNull { it.id == id } }
