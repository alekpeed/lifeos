package com.alekpeed.lifeos.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.habits.Habit
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.habits.saveHabits
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks

// The real "what needs you today" screen: overdue + due-today tasks (checkable
// right here) and habits you haven't checked in yet (check-in right here). Reads
// live from Tasks/Habits storage every time it opens, so it's never stale.
@Composable
fun TodayScreen() {
    val tasks = remember { mutableStateListOf<Task>().apply { addAll(loadTasks()) } }
    val habits = remember { mutableStateListOf<Habit>().apply { addAll(loadHabits()) } }
    fun persistTasks() = saveTasks(tasks)
    fun persistHabits() = saveHabits(habits)

    val overdue = tasks.filter { !it.done && it.due != null && it.due < today() }.sortedBy { it.due }
    val dueToday = tasks.filter { !it.done && it.due == today() }
    val pendingHabits = habits.filter { !it.checkedInToday }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (Native.supportsTts) {
                TextButton(onClick = {
                    val speech = buildString {
                        append("Today. ")
                        append(if (overdue.isEmpty()) "" else "${overdue.size} overdue: ${overdue.joinToString(". ") { it.title }}. ")
                        append(if (dueToday.isEmpty()) "" else "${dueToday.size} due today: ${dueToday.joinToString(". ") { it.title }}. ")
                        append(if (pendingHabits.isEmpty()) "All habits checked in." else "${pendingHabits.size} habits waiting: ${pendingHabits.joinToString(". ") { it.name }}.")
                    }
                    Native.speak(speech)
                }) { Text("🔊 Read aloud") }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (overdue.isEmpty() && dueToday.isEmpty() && pendingHabits.isEmpty()) {
            Text(
                "Clear. Nothing due today.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (overdue.isNotEmpty()) {
                item {
                    Section("OVERDUE", MaterialTheme.colorScheme.error) {
                        overdue.forEach { task ->
                            TaskRow(task, showDue = true) { checked ->
                                val i = tasks.indexOfFirst { it.id == task.id }
                                if (i >= 0) { tasks[i] = task.copy(done = checked); persistTasks() }
                            }
                        }
                    }
                }
            }
            if (dueToday.isNotEmpty()) {
                item {
                    Section("DUE TODAY", MaterialTheme.colorScheme.primary) {
                        dueToday.forEach { task ->
                            TaskRow(task, showDue = false) { checked ->
                                val i = tasks.indexOfFirst { it.id == task.id }
                                if (i >= 0) { tasks[i] = task.copy(done = checked); persistTasks() }
                            }
                        }
                    }
                }
            }
            if (pendingHabits.isNotEmpty()) {
                item {
                    Section("HABITS", MaterialTheme.colorScheme.onSurfaceVariant) {
                        pendingHabits.forEach { habit ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("🔥 ${habit.streak}", modifier = Modifier.padding(end = 10.dp))
                                Text(habit.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                TextButton(onClick = {
                                    val i = habits.indexOfFirst { it.name == habit.name }
                                    if (i >= 0) { habits[i] = habit.copy(checkins = habit.checkins + today()); persistHabits() }
                                }) { Text("Check in") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, color: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = color)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun TaskRow(task: Task, showDue: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = task.done, onCheckedChange = onToggle)
        Text(task.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (showDue && task.due != null) {
            Text(
                relativeLabel(task.due),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
