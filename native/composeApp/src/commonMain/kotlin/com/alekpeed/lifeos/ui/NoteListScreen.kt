package com.alekpeed.lifeos.ui

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

private data class NoteItem(val title: String, val note: String)

// A reusable add / list / delete screen where each item has a title plus a short
// secondary note (ingredients, a reference, a date, an open-on). Persists as
// "<title>\t<note>" per line, native on Android + Windows.
@Composable
fun NoteListScreen(
    title: String,
    titlePlaceholder: String,
    notePlaceholder: String,
    seed: List<Pair<String, String>> = emptyList(),
) {
    val items = remember {
        val saved = Storage.read(title)?.lines()?.filter { it.isNotBlank() }?.map { line ->
            val parts = line.split("\t", limit = 2)
            NoteItem(parts.getOrElse(0) { line }, parts.getOrElse(1) { "" })
        }
        mutableStateListOf<NoteItem>().apply {
            addAll(saved ?: seed.map { NoteItem(it.first, it.second) })
        }
    }
    fun persist() = Storage.write(title, items.joinToString("\n") { "${it.title}\t${it.note}" })
    var titleInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = titleInput,
            onValueChange = { titleInput = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(titlePlaceholder) },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(notePlaceholder) },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = titleInput.trim().replace("\t", " ").replace("\n", " ")
                val n = noteInput.trim().replace("\t", " ").replace("\n", " ")
                if (t.isNotEmpty()) {
                    items.add(NoteItem(t, n))
                    persist()
                    titleInput = ""
                    noteInput = ""
                }
            }) { Text("Add") }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(items) { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        if (item.note.isNotBlank()) {
                            Text(
                                item.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { if (index < items.size) { items.removeAt(index); persist() } }) { Text("✕") }
                }
            }
        }
    }
}
