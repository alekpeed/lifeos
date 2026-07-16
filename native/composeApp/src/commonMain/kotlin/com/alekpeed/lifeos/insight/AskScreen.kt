package com.alekpeed.lifeos.insight

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.ai.AiClient
import com.alekpeed.lifeos.data.aiContext
import com.alekpeed.lifeos.data.searchAll
import kotlinx.coroutines.launch

private const val ASK_SYSTEM =
    "You are Ask, the assistant inside Life OS, a personal life-management app. " +
        "Answer the user's question using the CONTEXT (their own saved data) when it's relevant. " +
        "Be concise and direct — a sentence or two unless more is genuinely needed. " +
        "If the context doesn't contain the answer, say so briefly and answer from general knowledge if you can."

// Ask, backed by a real model when a key is set (Settings). It grounds the answer
// in the user's saved data. Without a key it falls back to live local search over
// that same data, so the module is always useful.
@Composable
fun AskScreen() {
    var query by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hasKey = AiClient.hasKey()

    fun run() {
        val q = query.trim()
        if (q.isEmpty() || loading) return
        answer = null
        loading = true
        scope.launch {
            val reply = AiClient.ask(ASK_SYSTEM, "CONTEXT:\n${aiContext(q)}\n\nQUESTION: $q")
            answer = reply.text
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Ask", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasKey) "Ask anything about your life; answers are grounded in your saved data."
            else "No API key set — searching your data directly. Add a key in Settings for real answers.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; if (!hasKey) answer = null },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(if (hasKey) "Ask about anything…" else "Search everything…") },
            )
            Spacer(Modifier.width(10.dp))
            if (hasKey) Button(onClick = { run() }, enabled = !loading) { Text("Ask") }
        }

        Spacer(Modifier.height(16.dp))

        if (hasKey) {
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                answer != null -> Text(answer!!, style = MaterialTheme.typography.bodyLarge)
                else -> Text(
                    "Type a question and tap Ask.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val hits = remember(query) { searchAll(query) }
            if (query.isBlank()) {
                Text("Type to search across every module.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${hits.size} result${if (hits.size == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    items(hits) { hit ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(hit.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(120.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(hit.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
