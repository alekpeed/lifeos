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
import com.alekpeed.lifeos.calendar.datedWorklist
import com.alekpeed.lifeos.data.linesOf
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.documents.saveDocuments
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.habits.saveHabits
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.rabbitholes.COLD_AFTER_DAYS
import com.alekpeed.lifeos.rabbitholes.daysCold
import com.alekpeed.lifeos.rabbitholes.loadHoles
import com.alekpeed.lifeos.rabbitholes.saveHoles
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks

// One prioritized line: what, why, where it lives, and (for tasks/habits) a
// one-tap action that resolves it right here.
private data class BriefLine(
    val key: String,
    val text: String,
    val note: String,
    val moduleId: String,
    val action: String? = null,      // label of the primary one-tap action, if any
    val resolve: (() -> Unit)? = null,
    val action2: String? = null,     // an optional secondary action (e.g. Snooze)
    val resolve2: (() -> Unit)? = null,
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
        val habits = loadHabits()
        val out = mutableListOf<BriefLine>()

        fun completeTask(id: Long) {
            val all = loadTasks().map { if (it.id == id) it.copy(status = "done", completedDate = today().toString()) else it }
            saveTasks(all)
            tick += 1
        }
        fun snoozeTask(id: Long) {
            val all = loadTasks().map { if (it.id == id) it.copy(snoozedUntil = today().plusDays(1).toString()) else it }
            saveTasks(all)
            tick += 1
        }
        fun renewDocument(id: Long) {
            val docs = loadDocuments()
            val next = docs.documents.map { doc ->
                if (doc.id == id) {
                    val base = parseDateOrNull(doc.expiryDate) ?: today()
                    doc.copy(expiryDate = base.plusDays(365).toString())
                } else doc
            }
            saveDocuments(docs.copy(documents = next))
            tick += 1
        }

        // Everything owed, through the shared query (§12.1.1). This screen used to walk
        // Tasks, Finance, Education and Documents itself, with its own horizon for each;
        // the horizons now live in datedWorklist so Briefing, Daily Paper and Today
        // cannot disagree about whether the same bill is due soon.
        //
        // Ordering is by urgency rather than by module, which is what the screen claims
        // to do: an overdue bill outranks a task due this afternoon.
        datedWorklist().forEach { item ->
            val note = when {
                item.moduleId == "documents" && item.isOverdue(now) -> "expired"
                item.moduleId == "documents" -> "expires soon"
                item.date == now -> "Today" + (item.note.ifBlank { "" }.let { if (it.isBlank()) "" else " · $it" })
                else -> relativeLabel(item.date) + (item.note.ifBlank { "" }.let { if (it.isBlank()) "" else " · $it" })
            }
            val id = item.recordId
            when {
                item.moduleId == "tasks" && id != null -> out.add(
                    BriefLine(
                        item.key, item.title, note, "tasks",
                        "Done ✓", { completeTask(id) }, "Snooze", { snoozeTask(id) },
                    ),
                )
                item.moduleId == "documents" && id != null -> out.add(
                    BriefLine(item.key, item.title, note, "documents", "Renew +1y", { renewDocument(id) }),
                )
                else -> out.add(BriefLine(item.key, item.title, note, item.moduleId))
            }
        }

        // A capsule that has opened and not been read (§5.4). The durable half of the
        // surfacing pair: an alarm set five years ago will not survive a new phone, but
        // this is computed from the record and cannot be lost.
        com.alekpeed.lifeos.timecapsules.unreadCapsules().forEach { c ->
            out.add(
                BriefLine(
                    "cap${c.id}",
                    c.title.ifBlank { "A sealed note" },
                    "a time capsule opened" + if (c.sealedUntil.isNotBlank()) " on ${c.sealedUntil}" else "",
                    "time-capsules",
                ),
            )
        }

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
        // Open threads you've stopped pulling on. Last in the list on purpose: this
        // is the "you left this half-finished" nudge, not something due. Resolving
        // one from here is a real answer — an abandoned thread you're never going
        // back to should be closed, not nagged about forever.
        fun resolveHole(id: Long) {
            val holes = loadHoles()
            saveHoles(holes.copy(holes = holes.holes.map { if (it.id == id) it.copy(status = "resolved") else it }))
            tick += 1
        }
        loadHoles().holes
            .filter { it.status != "resolved" }
            .mapNotNull { h -> daysCold(h)?.takeIf { it >= COLD_AFTER_DAYS }?.let { h to it } }
            .sortedByDescending { it.second }
            .forEach { (h, days) ->
                out.add(
                    BriefLine(
                        "h${h.id}",
                        h.topic.ifBlank { "(untitled thread)" },
                        "gone cold — untouched $days days",
                        "rabbit-holes",
                        "Resolve",
                        { resolveHole(h.id) },
                    ),
                )
            }
        out
    }

    val rollup = remember(tick) {
        listOf(
            "Ideas" to linesOf("Ideas").size,
            "Rabbit Holes" to linesOf("Rabbit Holes").size,
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
                        if (line.action2 != null && line.resolve2 != null) {
                            TextButton(onClick = { line.resolve2.invoke() }) { Text(line.action2) }
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
