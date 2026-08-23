package com.alekpeed.lifeos.core

import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.habits.saveHabits
import com.alekpeed.lifeos.ideas.appendIdea
import com.alekpeed.lifeos.people.Contact
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.people.saveContacts
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks

// The natural-language write path (§2 Group A).
//
// This was CommandScreen.kt, and it was the only place in the app where a sentence
// became a record. Ask reads and answers; the Assistant converses; neither creates
// anything. Deleting the Command screen outright would have deleted a capability, not
// a duplicate — so the screen is gone and this is what came out of it, moved somewhere
// Ask can call it.
//
// One box now does both, told apart by what was typed rather than by a mode switch:
// `classify` decides whether a line is a question or something to keep, and the button
// says which before you press it. Nothing is ever written on a guess — a command is
// proposed and confirmed, the same propose-then-confirm SmartScan runs for the camera,
// so a misread costs a tap rather than a wrong record.

enum class Intent { QUESTION, COMMAND }

// Everything the confirm needs: what to make, of what, when, for how much.
data class CaptureCmd(
    val type: String,
    val title: String,
    val due: String = "",
    val amount: Double? = null,
)

// What happened, and where it landed.
data class CaptureResult(val message: String, val moduleId: String)

val CAPTURE_TYPES = listOf("task", "idea", "contact", "habit", "bill")

// ---- reading the line ---------------------------------------------------------------

private val QUESTION_OPENERS = setOf(
    "what", "whats", "when", "whens", "where", "wheres", "who", "whos", "why", "how",
    "which", "whose", "is", "are", "was", "were", "do", "does", "did", "can", "could",
    "should", "will", "would", "am", "have", "has", "any", "anything", "tell", "show",
    "find", "search", "list", "summarize", "summarise", "explain", "compare",
)

// Verbs that mean "keep this", not "answer this". "remind me to call the landlord" and
// "what did I say about the landlord" open the same way and mean opposite things, which
// is why the first word decides rather than the subject.
private val COMMAND_OPENERS = setOf(
    "add", "new", "create", "make", "remind", "remember", "note", "log", "capture",
    "buy", "call", "email", "text", "book", "pay", "renew", "cancel", "schedule",
    "finish", "fix", "send", "order", "pick", "drop", "clean", "wash", "water",
    "check", "did", "went", "ran", "walked", "read", "took", "start", "started",
)

private fun firstWord(text: String): String =
    text.trim().substringBefore(' ').lowercase().trim('“', '"', '\'', ',', '.', '!', '?')

// A question is asked; anything else is kept. Deliberately conservative in one
// direction only: when a line could read either way it is treated as a question,
// because answering something that was meant as a note wastes a moment, while filing
// something that was meant as a question leaves a record you did not ask for.
fun classify(text: String): Intent {
    val t = text.trim()
    if (t.isEmpty()) return Intent.QUESTION
    if (t.endsWith("?")) return Intent.QUESTION
    val first = firstWord(t)
    // "did my workout" is a check-in; "did I pay the rent" is a question. The pronoun
    // after the verb is what separates them.
    if (first == "did" || first == "check") {
        val second = t.split(Regex("\\s+")).getOrNull(1)?.lowercase()
        if (second == "i" || second == "we" || second == "you") return Intent.QUESTION
    }
    if (first in COMMAND_OPENERS) return Intent.COMMAND
    if (first in QUESTION_OPENERS) return Intent.QUESTION
    // Neither: a bare fragment ("milk", "dentist tuesday"). Nobody asks a question that
    // way, and it is exactly what quick capture is for.
    return Intent.COMMAND
}

// A lightweight local due-date read: trailing "today" / "tomorrow" / "next week"
// / "in N days" set the date and are stripped from the title. Keyless — works
// without AI, and the AI path can still supply an exact DUE line.
fun extractDue(text: String): Pair<String, String> {
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

private val CHECKIN_OPENERS = setOf("did", "went", "ran", "walked", "read", "took", "finished")
private val TASK_OPENERS = setOf(
    "add", "new", "create", "make", "remind", "buy", "call", "email", "text", "book",
    "pay", "renew", "cancel", "schedule", "finish", "fix", "send", "order", "pick",
    "drop", "clean", "wash", "water", "start",
)

// The keyless guess. It replaces the old screen's "→ Task" / "→ Idea" buttons, which
// asked the question this answers; the confirm card still lets you change the answer,
// so being wrong costs one tap rather than a wrong record.
fun guessCapture(text: String): CaptureCmd {
    val raw = text.trim()
    val first = firstWord(raw)
    // A past-tense opener is something you did, not something to do.
    if (first in CHECKIN_OPENERS) return CaptureCmd("habit", stripOpener(raw, first))
    val (title, due) = extractDue(raw)
    if (due.isNotBlank() || first in TASK_OPENERS) {
        return CaptureCmd("task", stripOpener(title, first.takeIf { it in NOISE_OPENERS } ?: ""), due)
    }
    return CaptureCmd("idea", title)
}

// "add" and "remind me to" say what to do with the line, not what the line says; the
// verbs that are part of the thing itself ("call the landlord") are kept.
private val NOISE_OPENERS = setOf("add", "new", "create", "make", "remind", "remember", "note", "log", "capture")

private fun stripOpener(text: String, opener: String): String {
    if (opener.isEmpty()) return text.trim()
    var t = text.trim()
    if (!t.lowercase().startsWith(opener)) return t
    t = t.drop(opener.length).trim()
    // "remind me to X", "note down X", "did my X", "went for a X" — filler that says
    // what to do with the line rather than what the line is.
    t = t.removePrefix("me to ").removePrefix("me ").removePrefix("down ").removePrefix("that ")
        .removePrefix("to ").removePrefix("my ").removePrefix("for a ").removePrefix("for ")
        .removePrefix("a ").removePrefix("the ")
    return t.trim().ifEmpty { text.trim() }
}

// ---- writing the record ---------------------------------------------------------------

// Tasks carry the extracted due date; bills go through the real Finance model (monthly
// by default); ideas / contacts / habits as before.
fun createRecord(cmd: CaptureCmd): CaptureResult {
    val t = cmd.title.trim().replace("\n", " ")
    if (t.isEmpty()) return CaptureResult("", "")
    return when (cmd.type) {
        "task" -> {
            val (title, localDue) = extractDue(t)
            val due = cmd.due.ifBlank { localDue }
            val tasks = loadTasks()
            val id = (tasks.maxOfOrNull { it.id } ?: 0L) + 1
            saveTasks(tasks + Task(id, title, due = due))
            CaptureResult("Added task: “$title”" + if (due.isNotBlank()) " (due $due)" else "", "tasks")
        }
        "bill" -> {
            com.alekpeed.lifeos.finance.financeAddBill(t, cmd.amount ?: 0.0, cmd.due.ifBlank { today().toString() })
            CaptureResult("Added bill: “$t”", "finance")
        }
        "idea" -> { appendIdea(t); CaptureResult("Added idea: “$t”", "ideas") }
        "contact" -> {
            val cd = loadContacts()
            val id = (cd.contacts.maxOfOrNull { it.id } ?: 0L) + 1
            saveContacts(cd.copy(contacts = cd.contacts + Contact(id, t)))
            CaptureResult("Added contact: “$t”", "contacts")
        }
        "habit" -> {
            val habits = loadHabits()
            val match = habits.firstOrNull { t.contains(it.name, ignoreCase = true) || it.name.contains(t, ignoreCase = true) }
            if (match != null) {
                saveHabits(habits.map { if (it.name == match.name) it.copy(checkins = it.checkins + today()) else it })
                CaptureResult("Checked in: “${match.name}”", "habits")
            } else {
                saveHabits(habits + com.alekpeed.lifeos.habits.Habit(t, setOf(today())))
                CaptureResult("New habit + checked in: “$t”", "habits")
            }
        }
        else -> { appendIdea(t); CaptureResult("Added idea: “$t”", "ideas") }
    }
}

// ---- the AI parse ----------------------------------------------------------------------

const val PARSE_SYSTEM =
    "You classify a person's quick command into ONE action for a life-management app. " +
        "Respond with EXACTLY these lines and nothing else:\n" +
        "TYPE: <task|idea|contact|habit|bill>\n" +
        "TITLE: <the cleaned-up text of the thing>\n" +
        "DUE: <YYYY-MM-DD or blank>\n" +
        "AMOUNT: <number or blank>\n" +
        "Use 'habit' only for check-in style commands (e.g. 'did my workout'); 'contact' for a person to remember; " +
        "'bill' for a payment obligation (rent, electric — AMOUNT is its cost); 'task' for something to do; otherwise 'idea'. " +
        "Resolve relative dates (tomorrow, Friday) against TODAY given in the message."

fun parseAction(reply: String): CaptureCmd? {
    val type = Regex("(?im)^TYPE:\\s*(task|idea|contact|habit|bill)").find(reply)?.groupValues?.get(1)?.lowercase()
    val title = Regex("(?im)^TITLE:\\s*(.+)$").find(reply)?.groupValues?.get(1)?.trim()
    val due = Regex("(?im)^DUE:\\s*(\\d{4}-\\d{2}-\\d{2})").find(reply)?.groupValues?.get(1) ?: ""
    val amount = Regex("(?im)^AMOUNT:\\s*\\$?([0-9]+(?:\\.[0-9]+)?)").find(reply)?.groupValues?.get(1)?.toDoubleOrNull()
    return if (type != null && !title.isNullOrBlank()) CaptureCmd(type, title, due, amount) else null
}
