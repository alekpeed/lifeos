package com.alekpeed.lifeos.paper

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.data.DATA_SOURCES
import com.alekpeed.lifeos.data.countOf
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.education.loadEducation
import com.alekpeed.lifeos.habits.Habit
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.habits.saveHabits
import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.places.loadPlaces
import com.alekpeed.lifeos.recipes.loadRecipes
import com.alekpeed.lifeos.ai.AiClient
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private val OVERDUE = Color(0xFFD64545)

private const val EDITORIAL_SYSTEM =
    "You are the editor of The Daily Ledger, a one-person newspaper inside Life OS, a personal " +
        "life-management app. Write a concise 3-5 sentence editorial grounded ONLY in the FACTS provided — " +
        "never invent tasks, numbers, events, or a mood the facts don't support. If a RECENT ISSUE is " +
        "provided and a genuine callback adds something, reference it naturally; otherwise don't force it. " +
        "Warm, dry, a touch literary. Output only the prose — no headline, no preamble."

// A bounded packet of today's real facts for the editorial — the same discipline
// the web app uses: numbers and named items only, nothing to embellish beyond
// what's here. Recent finalized issues are appended so the AI can make a genuine
// callback (the web's "AI with continuity").
private fun buildEditorialContext(
    docket: List<Docket>,
    habits: List<Habit>,
    onThisDay: List<OnThisDay>,
    editorsPick: String,
): String = buildString {
    append("DATE: ${today()}\n")
    val overdue = docket.count { it.overdue }
    append("Docket: ${docket.size} item(s) due in the next 7 days")
    if (overdue > 0) append(", $overdue overdue")
    append(".\n")
    docket.take(8).forEach { append("- ${it.kind}: ${it.title.ifBlank { "(untitled)" }} (${it.date}${if (it.overdue) ", OVERDUE" else ""})\n") }
    val done = habits.count { today() in it.checkins }
    append("Habits: $done/${habits.size} checked in today.\n")
    if (onThisDay.isNotEmpty()) {
        append("On this day in past years: ")
        append(onThisDay.take(5).joinToString("; ") { "${it.kind} ${it.title} (${it.year})" })
        append(".\n")
    }
    if (editorsPick.isNotBlank()) append("Editor's pick to nudge: $editorsPick.\n")
    val recent = editorialHistory()
    if (recent.isNotEmpty()) {
        append("\nRECENT ISSUES (most recent first, for a possible callback — do not quote verbatim):\n")
        recent.forEach { (d, t) -> append("[$d] $t\n") }
    }
}

// One want-to-go place / unread book / untried recipe, chosen stably for the day.
private fun computeEditorsPick(): String {
    val cands = buildList {
        loadPlaces().places.filter { it.listType == "wantToGo" }.forEach { add("visit ${it.name}") }
        loadBooks().books.filter { it.status == "to_read" }.forEach { add("start reading ${it.title}") }
        loadRecipes().recipes.filter { it.cookLogs.isEmpty() }.forEach { add("cook ${it.title}") }
    }.filter { it.isNotBlank() }
    if (cands.isEmpty()) return ""
    val idx = (today().toEpochDays().mod(cands.size))
    return cands[idx]
}

private fun loadTodaysEditorial(): String {
    val raw = Storage.read("PaperEditorial") ?: return ""
    val parts = raw.split("|", limit = 2)
    return if (parts.getOrNull(0) == today().toString()) parts.getOrElse(1) { "" } else ""
}

private fun editorialHistory(): List<Pair<String, String>> =
    (Storage.read("PaperEditorialHistory") ?: "").lines().filter { it.isNotBlank() }.mapNotNull {
        val p = it.split("\t", limit = 2); if (p.size == 2) p[0] to p[1] else null
    }

private fun saveTodaysEditorial(text: String) {
    Storage.write("PaperEditorial", "${today()}|$text")
    // Prepend to history (most recent first), keep the last 3, one line each.
    val prior = editorialHistory().filterNot { it.first == today().toString() }
    val next = (listOf(today().toString() to text.replace("\n", " ").replace("\t", " ")) + prior).take(3)
    Storage.write("PaperEditorialHistory", next.joinToString("\n") { "${it.first}\t${it.second}" })
}

private data class Docket(val kind: String, val title: String, val date: String, val overdue: Boolean, val key: String)
private data class OnThisDay(val kind: String, val title: String, val year: String)

// The next 7 days of dated obligations: tasks, assignments, and document expiries.
private fun computeDocket(): List<Docket> {
    val now = today()
    val horizon = now.plusDays(7)
    fun soon(d: LocalDate?) = d != null && d <= horizon
    val out = mutableListOf<Docket>()
    loadTasksSafe().forEach { (id, title, due, done) ->
        val d = parseDateOrNull(due)
        if (!done && soon(d)) out.add(Docket("Task", title, due, d!! < now, "task:$id"))
    }
    loadEducation().assignments.forEach { a ->
        val d = parseDateOrNull(a.dueDate)
        if (!a.done && soon(d)) out.add(Docket("Assignment", a.title, a.dueDate, d!! < now, "asg:${a.id}"))
    }
    loadDocuments().documents.forEach { doc ->
        val d = parseDateOrNull(doc.expiryDate)
        if (soon(d)) out.add(Docket("Document", doc.title, doc.expiryDate, d!! < now, "doc:${doc.id}"))
    }
    return out.sortedBy { it.date }
}

// Minimal task read (id, title, due, done) without importing the tasks screen deps.
private fun loadTasksSafe(): List<TaskLite> =
    com.alekpeed.lifeos.tasks.loadTasks().map { TaskLite(it.id, it.title, it.due, it.done) }
private data class TaskLite(val id: Long, val title: String, val due: String, val done: Boolean)

private fun computeOnThisDay(): List<OnThisDay> {
    val md = today().toString().substring(5, 10)
    val thisYear = today().toString().take(4)
    fun on(date: String) = date.length >= 10 && date.substring(5, 10) == md && date.take(4) != thisYear
    val out = mutableListOf<OnThisDay>()
    loadMilestones().milestones.forEach { if (on(it.date)) out.add(OnThisDay("Milestone", it.title.ifBlank { "(untitled)" }, it.date.take(4))) }
    loadPlaces().places.forEach { p -> p.visitDates.forEach { d -> if (on(d)) out.add(OnThisDay("Visited", p.name.ifBlank { "(untitled)" }, d.take(4))) } }
    loadBooks().books.forEach { b -> if (on(b.finishedDate)) out.add(OnThisDay("Finished", b.title.ifBlank { "(untitled)" }, b.finishedDate.take(4))) }
    loadRecipes().recipes.forEach { r -> r.cookLogs.forEach { l -> if (on(l.date)) out.add(OnThisDay("Cooked", r.title.ifBlank { "(untitled)" }, l.date.take(4))) } }
    return out.sortedByDescending { it.year }
}

private fun loadChecklist(): Set<String> {
    val raw = Storage.read("PaperChecklist") ?: return emptySet()
    val parts = raw.split("|", limit = 2)
    if (parts.getOrNull(0) != today().toString()) return emptySet()
    return parts.getOrElse(1) { "" }.split(",").filter { it.isNotBlank() }.toSet()
}

private fun saveChecklist(keys: Set<String>) {
    Storage.write("PaperChecklist", "${today()}|${keys.joinToString(",")}")
}

@Composable
fun DailyPaperScreen() {
    var habits by remember { mutableStateOf(loadHabits()) }
    var checked by remember { mutableStateOf(loadChecklist()) }
    val docket = remember { computeDocket() }
    val onThisDay = remember { computeOnThisDay() }
    val editorsPick = remember { computeEditorsPick() }
    val almanac = remember { DATA_SOURCES.map { it.label to countOf(it.key) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(8) }

    val scope = rememberCoroutineScope()
    val hasKey = remember { AiClient.hasKey() }
    var editorial by remember { mutableStateOf(loadTodaysEditorial()) }
    var writing by remember { mutableStateOf(false) }

    fun writeEditorial() {
        if (writing) return
        writing = true
        scope.launch {
            val reply = AiClient.ask(EDITORIAL_SYSTEM, buildEditorialContext(docket, habits, onThisDay, editorsPick), maxTokens = 400)
            if (!reply.isError) { editorial = reply.text; saveTodaysEditorial(reply.text) }
            else editorial = reply.text
            writing = false
        }
    }

    // Weather block: reuses the city Tools/Today saved; silent when unset/offline.
    var weather by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val city = com.alekpeed.lifeos.Storage.read("WeatherCity")?.trim().orEmpty()
        if (city.isNotEmpty()) {
            com.alekpeed.lifeos.integrations.WeatherClient.forCity(city).onSuccess { w ->
                weather = "${w.place} — ${w.tempF}°F, ${w.description}. High ${w.highF}°, low ${w.lowF}°."
            }
        }
    }

    // The whole issue as plain text — shared by the Telegram digest and the PDF.
    fun issueText(): String = buildString {
        append("📰 The Daily Ledger — ${today()}\n")
        weather?.let { append("\n☀️ $it\n") }
        if (editorial.isNotBlank()) append("\n$editorial\n")
        if (editorsPick.isNotBlank()) append("\n🎯 Maybe today: $editorsPick\n")
        if (docket.isNotEmpty()) {
            append("\nON THE DOCKET\n")
            docket.forEach { append("• ${if (it.overdue) "OVERDUE " else ""}${it.title.ifBlank { "(untitled)" }} (${it.kind}, ${it.date})\n") }
        }
        val pending = habits.filter { today() !in it.checkins }
        if (pending.isNotEmpty()) append("\nHABITS WAITING: ${pending.joinToString(", ") { it.name }}\n")
        if (onThisDay.isNotEmpty()) {
            append("\nON THIS DAY\n")
            onThisDay.forEach { append("• ${it.title} (${it.year})\n") }
        }
    }

    // Send the whole issue to Telegram as a plain-text digest.
    var tgStatus by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    fun sendDigest() {
        if (sending) return
        sending = true; tgStatus = null
        val digest = issueText()
        scope.launch {
            com.alekpeed.lifeos.integrations.TelegramClient.send(digest)
                .onSuccess { tgStatus = "Sent ✓" }
                .onFailure { tgStatus = it.message }
            sending = false
        }
    }

    fun toggleCheck(key: String, on: Boolean) {
        checked = if (on) checked + key else checked - key
        saveChecklist(checked)
    }
    fun checkInHabit(h: Habit) {
        val t = today()
        val updated = if (t in h.checkins) h.copy(checkins = h.checkins - t) else h.copy(checkins = h.checkins + t)
        habits = habits.map { if (it.name == h.name) updated else it }
        saveHabits(habits)
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("The Daily Ledger", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(today().toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (Native.supportsPdfExport) {
                TextButton(onClick = { Native.exportTextAsPdf("The Daily Ledger — ${today()}", issueText()) }) { Text("🖨 PDF") }
            }
            if (com.alekpeed.lifeos.integrations.TelegramClient.isConfigured()) {
                TextButton(onClick = { sendDigest() }, enabled = !sending) { Text(if (sending) "Sending…" else "📨 Telegram") }
            }
        }
        tgStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            weather?.let { w ->
                item { Head("The Sky Today") }
                item { Text(w, style = MaterialTheme.typography.bodyMedium) }
            }
            item { Head("Editorial") }
            item {
                when {
                    !hasKey -> Muted("Add an AI key in Settings and the editor will write a short daily column, grounded only in the facts below.")
                    writing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp).width(16.dp))
                        Spacer(Modifier.width(10.dp)); Text("Writing…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    editorial.isNotBlank() -> Column {
                        Text(editorial, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { writeEditorial() }) { Text("↻ Regenerate") }
                    }
                    else -> Button(onClick = { writeEditorial() }) { Text("Write today's editorial") }
                }
            }
            if (editorsPick.isNotBlank()) {
                item { Head("Editor's Pick") }
                item { Text("Maybe today: $editorsPick.", style = MaterialTheme.typography.bodyMedium) }
            }

            item { Head("On the Docket") }
            if (docket.isEmpty()) item { Muted("The docket is clear for the next seven days — a rare and beautiful thing.") }
            else docket.forEach { d ->
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = d.key in checked, onCheckedChange = { toggleCheck(d.key, it) })
                        if (d.overdue) Text("OVERDUE ", style = MaterialTheme.typography.labelSmall, color = OVERDUE, fontWeight = FontWeight.Bold)
                        Text(d.kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(84.dp))
                        Text(
                            d.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                            textDecoration = if (d.key in checked) TextDecoration.LineThrough else null,
                        )
                        Text(d.date, style = MaterialTheme.typography.labelSmall, color = if (d.overdue) OVERDUE else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Head("Today's Habits") }
            if (habits.isEmpty()) item { Muted("No habits yet.") }
            else habits.forEach { h ->
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = today() in h.checkins, onCheckedChange = { checkInHabit(h) })
                        Text(h.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (h.streak > 0) Text("🔥 ${h.streak}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (onThisDay.isNotEmpty()) {
                item { Head("On This Day") }
                onThisDay.forEach { o ->
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(o.kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(84.dp))
                            Text(o.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(o.year, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { Head("The Almanac") }
            item {
                Text(
                    almanac.joinToString("   ") { "${it.first} ${it.second}" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Head(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
