package com.alekpeed.lifeos.insight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

private const val ASSISTANT_SYSTEM =
    "You are the assistant inside Life OS, a personal life-management app. " +
        "Help the user with their life using the CONTEXT (a snapshot of their saved data) when relevant. " +
        "Be warm, concise, and practical. Suggest concrete next steps when it helps."

private data class ChatMsg(val fromUser: Boolean, val text: String)

private fun loadChat(): List<ChatMsg> =
    Storage.read("AI Assistant")?.lines()?.filter { it.isNotBlank() }?.map { line ->
        val p = line.split("\t", limit = 2)
        ChatMsg(p.getOrElse(0) { "you" } == "you", p.getOrElse(1) { line })
    } ?: emptyList()

private fun saveChat(msgs: List<ChatMsg>) {
    Storage.write("AI Assistant", msgs.joinToString("\n") { "${if (it.fromUser) "you" else "ai"}\t${it.text.replace("\n", " ")}" })
}

// The assistant, now a real chat backed by the model (when a key is set). Each
// message is answered with a snapshot of the user's data as grounding. The
// conversation persists. Without a key it says so instead of pretending.
@Composable
fun AssistantScreen() {
    val chat = remember { mutableStateListOf<ChatMsg>().apply { addAll(loadChat()) } }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun send() {
        val t = input.trim()
        if (t.isEmpty() || loading) return
        chat.add(ChatMsg(true, t))
        saveChat(chat)
        input = ""
        loading = true
        scope.launch {
            val reply = AiClient.ask(ASSISTANT_SYSTEM, "CONTEXT:\n${aiContext(t)}\n\nUSER: $t")
            chat.add(ChatMsg(false, reply.text))
            saveChat(chat)
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("AI Assistant", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (chat.isNotEmpty()) {
                TextButton(onClick = { chat.clear(); saveChat(chat) }) { Text("Clear") }
            }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.fillMaxSize().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(chat) { _, m ->
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
