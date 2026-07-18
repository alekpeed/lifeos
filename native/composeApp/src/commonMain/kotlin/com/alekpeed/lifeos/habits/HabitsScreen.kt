package com.alekpeed.lifeos.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.minusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.ui.SaveToast

// Habits with a real daily-reset streak (derived from actual check-in days, not a
// counter you can inflate by tapping twice) and a 7-day history strip. "Check in"
// is a no-op once you've already checked in today; missing a day genuinely breaks
// the streak the next time you check in. Persists.
@Composable
fun HabitsScreen() {
    val habits = remember { mutableStateListOf<Habit>().apply { addAll(loadHabits()) } }
    fun persist() { saveHabits(habits); SaveToast.show() }
    var input by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }

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
                    habits.add(Habit(t, emptySet()))
                    persist()
                    input = ""
                }
            }) { Text("Add") }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(habits) { index, habit ->
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded = if (expanded == habit.name) null else habit.name },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🔥 ${habit.streak}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(12.dp))
                        Text(habit.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        // Checking in twice still can't inflate the streak — but a
                        // mis-tap can now be UNDONE by tapping again today.
                        TextButton(
                            onClick = {
                                if (index < habits.size) {
                                    habits[index] = if (habit.checkedInToday) {
                                        habit.copy(checkins = habit.checkins - today())
                                    } else {
                                        habit.copy(checkins = habit.checkins + today())
                                    }
                                    persist()
                                }
                            },
                        ) { Text(if (habit.checkedInToday) "✓ Done (undo)" else "Check in") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (i in 6 downTo 0) {
                            val day = today().minusDays(i)
                            val on = day in habit.checkins
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (on) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                            )
                        }
                    }
                    if (expanded == habit.name) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 8.dp)
                                .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                        ) {
                            Text("Name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                habit.name,
                                { v ->
                                    val clean = v.replace("|", "/").replace("\n", " ")
                                    if (index < habits.size) { habits[index] = habit.copy(name = clean); expanded = clean; persist() }
                                },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("Notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                habit.notes,
                                { v -> if (index < habits.size) { habits[index] = habit.copy(notes = v); persist() } },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Why / how / when") },
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${habit.checkins.size} check-in${if (habit.checkins.size == 1) "" else "s"} all-time" +
                                    (habit.lastCheckIn?.let { " · last $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = {
                                if (index < habits.size) { habits.removeAt(index); expanded = null; persist() }
                            }) { Text("Delete habit", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}
