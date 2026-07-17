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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.ui.SaveToast

// Real spaced repetition: facts due today (or overdue) surface first with
// Know It / Forgot buttons that reschedule them along an interval ladder
// (1 → 3 → 7 → 14 → 30 → 90 days); everything else is listed as upcoming with its
// next-review date. Persists.
@Composable
fun RecallScreen() {
    val facts = remember { mutableStateListOf<Fact>().apply { addAll(loadFacts()) } }
    fun persist() { saveFacts(facts); SaveToast.show() }
    var input by remember { mutableStateOf("") }

    val due = facts.filter { it.isDue() }.sortedBy { it.nextReview }
    val upcoming = facts.filterNot { it.isDue() }.sortedBy { it.nextReview }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Recall", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("A fact to remember") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim().replace("\t", " ").replace("\n", " ")
                if (t.isNotEmpty()) {
                    facts.add(Fact(t, 1, today()))
                    persist()
                    input = ""
                }
            }) { Text("Add") }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (due.isNotEmpty()) {
                item {
                    Text("DUE (${due.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                items(due.size) { i ->
                    val fact = due[i]
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(fact.text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val idx = facts.indexOfFirst { it.text == fact.text && it.nextReview == fact.nextReview }
                                if (idx >= 0) { facts[idx] = fact.knowIt(); persist() }
                            }) { Text("Know it") }
                            OutlinedButton(onClick = {
                                val idx = facts.indexOfFirst { it.text == fact.text && it.nextReview == fact.nextReview }
                                if (idx >= 0) { facts[idx] = fact.forgot(); persist() }
                            }) { Text("Forgot") }
                        }
                    }
                }
            }
            if (upcoming.isNotEmpty()) {
                item {
                    Text(
                        "UPCOMING",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = if (due.isNotEmpty()) 6.dp else 0.dp),
                    )
                }
                items(upcoming.size) { i ->
                    val fact = upcoming[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(fact.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            relativeLabel(fact.nextReview),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (due.isEmpty() && upcoming.isEmpty()) {
                item {
                    Text(
                        "Add a fact to start reviewing it on a schedule.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
