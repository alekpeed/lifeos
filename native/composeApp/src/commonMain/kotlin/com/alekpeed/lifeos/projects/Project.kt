package com.alekpeed.lifeos.projects

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// W-04 — a project is a record, not a word typed into a task.
//
// "Project" was a free-text field on Task. Two tasks in the same project agreed only by
// spelling; the project itself could hold nothing — no dates, no notes, no documents, no
// sense of whether it was finished. There was no way to say "this project is done" or to
// see what it cost you, because there was nothing to say it about.
//
// A project now has an id, and a task points at it. Everything a project accumulates —
// the passport for the trip, the contractor's number, the article you're working from —
// is held as a link to the record that already exists elsewhere, the same rule Travel
// follows: a project references a Documents id, it does not copy the document.

@Serializable
enum class ProjectStatus { ACTIVE, PAUSED, DONE, ARCHIVED }

@Serializable
data class Project(
    val id: Long,
    val name: String,
    val description: String = "",
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val startDate: String = "",
    // What it is meant to be finished by. Surfaced in Calendar, so a project with a date
    // stops being something you have to remember to look at.
    val targetDate: String = "",
    val completedDate: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    // Links, not copies. Ids into Documents, Links, Contacts and Milestones.
    val documentIds: List<Long> = emptyList(),
    val linkIds: List<Long> = emptyList(),
    val contactIds: List<Long> = emptyList(),
    val milestoneIds: List<Long> = emptyList(),
) {
    fun start(): LocalDate? = parseDateOrNull(startDate)
    fun target(): LocalDate? = parseDateOrNull(targetDate)

    val open: Boolean get() = status == ProjectStatus.ACTIVE || status == ProjectStatus.PAUSED

    // Past its target date and not finished. Null target means it cannot be late.
    fun overdue(from: LocalDate = today()): Boolean {
        val t = target() ?: return false
        return open && t < from
    }
}

@Serializable
data class ProjectsData(
    val projects: List<Project> = emptyList(),
    // Set once the free-text project names on existing tasks have been turned into
    // records. Kept in the data rather than a separate key so it travels with a restore.
    val migrated: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadProjects(): ProjectsData {
    val raw = Storage.read("Projects")
    if (raw.isNullOrBlank()) return ProjectsData()
    return runCatching { json.decodeFromString<ProjectsData>(raw) }.getOrElse { ProjectsData() }
}

fun saveProjects(data: ProjectsData) {
    Storage.write("Projects", json.encodeToString(data))
}

fun projectStatusLabel(s: ProjectStatus) = when (s) {
    ProjectStatus.ACTIVE -> "Active"
    ProjectStatus.PAUSED -> "Paused"
    ProjectStatus.DONE -> "Done"
    ProjectStatus.ARCHIVED -> "Archived"
}

// ---- resolving a task's project ---------------------------------------------------------

// The name to show for a task. Prefers the record; falls back to the legacy string so a
// task that arrived from a device which has not migrated yet still reads correctly rather
// than losing its project on the way in.
fun projectNameOf(task: Task, projects: List<Project> = loadProjects().projects): String {
    task.projectId?.let { id -> projects.firstOrNull { it.id == id }?.let { return it.name } }
    return task.project
}

fun tasksIn(project: Project, tasks: List<Task> = loadTasks()): List<Task> =
    tasks.filter {
        it.projectId == project.id ||
            (it.projectId == null && it.project.isNotBlank() && it.project.equals(project.name, ignoreCase = true))
    }

data class ProjectProgress(val total: Int, val done: Int) {
    val open: Int get() = total - done
    // 0f..1f, and 0 for an empty project rather than a division by zero.
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    val complete: Boolean get() = total > 0 && done == total
}

fun projectProgress(project: Project, tasks: List<Task> = loadTasks()): ProjectProgress {
    val mine = tasksIn(project, tasks)
    return ProjectProgress(mine.size, mine.count { it.done })
}

// ---- migration ---------------------------------------------------------------------------

// Turn the free-text project names already on tasks into records, once.
//
// Additive by design: it creates a project for a name that has none and points the task
// at it. It never deletes a project, so a device that syncs in another device's projects
// before running this cannot lose them. Matching is by name, case-insensitively, so the
// two devices converge on one record for "Home renovation" rather than two.
//
// The legacy string is cleared on the tasks it moves, because leaving both would give the
// project two spellings of the truth — which is the whole thing this replaces.
fun migrateProjectStrings(): Int {
    val data = loadProjects()
    val tasks = loadTasks()

    val loose = tasks.filter { it.projectId == null && it.project.isNotBlank() }
    if (loose.isEmpty()) {
        if (!data.migrated) saveProjects(data.copy(migrated = true))
        return 0
    }

    val byName = data.projects.associateBy { it.name.trim().lowercase() }.toMutableMap()
    val created = mutableListOf<Project>()
    var nextId = (data.projects.maxOfOrNull { it.id } ?: 0L) + 1

    for (name in loose.map { it.project.trim() }.filter { it.isNotEmpty() }.distinct()) {
        val key = name.lowercase()
        if (byName.containsKey(key)) continue
        val p = Project(id = nextId++, name = name)
        created.add(p)
        byName[key] = p
    }

    val nextTasks = tasks.map { t ->
        if (t.projectId != null || t.project.isBlank()) {
            t
        } else {
            val p = byName[t.project.trim().lowercase()]
            if (p == null) t else t.copy(projectId = p.id, project = "")
        }
    }

    saveProjects(data.copy(projects = data.projects + created, migrated = true))
    saveTasks(nextTasks)
    return loose.size
}

// Cheap enough to call on every app start: it reads two keys and does nothing when there
// is nothing loose. Not gated on the `migrated` flag alone, because a task written by a
// device that has not migrated can arrive at any time and needs the same treatment.
fun ensureProjectsMigrated() {
    runCatching { migrateProjectStrings() }
}

// ---- editing --------------------------------------------------------------------------

// Deleting a project releases its tasks rather than taking them with it. Losing a week of
// tasks because a project was tidied away is not a tidy-up.
fun deleteProject(id: Long): Int {
    val data = loadProjects()
    if (data.projects.none { it.id == id }) return 0
    saveProjects(data.copy(projects = data.projects.filterNot { it.id == id }))

    val tasks = loadTasks()
    var freed = 0
    val next = tasks.map { t ->
        if (t.projectId == id) { freed++; t.copy(projectId = null) } else t
    }
    if (freed > 0) saveTasks(next)
    return freed
}

// Marking a project done stamps the date and closes it. Its open tasks are left alone on
// purpose — a finished project with three tasks still open is information, not a bug, and
// silently completing them would fake work that never happened.
fun completeProject(id: Long) {
    val data = loadProjects()
    saveProjects(
        data.copy(
            projects = data.projects.map {
                if (it.id == id) {
                    it.copy(status = ProjectStatus.DONE, completedDate = today().toString())
                } else {
                    it
                }
            },
        ),
    )
}
