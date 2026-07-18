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
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.habits.saveHabits
import com.alekpeed.lifeos.ideas.appendIdea
import com.alekpeed.lifeos.people.Contact
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.people.saveContacts
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks
import kotlinx.coroutines.launch


// A lightweight local due-date read: trailing "today" / "tomorrow" / "next week"
// / "in N days" set the date and are stripped from the title. Keyless — works
// without AI, and the AI path can still supply an exact DUE line.
private fun extractDue(text: String): Pair<String, String> {
    var t = text.trim()
    fun strip(re: Regex): Boolean {
        val m = re.find(t) ?: return false
        t = t.removeRange(m.range).trim().trimEnd(',', '.', ' ')
        return true
    }
    val due = when {
        strip(Regex("(?i)\\btomorrow\\b")) -> today().plusDays(1).toString()
        strip(Regex("(?i)\\bnext week\\b")) -> today().plusDays(7).toString()
        strip(Regex("(?i)\\btoday\\b")) -> today().toString()
        else -> {
            val m = Regex("(?i)\\bin (\\d{1,2}) days?\\b").find(t)
            if (m != null) {
                val n = m.groupValues[1].toIntOrNull() ?: 0
                t = t.removeRange(m.range).trim().trimEnd(',', '.', ' ')
                today().plusDays(n).toString()
            } else ""
        }
    }
    return t to due
}

// Everything the confirm needs: what to make, of what, when, for how much.
private data class ParsedCmd(val type: String, val title: String, val due: String = "", val amount: Double? = null)

// Create the record. Tasks carry the extracted due date; bills go through the
// real Finance model (monthly by default); ideas/contacts/habits as before.
// Returns (confirmation message, module id to open).
private fun createRecord(cmd: ParsedCmd): Pair<String, String> {
    val t = cmd.title.trim().replace("\n", " ")
    if (t.isEmpty()) return "" to ""
    return when (cmd.type) {
        "task" -> {
            val (title, localDue) = extractDue(t)
            val due = cmd.due.ifBlank { localDue }
            val tasks = loadTasks()
            val id = (tasks.maxOfOrNull { it.id } ?: 0L) + 1
            saveTasks(tasks + Task(id, title, due = due))
            ("Added task: “$title”" + if (due.isNotBlank()) " (due $due)" else "") to "tasks"
        }
        "bill" -> {
            com.alekpeed.lifeos.finance.financeAddBill(t, cmd.amount ?: 0.0, cmd.due.ifBlank { today().toString() })
            "Added bill: “$t”" to "finance"
        }
        "idea" -> { appendIdea(t); "Added idea: “$t”" to "ideas" }
        "contact" -> {
            val cd = loadContacts()
            val id = (cd.contacts.maxOfOrNull { it.id } ?: 0L) + 1
            saveContacts(cd.copy(contacts = cd.contacts + Contact(id, t)))
            "Added contact: “$t”" to "contacts"
        }
        "habit" -> {
            val habits = loadHabits()
            val match = habits.firstOrNull { t.contains(it.name, ignoreCase = true) || it.name.contains(t, ignoreCase = true) }
            if (match != null) {
                saveHabits(habits.map { if (it.name == match.name) it.copy(checkins = it.checkins + today()) else it })
                "Checked in: “${match.name}”" to "habits"
            } else {
                saveHabits(habits + com.alekpeed.lifeos.habits.Habit(t, setOf(today())))
                "New habit + checked in: “$t”" to "habits"
            }
        }
        else -> { appendIdea(t); "Added idea: “$t”" to "ideas" }
    }
}

private const val PARSE_SYSTEM =
    "You classify a person's quick command into ONE action for a life-management app. " +
        "Respond with EXACTLY these lines and nothing else:\n" +
        "TYPE: <task|idea|contact|habit|bill>\n" +
        "TITLE: <the cleaned-up text of the thing>\n" +
        "DUE: <YYYY-MM-DD or blank>\n" +
        "AMOUNT: <number or blank>\n" +
        "Use 'habit' only for check-in style commands (e.g. 'did my workout'); 'contact' for a person to remember; " +
        "'bill' for a payment obligation (rent, electric — AMOUNT is its cost); 'task' for something to do; otherwise 'idea'. " +
        "Resolve relative dates (tomorrow, Friday) against TODAY given in the message."

private fun parseAction(reply: String): ParsedCmd? {
    val type = Regex("(?im)^TYPE:\\s*(task|idea|contact|habit|bill)").find(reply)?.groupValues?.get(1)?.lowercase()
    val title = Regex("(?im)^TITLE:\\s*(.+)$").find(reply)?.groupValues?.get(1)?.trim()
    val due = Regex("(?im)^DUE:\\s*(\\d{4}-\\d{2}-\\d{2})").find(reply)?.groupValues?.get(1) ?: ""
    val amount = Regex("(?im)^AMOUNT:\\s*\\$?([0-9]+(?:\\.[0-9]+)?)").find(reply)?.groupValues?.get(1)?.toDoubleOrNull()
    return if (type != null && !title.isNullOrBlank()) ParsedCmd(type, title, due, amount) else null
}

// The quick-capture command bar: type once, fire it into the right module. With
// an AI key set, it can also parse a plain command ("remind me to call the
// landlord tomorrow") into a structured action, shown for one-tap confirm.
@Composable
fun CommandScreen() {
    var input by remember { mutableStateOf("") }
    var lastAction by remember { mutableStateOf("") }
    var lastModule by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ParsedCmd?>(null) }
    val scope = rememberCoroutineScope()
    val hasKey = remember { AiClient.hasKey() }

    fun run(cmd: ParsedCmd) {
        val (msg, module) = createRecord(cmd)
        if (msg.isNotEmpty()) { lastAction = msg; lastModule = module; input = ""; pending = null }
    }
    fun capture(type: String) = run(ParsedCmd(type, input))
    fun parse() {
        val q = input.trim()
        if (q.isEmpty() || parsing) return
        parsing = true; pending = null; lastAction = ""
        scope.launch {
            val reply = AiClient.ask(PARSE_SYSTEM, "TODAY: ${today()}\n$q", maxTokens = 160)
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

        pending?.let { cmd ->
            Spacer(Modifier.height(16.dp))
            Column {
                val detail = buildList {
                    if (cmd.due.isNotBlank()) add("due ${cmd.due}")
                    cmd.amount?.let { add("$$it") }
                }.joinToString(", ")
                Text("Create ${cmd.type}: “${cmd.title}”${if (detail.isNotEmpty()) " ($detail)" else ""}?", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { run(cmd) }) { Text("Confirm") }
                    TextButton(onClick = { pending = null }) { Text("Discard") }
                }
            }
        }

        if (lastAction.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lastAction, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (lastModule.isNotBlank()) {
                    TextButton(onClick = { com.alekpeed.lifeos.Nav.open(lastModule) }) { Text("Open →") }
                }
            }
        }
    }
}
