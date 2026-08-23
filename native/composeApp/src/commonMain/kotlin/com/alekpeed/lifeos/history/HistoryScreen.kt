package com.alekpeed.lifeos.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.DATA_SOURCES
import com.alekpeed.lifeos.ui.SaveToast
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// The surface for R-02 and R-03: Trash (what you deleted, and getting it back) and
// Activity (what changed, and putting one change back).
//
// One screen rather than a trash can bolted onto each of 38 modules — the log is
// app-wide, so the recovery UI is too, and a module that gets rebuilt later inherits it
// without doing anything.

private val KEY_LABELS: Map<String, String> = DATA_SOURCES.associate { it.key to it.label }

private fun moduleLabel(key: String) = KEY_LABELS[key] ?: key

private fun ago(millis: Long, now: Long = Clock.System.now().toEpochMilliseconds()): String {
    val mins = (now - millis) / 60_000L
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 24 * 60 -> "${mins / 60}h ago"
        mins < 7 * 24 * 60 -> "${mins / (24 * 60)}d ago"
        else -> {
            val d = Instant.fromEpochMilliseconds(millis)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            "$d"
        }
    }
}

// Field values are stored as raw JSON so nested shapes survive. For reading, a plain
// string should look like a plain string.
private fun pretty(raw: String): String {
    val t = raw.trim()
    val body = if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
        t.substring(1, t.length - 1).replace("\\\"", "\"").replace("\\n", " ")
    } else {
        t
    }
    return if (body.isBlank()) "(empty)" else if (body.length > 160) body.take(160) + "…" else body
}

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

@Composable
fun HistoryScreen() {
    var tab by remember { mutableStateOf(0) }
    // Every mutating action bumps this, which re-reads the log. The log is the source of
    // truth and it changes underneath this screen (a sync can add to it), so nothing here
    // caches a list across an action.
    var tick by remember { mutableStateOf(0) }

    val trashCount = remember(tick) { History.trashCount() }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Nothing is thrown away the moment you delete it. Deleted records wait " +
                "${History.retentionDays} days here, and recent edits can be put back.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0 }, { Text(if (trashCount > 0) "Trash · $trashCount" else "Trash") })
            FilterChip(tab == 1, { tab = 1 }, { Text("Activity") })
        }
        Spacer(Modifier.height(12.dp))

        if (tab == 0) {
            TrashTab(tick, onChange = { tick++; SaveToast.show(it) })
        } else {
            ActivityTab(tick, onChange = { tick++; SaveToast.show(it) })
        }
    }
}

@Composable
private fun TrashTab(tick: Int, onChange: (String) -> Unit) {
    val items = remember(tick) { History.trash() }
    var confirmEmpty by remember { mutableStateOf(false) }

    if (items.isEmpty()) {
        Text(
            "Nothing deleted in the last ${History.retentionDays} days.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${items.size} recoverable",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        TextButton({ confirmEmpty = true }) { Text("Empty trash") }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.seq }) { m ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            ) {
                Text(
                    m.label.ifBlank { "Untitled record" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    moduleLabel(m.key) + " · " + ago(m.at) + if (m.remote) " · from another device" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!m.reversible) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "One of its fields was too large to keep in full, so this one " +
                            "can't be put back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row {
                    if (m.reversible) {
                        TextButton({
                            onChange(
                                if (History.restore(m)) "Restored to ${moduleLabel(m.key)}"
                                else "Couldn't restore that one",
                            )
                        }) { Text("Restore") }
                    }
                    TextButton({
                        History.purge(m)
                        onChange("Deleted for good")
                    }) { Text("Delete for good") }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty the trash?") },
            text = { Text("${items.size} deleted record(s) will be gone for good. This cannot be undone.") },
            confirmButton = {
                TextButton({
                    History.emptyTrash()
                    confirmEmpty = false
                    onChange("Trash emptied")
                }) { Text("Empty trash") }
            },
            dismissButton = { TextButton({ confirmEmpty = false }) { Text("Keep them") } },
        )
    }
}

@Composable
private fun ActivityTab(tick: Int, onChange: (String) -> Unit) {
    var filter by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<Long?>(null) }

    val keys = remember(tick) { History.keysTouched() }
    val items = remember(tick, filter) { History.recent(300, filter) }

    if (keys.size > 1) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(filter == null, { filter = null }, { Text("Everything") })
            keys.forEach { k ->
                FilterChip(filter == k, { filter = k }, { Text(moduleLabel(k)) })
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (items.isEmpty()) {
        Text(
            "No changes recorded yet. Edits start appearing here as you make them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items, key = { it.seq }) { m ->
            val expanded = open == m.seq
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { open = if (expanded) null else m.seq }
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mark(m.change), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            m.label.ifBlank { "Untitled record" },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            verb(m.change) + " · " + moduleLabel(m.key) + " · " + ago(m.at) +
                                if (m.remote) " · another device" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    when (m.change) {
                        Change.UPDATE -> m.before.keys.forEach { f ->
                            Text(
                                f,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(pretty(m.before[f].orEmpty()), style = MaterialTheme.typography.bodySmall)
                            Text(
                                "→ " + pretty(m.after[f].orEmpty()),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        Change.DELETE -> Text(
                            "The whole record is kept in the trash tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Change.CREATE -> Text(
                            "Created here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (m.reversible) {
                        TextButton({
                            onChange(
                                if (History.undo(m)) "Put back"
                                else "That record has changed too much to undo",
                            )
                        }) { Text(if (m.change == Change.DELETE) "Restore" else "Undo this change") }
                    } else {
                        Text(
                            "Too large to store in full, so it can't be undone from here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    RecordHistory(m)
                }
            }
        }
    }
}

// Everything else that ever happened to the record an entry belongs to. Dropped into the
// expanded row here; it takes only a key and an id, so a module screen can put the same
// thing behind a "history" affordance on one of its own rows without new plumbing.
@Composable
fun RecordHistory(of: Mutation) {
    val rest = remember(of.seq) { History.historyOf(of.key, of.rec).filter { it.seq != of.seq } }
    if (rest.isEmpty()) return

    Spacer(Modifier.height(6.dp))
    Text(
        if (rest.size == 1) "1 earlier change to this record" else "${rest.size} earlier changes to this record",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    rest.take(8).forEach { e ->
        Text(
            mark(e.change) + "  " + verb(e.change).lowercase() + " " + ago(e.at) +
                if (e.change == Change.UPDATE && e.before.isNotEmpty()) {
                    " · " + e.before.keys.joinToString(", ")
                } else {
                    ""
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
