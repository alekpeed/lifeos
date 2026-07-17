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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.habits.saveHabits
import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.places.loadPlaces
import com.alekpeed.lifeos.recipes.loadRecipes
import kotlinx.datetime.LocalDate

private val OVERDUE = Color(0xFFD64545)

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
    val almanac = remember { DATA_SOURCES.map { it.label to countOf(it.key) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(8) }

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
        Text("The Daily Ledger", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(today().toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            item {
                Text(
                    "The AI editorial column joins the paper when the recap/editorial prompt is wired to the AI layer.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp),
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
