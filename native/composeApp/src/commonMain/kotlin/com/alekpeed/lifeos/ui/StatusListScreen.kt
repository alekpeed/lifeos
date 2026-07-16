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
import com.alekpeed.lifeos.Storage

private data class StatusItem(val text: String, val status: Int)

// A reusable add / list / delete screen where each item carries a status that
// cycles through the supplied labels when tapped (e.g. Want → Reading → Read).
// Persists as "<text>\t<statusIndex>" per line, native on Android + Windows.
@Composable
fun StatusListScreen(
    title: String,
    placeholder: String,
    statuses: List<String>,
    seed: List<Pair<String, Int>> = emptyList(),
) {
    val items = remember {
        val saved = Storage.read(title)?.lines()?.filter { it.isNotBlank() }?.map { line ->
            val parts = line.split("\t", limit = 2)
            StatusItem(parts.getOrElse(0) { line }, parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0)
        }
        mutableStateListOf<StatusItem>().apply {
            addAll(saved ?: seed.map { StatusItem(it.first, it.second) })
        }
    }
    fun persist() = Storage.write(title, items.joinToString("\n") { "${it.text}\t${it.status}" })
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(placeholder) },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim().replace("\t", " ").replace("\n", " ")
                if (t.isNotEmpty()) {
                    items.add(StatusItem(t, 0))
                    persist()
                    input = ""
                }
            }) { Text("Add") }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(items) { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    AssistChip(
                        onClick = {
                            if (index < items.size) {
                                val next = (item.status + 1) % statuses.size
                                items[index] = item.copy(status = next)
                                persist()
                            }
                        },
                        label = { Text(statuses.getOrElse(item.status) { statuses.first() }) },
                    )
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { if (index < items.size) { items.removeAt(index); persist() } }) { Text("✕") }
                }
            }
        }
    }
}
