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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.epochMillisAt
import com.alekpeed.lifeos.data.formatEpochMillis
import com.alekpeed.lifeos.data.nextClockTime
import com.alekpeed.lifeos.data.nowPlusHours
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.ExpiryState
import com.alekpeed.lifeos.documents.expiryState
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.education.loadEducation
import com.alekpeed.lifeos.finance.financeBills
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.ui.SaveToast

// One row of the cross-module attention feed — what needs you, from where.
private data class Attention(
    val icon: String,
    val title: String,
    val meta: String,
    val moduleId: String,
    val urgent: Boolean,
    val sortKey: String,
)

// The web Notifications view's aggregated feed: overdue / due-soon tasks, unpaid
// bills, open assignments, and expiring documents, each tappable to jump to its
// module. Assembled fresh on every open — no polling, no stored state.
private fun buildAttention(): List<Attention> {
    val now = today()
    val soon = now.plusDays(7)
    val out = mutableListOf<Attention>()

    loadTasks().filter { !it.done }.forEach { t ->
        val due = t.dueDate() ?: return@forEach
        val snoozed = t.snoozeDate()?.let { it > now } == true
        if (snoozed || due > soon) return@forEach
        out.add(Attention("✅", t.title, if (due < now) relativeLabel(due) else "due ${relativeLabel(due)}", "tasks", due < now, due.toString()))
    }
    financeBills().filter { !it.settled }.forEach { b ->
        val due = parseDateOrNull(b.dueDate) ?: return@forEach
        if (due > soon) return@forEach
        val label = (if (due < now) relativeLabel(due) else "due ${relativeLabel(due)}") + if (b.autopay) " · autopay" else ""
        out.add(Attention("💵", b.name, label, "finance", due < now && !b.autopay, due.toString()))
    }
    loadEducation().assignments.filter { !it.done }.forEach { a ->
        val due = parseDateOrNull(a.dueDate) ?: return@forEach
        if (due > soon) return@forEach
        out.add(Attention("🎓", a.title, if (due < now) relativeLabel(due) else "due ${relativeLabel(due)}", "education", due < now, due.toString()))
    }
    loadDocuments().documents.forEach { d ->
        when (expiryState(d)) {
            ExpiryState.EXPIRED -> out.add(Attention("📄", d.title, "expired", "documents", true, d.expiryDate))
            ExpiryState.SOON -> out.add(Attention("📄", d.title, "expires ${parseDateOrNull(d.expiryDate)?.let { relativeLabel(it) } ?: d.expiryDate}", "documents", false, d.expiryDate))
            else -> {}
        }
    }
    return out.sortedWith(compareBy({ !it.urgent }, { it.sortKey }))
}

private data class Reminder(val text: String, val atEpochMillis: Long?)

private fun Reminder.id(): Int = (text + (atEpochMillis ?: 0L)).hashCode()

private fun loadReminders(): List<Reminder> =
    Storage.read("Notifications")?.lines()?.filter { it.isNotBlank() }?.map { line ->
        val parts = line.split("\t", limit = 2)
        Reminder(parts.getOrElse(0) { line }, parts.getOrNull(1)?.toLongOrNull())
    } ?: emptyList()

private fun save(items: List<Reminder>) {
    Storage.write("Notifications", items.joinToString("\n") { "${it.text}\t${it.atEpochMillis ?: ""}" })
}

// Reminders backed by real device scheduling: "Now" posts immediately, or pick a
// quick time and it fires later via AlarmManager, even if the app is closed
// (desktop: saved, but nothing fires — no scheduler there). Any item can also be
// pinned as the ongoing "next up" ticker.
@Composable
fun NotificationsScreen() {
    val items = remember { mutableStateListOf<Reminder>().apply { addAll(loadReminders()) } }
    fun persist() { save(items); SaveToast.show() }
    var input by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf<String?>(null) }

    fun addReminder(atEpochMillis: Long?) {
        val t = input.trim().replace("\t", " ").replace("\n", " ")
        if (t.isEmpty()) return
        val r = Reminder(t, atEpochMillis)
        items.add(0, r)
        persist()
        if (Native.supportsNotifications) {
            if (atEpochMillis == null) {
                Native.postReminder("Reminder", t)
            } else {
                Native.scheduleReminder(r.id(), "Reminder", t, atEpochMillis)
            }
        }
        input = ""
    }

    val attention = remember { buildAttention() }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Notifications", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        // Needs attention — tap a row to jump to its module.
        if (attention.isNotEmpty()) {
            Text("NEEDS ATTENTION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            attention.take(10).forEach { a ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { Nav.open(a.moduleId) }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(a.icon, modifier = Modifier.padding(end = 8.dp))
                    Text(a.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        a.meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (a.urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (attention.size > 10) {
                Text("+${attention.size - 10} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Remind me to…") },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { addReminder(null) }) { Text("Now") }
            if (Native.supportsNotifications) {
                AssistChip(onClick = { addReminder(nowPlusHours(1)) }, label = { Text("In 1h") })
                AssistChip(onClick = { addReminder(nextClockTime(18)) }, label = { Text("This evening") })
                AssistChip(onClick = { addReminder(epochMillisAt(today().plusDays(1), 9, 0)) }, label = { Text("Tomorrow AM") })
            }
        }

        if (pinned != null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("📌 Pinned: ${pinned}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { pinned = null; Native.setPinnedNextUp(null) }) { Text("Clear") }
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(items) { index, item ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (Native.supportsNotifications) {
                            TextButton(onClick = { pinned = item.text; Native.setPinnedNextUp(item.text) }) { Text("Pin") }
                        }
                        TextButton(onClick = {
                            if (index < items.size) {
                                if (Native.supportsNotifications) Native.cancelReminder(item.id())
                                items.removeAt(index)
                                persist()
                            }
                        }) { Text("✕") }
                    }
                    item.atEpochMillis?.let { millis ->
                        Text(
                            "⏰ ${formatEpochMillis(millis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
