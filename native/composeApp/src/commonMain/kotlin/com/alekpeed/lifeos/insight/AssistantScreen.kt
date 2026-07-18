package com.alekpeed.lifeos.insight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.ai.AiClient
import com.alekpeed.lifeos.data.aiContext
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val ASSISTANT_SYSTEM =
    "You are the assistant inside Life OS, a personal life-management app. " +
        "Help the user with their life using the CONTEXT (a snapshot of their saved data) when relevant. " +
        "Be warm, concise, and practical. Suggest concrete next steps when it helps."

@Serializable
private data class ChatMsg(val fromUser: Boolean, val text: String)

@Serializable
private data class Conversation(val id: Long, val name: String, val msgs: List<ChatMsg> = emptyList())

@Serializable
private data class AssistantData(val conversations: List<Conversation> = emptyList(), val activeId: Long = 0)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun loadAssistant(): AssistantData {
    val raw = Storage.read("AI Assistant")
    if (raw.isNullOrBlank()) return AssistantData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<AssistantData>(raw) }.getOrElse { AssistantData() }
    }
    // Migrate the old single flat chat ("you\ttext" lines) into conversation #1.
    val msgs = raw.lines().filter { it.isNotBlank() }.map { line ->
        val p = line.split("\t", limit = 2)
        ChatMsg(p.getOrElse(0) { "you" } == "you", p.getOrElse(1) { line })
    }
    return if (msgs.isEmpty()) AssistantData()
    else AssistantData(listOf(Conversation(1, "Chat 1", msgs)), activeId = 1)
}

private fun saveAssistant(data: AssistantData) {
    Storage.write("AI Assistant", json.encodeToString(data))
}

// The assistant — real model-backed chat with multiple named conversations
// (create / switch / rename / delete), each with its own persisted history.
// Replies are grounded two ways: a snapshot of the user's data AND the tail of
// the current conversation, so follow-up questions actually follow up. Without
// a key it says so instead of pretending.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen() {
    var data by remember { mutableStateOf(loadAssistant()) }
    fun persist(next: AssistantData) { data = next; saveAssistant(next) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val active = data.conversations.firstOrNull { it.id == data.activeId } ?: data.conversations.firstOrNull()
    fun patchActive(f: (Conversation) -> Conversation) {
        val a = active ?: return
        persist(data.copy(conversations = data.conversations.map { if (it.id == a.id) f(it) else it }))
    }

    fun newConversation() {
        val id = (data.conversations.maxOfOrNull { it.id } ?: 0L) + 1
        persist(data.copy(conversations = data.conversations + Conversation(id, "Chat $id"), activeId = id))
    }

    fun send() {
        val t = input.trim()
        if (t.isEmpty() || loading) return
        if (active == null) {
            newConversation()
        }
        val convo = data.conversations.firstOrNull { it.id == data.activeId } ?: return
        val withUser = convo.copy(msgs = convo.msgs + ChatMsg(true, t))
        persist(data.copy(conversations = data.conversations.map { if (it.id == convo.id) withUser else it }))
        input = ""
        loading = true
        scope.launch {
            // Ground with the data snapshot plus this conversation's recent turns.
            val recent = withUser.msgs.takeLast(10)
                .joinToString("\n") { "${if (it.fromUser) "USER" else "ASSISTANT"}: ${it.text}" }
            val reply = AiClient.ask(ASSISTANT_SYSTEM, "CONTEXT:\n${aiContext(t)}\n\nCONVERSATION SO FAR:\n$recent\n\nUSER: $t")
            val cur = loadAssistant()
            val updated = cur.conversations.map {
                if (it.id == convo.id) it.copy(msgs = it.msgs + ChatMsg(false, reply.text)) else it
            }
            persist(cur.copy(conversations = updated))
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("AI Assistant", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            active?.let {
                TextButton(onClick = { renaming = !renaming }) { Text("Rename") }
                TextButton(onClick = {
                    val rest = data.conversations.filterNot { c -> c.id == it.id }
                    persist(data.copy(conversations = rest, activeId = rest.lastOrNull()?.id ?: 0))
                }) { Text("Delete") }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            data.conversations.forEach { c ->
                FilterChip(
                    selected = c.id == active?.id,
                    onClick = { persist(data.copy(activeId = c.id)); renaming = false },
                    label = { Text(c.name) },
                )
            }
            TextButton(onClick = { newConversation() }) { Text("+ New") }
        }
        if (renaming && active != null) {
            OutlinedTextField(
                active.name,
                { v -> patchActive { it.copy(name = v.replace("\n", " ").take(40)) } },
                modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Conversation name") },
            )
        }
        Spacer(Modifier.height(10.dp))

        val msgs = active?.msgs ?: emptyList()
        LazyColumn(Modifier.fillMaxSize().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(msgs) { _, m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(m.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp).width(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask the assistant…") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = { send() }, enabled = !loading) { Text("Send") }
        }
    }
}
