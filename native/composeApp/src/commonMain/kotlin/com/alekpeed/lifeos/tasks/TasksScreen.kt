package com.alekpeed.lifeos.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.today
import kotlinx.datetime.daysUntil

private fun dueColor(due: kotlinx.datetime.LocalDate?): androidx.compose.ui.graphics.Color? {
    if (due == null) return null
    val days = today().daysUntil(due)
    return when {
        days < 0 -> androidx.compose.ui.graphics.Color(0xFFE05C5C)
        days == 0 -> androidx.compose.ui.graphics.Color(0xFFE0A25C)
        else -> null
    }
}

private fun priorityColor(p: Priority): androidx.compose.ui.graphics.Color? = when (p) {
    Priority.HIGH -> androidx.compose.ui.graphics.Color(0xFFE05C5C)
    Priority.MEDIUM -> androidx.compose.ui.graphics.Color(0xFFE0A25C)
    Priority.LOW -> androidx.compose.ui.graphics.Color(0xFF5C9CE0)
    Priority.NONE -> null
}

// Tasks with real depth: due date (quick-picked, color-flagged when today/overdue),
// a cycling priority, and a free-text project tag. Quick-add stays a single title
// field; tapping a task expands inline editing for the rest, so the list stays
// scannable. Persists on every change.
@Composable
fun TasksScreen() {
    val tasks = remember { mutableStateListOf<Task>().apply { addAll(loadTasks()) } }
    fun persist() = saveTasks(tasks)
    var input by remember { mutableStateOf("") }
    var nextId by remember { mutableStateOf((tasks.maxOfOrNull { it.id } ?: 0L) + 1) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Tasks", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("New task") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim().replace("\t", " ").replace("\n", " ")
                if (t.isNotEmpty()) {
                    tasks.add(Task(nextId, t))
                    nextId += 1
                    persist()
                    input = ""
                }
            }) { Text("Add") }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(tasks, key = { it.id }) { task ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expandedId = if (expandedId == task.id) null else task.id }
                        .padding(vertical = 4.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = task.done,
                            onCheckedChange = { checked ->
                                val i = tasks.indexOfFirst { it.id == task.id }
                                if (i >= 0) { tasks[i] = task.copy(done = checked); persist() }
                            },
                        )
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (task.done) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f),
                        )
                        priorityColor(task.priority)?.let { c ->
                            Text("●", color = c, modifier = Modifier.padding(end = 6.dp))
                        }
                        task.due?.let { due ->
                            val c = dueColor(due)
                            Text(
                                relativeLabel(due),
                                style = MaterialTheme.typography.labelMedium,
                                color = c ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }
                    if (task.project.isNotBlank()) {
                        Text(
                            task.project,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 40.dp),
                        )
                    }

                    if (expandedId == task.id) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 40.dp, top = 8.dp, bottom = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                        ) {
                            Text("Due", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Today" to today(), "Tomorrow" to today().plusDays(1), "Next week" to today().plusDays(7)).forEach { (label, date) ->
                                    AssistChip(onClick = {
                                        val i = tasks.indexOfFirst { it.id == task.id }
                                        if (i >= 0) { tasks[i] = task.copy(due = date); persist() }
                                    }, label = { Text(label) })
                                }
                                TextButton(onClick = {
                                    val i = tasks.indexOfFirst { it.id == task.id }
                                    if (i >= 0) { tasks[i] = task.copy(due = null); persist() }
                                }) { Text("Clear") }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text("Priority", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Priority.entries.forEach { p ->
                                    AssistChip(onClick = {
                                        val i = tasks.indexOfFirst { it.id == task.id }
                                        if (i >= 0) { tasks[i] = task.copy(priority = p); persist() }
                                    }, label = { Text(p.label) })
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text("Project", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = task.project,
                                onValueChange = { v ->
                                    val i = tasks.indexOfFirst { it.id == task.id }
                                    if (i >= 0) { tasks[i] = task.copy(project = v.replace("\t", " ").replace("\n", " ")); persist() }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("e.g. Home renovation") },
                            )

                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                tasks.removeAll { it.id == task.id }
                                expandedId = null
                                persist()
                            }) { Text("Delete task") }
                        }
                    }
                }
            }
        }
    }
}
