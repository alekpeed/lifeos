package com.alekpeed.lifeos.knowledge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.alekpeed.lifeos.data.searchAll

private data class Node(val source: String, val label: String)

@Composable
fun KnowledgeGraphScreen() {
    var data by remember { mutableStateOf(loadKGraph()) }
    fun save(d: KGraphData) { data = d; saveKGraph(d) }
    var focus by remember { mutableStateOf<Node?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Knowledge Graph", style = MaterialTheme.typography.headlineMedium)
        val f = focus
        if (f == null) PickFocus { focus = it }
        else GraphView(data, ::save, f, onRefocus = { focus = it }, onChangeFocus = { focus = null })
    }
}

@Composable
private fun PickFocus(onFocus: (Node) -> Unit) {
    var query by remember { mutableStateOf("") }
    Text(
        "Pick anything — a task, a book, a person, a place — and see everything you've connected to it. Anything Search can find, the graph can link.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search everything…") })
    Spacer(Modifier.height(12.dp))
    val results = remember(query) { if (query.isBlank()) emptyList() else searchAll(query).take(30) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(results) { hit ->
            ResultRow(hit.text, hit.source, "Focus →") { onFocus(Node(hit.source, hit.text)) }
        }
    }
}

@Composable
private fun GraphView(
    data: KGraphData,
    save: (KGraphData) -> Unit,
    focus: Node,
    onRefocus: (Node) -> Unit,
    onChangeFocus: () -> Unit,
) {
    var addQuery by remember(focus) { mutableStateOf("") }
    val neighbors = data.edges.filter { it.touches(focus.source, focus.label) }
        .map { it.other(focus.source, focus.label) }
    val linked = neighbors.toSet()

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onChangeFocus) { Text("← Change focus") }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(focus.label, style = MaterialTheme.typography.titleMedium)
            Text(focus.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    Spacer(Modifier.height(12.dp))

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { SectionLabel("Connections (${neighbors.size})") }
        if (neighbors.isEmpty()) item { Muted("Nothing linked yet — connect something below.") }
        else items(neighbors) { (src, lbl) ->
            Row(Modifier.fillMaxWidth().clickable { onRefocus(Node(src, lbl)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lbl, style = MaterialTheme.typography.bodyLarge)
                    Text(src, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = {
                    save(data.copy(edges = data.edges.filterNot { it.touches(focus.source, focus.label) && it.other(focus.source, focus.label) == (src to lbl) }))
                }) { Text("×") }
            }
        }

        item {
            SectionLabel("Add a connection")
            OutlinedTextField(addQuery, { addQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search for something to link…") })
            Spacer(Modifier.height(6.dp))
        }
        val results = if (addQuery.isBlank()) emptyList() else searchAll(addQuery)
            .filter { !(it.source == focus.source && it.text == focus.label) && (it.source to it.text) !in linked }
            .take(20)
        items(results) { hit ->
            ResultRow(hit.text, hit.source, "+ Link") {
                save(data.copy(edges = data.edges + Edge(focus.source, focus.label, hit.source, hit.text)))
                addQuery = ""
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, source: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
