package com.alekpeed.lifeos.timecapsules

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
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.today
import kotlinx.datetime.daysUntil

@Composable
fun TimeCapsulesScreen() {
    var data by remember { mutableStateOf(loadCapsules()) }
    var counter by remember { mutableStateOf(data.capsules.maxOfOrNull { it.id } ?: 0L) }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: TimeCapsulesData) { data = d; saveCapsules(d) }

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    val sealed = data.capsules.filter { isSealed(it) }.sortedBy { it.sealedUntil }
    val opened = data.capsules.filter { !isSealed(it) }.sortedByDescending { it.sealedUntil.ifBlank { it.createdAt } }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Time Capsules", style = MaterialTheme.typography.headlineMedium)
        Text("Write a note now, seal it until a future date, and it surfaces on its own.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Title (e.g. For my 30th birthday)") })
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(body, { body = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Write to your future self…") })
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(date, { date = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Open on (YYYY-MM-DD)") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val d = parseDateOrNull(date)
                if (body.trim().isNotEmpty() && d != null) {
                    save(data.copy(capsules = data.capsules + TimeCapsule(freshId(), title.trim(), body.trim(), date.trim(), today().toString())))
                    title = ""; body = ""; date = ""
                }
            }) { Text("Seal it") }
        }
        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionLabel("Sealed (${sealed.size})") }
            if (sealed.isEmpty()) item { Muted("Nothing sealed right now.") }
            else items(sealed, key = { it.id }) { Capsule(data, ::save, it) }

            item { SectionLabel("Opened (${opened.size})") }
            if (opened.isEmpty()) item { Muted("None have opened yet.") }
            else items(opened, key = { it.id }) { Capsule(data, ::save, it) }
        }
    }
}

@Composable
private fun Capsule(data: TimeCapsulesData, save: (TimeCapsulesData) -> Unit, c: TimeCapsule) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { save(data.copy(capsules = data.capsules.filterNot { it.id == c.id })) }) { Text("×") }
        }
        if (isSealed(c)) {
            val days = today().daysUntil(parseDateOrNull(c.sealedUntil) ?: today()).coerceAtLeast(1)
            Text("🔒 Sealed — opens in $days day${if (days == 1) "" else "s"} (${c.sealedUntil})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(if (c.sealedUntil.isNotBlank()) "Opened ${c.sealedUntil}" else "Written", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(c.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
