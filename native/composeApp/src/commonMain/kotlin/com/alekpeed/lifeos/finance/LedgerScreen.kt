package com.alekpeed.lifeos.finance

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.epochMillisAt
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import kotlin.math.abs

private val CATEGORIES = listOf("General", "Bills", "Food", "Transport", "Fun", "Income")

private data class Entry(val desc: String, val amount: Double, val category: String, val recurring: Boolean)

private fun fmt(amount: Double): String {
    val sign = if (amount < 0) "-" else ""
    val cents = (abs(amount) * 100).toLong()
    return "$sign$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

private fun load(): List<Entry> =
    Storage.read("Finance")?.lines()?.filter { it.isNotBlank() }?.map { line ->
        val p = line.split("\t")
        Entry(
            desc = p.getOrElse(0) { line },
            amount = p.getOrElse(1) { "0" }.toDoubleOrNull() ?: 0.0,
            category = p.getOrElse(2) { "General" }.ifBlank { "General" },
            recurring = p.getOrElse(3) { "0" } == "1",
        )
    } ?: emptyList()

private fun save(entries: List<Entry>) {
    Storage.write("Finance", entries.joinToString("\n") { "${it.desc}\t${it.amount}\t${it.category}\t${if (it.recurring) 1 else 0}" })
}

// A money ledger with categories and a recurring flag. Balance up top, then a
// per-category breakdown computed live. Marking an entry recurring schedules a
// real reminder ~30 days out (Android) — a genuine nudge, not fabricated future
// data. Negative amounts are spending.
@Composable
fun LedgerScreen() {
    val entries = remember { mutableStateListOf<Entry>().apply { addAll(load()) } }
    fun persist() = save(entries)
    var desc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var recurring by remember { mutableStateOf(false) }

    val balance = entries.sumOf { it.amount }
    val byCategory = entries.groupBy { it.category }
        .map { (cat, es) -> cat to es.sumOf { it.amount } }
        .sortedByDescending { abs(it.second) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Finance", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Balance ${fmt(balance)}",
            style = MaterialTheme.typography.titleLarge,
            color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = desc,
            onValueChange = { desc = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("What was it?") },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("Amount (– to spend)") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val d = desc.trim().replace("\t", " ").replace("\n", " ")
                val a = amount.trim().toDoubleOrNull()
                if (d.isNotEmpty() && a != null) {
                    val e = Entry(d, a, category, recurring)
                    entries.add(0, e)
                    persist()
                    if (recurring && Native.supportsNotifications) {
                        Native.scheduleReminder(
                            id = (d + "recur").hashCode(),
                            title = "Recurring: $d",
                            body = "Due again — ${fmt(a)}",
                            atEpochMillis = epochMillisAt(today().plusDays(30), 9, 0),
                        )
                    }
                    desc = ""
                    amount = ""
                    recurring = false
                }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CATEGORIES.take(3).forEach { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CATEGORIES.drop(3).forEach { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
            }
            FilterChip(selected = recurring, onClick = { recurring = !recurring }, label = { Text("↻ Recurring") })
        }

        if (byCategory.size > 1) {
            Spacer(Modifier.height(14.dp))
            Text("BY CATEGORY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            byCategory.forEach { (cat, total) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(cat, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        fmt(total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (total < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(entries) { index, e ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.desc, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (e.recurring) "${e.category} · ↻" else e.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        fmt(e.amount),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (e.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = {
                        if (index < entries.size) {
                            if (e.recurring && Native.supportsNotifications) Native.cancelReminder((e.desc + "recur").hashCode())
                            entries.removeAt(index)
                            persist()
                        }
                    }) { Text("✕") }
                }
            }
        }
    }
}
