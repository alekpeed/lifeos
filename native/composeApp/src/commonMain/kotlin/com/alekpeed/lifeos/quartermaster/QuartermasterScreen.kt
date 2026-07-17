package com.alekpeed.lifeos.quartermaster

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.ui.SaveToast

private val DANGER = Color(0xFFD64545)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuartermasterScreen() {
    var data by remember { mutableStateOf(loadInventory()) }
    var counter by remember { mutableStateOf(data.items.maxOfOrNull { it.id } ?: 0L) }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: QuartermasterData) { data = d; saveInventory(d); SaveToast.show() }

    var name by remember { mutableStateOf("") }
    var loc by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    val lentOut = data.items.filter { it.lentTo.isNotBlank() }
    val onHand = data.items.filter { it.lentTo.isBlank() }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Quartermaster", style = MaterialTheme.typography.headlineMedium)
        Text("Your physical inventory, and who has what right now.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Item name") })
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(loc, { loc = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Location") })
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(tags, { tags = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Tags") })
            Spacer(Modifier.width(6.dp))
            Button(onClick = {
                val n = name.trim().replace("\n", " ")
                if (n.isNotEmpty()) {
                    val t = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    save(data.copy(items = data.items + InventoryItem(freshId(), n, loc.trim(), t)))
                    name = ""; loc = ""; tags = ""
                }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lentOut.isNotEmpty()) {
                item { SectionLabel("Lent out (${lentOut.size})") }
                items(lentOut, key = { it.id }) { ItemCard(data, ::save, it) }
            }
            item { SectionLabel("On hand (${onHand.size})") }
            if (onHand.isEmpty()) {
                item { Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(onHand, key = { it.id }) { ItemCard(data, ::save, it) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemCard(data: QuartermasterData, save: (QuartermasterData) -> Unit, item: InventoryItem) {
    fun patch(f: (InventoryItem) -> InventoryItem) = save(data.copy(items = data.items.map { if (it.id == item.id) f(it) else it }))
    var lendTo by remember(item.id) { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.name.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { save(data.copy(items = data.items.filterNot { it.id == item.id })) }) { Text("×") }
        }
        val chips = buildList {
            if (item.location.isNotBlank()) add("📍 ${item.location}")
            item.tags.forEach { add("#$it") }
        }
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (item.lentTo.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lent to ${item.lentTo} since ${item.lentSince}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { patch { it.copy(lentTo = "", lentSince = "") } }) { Text("Mark returned") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(lendTo, { lendTo = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Lend to…") })
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    val who = lendTo.trim().replace("\n", " ")
                    if (who.isNotEmpty()) { patch { it.copy(lentTo = who, lentSince = today().toString()) }; lendTo = "" }
                }) { Text("Lend it out") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}
