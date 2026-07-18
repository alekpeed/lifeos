package com.alekpeed.lifeos.ideas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.ui.SaveToast

// Free-form capture, native on both platforms — now with tags, a tag filter, and
// an archive so a jotted idea can be tidied away without deleting it.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdeasScreen() {
    var data by remember { mutableStateOf(loadIdeas()) }
    fun save(d: IdeasData) { data = d; saveIdeas(d); SaveToast.show() }
    var nextId by remember { mutableStateOf((data.ideas.maxOfOrNull { it.id } ?: 0L) + 1) }

    var input by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    val allTags = data.ideas.filterNot { it.archived }.flatMap { it.tags }.distinct().sorted()
    val visible = data.ideas
        .filter { it.archived == showArchived }
        .filter { tagFilter == null || tagFilter in it.tags }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Ideas", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(input, { input = it }, modifier = Modifier.fillMaxWidth(), singleLine = false, placeholder = { Text("New idea") })
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(tagInput, { tagInput = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Tags (comma-separated)") })
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim()
                if (t.isNotEmpty()) {
                    val tags = tagInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    save(data.copy(ideas = data.ideas + Idea(nextId, t, tags, false, today().toString())))
                    nextId += 1
                    input = ""; tagInput = ""
                }
            }) { Text("Add") }
        }

        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (allTags.isNotEmpty()) {
                FilterChip(selected = tagFilter == null, onClick = { tagFilter = null }, label = { Text("All") })
                allTags.forEach { tag ->
                    FilterChip(selected = tagFilter == tag, onClick = { tagFilter = if (tagFilter == tag) null else tag }, label = { Text("#$tag") })
                }
            }
            val archivedCount = data.ideas.count { it.archived }
            FilterChip(
                selected = showArchived,
                onClick = { showArchived = !showArchived; tagFilter = null },
                label = { Text(if (showArchived) "Archived ($archivedCount)" else "Show archived ($archivedCount)") },
            )
        }

        Spacer(Modifier.height(10.dp))

        if (visible.isEmpty()) {
            Text(
                if (showArchived) "Nothing archived." else "No ideas yet — jot one above.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visible, key = { it.id }) { idea ->
                fun patch(f: (Idea) -> Idea) = save(data.copy(ideas = data.ideas.map { if (it.id == idea.id) f(it) else it }))
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(idea.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        // Promote → a real Task (idea text + tags carry over); the
                        // idea archives so it doesn't linger in both piles.
                        if (!idea.archived) {
                            TextButton(onClick = {
                                val tasks = com.alekpeed.lifeos.tasks.loadTasks()
                                val tid = (tasks.maxOfOrNull { it.id } ?: 0L) + 1
                                com.alekpeed.lifeos.tasks.saveTasks(
                                    tasks + com.alekpeed.lifeos.tasks.Task(tid, idea.text.replace("\n", " "), tags = idea.tags),
                                )
                                patch { it.copy(archived = true) }
                            }) { Text("→ Task") }
                        }
                        TextButton(onClick = { patch { it.copy(archived = !it.archived) } }) {
                            Text(if (idea.archived) "Restore" else "Archive")
                        }
                        TextButton(onClick = { save(data.copy(ideas = data.ideas.filterNot { it.id == idea.id })) }) { Text("✕") }
                    }
                    if (idea.tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            idea.tags.forEach { Text("#$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        }
    }
}
