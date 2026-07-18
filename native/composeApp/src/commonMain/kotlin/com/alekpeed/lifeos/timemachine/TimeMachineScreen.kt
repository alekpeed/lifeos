package com.alekpeed.lifeos.timemachine

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.collections.loadCollections
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.places.loadPlaces
import com.alekpeed.lifeos.recipes.loadRecipes
import com.alekpeed.lifeos.timecapsules.loadCapsules
import com.alekpeed.lifeos.ui.DateField
import kotlinx.datetime.daysUntil

private data class Event(val icon: String, val text: String, val source: String)

// Everything the dated modules record for one specific day.
private fun eventsOn(date: String): List<Event> {
    val out = mutableListOf<Event>()
    loadMilestones().milestones.filter { it.date == date }.forEach { out.add(Event("🏆", it.title.ifBlank { "(untitled)" }, "Milestone")) }
    loadPlaces().places.forEach { p -> if (p.visitDates.contains(date)) out.add(Event("📍", "Visited ${p.name.ifBlank { "(untitled)" }}", "Places")) }
    loadBooks().books.forEach { b ->
        if (b.startedDate == date) out.add(Event("📖", "Started ${b.title.ifBlank { "(untitled)" }}", "Books"))
        if (b.finishedDate == date) out.add(Event("📗", "Finished ${b.title.ifBlank { "(untitled)" }}", "Books"))
        b.logs.filter { it.date == date }.forEach { out.add(Event("📖", "Read ${it.pagesRead} pages of ${b.title.ifBlank { "(untitled)" }}", "Books")) }
    }
    loadRecipes().recipes.forEach { r -> r.cookLogs.filter { it.date == date }.forEach { out.add(Event("🍳", "Cooked ${r.title.ifBlank { "(untitled)" }}", "Recipes")) } }
    loadCollections().collections.forEach { c -> c.items.filter { it.acquiredDate == date }.forEach { out.add(Event("🗂", "Acquired ${it.name.ifBlank { "(untitled)" }}", c.name)) } }
    loadCapsules().capsules.filter { it.createdAt == date }.forEach { out.add(Event("⏳", "Sealed a time capsule", "Time Capsules")) }
    return out
}

// Every dated event in the record as (store label, ISO date) — the raw material
// for the then-vs-now counts. Gathered once per open.
private fun allDatedEvents(): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    loadMilestones().milestones.forEach { if (it.date.isNotBlank()) out.add("Milestones" to it.date) }
    loadPlaces().places.forEach { p -> p.visitDates.forEach { out.add("Place visits" to it) } }
    loadBooks().books.forEach { b ->
        if (b.startedDate.isNotBlank()) out.add("Books started" to b.startedDate)
        b.logs.forEach { out.add("Reading sessions" to it.date) }
    }
    loadRecipes().recipes.forEach { r -> r.cookLogs.forEach { out.add("Cook sessions" to it.date) } }
    loadCollections().collections.forEach { c -> c.items.forEach { if (it.acquiredDate.isNotBlank()) out.add("Collection items" to it.acquiredDate) } }
    loadCapsules().capsules.forEach { if (it.createdAt.isNotBlank()) out.add("Capsules sealed" to it.createdAt) }
    com.alekpeed.lifeos.tasks.loadTasks().forEach { if (it.done && it.completedDate.isNotBlank()) out.add("Tasks completed" to it.completedDate) }
    com.alekpeed.lifeos.health.loadHealth().let { h ->
        h.workouts.forEach { out.add("Workouts" to it.date) }
        h.logs.forEach { out.add("Health logs" to it.date) }
    }
    com.alekpeed.lifeos.habits.loadHabits().forEach { hb -> hb.checkins.forEach { out.add("Habit check-ins" to it.toString()) } }
    return out
}

@Composable
fun TimeMachineScreen() {
    var date by remember { mutableStateOf(today().toString()) }
    val events = remember(date) { eventsOn(date) }
    val dated = remember { allDatedEvents() }

    // Scrubber range: the earliest dated record → today.
    val earliest = remember(dated) { dated.minOfOrNull { it.second }?.let { parseDateOrNull(it) } ?: today() }
    val totalDays = remember(earliest) { maxOf(earliest.daysUntil(today()), 0) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Time Machine", style = MaterialTheme.typography.headlineMedium)
        Text("Scrub back and see what you recorded on any given day.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        // The scrubber — drag across your whole recorded history.
        if (totalDays > 0) {
            val pos = parseDateOrNull(date)?.let { earliest.daysUntil(it).coerceIn(0, totalDays) } ?: totalDays
            Slider(
                value = pos.toFloat(),
                onValueChange = { v -> date = earliest.plusDays(v.toInt().coerceIn(0, totalDays)).toString() },
                valueRange = 0f..totalDays.toFloat(),
            )
        }
        DateField(date) { v -> date = v }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { parseDateOrNull(date)?.let { date = it.plusDays(-1).toString() } }, label = { Text("◀ Prev") })
            Spacer(Modifier.width(8.dp))
            AssistChip(onClick = { parseDateOrNull(date)?.let { if (it < today()) date = it.plusDays(1).toString() } }, label = { Text("Next ▶") })
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { date = today().toString() }, label = { Text("Today") })
        }
        Spacer(Modifier.height(14.dp))

        Text(
            if (date == today().toString()) "Today" else "Life OS on $date",
            style = MaterialTheme.typography.titleMedium,
        )
        Text("${events.size} thing${if (events.size == 1) "" else "s"} recorded that day.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        // Then vs now — how much of the record existed by the selected day.
        val thenTotal = dated.count { it.second <= date }
        Text(
            "THEN vs NOW — $thenTotal of ${dated.size} dated records existed by this day",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        val byStore = dated.groupBy { it.first }
        byStore.entries.sortedByDescending { it.value.size }.take(6).forEach { (label, list) ->
            val then = list.count { it.second <= date }
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("$then → ${list.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(10.dp))

        if (events.isEmpty()) {
            Text("No dated activity logged for that day.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(events) { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.icon, modifier = Modifier.padding(end = 10.dp))
                        Text(e.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(e.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Text(
            "Titles read as they are today; a full point-in-time rebuild isn't something the app records yet.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp),
        )
    }
}
