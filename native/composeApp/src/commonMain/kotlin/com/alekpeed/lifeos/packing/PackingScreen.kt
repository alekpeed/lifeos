package com.alekpeed.lifeos.packing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
fun PackingScreen() {
    var data by remember { mutableStateOf(loadPacking()) }
    var counter by remember {
        mutableStateOf(
            maxOf(
                data.lists.maxOfOrNull { it.id } ?: 0L,
                data.lists.flatMap { it.items }.maxOfOrNull { it.id } ?: 0L,
            ),
        )
    }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: PackingData) { data = d; savePacking(d) }

    var openId by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        val open = data.lists.firstOrNull { it.id == openId }
        if (open != null) {
            ListDetail(data, ::save, ::freshId, open) { openId = null }
        } else {
            ListsOverview(data, ::save, ::freshId) { openId = it }
        }
    }
}

@Composable
private fun ListsOverview(data: PackingData, save: (PackingData) -> Unit, freshId: () -> Long, onOpen: (Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    Text("Trip Packing Lists", style = MaterialTheme.typography.headlineMedium)
    Text("One checklist per trip, with templates to get started fast.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Trip name (e.g. Tokyo)") })
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(date, { date = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Trip date (YYYY-MM-DD)") })
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            val n = name.trim().replace("\n", " ")
            if (n.isNotEmpty()) {
                val id = freshId()
                save(data.copy(lists = data.lists + PackingList(id, n, date.trim())))
                name = ""; date = ""; onOpen(id)
            }
        }) { Text("Create") }
    }
    Spacer(Modifier.height(14.dp))

    Text("Your trips (${data.lists.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    if (data.lists.isEmpty()) { Text("No trips yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(data.lists, key = { it.id }) { list ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpen(list.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(list.name.ifBlank { "(untitled trip)" }, style = MaterialTheme.typography.bodyLarge)
                    val packed = list.items.count { it.packed }
                    Text(
                        listOf(list.tripDate.ifBlank { null }, "$packed/${list.items.size} packed").filterNotNull().joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = { save(data.copy(lists = data.lists.filterNot { it.id == list.id })) }) { Text("×") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListDetail(data: PackingData, save: (PackingData) -> Unit, freshId: () -> Long, list: PackingList, onBack: () -> Unit) {
    fun patch(f: (PackingList) -> PackingList) = save(data.copy(lists = data.lists.map { if (it.id == list.id) f(it) else it }))
    var itemName by remember { mutableStateOf("") }
    var itemCat by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("← Trips") }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(list.name.ifBlank { "(untitled trip)" }, style = MaterialTheme.typography.titleLarge)
            if (list.tripDate.isNotBlank()) Text(list.tripDate, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(10.dp))

    Text("Add from a template", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PACKING_TEMPLATES.forEach { (label, groups) ->
            AssistChip(onClick = {
                var next = list.items
                var id = counterSeed(next)
                groups.forEach { (cat, names) ->
                    names.forEach { nm ->
                        id += 1
                        next = next + PackItem(id, nm, cat)
                    }
                }
                patch { it.copy(items = next) }
            }, label = { Text(label) })
        }
    }
    Spacer(Modifier.height(10.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(itemName, { itemName = it }, modifier = Modifier.weight(2f), singleLine = true, placeholder = { Text("Add an item") })
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(itemCat, { itemCat = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Category") })
        Spacer(Modifier.width(6.dp))
        Button(onClick = {
            val n = itemName.trim().replace("\n", " ")
            if (n.isNotEmpty()) {
                patch { it.copy(items = it.items + PackItem(freshId(), n, itemCat.trim().ifBlank { "Other" })) }
                itemName = ""; itemCat = ""
            }
        }) { Text("Add") }
    }
    Spacer(Modifier.height(12.dp))

    val packed = list.items.count { it.packed }
    Text("Packed $packed / ${list.items.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))

    if (list.items.isEmpty()) {
        Text("Nothing on this list yet — add items or use a template above.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val grouped = list.items.groupBy { it.category.ifBlank { "Other" } }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        grouped.forEach { (cat, catItems) ->
            item { Text(cat, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            items(catItems, key = { it.id }) { it2 ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = it2.packed, onCheckedChange = { c ->
                        patch { l -> l.copy(items = l.items.map { if (it.id == it2.id) it.copy(packed = c) else it }) }
                    })
                    Text(
                        it2.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                        textDecoration = if (it2.packed) TextDecoration.LineThrough else null,
                    )
                    TextButton(onClick = { patch { l -> l.copy(items = l.items.filterNot { it.id == it2.id }) } }) { Text("×") }
                }
            }
        }
    }
}

// Largest existing item id in a list, so template bulk-adds get fresh ids
// without threading the screen's counter through the template loop.
private fun counterSeed(items: List<PackItem>): Long =
    (items.maxOfOrNull { it.id } ?: 0L) + 1_000_000L
