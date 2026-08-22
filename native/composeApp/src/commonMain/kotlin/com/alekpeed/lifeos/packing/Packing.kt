package com.alekpeed.lifeos.packing

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Packing lists — one checklist per trip, items grouped by category, with templates
// that bulk-add common items. Persists as one JSON blob under "Packing"; old flat stubs
// migrate into a single list.
//
// Absorbed into Travel (§5.1): a packing list is meaningless outside a trip, so it is
// reached through the trip rather than from its own nav slot. The data deliberately did
// NOT move — lists stay under the "Packing" key and gained an optional `tripId` instead.
// Migrating them into TravelData would have rewritten every existing list to gain a
// nav change, and a list belonging to no trip would have had nowhere to land. Old JSON
// still loads (the field defaults), and a list with tripId 0 shows as unassigned.

@Serializable
data class PackItem(val id: Long, val name: String, val category: String = "Other", val packed: Boolean = false)

@Serializable
data class PackingList(
    val id: Long,
    val name: String,
    val tripDate: String = "",
    val items: List<PackItem> = emptyList(),
    // 0 = not attached to a trip.
    val tripId: Long = 0L,
)

// A saved template. Same shape as the built-ins, but stored, so "save this list as a
// template" is one button (§5.1).
@Serializable
data class PackTemplateGroup(val category: String, val items: List<String> = emptyList())

@Serializable
data class PackTemplate(val id: Long, val name: String, val groups: List<PackTemplateGroup> = emptyList())

@Serializable
data class PackingData(
    val lists: List<PackingList> = emptyList(),
    val templates: List<PackTemplate> = emptyList(),
)

// The built-in starting points, always offered alongside any you save yourself.
//
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

fun packingListsFor(tripId: Long): List<PackingList> =
    loadPacking().lists.filter { it.tripId == tripId }

// Lists belonging to no trip. They stay reachable after the module lost its nav slot.
fun unassignedPackingLists(): List<PackingList> = loadPacking().lists.filter { it.tripId == 0L }

fun PackingList.packedCount(): Int = items.count { it.packed }

// Built-ins and saved templates in one list, so a caller never has to know which is which.
fun allTemplates(): List<PackTemplate> {
    val builtIn = PACKING_TEMPLATES.mapIndexed { i, (name, groups) ->
        PackTemplate(
            id = -(i + 1L), // negative ids: built-ins are not stored and cannot be deleted
            name = name,
            groups = groups.map { (cat, items) -> PackTemplateGroup(cat, items) },
        )
    }
    return builtIn + loadPacking().templates
}

// Turn a list into a reusable template. Packed state is deliberately dropped — a
// template is what to bring, not what you already put in the bag.
fun saveAsTemplate(list: PackingList, name: String) {
    val data = loadPacking()
    val groups = list.items.groupBy { it.category }
        .map { (cat, items) -> PackTemplateGroup(cat, items.map { it.name }) }
    val id = (data.templates.maxOfOrNull { it.id } ?: 0L) + 1
    savePacking(data.copy(templates = data.templates + PackTemplate(id, name.trim().ifBlank { list.name }, groups)))
}

fun deleteTemplate(id: Long) {
    val data = loadPacking()
    savePacking(data.copy(templates = data.templates.filterNot { it.id == id }))
}
