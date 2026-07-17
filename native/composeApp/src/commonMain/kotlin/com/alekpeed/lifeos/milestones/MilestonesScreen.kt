package com.alekpeed.lifeos.milestones

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
import androidx.compose.material3.FilterChip
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
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.places.loadPlaces
import com.alekpeed.lifeos.recipes.loadRecipes

private val DANGER = Color(0xFFD64545)

@Composable
fun MilestonesScreen() {
    var data by remember { mutableStateOf(loadMilestones()) }
    var counter by remember { mutableStateOf(data.milestones.maxOfOrNull { it.id } ?: 0L) }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: MilestonesData) { data = d; saveMilestones(d) }

    var tab by remember { mutableStateOf("timeline") }
    var selected by remember { mutableStateOf<Long?>(null) }
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Milestones", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == "timeline", onClick = { tab = "timeline" }, label = { Text("Timeline") })
            FilterChip(selected = tab == "recap", onClick = { tab = "recap"; selected = null }, label = { Text("Yearly recap") })
        }
        Spacer(Modifier.height(12.dp))

        if (tab == "recap") { Recap(data); return@Column }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("New milestone") })
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim().replace("\n", " ")
                if (t.isNotEmpty()) { save(data.copy(milestones = data.milestones + Milestone(freshId(), t, today().toString()))); input = "" }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(12.dp))

        val sorted = data.milestones.sortedByDescending { it.date.ifBlank { "0000" } }
        if (sorted.isEmpty()) { Muted("No milestones logged yet."); return@Column }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var lastYear = ""
            sorted.forEach { m ->
                val year = m.date.take(4).ifBlank { "Undated" }
                if (year != lastYear) {
                    lastYear = year
                    item { Text(year, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                }
                item {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = if (selected == m.id) null else m.id }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge)
                                val meta = listOf(m.category, m.date).filter { it.isNotBlank() }.joinToString(" · ")
                                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (selected == m.id) MilestoneDetail(data, ::save, m) { selected = null }
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestoneDetail(data: MilestonesData, save: (MilestonesData) -> Unit, m: Milestone, onClose: () -> Unit) {
    fun patch(f: (Milestone) -> Milestone) = save(data.copy(milestones = data.milestones.map { if (it.id == m.id) f(it) else it }))
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) {
        Label("Title"); Field(m.title, "Title") { v -> patch { it.copy(title = v.replace("\n", " ")) } }
        Label("Date"); Field(m.date, "YYYY-MM-DD") { v -> patch { it.copy(date = v.trim()) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { patch { it.copy(date = today().toString()) } }, label = { Text("Today") })
            if (m.date.isNotBlank()) TextButton(onClick = { patch { it.copy(date = "") } }) { Text("Clear") }
        }
        Label("Category"); Field(m.category, "birthday, achievement, travel, career…") { v -> patch { it.copy(category = v.replace("\n", " ")) } }
        Label("Notes"); Field(m.notes, "Notes", singleLine = false) { v -> patch { it.copy(notes = v) } }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Close") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { save(data.copy(milestones = data.milestones.filterNot { it.id == m.id })); onClose() }) { Text("Delete", color = DANGER) }
        }
    }
}

@Composable
private fun Recap(data: MilestonesData) {
    var year by remember { mutableStateOf(today().toString().take(4)) }
    val y = year

    // Real cross-module stats computed from native storage for the chosen year.
    val books = remember(y) { loadBooks() }
    val recipes = remember(y) { loadRecipes() }
    val places = remember(y) { loadPlaces() }
    val booksFinished = books.books.count { it.finishedDate.startsWith(y) }
    val pagesRead = books.books.flatMap { it.logs }.filter { it.date.startsWith(y) }.sumOf { it.pagesRead }
    val cookSessions = recipes.recipes.flatMap { it.cookLogs }.count { it.date.startsWith(y) }
    val visits = places.places.flatMap { it.visitDates }.count { it.startsWith(y) }
    val ms = data.milestones.filter { it.date.startsWith(y) }.sortedBy { it.date }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Year", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(year, { year = it.filter { c -> c.isDigit() }.take(4) }, modifier = Modifier.width(120.dp), singleLine = true)
        }
        Spacer(Modifier.height(10.dp))
        Text("$y in review", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        StatRow("Milestones", ms.size.toString())
        StatRow("Books finished", booksFinished.toString())
        StatRow("Pages read", pagesRead.toString())
        StatRow("Recipe cook sessions", cookSessions.toString())
        StatRow("Place visits", visits.toString())
        Text(
            "More stats join the recap as other modules gain dated history; the AI narrative wires to the AI layer next.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Milestones this year", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ms.isEmpty()) Muted("No milestones logged this year.")
        else ms.forEach { StatRow(it.title.ifBlank { "(untitled)" }, it.date) }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Field(value: String, placeholder: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = singleLine, placeholder = { Text(placeholder) })
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
