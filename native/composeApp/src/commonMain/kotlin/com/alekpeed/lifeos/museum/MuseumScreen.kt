package com.alekpeed.lifeos.museum

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.education.loadEducation
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.recipes.loadRecipes
import com.alekpeed.lifeos.tasks.loadTasks
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

// Museum of Finished Things — a read-only trophy case over completions that
// already live in other modules (done tasks + assignments, finished books,
// milestones, mastered recipes, best habit streaks). Ported from the web view;
// stores nothing of its own, reads each module's real native storage.

private data class Plaque(val title: String, val meta: String)
private data class Wing(val title: String, val items: List<Plaque>)

private fun longestStreak(dates: Set<LocalDate>): Int {
    val sorted = dates.toList().sorted()
    if (sorted.isEmpty()) return 0
    var best = 1; var run = 1
    for (i in 1 until sorted.size) {
        run = if (sorted[i - 1].daysUntil(sorted[i]) == 1) run + 1 else 1
        if (run > best) best = run
    }
    return best
}

@Composable
fun MuseumScreen() {
    val wings = remember {
        val tasks = loadTasks().filter { it.status == "done" }
        val assignments = loadEducation().assignments.filter { it.done }
        val finishedBooks = loadBooks().books.filter { it.status == "finished" }
            .sortedByDescending { it.finishedDate }
        val milestones = loadMilestones().milestones.sortedByDescending { it.date }
        val masteredRecipes = loadRecipes().recipes.filter { it.cookLogs.isNotEmpty() }
            .sortedByDescending { it.cookLogs.size }
        val streaks = loadHabits().map { it.name to longestStreak(it.checkins) }
            .filter { it.second > 0 }.sortedByDescending { it.second }

        listOf(
            Wing("Books Read", finishedBooks.map { Plaque(it.title.ifBlank { "(untitled)" }, listOf(it.author, it.finishedDate).filter { s -> s.isNotBlank() }.joinToString(" · ")) }),
            Wing(
                "Tasks & Assignments Completed",
                (tasks.map { Plaque(it.title.ifBlank { "(untitled)" }, "task") } +
                    assignments.map { Plaque(it.title.ifBlank { "(untitled)" }, "assignment") }),
            ),
            Wing("Milestones Achieved", milestones.map { Plaque(it.title.ifBlank { "(untitled)" }, listOf(it.category, it.date).filter { s -> s.isNotBlank() }.joinToString(" · ")) }),
            Wing("Recipes Mastered", masteredRecipes.map { Plaque(it.title.ifBlank { "(untitled)" }, "Cooked ${it.cookLogs.size}×") }),
            Wing("Best Habit Streaks", streaks.map { Plaque(it.first, "🔥 ${it.second}-day streak") }),
        )
    }
    val totalFinished = wings.sumOf { it.items.size }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Museum of Finished Things", style = MaterialTheme.typography.headlineMedium)
        Text("$totalFinished things finished, and counting.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            wings.forEach { w ->
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(w.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${w.items.size}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (w.items.isEmpty()) {
                    item { Text("Nothing here yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    w.items.forEach { p ->
                        item {
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(p.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                if (p.meta.isNotBlank()) Text(p.meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
