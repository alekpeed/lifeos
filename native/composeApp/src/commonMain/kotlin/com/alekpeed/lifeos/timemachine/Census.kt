package com.alekpeed.lifeos.timemachine

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.collections.loadCollections
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.finance.financeBillStubs
import com.alekpeed.lifeos.finance.financeSubStubs
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.ideas.loadIdeas
import com.alekpeed.lifeos.links.loadLinks
import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.places.loadPlaces
import com.alekpeed.lifeos.quartermaster.loadInventory
import com.alekpeed.lifeos.rabbitholes.loadHoles
import com.alekpeed.lifeos.recipes.loadRecipes
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.timecapsules.loadCapsules
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// The record census — what exists in every content store right now, and when each
// record first appeared. This is what lets the Time Machine answer "how much of
// today's record already existed on that day" instead of only counting dated events.
//
// Records don't carry a creation timestamp (ids are sequential, not clocks), so the
// birth date is kept in a side registry: every store is scanned at app open, and any
// key not seen before is stamped with today's date. The first ever scan is different
// — everything already in the app is marked LEGACY, meaning "was here before the app
// started keeping track", rather than pretending it was all created that day. The
// screen says so; overstating what the data knows is worse than a gap.

// One record, reduced to what the census needs: a stable key and something to call it.
data class Stub(val key: String, val title: String)

// One store's current contents.
data class StoreSnapshot(val label: String, val stubs: List<Stub>)

const val LEGACY = "legacy"

@Serializable
data class Births(
    val seeded: Boolean = false,
    val born: Map<String, String> = emptyMap(),
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private const val BIRTHS_KEY = "RecordBirths"

fun loadBirths(): Births {
    val raw = Storage.read(BIRTHS_KEY)
    if (raw.isNullOrBlank()) return Births()
    return runCatching { json.decodeFromString<Births>(raw) }.getOrElse { Births() }
}

private fun saveBirths(b: Births) {
    Storage.write(BIRTHS_KEY, json.encodeToString(b))
}

private fun titled(s: String) = s.ifBlank { "(untitled)" }

// Every store worth counting existence for. Habits are keyed by name — they have no
// id — which is fine because the name is what identifies them in their own store too.
fun census(): List<StoreSnapshot> = listOf(
    StoreSnapshot("Tasks", loadTasks().map { Stub("Tasks#${it.id}", titled(it.title)) }),
    StoreSnapshot("Ideas", loadIdeas().ideas.map { Stub("Ideas#${it.id}", titled(it.text)) }),
    StoreSnapshot("Places", loadPlaces().places.map { Stub("Places#${it.id}", titled(it.name)) }),
    StoreSnapshot("Links", loadLinks().links.map { Stub("Links#${it.id}", titled(it.title.ifBlank { it.url })) }),
    StoreSnapshot("Books", loadBooks().books.map { Stub("Books#${it.id}", titled(it.title)) }),
    StoreSnapshot("Recipes", loadRecipes().recipes.map { Stub("Recipes#${it.id}", titled(it.title)) }),
    StoreSnapshot("Bills", financeBillStubs().map { Stub("Bills#${it.first}", titled(it.second)) }),
    StoreSnapshot("Subscriptions", financeSubStubs().map { Stub("Subs#${it.first}", titled(it.second)) }),
    StoreSnapshot("Documents", loadDocuments().documents.map { Stub("Documents#${it.id}", titled(it.title)) }),
    StoreSnapshot("Contacts", loadContacts().contacts.map { Stub("Contacts#${it.id}", titled(it.name)) }),
    StoreSnapshot("Milestones", loadMilestones().milestones.map { Stub("Milestones#${it.id}", titled(it.title)) }),
    StoreSnapshot("Habits", loadHabits().map { Stub("Habits#${it.name}", titled(it.name)) }),
    StoreSnapshot("Collections", loadCollections().collections.map { Stub("Collections#${it.id}", titled(it.name)) }),
    StoreSnapshot("Rabbit Holes", loadHoles().holes.map { Stub("Holes#${it.id}", titled(it.topic)) }),
    StoreSnapshot("Time Capsules", loadCapsules().capsules.map { Stub("Capsules#${it.id}", titled(it.title)) }),
    StoreSnapshot("Inventory", loadInventory().items.map { Stub("Inventory#${it.id}", titled(it.name)) }),
)

// Stamp any record the registry hasn't seen. Called at app open (and again when the
// Time Machine opens) so a record's birth date is the day it actually turned up
// rather than the day someone happened to look at this screen. Cheap: one read of
// each store plus a write only when something is new. Deleted records keep their
// entry — harmless, and it means a restored record keeps its original date.
fun recordBirths(): Births {
    val cur = loadBirths()
    val stamp = if (cur.seeded) today().toString() else LEGACY
    val out = cur.born.toMutableMap()
    var added = 0
    census().forEach { store ->
        store.stubs.forEach { st ->
            if (!out.containsKey(st.key)) {
                out[st.key] = stamp
                added++
            }
        }
    }
    if (added == 0 && cur.seeded) return cur
    val next = Births(seeded = true, born = out)
    saveBirths(next)
    return next
}

// Did this record exist on `date`? A LEGACY record predates the registry, so it
// counts as having been there all along.
fun existedOn(births: Births, key: String, date: String): Boolean {
    val b = births.born[key] ?: return false
    return b == LEGACY || b <= date
}
