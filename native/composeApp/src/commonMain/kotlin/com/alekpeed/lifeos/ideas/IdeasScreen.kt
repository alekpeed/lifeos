package com.alekpeed.lifeos.ideas

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
import com.alekpeed.lifeos.ui.SaveToast

// One idea per line. Ids are reassigned on load — unique-within-session is enough.
private fun loadIdeas(): List<Idea> =
    Storage.read("Ideas")?.lines()?.filter { it.isNotBlank() }?.mapIndexed { i, line -> Idea(i + 1L, line) }
        ?: listOf(Idea(1, "Ideas land here"), Idea(2, "Jot anything, tidy it later"))

// Free-form capture, native on both platforms. Persists locally on every change.
@Composable
fun IdeasScreen() {
    val ideas = remember { mutableStateListOf<Idea>().apply { addAll(loadIdeas()) } }
    fun persist() { Storage.write("Ideas", ideas.joinToString("\n") { it.text }); SaveToast.show() }
    var input by remember { mutableStateOf("") }
    var nextId by remember { mutableStateOf((ideas.maxOfOrNull { it.id } ?: 0L) + 1) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Ideas", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("New idea") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim().replace("\n", " ")
                if (t.isNotEmpty()) {
                    ideas.add(Idea(nextId, t))
                    nextId += 1
                    persist()
                    input = ""
                }
            }) { Text("Add") }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(ideas, key = { it.id }) { idea ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(idea.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = {
                        val i = ideas.indexOfFirst { it.id == idea.id }
                        if (i >= 0) { ideas.removeAt(i); persist() }
                    }) { Text("✕") }
                }
            }
        }
    }
}
