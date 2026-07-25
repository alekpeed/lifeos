package com.alekpeed.lifeos.timemachine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// Everything the dated modules record for one specific day. This half is genuinely
// historical — these logs carry their own dates, so they read true for any past day.
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
    com.alekpeed.lifeos.tasks.loadTasks().filter { it.done && it.completedDate == date }
        .forEach { out.add(Event("✅", "Finished ${it.title.ifBlank { "(untitled)" }}", "Tasks")) }
    com.alekpeed.lifeos.habits.loadHabits().forEach { h ->
        if (h.checkins.any { it.toString() == date }) out.add(Event("🔁", "Checked in: ${h.name.ifBlank { "(untitled)" }}", "Habits"))
    }
    com.alekpeed.lifeos.health.loadHealth().let { h ->
        h.workouts.filter { it.date == date }.forEach { w ->
            val mins = w.minutes?.let { "${it.toInt()}m" }
            out.add(Event("🏃", listOfNotNull(w.type.ifBlank { "Workout" }, mins).joinToString(" · "), "Health"))
        }
        h.logs.filter { it.date == date }.forEach { out.add(Event("❤️", "Health log", "Health")) }
    }
    return out
}

// The earliest day anything in the record points at — dated logs plus real birth
// dates. LEGACY births carry no date, so they can't set the start of the timeline.
private fun earliestDate(births: Births): String {
    val dates = mutableListOf<String>()
    births.born.values.forEach { if (it != LEGACY && it.isNotBlank()) dates.add(it) }
    loadMilestones().milestones.forEach { if (it.date.isNotBlank()) dates.add(it.date) }
    loadPlaces().places.forEach { p -> p.visitDates.forEach { if (it.isNotBlank()) dates.add(it) } }
    loadBooks().books.forEach { b ->
        if (b.startedDate.isNotBlank()) dates.add(b.startedDate)
        b.logs.forEach { if (it.date.isNotBlank()) dates.add(it.date) }
    }
    loadRecipes().recipes.forEach { r -> r.cookLogs.forEach { if (it.date.isNotBlank()) dates.add(it.date) } }
    loadCollections().collections.forEach { c -> c.items.forEach { if (it.acquiredDate.isNotBlank()) dates.add(it.acquiredDate) } }
    loadCapsules().capsules.forEach { if (it.createdAt.isNotBlank()) dates.add(it.createdAt) }
    com.alekpeed.lifeos.tasks.loadTasks().forEach { if (it.done && it.completedDate.isNotBlank()) dates.add(it.completedDate) }
    com.alekpeed.lifeos.health.loadHealth().let { h ->
        h.workouts.forEach { if (it.date.isNotBlank()) dates.add(it.date) }
        h.logs.forEach { if (it.date.isNotBlank()) dates.add(it.date) }
    }
    com.alekpeed.lifeos.habits.loadHabits().forEach { hb -> hb.checkins.forEach { dates.add(it.toString()) } }
    return dates.filter { it <= today().toString() }.minOrNull() ?: today().toString()
}

@Composable
fun TimeMachineScreen() {
    // Stamp anything new before reading, so a record added since the last app open is
    // dated today rather than dated whenever this screen next gets opened.
    val births = remember { recordBirths() }
    val stores = remember { census().filter { it.stubs.isNotEmpty() } }

    var date by remember { mutableStateOf(today().toString()) }
    val events = remember(date) { eventsOn(date) }

    val earliest = remember(births) { parseDateOrNull(earliestDate(births)) ?: today() }
    val totalDays = remember(earliest) { maxOf(earliest.daysUntil(today()), 0) }
    val atPresent = date == today().toString()

    // How much of the record existed by the chosen day, per store and in total.
    val rows = remember(date, stores, births) {
        stores.map { s -> Triple(s.label, s.stubs.count { existedOn(births, it.key, date) }, s.stubs.size) }
    }
    val thenTotal = rows.sumOf { it.second }
    val nowTotal = rows.sumOf { it.third }

    // Records whose birth date is exactly this day.
    val bornThatDay = remember(date, stores, births) {
        stores.flatMap { s -> s.stubs.filter { births.born[it.key] == date }.map { s.label to it.title } }
    }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column {
                Text("Time Machine", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (totalDays > 0) {
                        "Scrub back through $totalDays day${if (totalDays == 1) "" else "s"} of recorded life and see what the app knew on any given day."
                    } else {
                        "Scrub back and see what the app knew on any given day."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                // The scrubber — drag across your whole recorded history.
                if (totalDays > 0) {
                    val pos = parseDateOrNull(date)?.let { earliest.daysUntil(it).coerceIn(0, totalDays) } ?: totalDays
                    Slider(
                        value = pos.toFloat(),
                        onValueChange = { v -> date = earliest.plusDays(v.toInt().coerceIn(0, totalDays)).toString() },
                        valueRange = 0f..totalDays.toFloat(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(earliest.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DateField(date) { v -> date = v }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = { parseDateOrNull(date)?.let { date = it.plusDays(-1).toString() } }, label = { Text("◀ Prev") })
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = { parseDateOrNull(date)?.let { if (it < today()) date = it.plusDays(1).toString() } }, label = { Text("Next ▶") })
                    if (!atPresent) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(onClick = { date = today().toString() }, label = { Text("Return to today") })
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text(
                    if (atPresent) "The present" else "Life OS as of $date",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$thenTotal", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (atPresent) " records live in the app today." else " of today's $nowTotal records already existed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        // The existence grid: two stores per row, then → now.
        items(rows.chunked(2)) { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (label, then, now) ->
                    Box(Modifier.weight(1f)) {
                        Column {
                            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$then", style = MaterialTheme.typography.titleMedium)
                                if (!atPresent && then != now) {
                                    Text(
                                        " → $now",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                // Keep a lone last cell half-width rather than stretched across the row.
                if (pair.size == 1) Box(Modifier.weight(1f)) {}
            }
        }

        if (!atPresent) {
            item {
                Column(Modifier.padding(top = 14.dp)) {
                    Text("Added that day (${bornThatDay.size})", style = MaterialTheme.typography.titleSmall)
                    if (bornThatDay.isEmpty()) {
                        Text(
                            "Nothing new entered the record that day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(bornThatDay.take(25)) { (label, title) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (bornThatDay.size > 25) {
                item {
                    Text(
                        "…and ${bornThatDay.size - 25} more.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(top = 14.dp)) {
                Text("Lived that day (${events.size})", style = MaterialTheme.typography.titleSmall)
                if (events.isEmpty()) {
                    Text(
                        "No dated activity logged for that day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(events) { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(e.icon, modifier = Modifier.padding(end = 10.dp))
                Text(e.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(e.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            // What this view can and can't know. Worth stating on screen: the counts look
            // authoritative, and two of the three caveats would otherwise be invisible.
            Text(
                "What this can't show: records deleted since then are missing from the picture, " +
                    "titles read as they do today rather than as they did then, and anything that was " +
                    "already in the app before it started tracking arrivals counts as having been there all along.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
