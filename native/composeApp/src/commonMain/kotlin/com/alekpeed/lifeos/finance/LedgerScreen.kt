package com.alekpeed.lifeos.finance

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.epochMillisAt
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

private val CATEGORIES = listOf("General", "Bills", "Food", "Transport", "Fun", "Income")

@Serializable
private data class Entry(
    val id: Long,
    val desc: String,
    val amount: Double,
    val category: String = "General",
    val recurring: Boolean = false,
    val date: String = "",
)

@Serializable
private data class FinanceData(val entries: List<Entry> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun fmt(amount: Double): String {
    val sign = if (amount < 0) "-" else ""
    val cents = (abs(amount) * 100).toLong()
    return "$sign$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

private fun load(): List<Entry> {
    val raw = Storage.read("Finance")
    if (raw.isNullOrBlank()) return emptyList()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<FinanceData>(raw).entries }.getOrElse { emptyList() }
    }
    // Migrate old "desc\tamount\tcategory\trecurring" lines (undated).
    return raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val p = line.split("\t")
        Entry(
            id = i + 1L,
            desc = p.getOrElse(0) { line },
            amount = p.getOrElse(1) { "0" }.toDoubleOrNull() ?: 0.0,
            category = p.getOrElse(2) { "General" }.ifBlank { "General" },
            recurring = p.getOrElse(3) { "0" } == "1",
        )
    }
}

private fun save(entries: List<Entry>) {
    Storage.write("Finance", json.encodeToString(FinanceData(entries)))
}

// Public read-only accessor for the stats layer (The Almanac): each entry as
// (amount, category, recurring, date) without leaking the private model.
data class FinancePoint(val desc: String, val amount: Double, val category: String, val recurring: Boolean, val date: String)
fun financeSeries(): List<FinancePoint> = load().map { FinancePoint(it.desc, it.amount, it.category, it.recurring, it.date) }

// A money ledger with categories, dates, and a recurring flag. All-time balance
// and a live this-month income / spending / net summary up top, then a
// per-category breakdown. Marking an entry recurring schedules a real reminder
// ~30 days out (Android). Negative amounts are spending.
@Composable
fun LedgerScreen() {
    var entries by remember { mutableStateOf(load()) }
    fun persist(next: List<Entry>) { entries = next; save(next) }
    var nextId by remember { mutableStateOf((entries.maxOfOrNull { it.id } ?: 0L) + 1) }
    var desc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var recurring by remember { mutableStateOf(false) }

    val balance = entries.sumOf { it.amount }
    val month = today().toString().take(7)
    val monthEntries = entries.filter { it.date.startsWith(month) }
    val monthIncome = monthEntries.filter { it.amount > 0 }.sumOf { it.amount }
    val monthSpend = monthEntries.filter { it.amount < 0 }.sumOf { it.amount }
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
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        ) {
            MonthStat("This month in", fmt(monthIncome), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            MonthStat("Out", fmt(monthSpend), MaterialTheme.colorScheme.error, Modifier.weight(1f))
            MonthStat("Net", fmt(monthIncome + monthSpend), if (monthIncome + monthSpend < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(desc, { desc = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("What was it?") })
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                amount, { amount = it }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Amount (– to spend)") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val d = desc.trim().replace("\n", " ")
                val a = amount.trim().toDoubleOrNull()
                if (d.isNotEmpty() && a != null) {
                    val e = Entry(nextId, d, a, category, recurring, today().toString())
                    nextId += 1
                    persist(listOf(e) + entries)
                    if (recurring && Native.supportsNotifications) {
                        Native.scheduleReminder(
                            id = (d + "recur").hashCode(),
                            title = "Recurring: $d",
                            body = "Due again — ${fmt(a)}",
                            atEpochMillis = epochMillisAt(today().plusDays(30), 9, 0),
                        )
                    }
                    desc = ""; amount = ""; recurring = false
                }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CATEGORIES.take(3).forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CATEGORIES.drop(3).forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
            FilterChip(selected = recurring, onClick = { recurring = !recurring }, label = { Text("↻ Recurring") })
        }

        if (byCategory.size > 1) {
            Spacer(Modifier.height(14.dp))
            Text("BY CATEGORY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            byCategory.forEach { (cat, total) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(cat, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(fmt(total), style = MaterialTheme.typography.bodyMedium, color = if (total < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(entries, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.desc, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(e.category, if (e.recurring) "↻" else null, e.date.ifBlank { null }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(fmt(e.amount), style = MaterialTheme.typography.bodyLarge, color = if (e.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    TextButton(onClick = {
                        if (e.recurring && Native.supportsNotifications) Native.cancelReminder((e.desc + "recur").hashCode())
                        persist(entries.filterNot { it.id == e.id })
                    }) { Text("✕") }
                }
            }
        }
    }
}

@Composable
private fun MonthStat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}
