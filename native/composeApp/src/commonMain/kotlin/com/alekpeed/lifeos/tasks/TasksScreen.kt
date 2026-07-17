package com.alekpeed.lifeos.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen() {
    val tasks = remember { mutableStateListOf<Task>().apply { addAll(loadTasks()) } }
    fun persist() = saveTasks(tasks)
    var input by remember { mutableStateOf("") }
    var nextId by remember { mutableStateOf((tasks.maxOfOrNull { it.id } ?: 0L) + 1) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    fun update(id: Long, f: (Task) -> Task) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i >= 0) { tasks[i] = f(tasks[i]); persist() }
    }
    fun toggleDone(task: Task) {
        val goingDone = task.status != "done"
        if (goingDone && task.recur.isNotEmpty()) {
            task.dueDate()?.let { d ->
                nextRecurDate(d, task.recur)?.let { nd ->
                    tasks.add(
                        task.copy(
                            id = nextId, status = "not_started", due = nd.toString(),
                            subtasks = task.subtasks.map { it.copy(done = false) },
                        ),
                    )
                    nextId += 1
                }
            }
        }
        update(task.id) { it.copy(status = if (goingDone) "done" else "not_started") }
    }

    val shown = tasks.sortedWith(
        compareBy({ it.done }, { it.dueDate()?.toString() ?: "9999-99-99" }, { priorityRank(it.priority) }),
    )

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
                if (t.isNotEmpty()) { tasks.add(Task(nextId, t)); nextId += 1; persist(); input = "" }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(shown, key = { it.id }) { task ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { expandedId = if (expandedId == task.id) null else task.id }
                        .padding(vertical = 4.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = task.done, onCheckedChange = { toggleDone(task) })
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
                    // meta chips row (project, status, waiting, subtasks, tags)
                    val metas = buildList {
                        if (task.project.isNotBlank()) add(task.project)
                        if (task.status == "in_progress") add("In progress")
                        if (task.status == "waiting") add(if (task.waitingOn.isNotBlank()) "Waiting: ${task.waitingOn}" else "Waiting")
                        if (task.subtasks.isNotEmpty()) add("${task.subtasks.count { it.done }}/${task.subtasks.size}")
                        task.tags.forEach { add("#$it") }
                        if (task.recur.isNotEmpty()) add("⟳ ${task.recur}")
                    }
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

                    if (expandedId == task.id) TaskEditor(task, { id, f -> update(id, f) }) {
                        tasks.removeAll { it.id == task.id }; expandedId = null; persist()
                    }
                }
            }
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
                FilterChip(selected = task.status == v, onClick = { update(task.id) { it.copy(status = v) } }, label = { Text(lbl) })
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
