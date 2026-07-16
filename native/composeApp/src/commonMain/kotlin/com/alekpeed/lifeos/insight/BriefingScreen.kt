package com.alekpeed.lifeos.insight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.linesOf
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.tasks.loadTasks

private data class BriefLine(val text: String, val note: String, val urgent: Boolean)

// A real prioritized worklist, not a list dump: overdue tasks first, then due
// today, then habit streaks about to break, then a light rollup of everything
// else. Computed fresh from live storage every time it opens.
@Composable
fun BriefingScreen() {
    val tasks = loadTasks()
    val habits = loadHabits()

    val overdue = tasks.filter { !it.done && it.due != null && it.due < today() }.sortedBy { it.due }
        .map { BriefLine(it.title, relativeLabel(it.due!!), urgent = true) }
    val dueToday = tasks.filter { !it.done && it.due == today() }
        .map { BriefLine(it.title, "Today", urgent = true) }
    val atRisk = habits.filter { it.streak > 0 && !it.checkedInToday }
        .map { BriefLine(it.name, "${it.streak}-day streak — check in today", urgent = true) }

    val rollup = listOf(
        "Ideas" to linesOf("Ideas").size,
        "Rabbit Holes" to linesOf("Rabbit Holes").size,
        "Notifications" to linesOf("Notifications").size,
    ).filter { it.second > 0 }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Briefing", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "What actually needs you, in order.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        val urgent = overdue + dueToday + atRisk
        if (urgent.isEmpty() && rollup.isEmpty()) {
            Text(
                "Nothing urgent. You're caught up.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(urgent.size) { i ->
                    val line = urgent[i]
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(line.text, style = MaterialTheme.typography.bodyLarge)
                            Text(line.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
