package com.alekpeed.lifeos.habits

import androidx.compose.foundation.background
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

// Habits with a real daily-reset streak (derived from actual check-in days, not a
// counter you can inflate by tapping twice) and a 7-day history strip. "Check in"
// is a no-op once you've already checked in today; missing a day genuinely breaks
// the streak the next time you check in. Persists.
@Composable
fun HabitsScreen() {
    val habits = remember { mutableStateListOf<Habit>().apply { addAll(loadHabits()) } }
    fun persist() = saveHabits(habits)
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🔥 ${habit.streak}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(12.dp))
                        Text(habit.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        TextButton(
                            enabled = !habit.checkedInToday,
                            onClick = {
                                if (index < habits.size) {
                                    habits[index] = habit.copy(checkins = habit.checkins + today())
                                    persist()
                                }
                            },
                        ) { Text(if (habit.checkedInToday) "✓ Done today" else "Check in") }
                        TextButton(onClick = {
                            if (index < habits.size) {
                                habits.removeAt(index)
                                persist()
                            }
                        }) { Text("✕") }
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
                }
            }
        }
    }
}
