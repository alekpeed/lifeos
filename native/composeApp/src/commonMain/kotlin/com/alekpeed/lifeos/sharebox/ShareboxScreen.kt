package com.alekpeed.lifeos.sharebox

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.alekpeed.lifeos.platform.Native

private val URGENT = Color(0xFFD64545)
private val SOON = Color(0xFFE0A25C)

@Composable
fun ShareboxScreen() {
    var data by remember { mutableStateOf(loadSharebox()) }
    var counter by remember { mutableStateOf(data.items.maxOfOrNull { it.id } ?: 0L) }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: ShareboxData) { data = d; saveSharebox(d) }

    var kind by remember { mutableStateOf("link") }
    var urgency by remember { mutableStateOf("normal") }
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Sharebox", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Links and notes with an urgency flag. Syncs to your own devices; sharing a space with a friend comes with the multi-user backend.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Text("Your name here", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = data.myName, onValueChange = { save(data.copy(myName = it.replace("\n", " "))) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("e.g. Alek") },
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == "link", onClick = { kind = "link" }, label = { Text("🔗 Link") })
            FilterChip(selected = kind == "note", onClick = { kind = "note" }, label = { Text("📝 Note") })
        }
        Spacer(Modifier.height(6.dp))
        if (kind == "link") {
            OutlinedTextField(url, { url = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("https://…") })
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Title (optional)") })
        } else {
            OutlinedTextField(noteBody, { noteBody = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Write a note…") })
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            URGENCIES.forEach { (v, lbl) ->
                FilterChip(selected = urgency == v, onClick = { urgency = v }, label = { Text(lbl) })
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                val poster = data.myName.ifBlank { "Someone" }
                val newItem = if (kind == "link") {
                    val u = url.trim()
                    if (u.isEmpty()) return@Button
                    ShareItem(freshId(), "link", normalizeUrl(u), title.trim(), "", urgency, poster, today().toString())
                } else {
                    val b = noteBody.trim()
                    if (b.isEmpty()) return@Button
                    ShareItem(freshId(), "note", "", "", b, urgency, poster, today().toString())
                }
                save(data.copy(items = data.items + newItem))
                url = ""; title = ""; noteBody = ""
            }) { Text("Share") }
        }
        Spacer(Modifier.height(14.dp))

        val sorted = data.items.sortedWith(compareBy({ urgencyRank(it.urgency) }, { -it.id }))
        Text("Shared (${data.items.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (sorted.isEmpty()) {
            Text("Nothing shared yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sorted, key = { it.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (item.kind == "link") "🔗" else "📝", modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (item.kind == "link") item.title.ifBlank { item.url } else item.body,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val when0 = listOf(item.postedBy.ifBlank { "Someone" }, item.createdAt).filter { it.isNotBlank() }.joinToString(" · ")
                        Text(when0, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val uColor = when (item.urgency) { "urgent" -> URGENT; "soon" -> SOON; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                    Text(item.urgency, style = MaterialTheme.typography.labelSmall, color = uColor, modifier = Modifier.padding(end = 6.dp))
                    if (item.kind == "link") {
                        TextButton(onClick = { Native.shareText(if (item.title.isNotBlank()) "${item.title} — ${item.url}" else item.url) }) { Text("↗") }
                    }
                    TextButton(onClick = { save(data.copy(items = data.items.filterNot { it.id == item.id })) }) { Text("×") }
                }
            }
        }
    }
}
