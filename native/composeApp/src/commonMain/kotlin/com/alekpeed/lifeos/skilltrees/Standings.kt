package com.alekpeed.lifeos.skilltrees

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.collections.loadCollections
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.education.loadEducation
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.places.loadPlaces
import com.alekpeed.lifeos.recipes.loadRecipes
import com.alekpeed.lifeos.tasks.loadTasks
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.floor
import kotlin.math.sqrt

// §5.2 Tier 1 — Standings.
//
// What the old Skill Trees was, kept exactly, with the two things that made it fragile
// fixed.
//
// The concept was never the problem. The problem was the word: both this and a real
// skill said "Level 4" while meaning different things — one accumulated, one earned. So
// this tier counts in RANKS, and the word "level" belongs to Tier 2 alone. Practice
// hours may feed a Standing; a Standing may never feed a skill's level.
//
// Fix 1: a Standing is a record, not three hardcoded branches. Cutting a module now
// means reweighting a Standing rather than silently losing a branch — which is what
// killed two of the original five when Chords and Languages were removed.
//
// Fix 2: seeded rather than fixed. Executor, Discipline and Scholar ship preconfigured
// at their existing weights, so the screen reads as it always has on first open, and all
// three can be renamed, reweighted or deleted.

// A countable event somewhere else in the app.
@Serializable
enum class SourceKind {
    TASKS_COMPLETED,
    ASSIGNMENTS_COMPLETED,
    HABIT_CHECKINS,
    BOOKS_FINISHED,
    RECIPES_COOKED,
    PLACES_VISITED,
    PRACTICE_HOURS,
    DOCUMENTS_FILED,
    COLLECTION_ITEMS,
}

fun sourceLabel(k: SourceKind) = when (k) {
    SourceKind.TASKS_COMPLETED -> "Tasks completed"
    SourceKind.ASSIGNMENTS_COMPLETED -> "Assignments completed"
    SourceKind.HABIT_CHECKINS -> "Habit check-ins"
    SourceKind.BOOKS_FINISHED -> "Books finished"
    SourceKind.RECIPES_COOKED -> "Recipes cooked"
    SourceKind.PLACES_VISITED -> "Places visited"
    SourceKind.PRACTICE_HOURS -> "Practice hours logged"
    SourceKind.DOCUMENTS_FILED -> "Documents filed"
    SourceKind.COLLECTION_ITEMS -> "Collection items acquired"
}

// XP per event for one source, inside one Standing.
@Serializable
data class SourceWeight(val kind: SourceKind, val xp: Int)

@Serializable
data class Standing(
    val id: Long,
    val name: String,
    val icon: String = "◆",
    val blurb: String = "",
    val sources: List<SourceWeight> = emptyList(),
    // Optional names for the rungs, in order from rank 1. Past the end of the list a
    // rank is shown by number.
    val rankNames: List<String> = emptyList(),
)

@Serializable
data class StandingsData(
    val standings: List<Standing> = emptyList(),
    // Set once the seeded three have been written, so deleting them all does not bring
    // them back on the next open.
    val seeded: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// ---- the rank curve, carried over unchanged ------------------------------------------

// rank = floor(sqrt(xp / 10)) + 1 — fast early, slower later.
fun rankOf(xp: Int): Int = floor(sqrt(xp / 10.0)).toInt() + 1

fun xpForRank(rank: Int): Int = 10 * (rank - 1) * (rank - 1)

// How far through the current rank, 0f..1f.
fun rankProgress(xp: Int): Float {
    val r = rankOf(xp)
    val floorXp = xpForRank(r)
    val ceilXp = xpForRank(r + 1)
    val span = ceilXp - floorXp
    return if (span <= 0) 1f else ((xp - floorXp).toFloat() / span).coerceIn(0f, 1f)
}

fun rankLabel(s: Standing, rank: Int): String =
    s.rankNames.getOrNull(rank - 1)?.takeIf { it.isNotBlank() } ?: "Rank $rank"

// ---- counting -------------------------------------------------------------------------

// How many of each countable event exist right now. Read once and shared across every
// Standing, because several of them will name the same source and each load is a file.
data class ActivityCounts(val byKind: Map<SourceKind, Int>) {
    operator fun get(k: SourceKind): Int = byKind[k] ?: 0
}

fun activityCounts(): ActivityCounts {
    val counts = mutableMapOf<SourceKind, Int>()
    fun put(k: SourceKind, n: Int) { counts[k] = n }

    put(SourceKind.TASKS_COMPLETED, runCatching { loadTasks().count { it.done } }.getOrDefault(0))
    put(
        SourceKind.ASSIGNMENTS_COMPLETED,
        runCatching { loadEducation().assignments.count { it.done } }.getOrDefault(0),
    )
    put(SourceKind.HABIT_CHECKINS, runCatching { loadHabits().sumOf { it.checkins.size } }.getOrDefault(0))
    put(
        SourceKind.BOOKS_FINISHED,
        runCatching { loadBooks().books.count { it.status == "finished" } }.getOrDefault(0),
    )
    put(
        SourceKind.RECIPES_COOKED,
        runCatching { loadRecipes().recipes.sumOf { it.cookLogs.size } }.getOrDefault(0),
    )
    put(
        SourceKind.PLACES_VISITED,
        runCatching { loadPlaces().places.sumOf { it.visitDates.size } }.getOrDefault(0),
    )
    // Whole hours only: a Standing counts activity, and half an hour is not an event.
    put(
        SourceKind.PRACTICE_HOURS,
        runCatching { loadSkills().logs.sumOf { it.minutes } / 60 }.getOrDefault(0),
    )
    put(SourceKind.DOCUMENTS_FILED, runCatching { loadDocuments().documents.size }.getOrDefault(0))
    put(
        SourceKind.COLLECTION_ITEMS,
        runCatching { loadCollections().collections.sumOf { it.items.size } }.getOrDefault(0),
    )
    return ActivityCounts(counts)
}

fun standingXp(s: Standing, counts: ActivityCounts): Int =
    s.sources.sumOf { counts[it.kind] * it.xp }

// The line under the bar: what the number is actually made of. Sources contributing
// nothing are dropped rather than shown as zeroes.
fun standingBlurb(s: Standing, counts: ActivityCounts): String {
    val parts = s.sources
        .filter { counts[it.kind] > 0 }
        .map { "${counts[it.kind]} ${sourceLabel(it.kind).lowercase()}" }
    return if (parts.isEmpty()) s.blurb.ifBlank { "nothing counted yet" } else parts.joinToString(" · ")
}

// ---- storage ----------------------------------------------------------------------------

// The three the module has always shown, at the weights it has always used.
private fun seeded(): List<Standing> = listOf(
    Standing(
        id = 1,
        name = "Executor",
        icon = "✅",
        blurb = "things carried to done",
        sources = listOf(
            SourceWeight(SourceKind.TASKS_COMPLETED, 10),
            SourceWeight(SourceKind.ASSIGNMENTS_COMPLETED, 10),
        ),
    ),
    Standing(
        id = 2,
        name = "Discipline",
        icon = "🔥",
        blurb = "showing up repeatedly",
        sources = listOf(SourceWeight(SourceKind.HABIT_CHECKINS, 5)),
    ),
    Standing(
        id = 3,
        name = "Scholar",
        icon = "📖",
        blurb = "books read to the end",
        sources = listOf(SourceWeight(SourceKind.BOOKS_FINISHED, 40)),
    ),
)

fun loadStandings(): StandingsData {
    val raw = Storage.read("Skill Trees")
    if (raw.isNullOrBlank()) return StandingsData(seeded(), seeded = false)
    val stored = runCatching { json.decodeFromString<StandingsData>(raw) }.getOrNull()
        ?: return StandingsData(seeded(), seeded = false)
    // A store written before Standings existed decodes as an empty, unseeded list; give
    // it the three it always had rather than an empty screen.
    return if (!stored.seeded && stored.standings.isEmpty()) StandingsData(seeded(), seeded = false) else stored
}

fun saveStandings(data: StandingsData) {
    Storage.write("Skill Trees", json.encodeToString(data.copy(seeded = true)))
}

fun nextStandingId(data: StandingsData): Long = (data.standings.maxOfOrNull { it.id } ?: 0L) + 1
