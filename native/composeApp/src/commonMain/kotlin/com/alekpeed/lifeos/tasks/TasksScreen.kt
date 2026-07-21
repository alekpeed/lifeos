package com.alekpeed.lifeos.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.ui.SaveToast
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

private fun dueColor(due: LocalDate?, done: Boolean): Color? {
    if (due == null || done) return null
    val days = today().daysUntil(due)
    return when {
        days < 0 -> Color(0xFFE05C5C)
        days == 0 -> Color(0xFFE0A25C)
        else -> null
    }
}

private fun priorityColor(p: String): Color? = when (p) {
    "urgent" -> Color(0xFFD64545)
    "high" -> Color(0xFFE05C5C)
    "medium" -> Color(0xFFE0A25C)
    "low" -> Color(0xFF5C9CE0)
    else -> null
}

// The chips shown under a task title (project, status, waiting-on, subtask count,
// tags, recurrence). Shared by the list rows and the board cards.
private fun taskMetas(task: Task): List<String> = buildList {
    if (task.project.isNotBlank()) add(task.project)
    if (task.status == "in_progress") add("In progress")
    if (task.status == "waiting") add(if (task.waitingOn.isNotBlank()) "Waiting: ${task.waitingOn}" else "Waiting")
    if (task.subtasks.isNotEmpty()) add("${task.subtasks.count { it.done }}/${task.subtasks.size}")
    task.tags.forEach { add("#$it") }
    if (task.recur.isNotEmpty()) add("⟳ ${task.recur}")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen() {
    val tasks = remember { mutableStateListOf<Task>().apply { addAll(loadTasks()) } }
    fun persist() { saveTasks(tasks); SaveToast.show() }
    var input by remember { mutableStateOf("") }
    var nextId by remember { mutableStateOf((tasks.maxOfOrNull { it.id } ?: 0L) + 1) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var board by remember { mutableStateOf(false) }
    var projectFilter by remember { mutableStateOf<String?>(null) }
    var hideDone by remember { mutableStateOf(false) }
    var showSnoozed by remember { mutableStateOf(false) }
    var sortByPriority by remember { mutableStateOf(false) }

    fun update(id: Long, f: (Task) -> Task) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i >= 0) { tasks[i] = f(tasks[i]); persist() }
    }
    // Marking a recurring task done spawns its next occurrence from the due date.
    fun spawnRecurrence(task: Task) {
        task.dueDate()?.let { d ->
            nextRecurDate(d, task.recur)?.let { nd ->
                tasks.add(task.copy(id = nextId, status = "not_started", due = nd.toString(), subtasks = task.subtasks.map { it.copy(done = false) }))
                nextId += 1
            }
        }
    }
    fun moveStatus(task: Task, newStatus: String) {
        if (newStatus == "done" && task.status != "done" && task.recur.isNotEmpty()) spawnRecurrence(task)
        // Stamp/clear the completion date so the yearly recap can count real years.
        update(task.id) { it.copy(status = newStatus, completedDate = if (newStatus == "done") today().toString() else "") }
    }
    fun toggleDone(task: Task) = moveStatus(task, if (task.status == "done") "not_started" else "done")

    val projects = tasks.map { it.project.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    fun visible(list: List<Task>) = list.filter { projectFilter == null || it.project.trim() == projectFilter }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Tasks", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("New task") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim().replace("\n", " ")
                if (t.isNotEmpty()) {
                    // Adding while filtered to a project drops the new task into that project.
                    tasks.add(Task(nextId, t, project = projectFilter ?: "")); nextId += 1; persist(); input = ""
                }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(10.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = !board, onClick = { board = false }, label = { Text("List") })
            FilterChip(selected = board, onClick = { board = true }, label = { Text("Board") })
            if (!board) {
                FilterChip(selected = hideDone, onClick = { hideDone = !hideDone }, label = { Text("Hide done") })
                val snoozed = visible(tasks).count { !it.done && (it.snoozeDate()?.let { d -> d > today() } == true) }
                if (snoozed > 0) FilterChip(selected = showSnoozed, onClick = { showSnoozed = !showSnoozed }, label = { Text("Snoozed ($snoozed)") })
                FilterChip(selected = sortByPriority, onClick = { sortByPriority = !sortByPriority }, label = { Text(if (sortByPriority) "By priority" else "By due") })
            }
        }
        if (projects.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = projectFilter == null, onClick = { projectFilter = null }, label = { Text("All") })
                projects.forEach { p ->
                    FilterChip(selected = projectFilter == p, onClick = { projectFilter = if (projectFilter == p) null else p }, label = { Text(p) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (board) {
            TaskBoard(
                tasks = visible(tasks),
                onOpen = { id -> board = false; expandedId = id },
                onMove = { task, s -> moveStatus(task, s) },
            )
        } else {
            val shown = visible(tasks)
                .filter { !hideDone || !it.done }
                .filter { showSnoozed || it.done || (it.snoozeDate()?.let { d -> d <= today() } ?: true) }
                .sortedWith(
                    if (sortByPriority) {
                        compareBy({ it.done }, { priorityRank(it.priority) }, { it.dueDate()?.toString() ?: "9999-99-99" })
                    } else {
                        compareBy({ it.done }, { it.dueDate()?.toString() ?: "9999-99-99" }, { priorityRank(it.priority) })
                    },
                )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        expanded = expandedId == task.id,
                        onToggleExpand = { expandedId = if (expandedId == task.id) null else task.id },
                        onToggleDone = { toggleDone(task) },
                        update = { id, f -> update(id, f) },
                        onDelete = { tasks.removeAll { it.id == task.id }; expandedId = null; persist() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(
    task: Task,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleDone: () -> Unit,
    update: (Long, (Task) -> Task) -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable { onToggleExpand() }.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.done, onCheckedChange = { onToggleDone() })
            priorityColor(task.priority)?.let { c ->
                Text("●", color = c, modifier = Modifier.padding(end = 6.dp))
            }
            Text(
                task.title, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            task.dueDate()?.let { due ->
                Text(
                    relativeLabel(due), style = MaterialTheme.typography.labelMedium,
                    color = dueColor(due, task.done) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val metas = taskMetas(task)
        if (metas.isNotEmpty()) {
            FlowRow(
                Modifier.padding(start = 40.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                metas.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (expanded) TaskEditor(task, update, onDelete)
    }
}

// Kanban: one column per status, horizontally scrollable. Cards carry ‹ / › to
// nudge a task to the previous/next status without opening it; tapping a card
// jumps back to the list with that task expanded for a full edit.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TaskBoard(tasks: List<Task>, onOpen: (Long) -> Unit, onMove: (Task, String) -> Unit) {
    val statusOrder = TASK_STATUSES.map { it.first }
    Row(
        Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TASK_STATUSES.forEach { (statusVal, label) ->
            val colTasks = tasks.filter { (it.status.ifBlank { "not_started" }) == statusVal }
                .sortedWith(compareBy({ it.dueDate()?.toString() ?: "9999-99-99" }, { priorityRank(it.priority) }))
            val idx = statusOrder.indexOf(statusVal)
            Column(Modifier.width(250.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text("$label · ${colTasks.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                if (colTasks.isEmpty()) {
                    Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                colTasks.forEach { task ->
                    BoardCard(
                        task = task,
                        canPrev = idx > 0,
                        canNext = idx in 0 until statusOrder.lastIndex,
                        onOpen = { onOpen(task.id) },
                        onPrev = { if (idx > 0) onMove(task, statusOrder[idx - 1]) },
                        onNext = { if (idx in 0 until statusOrder.lastIndex) onMove(task, statusOrder[idx + 1]) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardCard(
    task: Task,
    canPrev: Boolean,
    canNext: Boolean,
    onOpen: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpen() }.padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            priorityColor(task.priority)?.let { c -> Text("●", color = c, modifier = Modifier.padding(end = 6.dp)) }
            Text(
                task.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
        }
        task.dueDate()?.let { due ->
            Text(
                relativeLabel(due), style = MaterialTheme.typography.labelSmall,
                color = dueColor(due, task.done) ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Status is already implied by the column, so drop the status chips here.
        val metas = taskMetas(task).filter { it != "In progress" && !it.startsWith("Waiting") }
        if (metas.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                metas.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (canPrev) TextButton(onClick = onPrev) { Text("‹ Back") } else Spacer(Modifier.width(1.dp))
            Spacer(Modifier.weight(1f))
            if (canNext) TextButton(onClick = onNext) { Text("Next ›") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditor(task: Task, update: (Long, (Task) -> Task) -> Unit, onDelete: () -> Unit) {
    var newSub by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().padding(start = 40.dp, top = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) {
        Label("Status")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TASK_STATUSES.forEach { (v, lbl) ->
                FilterChip(
                    selected = task.status == v,
                    onClick = { update(task.id) { it.copy(status = v, completedDate = if (v == "done") today().toString() else "") } },
                    label = { Text(lbl) },
                )
            }
        }
        Label("Priority")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TASK_PRIORITIES.forEach { (v, lbl) ->
                FilterChip(selected = task.priority == v, onClick = { update(task.id) { it.copy(priority = v) } }, label = { Text(lbl) })
            }
        }
        Label("Due")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Today" to today(), "Tomorrow" to today().plusDays(1), "Next week" to today().plusDays(7)).forEach { (lbl, d) ->
                AssistChip(onClick = { update(task.id) { it.copy(due = d.toString()) } }, label = { Text(lbl) })
            }
            TextButton(onClick = { update(task.id) { it.copy(due = "") } }) { Text("Clear") }
        }
        Label("Repeats")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TASK_RECUR.forEach { (v, lbl) ->
                FilterChip(selected = task.recur == v, onClick = { update(task.id) { it.copy(recur = v) } }, label = { Text(lbl) })
            }
        }
        Label("Project")
        val projectOptions = remember { loadTasks().map { it.project.trim() }.filter { it.isNotEmpty() }.distinct().sorted() }
        if (projectOptions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                projectOptions.forEach { p ->
                    FilterChip(
                        selected = task.project.trim() == p,
                        onClick = { update(task.id) { t -> t.copy(project = if (t.project.trim() == p) "" else p) } },
                        label = { Text(p) },
                    )
                }
            }
        }
        EditField(task.project, "e.g. Home renovation") { v -> update(task.id) { it.copy(project = v.replace("\n", " ")) } }
        Label("Tags (comma separated)")
        EditField(task.tags.joinToString(", "), "errand, work") { v ->
            update(task.id) { it.copy(tags = v.split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() }) }
        }
        if (task.status == "waiting") {
            Label("Waiting on")
            EditField(task.waitingOn, "Who are you waiting on?") { v -> update(task.id) { it.copy(waitingOn = v.replace("\n", " ")) } }
        }
        Label("Notes")
        EditField(task.notes, "Notes", singleLine = false) { v -> update(task.id) { it.copy(notes = v) } }

        Label("Snooze")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { update(task.id) { it.copy(snoozedUntil = today().plusDays(1).toString()) } }, label = { Text("Tomorrow") })
            AssistChip(onClick = { update(task.id) { it.copy(snoozedUntil = today().plusDays(7).toString()) } }, label = { Text("+1 week") })
            if (task.snoozedUntil.isNotEmpty()) TextButton(onClick = { update(task.id) { it.copy(snoozedUntil = "") } }) { Text("Clear") }
        }
        if (task.snoozedUntil.isNotEmpty()) Text("Snoozed → ${task.snoozedUntil}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Label("Checklist")
        task.subtasks.forEach { sub ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sub.done, onCheckedChange = { c ->
                    update(task.id) { t -> t.copy(subtasks = t.subtasks.map { if (it.id == sub.id) it.copy(done = c) else it }) }
                })
                Text(sub.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    textDecoration = if (sub.done) TextDecoration.LineThrough else null)
                TextButton(onClick = { update(task.id) { t -> t.copy(subtasks = t.subtasks.filter { it.id != sub.id }) } }) { Text("×") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newSub, { newSub = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Add checklist item") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val txt = newSub.trim()
                if (txt.isNotEmpty()) {
                    val sid = (task.subtasks.maxOfOrNull { it.id } ?: 0L) + 1
                    update(task.id) { it.copy(subtasks = it.subtasks + Subtask(sid, txt)) }
                    newSub = ""
                }
            }) { Text("Add") }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDelete) { Text("Delete task", color = Color(0xFFD64545)) }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun EditField(value: String, placeholder: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine, placeholder = { Text(placeholder) },
    )
}
