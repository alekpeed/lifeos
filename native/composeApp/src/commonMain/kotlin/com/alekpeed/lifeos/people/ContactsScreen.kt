package com.alekpeed.lifeos.people

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
import androidx.compose.material3.OutlinedButton
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

// Contacts, with one-tap import from the phone's address book (Android). Imported
// names merge into the same list you can add to by hand, de-duplicated. On desktop
// the import button is hidden (no address book), the manual list still works.
@Composable
fun ContactsScreen() {
    val items = remember {
        mutableStateListOf<String>().apply {
            addAll(Storage.read("Contacts")?.lines()?.filter { it.isNotBlank() } ?: emptyList())
        }
    }
    fun persist() = Storage.write("Contacts", items.joinToString("\n"))
    var input by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Contacts", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("New contact") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim().replace("\n", " ")
                if (t.isNotEmpty() && items.none { it.equals(t, ignoreCase = true) }) {
                    items.add(t)
                    persist()
                    input = ""
                }
            }) { Text("Add") }
        }

        if (Native.supportsContacts) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                val before = items.size
                Native.importContacts().forEach { c ->
                    val label = if (c.detail.isBlank()) c.name else "${c.name} — ${c.detail}"
                    if (items.none { it.equals(label, ignoreCase = true) || it.startsWith("${c.name} —") }) {
                        items.add(label)
                    }
                }
                if (items.size != before) persist()
                note = if (items.size == before) "No new contacts (grant permission if this keeps happening)" else "Imported ${items.size - before} contact(s)"
            }) { Text("📇 Import from phone") }
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    TextButton(onClick = { if (index < items.size) { items.removeAt(index); persist() } }) { Text("✕") }
                }
            }
        }
    }
}
