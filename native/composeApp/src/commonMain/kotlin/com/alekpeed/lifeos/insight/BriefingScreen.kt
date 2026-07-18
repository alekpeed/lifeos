package com.alekpeed.lifeos.insight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.data.linesOf
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.ExpiryState
import com.alekpeed.lifeos.documents.expiryState
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.education.loadEducation
import com.alekpeed.lifeos.finance.financeBills
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.habits.saveHabits
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks

// One prioritized line: what, why, where it lives, and (for tasks/habits) a
// one-tap action that resolves it right here.
private data class BriefLine(
    val key: String,
    val text: String,
    val note: String,
    val moduleId: String,
    val action: String? = null,      // label of the one-tap action, if any
    val resolve: (() -> Unit)? = null,
)

// A real prioritized worklist, not a list dump: overdue tasks, due-today tasks,
// streaks about to break, unpaid bills, open assignments, and expiring
// documents — in that order. Task and habit rows resolve with one tap (Done /
// Check in); every row taps through to its module. Computed fresh from live
// storage; resolved rows disappear immediately.
@Composable
fun BriefingScreen() {
    var tick by remember { mutableStateOf(0) }

    val lines = remember(tick) {
        val now = today()
        val soon = now.plusDays(7)
        val tasks = loadTasks()
        val habits = loadHabits()
        val out = mutableListOf<BriefLine>()

        fun completeTask(id: Long) {
            val all = loadTasks().map { if (it.id == id) it.copy(status = "done") else it }
            saveTasks(all)
            tick += 1
        }
        tasks.filter { val d = it.dueDate(); !it.done && d != null && d < now }
            .sortedBy { it.dueDate() }
            .forEach { t -> out.add(BriefLine("t${t.id}", t.title, relativeLabel(t.dueDate()!!), "tasks", "Done ✓", { completeTask(t.id) })) }
        tasks.filter { !it.done && it.dueDate() == now }
            .forEach { t -> out.add(BriefLine("t${t.id}", t.title, "Today", "tasks", "Done ✓", { completeTask(t.id) })) }
        habits.filter { it.streak > 0 && !it.checkedInToday }
            .forEach { h ->
                out.add(
                    BriefLine("h${h.name}", h.name, "${h.streak}-day streak — check in today", "habits", "Check in", {
                        val all = loadHabits().map { if (it.name == h.name) it.copy(checkins = it.checkins + today()) else it }
                        saveHabits(all)
                        tick += 1
                    }),
                )
            }
        financeBills().filter { !it.settled }.forEach { b ->
            val due = parseDateOrNull(b.dueDate) ?: return@forEach
            if (due <= soon) out.add(BriefLine("b${b.name}", b.name, relativeLabel(due) + if (b.autopay) " · autopay" else "", "finance"))
        }
        loadEducation().assignments.filter { !it.done }.forEach { a ->
            val due = parseDateOrNull(a.dueDate) ?: return@forEach
            if (due <= soon) out.add(BriefLine("a${a.id}", a.title, relativeLabel(due), "education"))
        }
        loadDocuments().documents.forEach { d ->
            when (expiryState(d)) {
                ExpiryState.EXPIRED -> out.add(BriefLine("d${d.id}", d.title, "expired", "documents"))
                ExpiryState.SOON -> out.add(BriefLine("d${d.id}", d.title, "expires soon", "documents"))
                else -> {}
            }
        }
        out
    }

    val rollup = remember(tick) {
        listOf(
            "Ideas" to linesOf("Ideas").size,
            "Rabbit Holes" to linesOf("Rabbit Holes").size,
            "Notifications" to linesOf("Notifications").size,
        ).filter { it.second > 0 }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Briefing", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (Native.supportsTts && lines.isNotEmpty()) {
                TextButton(onClick = {
                    Native.speak("Briefing. " + lines.joinToString(". ") { "${it.text}, ${it.note}" })
                }) { Text("🔊") }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "What actually needs you, in order.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (lines.isEmpty() && rollup.isEmpty()) {
            Text(
                "Nothing urgent. You're caught up.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(lines.size, key = { lines[it].key }) { i ->
                    val line = lines[i]
                    Row(
                        Modifier.fillMaxWidth().clickable { Nav.open(line.moduleId) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("●", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(line.text, style = MaterialTheme.typography.bodyLarge)
                            Text(line.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (line.action != null && line.resolve != null) {
                            TextButton(onClick = { line.resolve.invoke() }) { Text(line.action) }
                        }
                    }
                }
                if (rollup.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("ALSO WAITING", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(rollup.size) { i ->
                        val (label, count) = rollup[i]
                        Row(Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
