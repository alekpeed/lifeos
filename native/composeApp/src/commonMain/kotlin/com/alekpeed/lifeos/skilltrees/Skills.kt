package com.alekpeed.lifeos.skilltrees

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.attach.Attachment
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.education.loadEducation
import com.alekpeed.lifeos.habits.loadHabits
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// §5.2 Tier 2 — Skills.
//
// The inversion. Standings derive a rank from activity; a Skill is something you declare
// you are learning, and a level moves only when a benchmark you wrote is met. Nothing
// here computes a level from a task count, and nothing outside this tier uses the word
// "level" at all.
//
// Deliberately absent: XP, anything that rises on its own, and any number derived from
// unrelated activity. Those belong to Standings, in their own band, under their own word.

@Serializable
data class Skill(
    val id: Long,
    val name: String,
    // music / language / code / physical / craft / other — free text, not an enum, so a
    // domain nobody thought of is not a code change.
    val domain: String = "",
    // Sub-skills: "Guitar" contains "Barre chords" and "Sight-reading", each independently
    // trackable.
    val parentId: Long? = null,
    val startedDate: String = "",
    val notes: String = "",
    val photoBlob: String = "",
    // A position on this skill's own ladder. Set by you or by a benchmark being met,
    // never derived.
    val currentLevel: Int = 0,
    // The ladder itself: A1→C2, white→black belt, grades 1–8. Nothing hardcoded, because
    // no two disciplines count the same way.
    val levelScale: List<String> = emptyList(),
    val targetLevel: Int? = null,
    val targetDate: String = "",
    // A paused skill stops decaying and stops nagging.
    val active: Boolean = true,
    // Links out, so a session is never logged twice. A habit check-in becomes practice,
    // a course contributes its minutes, a book stands as evidence.
    val habitNames: List<String> = emptyList(),
    val courseIds: List<Long> = emptyList(),
    val bookIds: List<Long> = emptyList(),
    // Minutes credited per check-in of a linked habit. A daily practice habit is not
    // worth guessing at, so it is stated.
    val minutesPerCheckin: Int = 30,
) {
    fun levelName(): String =
        levelScale.getOrNull(currentLevel)?.takeIf { it.isNotBlank() } ?: "Level $currentLevel"

    fun started(): LocalDate? = parseDateOrNull(startedDate)
}

@Serializable
data class PracticeLog(
    val id: Long,
    val skillId: Long,
    val date: String,
    val minutes: Int,
    // What was worked on. Deliberate practice is not time served, which is why this and
    // `quality` exist at all.
    val focus: String = "",
    val quality: Int = 3, // 1..5, self-rated
    val notes: String = "",
    val attachments: List<Attachment> = emptyList(),
)

// What "next level" concretely means. Achieving one is the honest trigger for a level
// change — an XP bar is not.
@Serializable
data class Benchmark(
    val id: Long,
    val skillId: Long,
    val label: String,
    val targetLevel: Int,
    val achieved: Boolean = false,
    val achievedDate: String = "",
)

@Serializable
data class SkillsData(
    val skills: List<Skill> = emptyList(),
    val logs: List<PracticeLog> = emptyList(),
    val benchmarks: List<Benchmark> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadSkills(): SkillsData {
    val raw = Storage.read("Skills")
    if (raw.isNullOrBlank()) return SkillsData()
    return runCatching { json.decodeFromString<SkillsData>(raw) }.getOrElse { SkillsData() }
}

fun saveSkills(data: SkillsData) {
    Storage.write("Skills", json.encodeToString(data))
}

fun nextSkillId(d: SkillsData): Long = (d.skills.maxOfOrNull { it.id } ?: 0L) + 1
fun nextLogId(d: SkillsData): Long = (d.logs.maxOfOrNull { it.id } ?: 0L) + 1
fun nextBenchmarkId(d: SkillsData): Long = (d.benchmarks.maxOfOrNull { it.id } ?: 0L) + 1

// ---- hours, and where they came from ---------------------------------------------------

// One contribution of practice time. Kept apart by origin so the screen can say where an
// hour came from, and so nothing is counted twice.
data class HoursBreakdown(
    val logged: Int,     // minutes from practice logs written here
    val habits: Int,     // minutes credited from linked habit check-ins
    val courses: Int,    // minutes from linked Education courses
) {
    val total: Int get() = logged + habits + courses
    val hours: Double get() = total / 60.0
}

// A linked habit's check-ins become practice, at the skill's stated rate. Check-ins on a
// day that already has a written log for this skill are skipped: the same session
// recorded twice is the thing this is meant to avoid.
private fun habitMinutes(skill: Skill, own: List<PracticeLog>): Int {
    if (skill.habitNames.isEmpty()) return 0
    val loggedDays = own.map { it.date }.toSet()
    val habits = runCatching { loadHabits() }.getOrDefault(emptyList())
    var days = 0
    skill.habitNames.forEach { name ->
        val h = habits.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return@forEach
        days += h.checkins.count { it.toString() !in loggedDays }
    }
    return days * skill.minutesPerCheckin
}

private fun courseMinutes(skill: Skill): Int {
    if (skill.courseIds.isEmpty()) return 0
    val ed = runCatching { loadEducation() }.getOrNull() ?: return 0
    return ed.assignments.filter { it.courseId in skill.courseIds }.sumOf { it.timeSpentMinutes }
}

fun hoursFor(skill: Skill, data: SkillsData = loadSkills()): HoursBreakdown {
    val own = data.logs.filter { it.skillId == skill.id }
    return HoursBreakdown(
        logged = own.sumOf { it.minutes },
        habits = habitMinutes(skill, own),
        courses = courseMinutes(skill),
    )
}

// Books finished in this skill's domain, attached as evidence rather than as hours — a
// book read is not a session practised.
fun evidenceBooks(skill: Skill): List<String> {
    if (skill.bookIds.isEmpty()) return emptyList()
    val books = runCatching { loadBooks().books }.getOrDefault(emptyList())
    return books.filter { it.id in skill.bookIds }.map { it.title.ifBlank { "(untitled)" } }
}

// ---- practice shape ----------------------------------------------------------------------

fun logsFor(skillId: Long, data: SkillsData = loadSkills()): List<PracticeLog> =
    data.logs.filter { it.skillId == skillId }.sortedByDescending { it.date }

fun lastPracticed(skill: Skill, data: SkillsData = loadSkills()): LocalDate? {
    val own = data.logs.filter { it.skillId == skill.id }.mapNotNull { parseDateOrNull(it.date) }
    val fromHabits = if (skill.habitNames.isEmpty()) {
        emptyList()
    } else {
        val habits = runCatching { loadHabits() }.getOrDefault(emptyList())
        skill.habitNames.flatMap { n ->
            habits.firstOrNull { it.name.equals(n, ignoreCase = true) }?.checkins?.toList().orEmpty()
        }
    }
    return (own + fromHabits).maxOrNull()
}

// Consecutive days practised, counting back from today (or yesterday, so a streak is not
// broken before the day is over).
fun practiceStreak(skill: Skill, data: SkillsData = loadSkills()): Int {
    val days = mutableSetOf<LocalDate>()
    data.logs.filter { it.skillId == skill.id }.forEach { parseDateOrNull(it.date)?.let(days::add) }
    if (skill.habitNames.isNotEmpty()) {
        val habits = runCatching { loadHabits() }.getOrDefault(emptyList())
        skill.habitNames.forEach { n ->
            habits.firstOrNull { it.name.equals(n, ignoreCase = true) }?.checkins?.forEach(days::add)
        }
    }
    if (days.isEmpty()) return 0
    var day = if (today() in days) today() else today().plusDays(-1)
    if (day !in days) return 0
    var n = 0
    while (day in days) {
        n++
        day = day.plusDays(-1)
    }
    return n
}

// ---- decay, on the ladder salvaged from Recall --------------------------------------------

// How a skill stands against the practice ladder. The rung is derived from the practice
// history rather than stored, so it cannot drift out of step with the logs: each session
// on a distinct day advances a rung, and a gap past the current rung's interval drops it
// back to the bottom, exactly as Recall's spaced repetition did.
data class SkillFreshness(
    val freshness: Freshness,
    val lastPracticed: LocalDate?,
    val daysSince: Int?,
    val stale: Boolean,
    // A paused skill is exempt: it is not going cold, it is put down on purpose.
    val exempt: Boolean,
)

fun freshnessOf(skill: Skill, data: SkillsData = loadSkills()): SkillFreshness {
    val last = lastPracticed(skill, data)
    if (!skill.active) {
        return SkillFreshness(freshnessStart(), last, last?.let { it.daysUntilToday() }, false, exempt = true)
    }
    if (last == null) {
        return SkillFreshness(freshnessStart(), null, null, stale = false, exempt = false)
    }

    // Walk the practice days in order. Each session advances a rung; a gap that outran
    // the rung it had reached drops back to the bottom, exactly as Recall's ladder did.
    //
    // Every rung is anchored to the session that set it, not to today — a single session
    // sixty days ago has to read as long overdue, which is the whole point.
    val days = practiceDays(skill, data)
    var interval = PRACTICE_LADDER.first()
    var prev: LocalDate? = null
    days.forEach { d ->
        val gap = prev?.let { p -> d.toEpochDays() - p.toEpochDays() }
        interval = when {
            gap == null -> PRACTICE_LADDER.first()
            gap > interval -> PRACTICE_LADDER.first()
            else -> PRACTICE_LADDER.firstOrNull { it > interval } ?: PRACTICE_LADDER.last()
        }
        prev = d
    }
    val f = Freshness(interval, last.plusDays(interval))
    return SkillFreshness(f, last, last.daysUntilToday(), stale = f.isStale(), exempt = false)
}

private fun LocalDate.daysUntilToday(): Int = today().toEpochDays() - this.toEpochDays()

private fun practiceDays(skill: Skill, data: SkillsData): List<LocalDate> {
    val days = mutableSetOf<LocalDate>()
    data.logs.filter { it.skillId == skill.id }.forEach { parseDateOrNull(it.date)?.let(days::add) }
    if (skill.habitNames.isNotEmpty()) {
        val habits = runCatching { loadHabits() }.getOrDefault(emptyList())
        skill.habitNames.forEach { n ->
            habits.firstOrNull { it.name.equals(n, ignoreCase = true) }?.checkins?.forEach(days::add)
        }
    }
    return days.sorted()
}

// ---- benchmarks ----------------------------------------------------------------------------

fun benchmarksFor(skillId: Long, data: SkillsData = loadSkills()): List<Benchmark> =
    data.benchmarks.filter { it.skillId == skillId }
        .sortedWith(compareBy({ it.achieved }, { it.targetLevel }))

// The next thing standing between this skill and its following level.
fun nextBenchmark(skill: Skill, data: SkillsData = loadSkills()): Benchmark? =
    data.benchmarks.filter { it.skillId == skill.id && !it.achieved }
        .minByOrNull { it.targetLevel }

// Marking a benchmark achieved is the one thing that moves a level, and only forward to
// the level that benchmark was written for. Returns the updated data.
fun achieveBenchmark(data: SkillsData, benchmarkId: Long): SkillsData {
    val b = data.benchmarks.firstOrNull { it.id == benchmarkId } ?: return data
    if (b.achieved) return data
    val done = b.copy(achieved = true, achievedDate = today().toString())
    val skills = data.skills.map { s ->
        if (s.id == b.skillId && b.targetLevel > s.currentLevel) s.copy(currentLevel = b.targetLevel) else s
    }
    return data.copy(
        skills = skills,
        benchmarks = data.benchmarks.map { if (it.id == benchmarkId) done else it },
    )
}

// ---- the whole picture ------------------------------------------------------------------------

// One skill's standing, for the overview that shows everything without drilling in.
data class SkillSummary(
    val skill: Skill,
    val hours: HoursBreakdown,
    val streak: Int,
    val freshness: SkillFreshness,
    val next: Benchmark?,
    val benchmarksMet: Int,
    val benchmarksTotal: Int,
    val children: List<Skill>,
)

fun skillSummaries(data: SkillsData = loadSkills()): List<SkillSummary> =
    data.skills.map { s ->
        val bs = data.benchmarks.filter { it.skillId == s.id }
        SkillSummary(
            skill = s,
            hours = hoursFor(s, data),
            streak = practiceStreak(s, data),
            freshness = freshnessOf(s, data),
            next = nextBenchmark(s, data),
            benchmarksMet = bs.count { it.achieved },
            benchmarksTotal = bs.size,
            children = data.skills.filter { it.parentId == s.id },
        )
    }

// Top-level skills, each with its sub-skills behind it — the tree the module is named for.
fun skillRoots(data: SkillsData = loadSkills()): List<Skill> =
    data.skills.filter { it.parentId == null || data.skills.none { p -> p.id == it.parentId } }
        .sortedBy { it.name.lowercase() }

// What has gone cold, worst first. The all-skills view's most useful column.
fun goneCold(data: SkillsData = loadSkills()): List<SkillSummary> =
    skillSummaries(data)
        .filter { it.freshness.stale && !it.freshness.exempt }
        .sortedByDescending { it.freshness.daysSince ?: 0 }

// Deleting a skill takes its logs and benchmarks with it — they mean nothing without it —
// and orphaned sub-skills are promoted rather than deleted along with the parent.
fun deleteSkill(data: SkillsData, id: Long): SkillsData = data.copy(
    skills = data.skills.filterNot { it.id == id }.map { if (it.parentId == id) it.copy(parentId = null) else it },
    logs = data.logs.filterNot { it.skillId == id },
    benchmarks = data.benchmarks.filterNot { it.skillId == id },
)
