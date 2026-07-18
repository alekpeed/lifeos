package com.alekpeed.lifeos.tasks

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.parseDateOrNull
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// A checklist item on a task.
@Serializable
data class Subtask(val id: Long, val text: String, val done: Boolean = false)

// The full task record — ported from the web app's Tasks: 4-state status, priority,
// due date, project/context, tags, notes, who you're waiting on, a checklist,
// recurrence, and snooze. Dates are kept as ISO strings so the record serializes
// cleanly; dueDate() / snoozeDate() parse them.
@Serializable
data class Task(
    val id: Long,
    val title: String,
    val status: String = "not_started", // not_started | in_progress | waiting | done
    val priority: String = "medium",    // low | medium | high | urgent
    val due: String = "",               // ISO date or ""
    val project: String = "",
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val waitingOn: String = "",
    val subtasks: List<Subtask> = emptyList(),
    val recur: String = "",             // "" | daily | weekly | monthly | yearly
    val snoozedUntil: String = "",      // ISO date or ""
    val completedDate: String = "",     // ISO date stamped when marked done (yearly recap)
) {
    val done: Boolean get() = status == "done"
    fun dueDate(): LocalDate? = parseDateOrNull(due)
    fun snoozeDate(): LocalDate? = parseDateOrNull(snoozedUntil)
}

val TASK_STATUSES = listOf(
    "not_started" to "Not started",
    "in_progress" to "In progress",
    "waiting" to "Waiting",
    "done" to "Done",
)
val TASK_PRIORITIES = listOf("low" to "Low", "medium" to "Medium", "high" to "High", "urgent" to "Urgent")
val TASK_RECUR = listOf("" to "Doesn't repeat", "daily" to "Daily", "weekly" to "Weekly", "monthly" to "Monthly", "yearly" to "Yearly")

fun statusLabel(s: String): String = TASK_STATUSES.firstOrNull { it.first == s }?.second ?: s
fun priorityRank(p: String): Int = when (p) { "urgent" -> 0; "high" -> 1; "medium" -> 2; else -> 3 }

// Next due date for a recurring task, from its current due date.
fun nextRecurDate(from: LocalDate, recur: String): LocalDate? = when (recur) {
    "daily" -> from.plus(DatePeriod(days = 1))
    "weekly" -> from.plus(DatePeriod(days = 7))
    "monthly" -> from.plus(DatePeriod(months = 1))
    "yearly" -> from.plus(DatePeriod(years = 1))
    else -> null
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadTasks(): List<Task> {
    val raw = Storage.read("Tasks")
    if (raw.isNullOrBlank()) {
        return listOf(Task(1, "This is a real native app now", status = "done"), Task(2, "Add a task below"))
    }
    // New format is a JSON array; old format is one tab-delimited line per task.
    if (raw.trimStart().startsWith("[")) {
        return runCatching { json.decodeFromString<List<Task>>(raw) }.getOrElse { emptyList() }
    }
    return raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line -> migrateLine(i + 1L, line) }
}

// Migrate the old "done \t title \t due \t priorityOrdinal \t project" line.
private fun migrateLine(id: Long, line: String): Task {
    val p = line.split("\t")
    val doneFlag = p.getOrElse(0) { "0" } == "1"
    val prio = when (p.getOrNull(3)?.toIntOrNull() ?: 0) { 3 -> "urgent"; 2 -> "high"; 1 -> "low"; else -> "medium" }
    return Task(
        id = id,
        title = p.getOrElse(1) { line },
        status = if (doneFlag) "done" else "not_started",
        priority = prio,
        due = p.getOrNull(2).orEmpty(),
        project = p.getOrElse(4) { "" },
    )
}

fun saveTasks(tasks: List<Task>) {
    Storage.write("Tasks", json.encodeToString(tasks))
}
