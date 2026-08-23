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
import com.alekpeed.lifeos.ui.MicIconButton
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
import com.alekpeed.lifeos.core.CAPTURE_TYPES
import com.alekpeed.lifeos.core.CaptureCmd
import com.alekpeed.lifeos.core.Intent
import com.alekpeed.lifeos.core.PARSE_SYSTEM
import com.alekpeed.lifeos.core.classify
import com.alekpeed.lifeos.core.createRecord
import com.alekpeed.lifeos.core.guessCapture
import com.alekpeed.lifeos.core.parseAction
import com.alekpeed.lifeos.data.aiContext
import com.alekpeed.lifeos.data.searchAll
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import kotlinx.coroutines.launch

private const val ASK_SYSTEM =
    "You are Ask, the assistant inside Life OS, a personal life-management app. " +
        "Answer the user's question using the CONTEXT (their own saved data) when it's relevant. " +
        "Be concise and direct — a sentence or two unless more is genuinely needed. " +
        "If the context doesn't contain the answer, say so briefly and answer from general knowledge if you can."

// Ask answers and creates, from one box (§2 Group A).
//
// It used to only read. Command owned the app's only natural-language write path, and
// deleting that screen would have deleted the capability — so it moved here, and what
// tells the two apart is what you typed, not a mode you picked first. "when is the rent
// due" is a question; "pay the rent friday" is a bill. The button says which before you
// press it.
//
// Nothing is written on a guess. A command is proposed and confirmed — SmartScan's
// propose-then-confirm, which is the same shape for the same reason: a misread should
// cost a tap, not leave a wrong record behind. The type is editable on the card, which
// is what the old screen's "→ Task" / "→ Idea" buttons were really for.
//
// Reading still has two ways in. Answer runs the model, grounded in a snapshot of your
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

    // The capture half. `pending` is the proposal awaiting a Confirm; `done` is what
    // happened, with the module it landed in so it can be opened.
    var pending by remember { mutableStateOf<CaptureCmd?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf<String?>(null) }
    var capturedModule by remember { mutableStateOf("") }

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
    // The AI parse when there is a key, the local read when there is not. Either way it
    // produces a proposal rather than a record.
    fun propose() {
        val q = query.trim()
        if (q.isEmpty() || parsing) return
        answer = null; captured = null; pending = null
        if (!hasKey) { pending = guessCapture(q); return }
        parsing = true
        scope.launch {
            val reply = AiClient.ask(PARSE_SYSTEM, "TODAY: ${today()}\n$q", maxTokens = 160)
            // A model that is unreachable or unhelpful must not lose the line: the local
            // read is what the app did before there were keys, and it still works.
            pending = if (reply.isError) guessCapture(q) else parseAction(reply.text) ?: guessCapture(q)
            parsing = false
        }
    }
    fun commit(cmd: CaptureCmd) {
        val result = createRecord(cmd)
        if (result.message.isNotEmpty()) {
            captured = result.message
            capturedModule = result.moduleId
            pending = null
            query = ""
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

        if (hasKey || canEmbed) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (hasKey) FilterChip(selected = mode == "answer", onClick = { mode = "answer" }, label = { Text("Answer") })
                if (canEmbed) FilterChip(selected = mode == "find", onClick = { mode = "find" }, label = { Text("Find in memory") })
            }
            Spacer(Modifier.height(10.dp))
        }

        // What the box will do with what is in it, decided as you type. The whole point
        // of one box is that you do not choose first — but you should be able to see the
        // choice before you commit to it.
        val intent = classify(query)
        val creating = intent == Intent.COMMAND && query.isNotBlank()

        Text(
            when {
                creating -> "This reads as something to keep. You'll get a proposal to confirm."
                mode == "find" -> "Search your whole life by meaning, not just keywords."
                hasKey -> "Ask a question, or write something down — one box, both."
                else -> "No API key set — searching your data directly. Anything that isn't a question is still captured."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; pending = null; if (!hasKey && mode != "find") answer = null },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(if (mode == "find") "e.g. money I owe, that trail we hiked…" else if (hasKey) "Ask about anything, or jot it down…" else "Search everything, or jot it down…") },
            )
            MicIconButton { spoken -> query = if (query.isBlank()) spoken else "$query $spoken" }
            Spacer(Modifier.width(4.dp))
            when {
                creating -> Button(onClick = { propose() }, enabled = !parsing) { Text("Create") }
                mode == "find" -> Button(onClick = { find() }, enabled = !searching && !building) { Text("Find") }
                hasKey -> Button(onClick = { ask() }, enabled = !loading) { Text("Ask") }
            }
        }

        // Kept from the Command bar: pasting is how half of what gets captured arrives.
        if (query.isBlank()) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { Native.readClipboard()?.let { query = it.trim() } }) { Text("📋 Paste") }
        }

        if (parsing) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp).width(16.dp))
                Spacer(Modifier.width(10.dp))
                Text("Reading that…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        pending?.let { cmd -> ConfirmCard(cmd, onType = { pending = cmd.copy(type = it) }, onConfirm = { commit(cmd) }, onDiscard = { pending = null }) }

        captured?.let { msg ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (capturedModule.isNotBlank()) {
                    TextButton(onClick = { Nav.open(capturedModule) }) { Text("Open →") }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        when {
            creating || pending != null -> Unit
            mode == "answer" -> AnswerPane(loading, answer)
            mode == "find" -> FindPane(
                indexed = indexed, stale = stale, building = building, buildMsg = buildMsg,
                searching = searching, results = results, onBuild = { buildIndex() },
            )
            else -> KeywordPane(query)
        }
    }
}

// The proposal. Never a record until Confirm — and the type is a row of chips rather
// than a verdict, because the machine's read of one sentence is a good first guess and
// nothing more.
@Composable
private fun ConfirmCard(
    cmd: CaptureCmd,
    onType: (String) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    Column {
        val detail = buildList {
            if (cmd.due.isNotBlank()) add("due ${cmd.due}")
            cmd.amount?.let { add("$$it") }
        }.joinToString(", ")
        Text(
            "“${cmd.title}”" + if (detail.isNotEmpty()) " · $detail" else "",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CAPTURE_TYPES.forEach { type ->
                FilterChip(selected = cmd.type == type, onClick = { onType(type) }, label = { Text(type) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onConfirm) { Text("Confirm") }
            TextButton(onClick = onDiscard) { Text("Discard") }
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
