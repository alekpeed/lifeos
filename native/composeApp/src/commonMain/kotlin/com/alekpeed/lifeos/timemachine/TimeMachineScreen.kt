package com.alekpeed.lifeos.timemachine

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.collections.loadCollections
import com.alekpeed.lifeos.data.DATA_SOURCES
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.timeLabel
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.Change
import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.places.loadPlaces
import com.alekpeed.lifeos.recipes.loadRecipes
import com.alekpeed.lifeos.timecapsules.loadCapsules
import com.alekpeed.lifeos.ui.DateField
import com.alekpeed.lifeos.ui.SaveToast
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

// The Time Machine, rebuilt on the event log (§4).
//
// Three things the old version could not do, because the data did not exist: what a
// record said on a day, what changed it and in which direction, and what was deleted.
// All three come off the log now, and a record can be put back to how it read.
//
// What it still cannot do is invent history from before the log started. That boundary
// is drawn on screen rather than papered over — the counts looked authoritative before
// and were partly guesswork, which is the failure mode worth avoiding twice.

private data class Lived(val icon: String, val text: String, val source: String)

// The dated modules' own logs. This half was always genuinely historical — these records
// carry their own dates, so they read true for any past day, log or no log.
private fun livedOn(date: String): List<Lived> {
    val out = mutableListOf<Lived>()
    loadMilestones().milestones.filter { it.date == date }.forEach { out.add(Lived("🏆", it.title.ifBlank { "(untitled)" }, "Milestone")) }
    loadPlaces().places.forEach { p -> if (p.visitDates.contains(date)) out.add(Lived("📍", "Visited ${p.name.ifBlank { "(untitled)" }}", "Places")) }
    loadBooks().books.forEach { b ->
        if (b.startedDate == date) out.add(Lived("📖", "Started ${b.title.ifBlank { "(untitled)" }}", "Books"))
        if (b.finishedDate == date) out.add(Lived("📗", "Finished ${b.title.ifBlank { "(untitled)" }}", "Books"))
        b.logs.filter { it.date == date }.forEach { out.add(Lived("📖", "Read ${it.pagesRead} pages of ${b.title.ifBlank { "(untitled)" }}", "Books")) }
    }
    loadRecipes().recipes.forEach { r -> r.cookLogs.filter { it.date == date }.forEach { out.add(Lived("🍳", "Cooked ${r.title.ifBlank { "(untitled)" }}", "Recipes")) } }
    loadCollections().collections.forEach { c -> c.items.filter { it.acquiredDate == date }.forEach { out.add(Lived("🗂", "Acquired ${it.name.ifBlank { "(untitled)" }}", c.name)) } }
    loadCapsules().capsules.filter { it.createdAt == date }.forEach { out.add(Lived("⏳", "Sealed a time capsule", "Time Capsules")) }
    com.alekpeed.lifeos.tasks.loadTasks().filter { it.done && it.completedDate == date }
        .forEach { out.add(Lived("✅", "Finished ${it.title.ifBlank { "(untitled)" }}", "Tasks")) }
    com.alekpeed.lifeos.habits.loadHabits().forEach { h ->
        if (h.checkins.any { it.toString() == date }) out.add(Lived("🔁", "Checked in: ${h.name.ifBlank { "(untitled)" }}", "Habits"))
    }
    com.alekpeed.lifeos.health.loadHealth().let { h ->
        h.workouts.filter { it.date == date }.forEach { w ->
            val mins = w.minutes?.let { "${it.toInt()}m" }
            out.add(Lived("🏃", listOfNotNull(w.type.ifBlank { "Workout" }, mins).joinToString(" · "), "Health"))
        }
        h.logs.filter { it.date == date }.forEach { out.add(Lived("❤️", "Health log", "Health")) }
    }
    return out
}

// The earliest day anything points at. The log's first event is the honest anchor; the
// dated logs and real birth dates extend the scrubber back beyond it, into the stretch
// where existence is all that can be known.
private fun earliestDate(births: Births): String {
    val dates = mutableListOf<String>()
    replayStart()?.let { dates.add(it.toString()) }
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

private val KEY_LABELS: Map<String, String> = DATA_SOURCES.associate { it.key to it.label }
private fun moduleLabel(key: String) = KEY_LABELS[key] ?: key

private fun mark(c: Change) = when (c) {
    Change.CREATE -> "+"
    Change.UPDATE -> "~"
    Change.DELETE -> "−"
}

private fun verb(c: Change) = when (c) {
    Change.CREATE -> "Added"
    Change.UPDATE -> "Edited"
    Change.DELETE -> "Deleted"
}

private fun clock(at: Long): String =
    timeLabel(Instant.fromEpochMilliseconds(at).toLocalDateTime(TimeZone.currentSystemDefault()).time)

@Composable
fun TimeMachineScreen() {
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0 }, { Text("A day") })
            FilterChip(tab == 1, { tab = 1 }, { Text("Compare") })
        }
        Spacer(Modifier.height(12.dp))
        if (tab == 0) DayView() else CompareView()
    }
}

@Composable
private fun DayView() {
    // Stamp anything new before reading, so a record added since the last app open is
    // dated today rather than dated whenever this screen next gets opened.
    val births = remember { recordBirths() }
    val stores = remember { census().filter { it.stubs.isNotEmpty() } }

    var date by remember { mutableStateOf(today().toString()) }
    var tick by remember { mutableStateOf(0) }

    val day = parseDateOrNull(date) ?: today()
    val hz = remember(tick) { horizon() }
    val replayable = hz.covers(day)

    val lived = remember(date) { livedOn(date) }
    val changes = remember(date, tick) { changesOn(day) }

    val earliest = remember(births, tick) { parseDateOrNull(earliestDate(births)) ?: today() }
    val totalDays = remember(earliest) { maxOf(earliest.daysUntil(today()), 0) }
    val atPresent = date == today().toString()

    val rows = remember(date, stores, births) {
        stores.map { s -> Triple(s.label, s.stubs.count { existedOn(births, it.key, date) }, s.stubs.size) }
    }
    val thenTotal = rows.sumOf { it.second }
    val nowTotal = rows.sumOf { it.third }

    val bornThatDay = remember(date, stores, births) {
        stores.flatMap { s -> s.stubs.filter { births.born[it.key] == date }.map { s.label to it.title } }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column {
                Text(
                    if (totalDays > 0) {
                        "Scrub back through $totalDays day${if (totalDays == 1) "" else "s"} of recorded life."
                    } else {
                        "Scrub back and see what the app knew on any given day."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

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
                DateField(date) { v -> date = clampToPast(parseDateOrNull(v) ?: today()).toString() }
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
                if (pair.size == 1) Box(Modifier.weight(1f)) {}
            }
        }

        // ---- what the log recorded, which is the whole point of the rebuild ----
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text("Changed that day (${changes.size})", style = MaterialTheme.typography.titleSmall)
                if (!replayable) {
                    Text(
                        if (hz.hasLog) {
                            "Before ${hz.from} the app wasn't recording changes yet, so this day " +
                                "can only be counted, not replayed."
                        } else {
                            "Nothing has been recorded yet — the change log starts with your next edit."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (changes.isEmpty()) {
                    Text(
                        "Nothing changed that day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(changes, key = { it.seq }) { e ->
            ChangeRow(e, day) { tick++ }
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
                Text("Lived that day (${lived.size})", style = MaterialTheme.typography.titleSmall)
                if (lived.isEmpty()) {
                    Text(
                        "No dated activity logged for that day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(lived) { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(e.icon, modifier = Modifier.padding(end = 10.dp))
                Text(e.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(e.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        item { HorizonNote(hz) }
    }
}

// One logged change, with its field-level before and after, and a way back.
@Composable
private fun ChangeRow(e: Entry, day: LocalDate, onChanged: () -> Unit) {
    var open by remember(e.seq) { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mark(e.kind), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(e.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    verb(e.kind) + " · " + moduleLabel(e.key) + " · " + clock(e.at) +
                        if (e.remote) " · another device" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (e.fields.isNotEmpty()) {
                TextButton({ open = !open }) { Text(if (open) "Hide" else "What changed") }
            }
        }

        if (open) {
            e.fields.forEach { f ->
                Spacer(Modifier.height(4.dp))
                Text(f.field, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(f.before, style = MaterialTheme.typography.bodySmall)
                Text("→ " + f.after, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }

        if (e.reversible) {
            // Two different things, kept separate on purpose. "Undo" reverses this one
            // event. "Put back to how it was" rewinds the record to the end of the day
            // being looked at, which is what a time machine is actually for.
            Row {
                TextButton({
                    val m = com.alekpeed.lifeos.history.History.all().firstOrNull { it.seq == e.seq }
                    val ok = m != null && com.alekpeed.lifeos.history.History.undo(m)
                    onChanged()
                    SaveToast.show(if (ok) "Undone" else "That record has moved on too far to undo")
                }) { Text("Undo this") }
                TextButton({
                    val ok = restoreRecordTo(e, day)
                    onChanged()
                    SaveToast.show(if (ok) "Put back to how it read on $day" else "Already reads that way")
                }) { Text("Put back to $day") }
            }
        }
    }
}

@Composable
private fun CompareView() {
    val modules = remember { modulesWithHistory() }
    var from by remember { mutableStateOf(today().plusDays(-7).toString()) }
    var to by remember { mutableStateOf(today().toString()) }
    var key by remember { mutableStateOf(modules.firstOrNull().orEmpty()) }
    var tick by remember { mutableStateOf(0) }

    if (modules.isEmpty()) {
        Text(
            "Nothing has been recorded yet. Once you edit something, the two dates below " +
                "can be compared field by field.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val a = parseDateOrNull(from) ?: today().plusDays(-7)
    val b = parseDateOrNull(to) ?: today()
    val diffs = remember(key, from, to, tick) { diffBetween(key, a, b) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "What changed in one module between two days, read off the log — a whole burst " +
                "of edits collapses into one before and after.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("From", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DateField(from) { v -> from = v }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("To", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DateField(to) { v -> to = v }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modules.forEach { m ->
                        FilterChip(key == m, { key = m }, { Text(moduleLabel(m)) })
                    }
                }
            }
            if (diffs.isEmpty()) {
                item {
                    Text(
                        if (b <= a) "The second date has to be after the first."
                        else "Nothing in ${moduleLabel(key)} changed between those days.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(diffs, key = { it.coll + "|" + it.rec }) { d ->
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(mark(d.kind), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                        Text(d.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            when (d.kind) {
                                Change.CREATE -> "added since"
                                Change.DELETE -> "gone since"
                                Change.UPDATE -> "${d.fields.size} field(s)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    d.fields.forEach { f ->
                        Spacer(Modifier.height(4.dp))
                        Text(f.field, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(f.before, style = MaterialTheme.typography.bodySmall)
                        Text("→ " + f.after, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
            item { HorizonNote(horizon()) }
        }
    }
}

// What this view can and can't know. Worth stating on screen: the counts look
// authoritative, and the boundary between replay and guesswork is otherwise invisible.
@Composable
private fun HorizonNote(hz: Horizon) {
    Text(
        buildString {
            if (hz.hasLog) {
                append("Changes are replayed exactly from ${hz.from} onwards — that is when the ")
                append("log starts, and it holds ${hz.events} event(s). ")
            }
            append("Before that, only existence is known: records deleted back then are missing ")
            append("from the counts, titles read as they do today, and anything already here when ")
            append("the app started tracking arrivals counts as having been here all along.")
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
}
