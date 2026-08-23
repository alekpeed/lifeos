package com.alekpeed.lifeos.tags

import com.alekpeed.lifeos.collections.loadCollections
import com.alekpeed.lifeos.collections.saveCollections
import com.alekpeed.lifeos.ideas.loadIdeas
import com.alekpeed.lifeos.ideas.saveIdeas
import com.alekpeed.lifeos.links.loadLinks
import com.alekpeed.lifeos.links.saveLinks
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.people.saveContacts
import com.alekpeed.lifeos.quartermaster.loadInventory
import com.alekpeed.lifeos.quartermaster.saveInventory
import com.alekpeed.lifeos.recipes.loadRecipes
import com.alekpeed.lifeos.recipes.saveRecipes
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks

// W-03 — one tag vocabulary instead of seven.
//
// Seven modules already had a `tags: List<String>` field and each one shipped its own
// comma-separated text box against it. Nothing connected them: a tag typed in Links was
// invisible in Tasks, "work" and "Work" were different things forever, and there was no
// way to see everything tagged one way or to rename a tag once you'd used it fifty times.
//
// The vocabulary is DERIVED, not stored. A separate tag table would immediately drift
// from the records — delete the last thing tagged "iceland" and the table still claims
// the tag exists. Reading the tags back out of the modules cannot drift, and the cost is
// seven small loads on a screen that is not on a hot path.
//
// Case is preserved rather than folded. Lowercasing everything would turn "NYC" into
// "nyc" permanently, which is worse than the problem it solves. Instead the registry
// groups spellings case-insensitively, shows them as separate tags, and offers to merge
// them — the taxonomy problem, surfaced rather than papered over.

// One record, anywhere, that carries tags.
data class TaggedRecord(
    val source: String,     // "Tasks" — the module's display name
    val moduleId: String,   // "tasks" — the registry id, so a hit can navigate
    val id: Long,
    val label: String,
    val tags: List<String>,
)

// A tag as it actually exists in the data.
data class TagUse(
    val tag: String,
    val count: Int,
    // Module display names it appears in, in registry order.
    val sources: List<String>,
) {
    val crossModule: Boolean get() = sources.size > 1
}

// Two spellings of the same word, e.g. "Work" used 4 times and "work" used 11.
data class TagClash(val spellings: List<TagUse>) {
    // Merging into the most-used spelling is nearly always what you want.
    val preferred: TagUse get() = spellings.maxByOrNull { it.count }!!
}

// What a tag looks like once typed: no leading hash, no stray whitespace, no commas
// (they are the separator, so a tag containing one could never be typed back in).
fun canonicalTag(raw: String): String =
    raw.trim()
        .removePrefix("#")
        .replace(",", " ")
        .split(" ", "\t", "\n")
        .filter { it.isNotBlank() }
        .joinToString(" ")

// Parse a comma-separated box into clean, de-duplicated tags. Duplicates are dropped
// case-insensitively so one record never carries both "work" and "Work".
fun parseTags(raw: String): List<String> {
    val out = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    raw.split(",").forEach { part ->
        val t = canonicalTag(part)
        if (t.isNotEmpty() && seen.add(t.lowercase())) out.add(t)
    }
    return out
}

fun formatTags(tags: List<String>): String = tags.joinToString(", ")

// Every module that carries tags, with the two operations the taxonomy needs: read the
// tagged records out, and rewrite one tag across them. Typed against each module's own
// data classes rather than poking at JSON — a rename that corrupts Contacts to save
// twenty lines is not a saving.
private class TagSource(
    val label: String,
    val moduleId: String,
    val read: () -> List<TaggedRecord>,
    // Replace `from` with `to` everywhere, or drop it when `to` is null. Returns how many
    // records changed.
    val rewrite: (from: String, to: String?) -> Int,
)

// Apply a rename to one record's tag list, keeping order and avoiding a duplicate when
// the target tag is already there. Shared by every source so they cannot disagree.
private fun rewriteList(tags: List<String>, from: String, to: String?): List<String>? {
    if (tags.none { it.equals(from, ignoreCase = true) }) return null
    val out = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (t in tags) {
        val next = if (t.equals(from, ignoreCase = true)) to else t
        if (next == null) continue
        if (seen.add(next.lowercase())) out.add(next)
    }
    return out
}

private val SOURCES: List<TagSource> = listOf(
    TagSource(
        label = "Tasks",
        moduleId = "tasks",
        read = {
            loadTasks().map { TaggedRecord("Tasks", "tasks", it.id, it.title, it.tags) }
        },
        rewrite = { from, to ->
            val all = loadTasks()
            var n = 0
            val next = all.map { t ->
                rewriteList(t.tags, from, to)?.let { n++; t.copy(tags = it) } ?: t
            }
            if (n > 0) saveTasks(next)
            n
        },
    ),
    TagSource(
        label = "Ideas",
        moduleId = "ideas",
        read = {
            loadIdeas().ideas.map { TaggedRecord("Ideas", "ideas", it.id, it.text, it.tags) }
        },
        rewrite = { from, to ->
            val data = loadIdeas()
            var n = 0
            val next = data.ideas.map { i ->
                rewriteList(i.tags, from, to)?.let { n++; i.copy(tags = it) } ?: i
            }
            if (n > 0) saveIdeas(data.copy(ideas = next))
            n
        },
    ),
    TagSource(
        label = "Links",
        moduleId = "links",
        read = {
            loadLinks().links.map {
                TaggedRecord("Links", "links", it.id, it.title.ifBlank { it.url }, it.tags)
            }
        },
        rewrite = { from, to ->
            val data = loadLinks()
            var n = 0
            val next = data.links.map { l ->
                rewriteList(l.tags, from, to)?.let { n++; l.copy(tags = it) } ?: l
            }
            if (n > 0) saveLinks(data.copy(links = next))
            n
        },
    ),
    TagSource(
        label = "Contacts",
        moduleId = "contacts",
        read = {
            loadContacts().contacts.map {
                TaggedRecord("Contacts", "contacts", it.id, it.name, it.tags)
            }
        },
        rewrite = { from, to ->
            val data = loadContacts()
            var n = 0
            val next = data.contacts.map { c ->
                rewriteList(c.tags, from, to)?.let { n++; c.copy(tags = it) } ?: c
            }
            if (n > 0) saveContacts(data.copy(contacts = next))
            n
        },
    ),
    TagSource(
        label = "Recipes",
        moduleId = "recipes",
        read = {
            loadRecipes().recipes.map {
                TaggedRecord("Recipes", "recipes", it.id, it.title, it.tags)
            }
        },
        rewrite = { from, to ->
            val data = loadRecipes()
            var n = 0
            val next = data.recipes.map { r ->
                rewriteList(r.tags, from, to)?.let { n++; r.copy(tags = it) } ?: r
            }
            if (n > 0) saveRecipes(data.copy(recipes = next))
            n
        },
    ),
    TagSource(
        label = "Quartermaster",
        moduleId = "quartermaster",
        read = {
            loadInventory().items.map {
                TaggedRecord("Quartermaster", "quartermaster", it.id, it.name, it.tags)
            }
        },
        rewrite = { from, to ->
            val data = loadInventory()
            var n = 0
            val next = data.items.map { i ->
                rewriteList(i.tags, from, to)?.let { n++; i.copy(tags = it) } ?: i
            }
            if (n > 0) saveInventory(data.copy(items = next))
            n
        },
    ),
    TagSource(
        label = "Collections",
        moduleId = "collections",
        // Items live inside collections, so the label carries the collection it is in —
        // "Blue Note 1568" alone does not say where to look for it.
        read = {
            loadCollections().collections.flatMap { c ->
                c.items.map {
                    TaggedRecord("Collections", "collections", it.id, "${it.name} · ${c.name}", it.tags)
                }
            }
        },
        rewrite = { from, to ->
            val data = loadCollections()
            var n = 0
            val next = data.collections.map { c ->
                c.copy(
                    items = c.items.map { i ->
                        rewriteList(i.tags, from, to)?.let { n++; i.copy(tags = it) } ?: i
                    },
                )
            }
            if (n > 0) saveCollections(data.copy(collections = next))
            n
        },
    ),
)

// The modules that carry tags, for a screen that wants to name them.
fun taggedModules(): List<String> = SOURCES.map { it.label }

// Every tagged record in the app. Blank tags and untagged records are dropped — this is
// the tag index, not a second copy of everything.
fun allTagged(): List<TaggedRecord> =
    SOURCES.flatMap { src ->
        runCatching { src.read() }.getOrDefault(emptyList())
    }.mapNotNull { r ->
        val tags = r.tags.map { canonicalTag(it) }.filter { it.isNotEmpty() }
        if (tags.isEmpty()) null else r.copy(tags = tags)
    }

// The vocabulary: every tag in use, most-used first, ties broken alphabetically so the
// list does not reshuffle when two counts are equal.
fun tagIndex(records: List<TaggedRecord> = allTagged()): List<TagUse> {
    val counts = LinkedHashMap<String, MutableList<TaggedRecord>>()
    for (r in records) for (t in r.tags) counts.getOrPut(t) { mutableListOf() }.add(r)
    return counts.map { (tag, rs) ->
        TagUse(
            tag = tag,
            count = rs.size,
            sources = SOURCES.map { it.label }.filter { label -> rs.any { it.source == label } },
        )
    }.sortedWith(compareByDescending<TagUse> { it.count }.thenBy { it.tag.lowercase() })
}

// Everything carrying a tag, across every module. The point of the whole exercise.
fun taggedWith(tag: String, records: List<TaggedRecord> = allTagged()): List<TaggedRecord> =
    records.filter { r -> r.tags.any { it.equals(tag, ignoreCase = true) } }

// Tags that differ only in case or spacing — the same idea recorded twice. Surfaced so
// they can be merged, rather than silently folded at write time.
fun tagClashes(index: List<TagUse> = tagIndex()): List<TagClash> =
    index.groupBy { it.tag.lowercase() }
        .values
        .filter { it.size > 1 }
        .map { group -> TagClash(group.sortedByDescending { it.count }) }
        .sortedByDescending { c -> c.spellings.sumOf { it.count } }

// Suggestions for a partly-typed tag: what is already in use, prefix matches first, then
// anything containing the fragment. An empty fragment offers the most-used tags, which is
// what you want when you have not typed anything yet.
fun suggestTags(
    fragment: String,
    exclude: List<String> = emptyList(),
    limit: Int = 8,
    index: List<TagUse> = tagIndex(),
): List<String> {
    val f = canonicalTag(fragment).lowercase()
    val taken = exclude.map { it.lowercase() }.toSet()
    val free = index.filter { it.tag.lowercase() !in taken }
    if (f.isEmpty()) return free.take(limit).map { it.tag }
    val starts = free.filter { it.tag.lowercase().startsWith(f) }
    val contains = free.filter { !it.tag.lowercase().startsWith(f) && it.tag.lowercase().contains(f) }
    return (starts + contains).take(limit).map { it.tag }
}

// Rename a tag everywhere at once. Renaming onto an existing tag is a merge, which is the
// same operation — a record that ends up with both keeps one.
// Returns the number of records changed.
fun renameTag(from: String, to: String): Int {
    val target = canonicalTag(to)
    if (target.isEmpty() || target == from) return 0
    return SOURCES.sumOf { runCatching { it.rewrite(from, target) }.getOrDefault(0) }
}

// Remove a tag from everything. The records stay; only the tag goes.
fun deleteTag(tag: String): Int =
    SOURCES.sumOf { runCatching { it.rewrite(tag, null) }.getOrDefault(0) }
