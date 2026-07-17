package com.alekpeed.lifeos.collections

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
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.today

private val DANGER = Color(0xFFD64545)

@Composable
fun CollectionsScreen() {
    var data by remember { mutableStateOf(loadCollections()) }
    var counter by remember {
        mutableStateOf(maxOf(data.collections.maxOfOrNull { it.id } ?: 0L, data.collections.flatMap { it.items }.maxOfOrNull { it.id } ?: 0L))
    }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: CollectionsData) { data = d; saveCollections(d) }
    var openId by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        val open = data.collections.firstOrNull { it.id == openId }
        if (open != null) Detail(data, ::save, ::freshId, open) { openId = null }
        else Overview(data, ::save, ::freshId) { openId = it }
    }
}

@Composable
private fun Overview(data: CollectionsData, save: (CollectionsData) -> Unit, freshId: () -> Long, onOpen: (Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    Text("Collections", style = MaterialTheme.typography.headlineMedium)
    Text("Track any collection you keep — records, cards, whatever.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Collection name (e.g. Vinyl records)") })
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(desc, { desc = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Description (optional)") })
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            val n = name.trim().replace("\n", " ")
            if (n.isNotEmpty()) {
                val id = freshId(); save(data.copy(collections = data.collections + Collection(id, n, desc.trim())))
                name = ""; desc = ""; onOpen(id)
            }
        }) { Text("Create") }
    }
    Spacer(Modifier.height(14.dp))
    Text("Your collections (${data.collections.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    if (data.collections.isEmpty()) { Muted("No collections yet."); return }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(data.collections, key = { it.id }) { c ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpen(c.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(c.name.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge)
                    Text("${c.items.size} item${if (c.items.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { save(data.copy(collections = data.collections.filterNot { it.id == c.id })) }) { Text("×") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Detail(data: CollectionsData, save: (CollectionsData) -> Unit, freshId: () -> Long, coll: Collection, onBack: () -> Unit) {
    fun patch(f: (Collection) -> Collection) = save(data.copy(collections = data.collections.map { if (it.id == coll.id) f(it) else it }))
    var iName by remember { mutableStateOf("") }
    var iDate by remember { mutableStateOf("") }
    var iTags by remember { mutableStateOf("") }
    var iNotes by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("← Collections") }
        Spacer(Modifier.width(4.dp))
        Text(coll.name.ifBlank { "(untitled)" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
    if (coll.description.isNotBlank()) Text(coll.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(iName, { iName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Item name") })
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(iDate, { iDate = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Acquired (YYYY-MM-DD)") })
        Spacer(Modifier.width(6.dp))
        AssistChip(onClick = { iDate = today().toString() }, label = { Text("Today") })
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(iTags, { iTags = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Tags") })
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(iNotes, { iNotes = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Notes") })
        Spacer(Modifier.width(6.dp))
        Button(onClick = {
            val n = iName.trim().replace("\n", " ")
            if (n.isNotEmpty()) {
                val t = iTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                patch { it.copy(items = it.items + CollItem(freshId(), n, iDate.trim(), t, iNotes.trim())) }
                iName = ""; iDate = ""; iTags = ""; iNotes = ""
            }
        }) { Text("Add") }
    }
    Spacer(Modifier.height(12.dp))

    val sorted = coll.items.sortedByDescending { it.acquiredDate }
    Text("Items (${sorted.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    if (sorted.isEmpty()) { Muted("Nothing in this collection yet."); return }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(sorted, key = { it.id }) { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge)
                    val chips = buildList {
                        if (item.acquiredDate.isNotBlank()) add(item.acquiredDate)
                        item.tags.forEach { add("#$it") }
                    }
                    if (chips.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                    }
                    if (item.notes.isNotBlank()) Text(item.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { patch { it.copy(items = it.items.filterNot { x -> x.id == item.id }) } }) { Text("×") }
            }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
