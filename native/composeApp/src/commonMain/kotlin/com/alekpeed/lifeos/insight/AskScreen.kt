package com.alekpeed.lifeos.insight

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.ai.AiClient
import com.alekpeed.lifeos.data.aiContext
import com.alekpeed.lifeos.data.searchAll
import kotlinx.coroutines.launch

private const val ASK_SYSTEM =
    "You are Ask, the assistant inside Life OS, a personal life-management app. " +
        "Answer the user's question using the CONTEXT (their own saved data) when it's relevant. " +
        "Be concise and direct — a sentence or two unless more is genuinely needed. " +
        "If the context doesn't contain the answer, say so briefly and answer from general knowledge if you can."

// Ask has two ways in. Answer runs the model, grounded in a snapshot of your
// data. Find is real semantic memory: every record is embedded once and a query
// is matched by meaning (a bill surfaces for "money I owe"), each hit ranked by
// % match and tappable to its module. With no key at all it falls back to a
// live keyword search, so the module is never dead.
@Composable
fun AskScreen() {
    val hasKey = AiClient.hasKey()
    val canEmbed = remember { AskIndex.available() }
    var mode by remember { mutableStateOf(if (canEmbed) "find" else "answer") }

    var query by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    var results by remember { mutableStateOf<List<AskIndex.Ranked>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var building by remember { mutableStateOf(false) }
    var buildMsg by remember { mutableStateOf<String?>(null) }
    var indexed by remember { mutableStateOf(AskIndex.indexedCount()) }
    var stale by remember { mutableStateOf(AskIndex.isStale()) }
    val scope = rememberCoroutineScope()

    fun ask() {
        val q = query.trim()
        if (q.isEmpty() || loading) return
        answer = null; loading = true
        scope.launch {
            answer = AiClient.ask(ASK_SYSTEM, "CONTEXT:\n${aiContext(q)}\n\nQUESTION: $q").text
            loading = false
        }
    }
    fun find() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        searching = true; results = null
        scope.launch {
            results = AskIndex.search(q) ?: emptyList()
            searching = false
        }
    }
    fun buildIndex() {
        if (building) return
        building = true; buildMsg = "Reading your data…"; results = null
        scope.launch {
            val n = AskIndex.build { done, total -> buildMsg = "Indexing… $done / $total" }
            building = false
            when {
                n < 0 -> buildMsg = "Couldn't build the index — check your connection / OpenAI key."
                n == 0 -> buildMsg = "Nothing to index yet."
                else -> { buildMsg = null; indexed = n; stale = false }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Ask", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))

        if (hasKey || canEmbed) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (hasKey) FilterChip(selected = mode == "answer", onClick = { mode = "answer" }, label = { Text("Answer") })
                if (canEmbed) FilterChip(selected = mode == "find", onClick = { mode = "find" }, label = { Text("Find in memory") })
            }
            Spacer(Modifier.height(10.dp))
        }

        Text(
            when {
                mode == "find" -> "Search your whole life by meaning, not just keywords."
                hasKey -> "Ask anything about your life; answers are grounded in your saved data."
                else -> "No API key set — searching your data directly. Add a key in Settings for real answers."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; if (!hasKey && mode != "find") answer = null },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(if (mode == "find") "e.g. money I owe, that trail we hiked…" else if (hasKey) "Ask about anything…" else "Search everything…") },
            )
            Spacer(Modifier.width(10.dp))
            when {
                mode == "find" -> Button(onClick = { find() }, enabled = !searching && !building) { Text("Find") }
                hasKey -> Button(onClick = { ask() }, enabled = !loading) { Text("Ask") }
            }
        }
        Spacer(Modifier.height(14.dp))

        when (mode) {
            "answer" -> AnswerPane(loading, answer)
            "find" -> FindPane(
                indexed = indexed, stale = stale, building = building, buildMsg = buildMsg,
                searching = searching, results = results, onBuild = { buildIndex() },
            )
            else -> KeywordPane(query)
        }
    }
}

@Composable
private fun AnswerPane(loading: Boolean, answer: String?) {
    when {
        loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp).width(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        answer != null -> Text(answer, style = MaterialTheme.typography.bodyLarge)
        else -> Text("Type a question and tap Ask.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FindPane(
    indexed: Int,
    stale: Boolean,
    building: Boolean,
    buildMsg: String?,
    searching: Boolean,
    results: List<AskIndex.Ranked>?,
    onBuild: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (indexed == 0) "No memory index yet." else "$indexed records indexed" + if (stale) " · out of date" else "",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
        if (!building && (indexed == 0 || stale)) {
            OutlinedButton(onClick = onBuild) { Text(if (indexed == 0) "Build index" else "Refresh") }
        }
    }
    if (building) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp).width(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(buildMsg ?: "Indexing…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else buildMsg?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(12.dp))

    when {
        searching -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp).width(18.dp))
            Spacer(Modifier.width(10.dp)); Text("Searching your memory…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        results == null -> if (indexed > 0) Text("Type something and tap Find.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        results.isEmpty() -> Text("No matches in your memory.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(results) { r ->
                Row(
                    Modifier.fillMaxWidth().clickable { Nav.open(r.moduleId) }.padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${(r.score * 100).toInt().coerceIn(0, 100)}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                        Text(r.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordPane(query: String) {
    val hits = remember(query) { searchAll(query) }
    if (query.isBlank()) {
        Text("Type to search across every module.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text("${hits.size} result${if (hits.size == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    LazyColumn(Modifier.fillMaxSize()) {
        items(hits) { hit ->
            Row(Modifier.fillMaxWidth().clickable { Nav.open(hit.moduleId) }.padding(vertical = 6.dp)) {
                Text(hit.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(120.dp))
                Spacer(Modifier.width(8.dp))
                Text(hit.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}
