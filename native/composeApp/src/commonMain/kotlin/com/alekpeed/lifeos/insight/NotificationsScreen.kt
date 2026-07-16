package com.alekpeed.lifeos.insight

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
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.platform.Native

// Reminders that become real device notifications. Adding one posts an actionable
// notification (Done / Snooze on Android) and saves it to the list. Any item can be
// pinned as the ongoing "next up" ticker. On desktop the notification buttons are
// hidden; the list still persists.
@Composable
fun NotificationsScreen() {
    val items = remember {
        mutableStateListOf<String>().apply {
            addAll(Storage.read("Notifications")?.lines()?.filter { it.isNotBlank() } ?: emptyList())
        }
    }
    fun persist() = Storage.write("Notifications", items.joinToString("\n"))
    var input by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Notifications", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Remind me to…") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim().replace("\n", " ")
                if (t.isNotEmpty()) {
                    items.add(0, t)
                    persist()
                    if (Native.supportsNotifications) Native.postReminder("Reminder", t)
                    input = ""
                }
            }) { Text("Add") }
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (Native.supportsNotifications) {
                        TextButton(onClick = { pinned = item; Native.setPinnedNextUp(item) }) { Text("Pin") }
                    }
                    TextButton(onClick = { if (index < items.size) { items.removeAt(index); persist() } }) { Text("✕") }
                }
            }
        }
    }
}
