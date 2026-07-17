package com.alekpeed.lifeos.packing

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Packing Lists — ported from the web app's Trip Packing Lists: one checklist
// per trip, items grouped by category, with built-in templates (weekend / beach
// / ski / international) that bulk-add common items. Persists as one JSON blob
// under "Packing"; old flat stubs migrate into a single list.

@Serializable
data class PackItem(val id: Long, val name: String, val category: String = "Other", val packed: Boolean = false)

@Serializable
data class PackingList(
    val id: Long,
    val name: String,
    val tripDate: String = "",
    val items: List<PackItem> = emptyList(),
)

@Serializable
data class PackingData(val lists: List<PackingList> = emptyList())

// name -> list of (category, itemNames). Nothing calls out anywhere; pure data.
val PACKING_TEMPLATES: List<Pair<String, List<Pair<String, List<String>>>>> = listOf(
    "Weekend trip" to listOf(
        "Clothing" to listOf("Underwear", "Socks", "Sleepwear", "Casual outfit"),
        "Toiletries" to listOf("Toothbrush", "Toothpaste", "Deodorant"),
        "Documents" to listOf("ID / license", "Wallet"),
        "Electronics" to listOf("Phone charger"),
    ),
    "Beach / warm" to listOf(
        "Clothing" to listOf("Swimsuit", "Sandals", "Sunhat", "Light clothing"),
        "Toiletries" to listOf("Sunscreen", "After-sun lotion", "Toothbrush"),
        "Gear" to listOf("Beach towel", "Sunglasses", "Reusable water bottle"),
        "Documents" to listOf("ID / license"),
    ),
    "Ski / cold" to listOf(
        "Clothing" to listOf("Thermal base layers", "Winter jacket", "Gloves", "Wool socks", "Beanie"),
        "Gear" to listOf("Goggles", "Hand warmers"),
        "Toiletries" to listOf("Lip balm", "Moisturizer", "Toothbrush"),
        "Documents" to listOf("ID / license"),
    ),
    "International" to listOf(
        "Documents" to listOf("Passport", "Visa (if needed)", "Travel insurance", "Copies of documents"),
        "Electronics" to listOf("Power adapter", "Phone charger", "Offline maps downloaded"),
        "Clothing" to listOf("Underwear", "Socks", "Versatile outfits"),
        "Toiletries" to listOf("Toothbrush", "Toothpaste (travel size)"),
        "Money" to listOf("Local currency", "Backup card"),
    ),
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadPacking(): PackingData {
    val raw = Storage.read("Packing")
    if (raw.isNullOrBlank()) return PackingData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<PackingData>(raw) }.getOrElse { PackingData() }
    }
    // Old SimpleListScreen stub: flat item names — fold into one list.
    val names = raw.lines().filter { it.isNotBlank() }
    if (names.isEmpty()) return PackingData()
    val items = names.mapIndexed { i, n -> PackItem(id = i + 2L, name = n.trim()) }
    return PackingData(lists = listOf(PackingList(id = 1L, name = "My packing list", items = items)))
}

fun savePacking(data: PackingData) {
    Storage.write("Packing", json.encodeToString(data))
}
