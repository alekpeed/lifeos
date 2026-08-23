package com.alekpeed.lifeos.collections

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.attach.Attachment
import com.alekpeed.lifeos.data.parseDateOrNull
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Collections (§5.3) — category-agnostic by design.
//
// It has to work equally for baseball cards, stamps, Pokémon, coins and vinyl without a
// line of per-category code. Everything a category would otherwise hardcode is a field
// you fill in: the reference standard is a label, not an integration; the condition
// grades are your own ordered list, so Poor→Gem Mint and G→MS-70 both work and neither
// is baked in.
//
// The one thing that makes this a collection rather than a list is `targetSet`: a list
// of catalog references that would complete it. Without that there is nothing to be
// missing, and "set completeness" has no meaning.

@Serializable
enum class ItemStatus { OWNED, WANTED, ON_ORDER, FOR_TRADE, SOLD }

fun itemStatusLabel(s: ItemStatus) = when (s) {
    ItemStatus.OWNED -> "Owned"
    ItemStatus.WANTED -> "Wanted"
    ItemStatus.ON_ORDER -> "On order"
    ItemStatus.FOR_TRADE -> "For trade"
    ItemStatus.SOLD -> "Sold"
}

@Serializable
data class CollItem(
    val id: Long,
    val name: String,
    // The reference the catalog system uses — a Scott number, a Beckett number, a
    // Discogs release id. Matched against the collection's targetSet, case-insensitively.
    val catalogNumber: String = "",
    val series: String = "",
    val setName: String = "",
    val year: String = "",
    val variant: String = "",
    // One value from this collection's own conditionScale.
    val condition: String = "",
    val graded: Boolean = false,
    val grader: String = "",
    val certNumber: String = "",
    val gradeValue: String = "",
    // Duplicates are normal in collecting and have to be first-class: two of a card is
    // trade stock, not a data-entry mistake.
    val quantity: Int = 1,
    val status: ItemStatus = ItemStatus.OWNED,
    val acquiredDate: String = "",
    val acquiredFrom: String = "",
    val acquiredPrice: Double = 0.0,
    val acquiredCurrency: String = "",
    val estimatedValue: Double = 0.0,
    val valuationDate: String = "",
    val valuationSource: String = "",
    val storageLocation: String = "",
    // Previous owners, the signing event, the story of the purchase. Free text because
    // provenance is a story, not a schema.
    val provenance: String = "",
    // Front and back is the norm for cards, coins and stamps, so photos are a list.
    val photos: List<Attachment> = emptyList(),
    val tags: List<String> = emptyList(),
    val notes: String = "",
) {
    fun acquired(): LocalDate? = parseDateOrNull(acquiredDate)

    // Cost and value both scale with quantity: three of a card cost three times as much
    // and are worth three times as much.
    val costBasis: Double get() = acquiredPrice * quantity
    val valuation: Double get() = estimatedValue * quantity

    val counted: Boolean get() = status == ItemStatus.OWNED || status == ItemStatus.FOR_TRADE
    val isDuplicate: Boolean get() = quantity > 1 && counted
}

@Serializable
data class Collection(
    val id: Long,
    val name: String,
    val category: String = "",
    val description: String = "",
    val coverPhotoBlob: String = "",
    // The reference standard in use: Scott, Beckett, Discogs, Krause, PSA. A label, so
    // nothing here depends on a service that might disappear.
    val catalogSystem: String = "",
    // Ordered worst to best. Everything condition-related reads its order from here.
    val conditionScale: List<String> = emptyList(),
    // The catalog references that would make the set complete.
    val targetSet: List<String> = emptyList(),
    val defaultCurrency: String = "USD",
    val items: List<CollItem> = emptyList(),
)

@Serializable
data class CollectionsData(val collections: List<Collection> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadCollections(): CollectionsData {
    val raw = Storage.read("Collections")
    if (raw.isNullOrBlank()) return CollectionsData()
    if (raw.trimStart().startsWith("{")) {
        // Every new field has a default, so a blob written by the old version decodes
        // straight into the new shape — an item keeps its name, date, tags and notes and
        // gains empty catalog fields, quantity 1 and status Owned.
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

fun nextCollectionId(d: CollectionsData): Long = (d.collections.maxOfOrNull { it.id } ?: 0L) + 1

fun nextItemId(c: Collection): Long = (c.items.maxOfOrNull { it.id } ?: 0L) + 1

// ---- the views §5.3 asks for --------------------------------------------------------------

private fun norm(ref: String) = ref.trim().lowercase()

// Owned against the target set. A collection with no target set has nothing to be
// missing, so completeness is null rather than 100% — claiming a set is complete when
// nobody said what completes it is the sort of confident wrong number worth avoiding.
data class SetCompleteness(
    val target: Int,
    val held: Int,
    val missing: List<String>,
) {
    val fraction: Float get() = if (target == 0) 0f else held.toFloat() / target
    val complete: Boolean get() = target > 0 && held == target
}

fun completeness(c: Collection): SetCompleteness? {
    if (c.targetSet.isEmpty()) return null
    val have = c.items.filter { it.counted }.map { norm(it.catalogNumber) }.filter { it.isNotEmpty() }.toSet()
    val target = c.targetSet.map { it.trim() }.filter { it.isNotEmpty() }
    val missing = target.filter { norm(it) !in have }
    return SetCompleteness(target = target.size, held = target.size - missing.size, missing = missing)
}

// What the collection cost against what it is worth. Kept as three numbers rather than
// one: an unrealised gain is not money, and folding cost into it hides that.
data class ValueRollup(
    val cost: Double,
    val value: Double,
    val currency: String,
    // Items with a price but no valuation, and vice versa — the rollup is only as
    // honest as its coverage, so it says how much it is missing.
    val unpriced: Int,
    val unvalued: Int,
) {
    val gain: Double get() = value - cost
}

fun valueRollup(c: Collection): ValueRollup {
    val held = c.items.filter { it.counted }
    return ValueRollup(
        cost = held.sumOf { it.costBasis },
        value = held.sumOf { it.valuation },
        currency = c.defaultCurrency.ifBlank { "USD" },
        unpriced = held.count { it.acquiredPrice <= 0.0 },
        unvalued = held.count { it.estimatedValue <= 0.0 },
    )
}

// Every wanted item across every collection, in one place — the list you actually need
// while standing in a shop, which is useless if it is filed per collection.
data class WantedItem(val collection: Collection, val item: CollItem)

fun wantList(data: CollectionsData = loadCollections()): List<WantedItem> =
    data.collections.flatMap { c ->
        c.items.filter { it.status == ItemStatus.WANTED || it.status == ItemStatus.ON_ORDER }
            .map { WantedItem(c, it) }
    }.sortedWith(compareBy({ it.collection.name.lowercase() }, { it.item.name.lowercase() }))

// Trade stock: anything held more than once.
fun duplicates(c: Collection): List<CollItem> =
    c.items.filter { it.isDuplicate }.sortedByDescending { it.quantity }

// How a collection is grouped when you look at it.
enum class GroupBy { NONE, SERIES, SET, YEAR, CONDITION }

fun groupLabel(g: GroupBy) = when (g) {
    GroupBy.NONE -> "Flat"
    GroupBy.SERIES -> "Series"
    GroupBy.SET -> "Set"
    GroupBy.YEAR -> "Year"
    GroupBy.CONDITION -> "Condition"
}

fun grouped(c: Collection, by: GroupBy): List<Pair<String, List<CollItem>>> {
    if (by == GroupBy.NONE) return listOf("" to c.items.sortedBy { it.name.lowercase() })
    val keyed = c.items.groupBy { item ->
        when (by) {
            GroupBy.SERIES -> item.series
            GroupBy.SET -> item.setName
            GroupBy.YEAR -> item.year
            GroupBy.CONDITION -> item.condition
            GroupBy.NONE -> ""
        }.ifBlank { "Unfiled" }
    }
    // Condition sorts by the collection's own scale, worst first, because alphabetical
    // order on "Mint, Good, Poor" is meaningless.
    val order: (String) -> Int = if (by == GroupBy.CONDITION) {
        { k -> c.conditionScale.indexOfFirst { it.equals(k, ignoreCase = true) }.let { if (it < 0) 9999 else it } }
    } else {
        { 0 }
    }
    return keyed.entries
        .sortedWith(compareBy({ order(it.key) }, { it.key == "Unfiled" }, { it.key.lowercase() }))
        .map { it.key to it.value.sortedBy { i -> i.name.lowercase() } }
}

// A per-collection list with values and photo counts, ready to attach to a Documents
// record. Plain text on purpose: an insurer wants something they can read and print,
// not a format that needs this app to open it.
fun insuranceExport(c: Collection): String = buildString {
    val roll = valueRollup(c)
    appendLine(c.name.ifBlank { "Collection" })
    if (c.category.isNotBlank()) appendLine("Category: ${c.category}")
    if (c.catalogSystem.isNotBlank()) appendLine("Catalog system: ${c.catalogSystem}")
    appendLine("Items held: ${c.items.count { it.counted }}")
    appendLine("Total cost basis: ${roll.currency} ${money(roll.cost)}")
    appendLine("Total estimated value: ${roll.currency} ${money(roll.value)}")
    if (roll.unvalued > 0) appendLine("Items with no valuation: ${roll.unvalued}")
    appendLine()
    c.items.filter { it.counted }.sortedBy { it.name.lowercase() }.forEach { i ->
        val bits = listOfNotNull(
            i.name.ifBlank { "(untitled)" },
            i.catalogNumber.ifBlank { null },
            i.year.ifBlank { null },
            i.condition.ifBlank { null },
            if (i.graded) "graded ${i.grader} ${i.gradeValue}".trim() else null,
            if (i.quantity > 1) "x${i.quantity}" else null,
            "value ${i.acquiredCurrency.ifBlank { roll.currency }} ${money(i.valuation)}",
            if (i.photos.isNotEmpty()) "${i.photos.size} photo(s)" else null,
            i.storageLocation.ifBlank { null },
        )
        appendLine("- " + bits.joinToString(" · "))
    }
}

private fun money(v: Double): String = ((v * 100).toLong() / 100.0).toString()
