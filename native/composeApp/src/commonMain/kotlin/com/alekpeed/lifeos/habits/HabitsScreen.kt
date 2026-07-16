package com.alekpeed.lifeos.habits

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

private data class Habit(val name: String, val streak: Int)

private fun load(): List<Habit> =
    Storage.read("Habits")?.lines()?.filter { it.isNotBlank() }?.map { line ->
        val parts = line.split("|")
        Habit(parts.getOrElse(0) { line }, parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0)
    } ?: listOf(Habit("Drink water", 0), Habit("Move 30 min", 0))

// Real module: track habits with a running streak; check in to bump it. Persists.
@Composable
fun HabitsScreen() {
    val habits = remember { mutableStateListOf<Habit>().apply { addAll(load()) } }
    fun persist() = Storage.write("Habits", habits.joinToString("\n") { "${it.name}|${it.streak}" })
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Habits", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("New habit") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim()
                if (t.isNotEmpty()) {
                    habits.add(Habit(t, 0))
                    persist()
                    input = ""
                }
            }) { Text("Add") }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(habits) { index, habit ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🔥 ${habit.streak}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(12.dp))
                    Text(habit.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = {
                        if (index < habits.size) {
                            habits[index] = habit.copy(streak = habit.streak + 1)
                            persist()
                        }
                    }) { Text("Check in") }
                    TextButton(onClick = {
                        if (index < habits.size) {
                            habits.removeAt(index)
                            persist()
                        }
                    }) { Text("✕") }
                }
            }
        }
    }
}
