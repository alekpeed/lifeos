package com.alekpeed.lifeos.collections

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Collections — ported from the web app's Collections Tracker: any freeform
// collection (records, cards, whatever) and the items in it. Each item has a
// name, acquired date, tags, and notes. Persists as one JSON blob under
// "Collections"; old flat stubs migrate into a single collection.

@Serializable
data class CollItem(
    val id: Long,
    val name: String,
    val acquiredDate: String = "",
    val tags: List<String> = emptyList(),
    val notes: String = "",
)

@Serializable
data class Collection(
    val id: Long,
    val name: String,
    val description: String = "",
    val items: List<CollItem> = emptyList(),
)

@Serializable
data class CollectionsData(val collections: List<Collection> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadCollections(): CollectionsData {
    val raw = Storage.read("Collections")
    if (raw.isNullOrBlank()) return CollectionsData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<CollectionsData>(raw) }.getOrElse { CollectionsData() }
    }
    // Old SimpleListScreen stub (flat item names) → one collection.
    val names = raw.lines().filter { it.isNotBlank() }
    if (names.isEmpty()) return CollectionsData()
    val items = names.mapIndexed { i, n -> CollItem(id = i + 2L, name = n.trim()) }
    return CollectionsData(collections = listOf(Collection(id = 1L, name = "My collection", items = items)))
}

fun saveCollections(data: CollectionsData) {
    Storage.write("Collections", json.encodeToString(data))
}
