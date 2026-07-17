package com.alekpeed.lifeos.education

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.today
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Education — ported from the web app's three-level model:
// Semesters → Courses → Assignments, plus weighted GPA and an "academic pacing
// check" (a self-set target/unit, dated checkpoints, and a progress log that
// flags when you've fallen behind your own intention). Everything persists as a
// single JSON blob under the "Education" key; old stub lines are migrated.

@Serializable
data class KeyDate(val label: String, val date: String)

@Serializable
data class Checkpoint(val date: String, val targetByThen: Int)

@Serializable
data class ProgressLog(val id: Long, val date: String, val unitsAdded: Int)

@Serializable
data class Semester(
    val id: Long,
    val name: String,
    val startDate: String = "",
    val endDate: String = "",
)

@Serializable
data class Course(
    val id: Long,
    val semesterId: Long,
    val name: String,
    val credits: Double = 3.0,
    val grade: String = "",              // "" | A | A- | B+ | ...
    val readingListTag: String = "",
    val notes: String = "",
    val keyDates: List<KeyDate> = emptyList(),
)

@Serializable
data class Assignment(
    val id: Long,
    val courseId: Long,
    val title: String,
    val dueDate: String = "",            // ISO date or ""
    val status: String = "not_started",  // not_started | in_progress | done
    val percentComplete: Int = 0,
    val timeSpentMinutes: Int = 0,
    val grade: Int? = null,              // 0-100 or null
    val pacingTarget: Int? = null,
    val pacingUnit: String = "pages",    // pages | words
    val paceCheckpoints: List<Checkpoint> = emptyList(),
    val progressLogs: List<ProgressLog> = emptyList(),
) {
    val done: Boolean get() = status == "done"
}

@Serializable
data class EducationData(
    val semesters: List<Semester> = emptyList(),
    val courses: List<Course> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
)

val ASSIGNMENT_STATUSES = listOf(
    "not_started" to "Not started",
    "in_progress" to "In progress",
    "done" to "Done",
)

val GRADE_POINTS = linkedMapOf(
    "A" to 4.0, "A-" to 3.7, "B+" to 3.3, "B" to 3.0, "B-" to 2.7,
    "C+" to 2.3, "C" to 2.0, "C-" to 1.7, "D" to 1.0, "F" to 0.0,
)
val GRADE_OPTIONS = listOf("") + GRADE_POINTS.keys.toList()

// The most recent checkpoint whose date has already passed, vs. everything
// logged so far. null when nothing is due yet. gap>0 means behind pace.
data class PacingStatus(val checkpoint: Checkpoint, val loggedTotal: Int, val gap: Int)

fun pacingStatusFor(a: Assignment): PacingStatus? {
    val todayStr = today().toString()
    val due = a.paceCheckpoints.filter { it.date <= todayStr }.sortedByDescending { it.date }
    if (due.isEmpty()) return null
    val checkpoint = due.first()
    val loggedTotal = a.progressLogs.sumOf { it.unitsAdded }
    return PacingStatus(checkpoint, loggedTotal, checkpoint.targetByThen - loggedTotal)
}

// Weighted GPA across graded courses (grade in GRADE_POINTS), weighted by credits.
fun runningGpa(courses: List<Course>): Pair<Double, Int>? {
    val graded = courses.filter { it.grade.isNotBlank() && GRADE_POINTS.containsKey(it.grade) }
    if (graded.isEmpty()) return null
    val totalCredits = graded.sumOf { it.credits }
    val totalPoints = graded.sumOf { (GRADE_POINTS[it.grade] ?: 0.0) * it.credits }
    return (if (totalCredits > 0) totalPoints / totalCredits else 0.0) to graded.size
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadEducation(): EducationData {
    val raw = Storage.read("Education")
    if (raw.isNullOrBlank()) return EducationData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<EducationData>(raw) }.getOrElse { EducationData() }
    }
    // Migrate the old StatusListScreen stub ("<course>\t<statusIndex>" per line):
    // drop each line into an "Imported" semester as a course so entries survive.
    val lines = raw.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return EducationData()
    val semId = 1L
    val courses = lines.mapIndexed { i, line ->
        Course(id = i + 2L, semesterId = semId, name = line.split("\t").first().trim())
    }
    return EducationData(
        semesters = listOf(Semester(id = semId, name = "Imported")),
        courses = courses,
    )
}

fun saveEducation(data: EducationData) {
    Storage.write("Education", json.encodeToString(data))
}
