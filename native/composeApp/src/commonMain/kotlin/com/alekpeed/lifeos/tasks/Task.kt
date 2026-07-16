package com.alekpeed.lifeos.tasks

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.parseDateOrNull
import kotlinx.datetime.LocalDate

enum class Priority(val label: String) {
    NONE("—"), LOW("Low"), MEDIUM("Med"), HIGH("High");

    companion object {
        fun fromOrdinalOrNull(s: String): Priority = entries.getOrElse(s.toIntOrNull() ?: 0) { NONE }
    }
}

// A task record: title plus the fields that make "what's due" a real question —
// a due date, a priority, and an optional project/context tag.
data class Task(
    val id: Long,
    val title: String,
    val done: Boolean = false,
    val due: LocalDate? = null,
    val priority: Priority = Priority.NONE,
    val project: String = "",
)

// One line per task: done \t title \t due(ISO or empty) \t priority(ordinal) \t project
private fun Task.toLine(): String =
    "${if (done) 1 else 0}\t$title\t${due?.toString().orEmpty()}\t${priority.ordinal}\t$project"

private fun parseLine(id: Long, line: String): Task {
    val p = line.split("\t")
    return Task(
        id = id,
        done = p.getOrElse(0) { "0" } == "1",
        title = p.getOrElse(1) { line },
        due = p.getOrNull(2)?.let { parseDateOrNull(it) },
        priority = Priority.fromOrdinalOrNull(p.getOrElse(3) { "0" }),
        project = p.getOrElse(4) { "" },
    )
}

fun loadTasks(): List<Task> =
    Storage.read("Tasks")?.lines()?.filter { it.isNotBlank() }?.mapIndexed { i, line -> parseLine(i + 1L, line) }
        ?: listOf(
            Task(1, "This is a real native app now"),
            Task(2, "Add a task below"),
        )

fun saveTasks(tasks: List<Task>) {
    Storage.write("Tasks", tasks.joinToString("\n") { it.toLine() })
}
