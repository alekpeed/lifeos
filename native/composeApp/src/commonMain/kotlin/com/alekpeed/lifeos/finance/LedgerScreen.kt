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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage
import kotlin.math.abs

private data class Entry(val desc: String, val amount: Double)

private fun fmt(amount: Double): String {
    val sign = if (amount < 0) "-" else ""
    val cents = (abs(amount) * 100).toLong()
    return "$sign$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

// A manual money ledger: label an entry, give it an amount (negative for spending),
// see a running balance. Genuinely functional and persistent today; the Plaid
// account sync from the web app layers on later without changing this screen.
@Composable
fun LedgerScreen() {
    val entries = remember {
        val saved = Storage.read("Finance")?.lines()?.filter { it.isNotBlank() }?.map { line ->
            val parts = line.split("\t", limit = 2)
            Entry(parts.getOrElse(0) { line }, parts.getOrElse(1) { "0" }.toDoubleOrNull() ?: 0.0)
        }
        mutableStateListOf<Entry>().apply { addAll(saved ?: emptyList()) }
    }
    fun persist() = Storage.write("Finance", entries.joinToString("\n") { "${it.desc}\t${it.amount}" })
    var desc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val balance = entries.sumOf { it.amount }

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
                    entries.add(0, Entry(d, a))
                    persist()
                    desc = ""
                    amount = ""
                }
            }) { Text("Add") }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(entries) { index, e ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(e.desc, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        fmt(e.amount),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (e.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { if (index < entries.size) { entries.removeAt(index); persist() } }) { Text("✕") }
                }
            }
        }
    }
}
