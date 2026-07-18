package com.alekpeed.lifeos.ghostdays

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.places.loadPlaces
import com.alekpeed.lifeos.recipes.loadRecipes

// Ghost Days — "on this day across the years." A read-only view (ported from the
// web) that scans the dated modules for anything that happened on today's
// month-day in a past year: milestones, places visited, books started/finished,
// recipes cooked, tasks completed, and workouts logged.

private data class Ghost(val year: String, val kind: String, val text: String)

@Composable
fun GhostDaysScreen() {
    val ghosts = remember {
        val t = today()
        val md = t.toString().substring(5, 10)   // MM-DD
        val thisYear = t.toString().take(4)
        fun on(date: String) = date.length >= 10 && date.substring(5, 10) == md && date.take(4) != thisYear

        val out = mutableListOf<Ghost>()
        loadMilestones().milestones.forEach { if (on(it.date)) out.add(Ghost(it.date.take(4), "Milestone", it.title.ifBlank { "(untitled)" })) }
        loadPlaces().places.forEach { p -> p.visitDates.forEach { d -> if (on(d)) out.add(Ghost(d.take(4), "Visited", p.name.ifBlank { "(untitled)" })) } }
        loadBooks().books.forEach { b ->
            if (on(b.finishedDate)) out.add(Ghost(b.finishedDate.take(4), "Finished reading", b.title.ifBlank { "(untitled)" }))
            if (on(b.startedDate)) out.add(Ghost(b.startedDate.take(4), "Started reading", b.title.ifBlank { "(untitled)" }))
        }
        loadRecipes().recipes.forEach { r -> r.cookLogs.forEach { l -> if (on(l.date)) out.add(Ghost(l.date.take(4), "Cooked", r.title.ifBlank { "(untitled)" })) } }
        // Task completions carry a real date now (Task.completedDate).
        com.alekpeed.lifeos.tasks.loadTasks().forEach { tk ->
            if (tk.done && on(tk.completedDate)) out.add(Ghost(tk.completedDate.take(4), "Completed", tk.title.ifBlank { "(untitled)" }))
        }
        com.alekpeed.lifeos.health.loadHealth().workouts.forEach { w ->
            if (on(w.date)) out.add(Ghost(w.date.take(4), "Worked out", w.type))
        }
        // Contact birthdays today (annual, any year) — blank year, floated to the top.
        loadContacts().contacts.forEach { c ->
            val b = c.birthday
            val bmd = when { b.length >= 10 -> b.substring(5, 10); b.length == 5 -> b; else -> "" }
            if (bmd == md) out.add(Ghost("", "Birthday", c.name.ifBlank { "(unnamed)" }))
        }
        out.sortedWith(compareBy<Ghost> { it.year.isNotBlank() }.thenByDescending { it.year })
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Ghost Days", style = MaterialTheme.typography.headlineMedium)
        Text("Everything that happened on ${today()} across the years.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        if (ghosts.isEmpty()) {
            Text("No ghosts today — a quiet page in the record.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ghosts) { g ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(g.kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(120.dp))
                    Text(g.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    val label = if (g.year.isBlank()) "today" else {
                        val n = today().toString().take(4).toInt() - (g.year.toIntOrNull() ?: today().toString().take(4).toInt())
                        if (n <= 0) "this year" else "$n year${if (n == 1) "" else "s"} ago"
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
