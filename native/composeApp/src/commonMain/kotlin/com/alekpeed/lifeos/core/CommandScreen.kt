package com.alekpeed.lifeos.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.ai.AiClient
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.habits.saveHabits
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks
import kotlinx.coroutines.launch

private fun appendLine(key: String, line: String) {
    val existing = Storage.read(key).orEmpty()
    Storage.write(key, if (existing.isBlank()) line else "$existing\n$line")
}

// Create a record of the given type. Tasks go through the real JSON model (not
// the old tab format — that would corrupt the now-JSON Tasks blob); ideas and
// contacts are plain lines; a habit command checks in a matching habit today.
private fun createRecord(type: String, title: String): String {
    val t = title.trim().replace("\n", " ")
    if (t.isEmpty()) return ""
    return when (type) {
        "task" -> {
            val tasks = loadTasks()
            val id = (tasks.maxOfOrNull { it.id } ?: 0L) + 1
            saveTasks(tasks + Task(id, t))
            "Added task: “$t”"
        }
        "idea" -> { appendLine("Ideas", t); "Added idea: “$t”" }
        "contact" -> { appendLine("Contacts", t); "Added contact: “$t”" }
        "habit" -> {
            val habits = loadHabits()
            val match = habits.firstOrNull { t.contains(it.name, ignoreCase = true) || it.name.contains(t, ignoreCase = true) }
            if (match != null) {
                saveHabits(habits.map { if (it.name == match.name) it.copy(checkins = it.checkins + today()) else it })
                "Checked in: “${match.name}”"
            } else {
                saveHabits(habits + com.alekpeed.lifeos.habits.Habit(t, setOf(today())))
                "New habit + checked in: “$t”"
            }
        }
        else -> { appendLine("Ideas", t); "Added idea: “$t”" }
    }
}

private const val PARSE_SYSTEM =
    "You classify a person's quick command into ONE action for a life-management app. " +
        "Respond with EXACTLY two lines and nothing else:\n" +
        "TYPE: <task|idea|contact|habit>\n" +
        "TITLE: <the cleaned-up text of the thing>\n" +
        "Use 'habit' only for check-in style commands (e.g. 'did my workout'); 'contact' for a person to remember; " +
        "'task' for something to do; otherwise 'idea'."

private fun parseAction(reply: String): Pair<String, String>? {
    val type = Regex("(?im)^TYPE:\\s*(task|idea|contact|habit)").find(reply)?.groupValues?.get(1)?.lowercase()
    val title = Regex("(?im)^TITLE:\\s*(.+)$").find(reply)?.groupValues?.get(1)?.trim()
    return if (type != null && !title.isNullOrBlank()) type to title else null
}

// The quick-capture command bar: type once, fire it into the right module. With
// an AI key set, it can also parse a plain command ("remind me to call the
// landlord tomorrow") into a structured action, shown for one-tap confirm.
@Composable
fun CommandScreen() {
    var input by remember { mutableStateOf("") }
    var lastAction by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    val hasKey = remember { AiClient.hasKey() }

    fun capture(type: String) {
        val msg = createRecord(type, input)
        if (msg.isNotEmpty()) { lastAction = msg; input = ""; pending = null }
    }
    fun parse() {
        val q = input.trim()
        if (q.isEmpty() || parsing) return
        parsing = true; pending = null; lastAction = ""
        scope.launch {
            val reply = AiClient.ask(PARSE_SYSTEM, q, maxTokens = 120)
            pending = if (reply.isError) null else parseAction(reply.text)
            if (pending == null && !reply.isError) lastAction = "Couldn't parse that — use the buttons below."
            else if (reply.isError) lastAction = reply.text
            parsing = false
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Command", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("Capture once, send it where it belongs.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Type anything…") })
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { capture("task") }) { Text("→ Task") }
            Button(onClick = { capture("idea") }) { Text("→ Idea") }
            OutlinedButton(onClick = { Native.readClipboard()?.let { input = it.trim() } }) { Text("📋 Paste") }
        }
        if (hasKey) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { parse() }, enabled = !parsing) { Text("✨ Parse with AI") }
                if (parsing) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp).width(16.dp))
                }
            }
        }

        pending?.let { (type, title) ->
            Spacer(Modifier.height(16.dp))
            Column {
                Text("Create ${type}: “$title”?", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        val msg = createRecord(type, title)
                        if (msg.isNotEmpty()) { lastAction = msg; input = ""; pending = null }
                    }) { Text("Confirm") }
                    TextButton(onClick = { pending = null }) { Text("Discard") }
                }
            }
        }

        if (lastAction.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(lastAction, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
