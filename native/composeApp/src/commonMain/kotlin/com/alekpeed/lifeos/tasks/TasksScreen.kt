package com.alekpeed.lifeos.tasks

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage

// One task per line, stored as "<done>\t<title>" (done = 1/0). Ids are reassigned
// on load — they only need to be unique within a session for the list key.
private fun loadTasks(): List<Task> =
    Storage.read("Tasks")?.lines()?.filter { it.isNotBlank() }?.mapIndexed { i, line ->
        val parts = line.split("\t", limit = 2)
        Task(i + 1L, parts.getOrElse(1) { line }, parts.getOrElse(0) { "0" } == "1")
    } ?: listOf(
        Task(1, "This is a real native app now"),
        Task(2, "Add a task below"),
    )

// First real module, native on both Android and Windows: add tasks, check them
// off. Persists locally on every change, so tasks survive relaunch.
@Composable
fun TasksScreen() {
    val tasks = remember { mutableStateListOf<Task>().apply { addAll(loadTasks()) } }
    fun persist() = Storage.write("Tasks", tasks.joinToString("\n") { "${if (it.done) 1 else 0}\t${it.title}" })
    var input by remember { mutableStateOf("") }
    var nextId by remember { mutableStateOf((tasks.maxOfOrNull { it.id } ?: 0L) + 1) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Tasks", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("New task") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim().replace("\t", " ").replace("\n", " ")
                if (t.isNotEmpty()) {
                    tasks.add(Task(nextId, t))
                    nextId += 1
                    persist()
                    input = ""
                }
            }) { Text("Add") }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(tasks, key = { it.id }) { task ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = task.done,
                        onCheckedChange = { checked ->
                            val i = tasks.indexOfFirst { it.id == task.id }
                            if (i >= 0) { tasks[i] = task.copy(done = checked); persist() }
                        },
                    )
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
    }
}
