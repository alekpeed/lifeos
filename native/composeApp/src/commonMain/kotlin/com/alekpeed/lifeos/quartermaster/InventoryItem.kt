package com.alekpeed.lifeos.quartermaster

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Quartermaster — ported from the web app's Quartermaster view: a physical
// inventory with a lending ledger. Each item has a name, location, and tags,
// and can be lent out (who has it + since when). Persists as one JSON blob under
// "Quartermaster"; old note stubs migrate. AI photo-cataloging (draft an item
// list from a shelf/pantry photo) is built on the vision layer; few-shot
// stock-from-photo is the next step.

@Serializable
data class InventoryItem(
    val id: Long,
    val name: String,
    val location: String = "",
    val tags: List<String> = emptyList(),
    val lentTo: String = "",
    val lentSince: String = "",
    val photoBlob: String = "",      // blob-store id of an attached photo, if any
)

// A labeled reference photo for the few-shot stock check: a human label
// ("low"/"full"/…) plus a blob-store id for the image.
@Serializable
data class StockRef(val id: Long, val label: String, val blob: String)

@Serializable
data class QuartermasterData(
    val items: List<InventoryItem> = emptyList(),
    val stockRefs: List<StockRef> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadInventory(): QuartermasterData {
    val raw = Storage.read("Quartermaster")
    if (raw.isNullOrBlank()) return QuartermasterData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<QuartermasterData>(raw) }.getOrElse { QuartermasterData() }
    }
    val items = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val parts = line.split("\t", limit = 2)
        InventoryItem(id = i + 1L, name = parts[0].trim(), location = parts.getOrElse(1) { "" })
    }
    return QuartermasterData(items)
}

fun saveInventory(data: QuartermasterData) {
    Storage.write("Quartermaster", json.encodeToString(data))
}
