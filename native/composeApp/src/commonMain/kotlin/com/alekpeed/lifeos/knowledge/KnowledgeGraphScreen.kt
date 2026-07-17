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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.ai.AiClient
import com.alekpeed.lifeos.data.DATA_SOURCES
import com.alekpeed.lifeos.data.linesOf
import com.alekpeed.lifeos.data.searchAll
import com.alekpeed.lifeos.ui.SaveToast
import kotlinx.coroutines.launch

private data class Node(val source: String, val label: String)

private const val KG_SYSTEM =
    "You suggest non-obvious but real connections in a personal knowledge graph. You are given a FOCUS " +
        "record and a numbered CANDIDATES list. Pick up to 5 candidates that genuinely relate to the focus. " +
        "Respond with ONLY lines of the form 'N | short reason', where N is a number from the CANDIDATES list. " +
        "Use only numbers shown — never invent a record. If none fit, respond exactly 'none'."

// Parse the model's reply into (candidate, reason) pairs, keeping only indices
// that exist in the closed candidate list — so a hallucinated number is dropped,
// never turned into a link.
private fun parseSuggestions(text: String, candidates: List<Node>): List<Pair<Node, String>> {
    val out = mutableListOf<Pair<Node, String>>()
    text.lines().forEach { line ->
        val m = Regex("""^\s*(\d+)\s*[|:.\-)]\s*(.+)$""").find(line) ?: return@forEach
        val idx = (m.groupValues[1].toIntOrNull() ?: return@forEach) - 1
        val cand = candidates.getOrNull(idx) ?: return@forEach
        if (out.none { it.first == cand }) out.add(cand to m.groupValues[2].trim())
    }
    return out.take(5)
}

@Composable
fun KnowledgeGraphScreen() {
    var data by remember { mutableStateOf(loadKGraph()) }
    fun save(d: KGraphData) { data = d; saveKGraph(d); SaveToast.show() }
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

    val scope = rememberCoroutineScope()
    val hasKey = remember { AiClient.hasKey() }
    var suggestions by remember(focus) { mutableStateOf<List<Pair<Node, String>>?>(null) }
    var suggesting by remember(focus) { mutableStateOf(false) }
    val candidates = remember(focus, data) {
        DATA_SOURCES.flatMap { ds -> linesOf(ds.key).map { Node(ds.label, it) } }
            .filter { !(it.source == focus.source && it.label == focus.label) && (it.source to it.label) !in linked }
            .distinct().take(40)
    }
    fun suggest() {
        if (suggesting || candidates.isEmpty()) return
        suggesting = true
        scope.launch {
            val listStr = candidates.mapIndexed { i, n -> "${i + 1}. [${n.source}] ${n.label}" }.joinToString("\n")
            val reply = AiClient.ask(KG_SYSTEM, "FOCUS: [${focus.source}] ${focus.label}\nCANDIDATES:\n$listStr", maxTokens = 500)
            suggestions = if (reply.isError) emptyList() else parseSuggestions(reply.text, candidates)
            suggesting = false
        }
    }

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

        if (hasKey && candidates.isNotEmpty()) {
            item {
                SectionLabel("AI-suggested connections")
                when {
                    suggesting -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp).width(16.dp))
                        Spacer(Modifier.width(10.dp)); Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    suggestions == null -> Button(onClick = { suggest() }) { Text("✨ Suggest connections") }
                    suggestions!!.isEmpty() -> Column {
                        Muted("Nothing stood out this pass.")
                        TextButton(onClick = { suggest() }) { Text("Try again") }
                    }
                    else -> Spacer(Modifier.height(0.dp))
                }
            }
            suggestions?.takeIf { it.isNotEmpty() }?.let { sugg ->
                items(sugg) { (node, reason) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(node.label, style = MaterialTheme.typography.bodyLarge)
                            Text(node.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            save(data.copy(edges = data.edges + Edge(focus.source, focus.label, node.source, node.label)))
                            suggestions = suggestions?.filterNot { it.first == node }
                        }) { Text("+ Link") }
                    }
                }
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
