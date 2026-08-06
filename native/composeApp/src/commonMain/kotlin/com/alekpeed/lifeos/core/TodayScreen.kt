package com.alekpeed.lifeos.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.ExpiryState
import com.alekpeed.lifeos.documents.expiryState
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.education.loadEducation
import com.alekpeed.lifeos.finance.financeBills
import com.alekpeed.lifeos.habits.Habit
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.habits.saveHabits
import com.alekpeed.lifeos.ideas.loadIdeas
import com.alekpeed.lifeos.integrations.WeatherClient
import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.places.loadPlaces
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks

// One due-soon line from another module, tappable to jump there.
private data class DueLine(val icon: String, val title: String, val meta: String, val moduleId: String, val urgent: Boolean)

private fun alsoDue(): List<DueLine> {
    val now = today()
    val soon = now.plusDays(7)
    val out = mutableListOf<DueLine>()
    financeBills().filter { !it.settled }.forEach { b ->
        val due = parseDateOrNull(b.dueDate) ?: return@forEach
        if (due <= soon) out.add(DueLine("💵", b.name, relativeLabel(due) + if (b.autopay) " · autopay" else "", "finance", due < now && !b.autopay))
    }
    loadEducation().assignments.filter { !it.done }.forEach { a ->
        val due = parseDateOrNull(a.dueDate) ?: return@forEach
        if (due <= soon) out.add(DueLine("🎓", a.title, relativeLabel(due), "education", due < now))
    }
    loadDocuments().documents.forEach { d ->
        when (expiryState(d)) {
            ExpiryState.EXPIRED -> out.add(DueLine("📄", d.title, "expired", "documents", true))
            ExpiryState.SOON -> out.add(DueLine("📄", d.title, "expires soon", "documents", false))
            else -> {}
        }
    }
    return out.sortedBy { !it.urgent }
}

// "On this day" — same month-day in an earlier year, across milestones, book
// finishes, and place visits.
private fun onThisDay(): List<String> {
    val now = today()
    val mmdd = now.toString().substring(5)
    val year = now.toString().take(4)
    fun hit(date: String) = date.length >= 10 && date.substring(5) == mmdd && !date.startsWith(year)
    val out = mutableListOf<String>()
    loadMilestones().milestones.filter { hit(it.date) }.forEach { out.add("🏆 ${it.title} (${it.date.take(4)})") }
    com.alekpeed.lifeos.books.loadBooks().books.filter { hit(it.finishedDate) }.forEach { out.add("📚 Finished ${it.title} (${it.finishedDate.take(4)})") }
    loadPlaces().places.forEach { p ->
        p.visitDates.filter { hit(it) }.forEach { out.add("📍 ${p.name} (${it.take(4)})") }
    }
    return out
}

// The real "what needs you today" screen: weather up top, overdue + due-today
// tasks (checkable right here), habits you haven't checked in yet, due-soon
// items from Finance/Education/Documents (tap to jump), "on this day" echoes,
// and a surprise-me pull from the idea pile. Reads live storage every open.
@Composable
fun TodayScreen() {
    val tasks = remember { mutableStateListOf<Task>().apply { addAll(loadTasks()) } }
    val habits = remember { mutableStateListOf<Habit>().apply { addAll(loadHabits()) } }
    fun persistTasks() = saveTasks(tasks)
    fun persistHabits() = saveHabits(habits)

    val overdue = tasks.filter { val d = it.dueDate(); !it.done && d != null && d < today() }.sortedBy { it.dueDate() }
    val dueToday = tasks.filter { !it.done && it.dueDate() == today() }
    val pendingHabits = habits.filter { !it.checkedInToday }
    val also = remember { alsoDue() }
    val echoes = remember { onThisDay() }

    // Weather: reuses the city Tools saved; quiet when unset or offline.
    var weather by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val city = Storage.read("WeatherCity")?.trim().orEmpty()
        if (city.isNotEmpty()) {
            WeatherClient.forCity(city).onSuccess { w ->
                weather = "${w.place} · ${w.tempF}°F ${w.description} · H ${w.highF}° / L ${w.lowF}°"
            }
        }
    }

    // Surprise me: one thing you said you wanted to get to, from anywhere in the app —
    // a place on the want-to-go list, an unread book, an untried recipe, a bucket-list
    // goal, or a stashed idea. Tapping it goes there.
    var surprise by remember { mutableStateOf<Pick?>(null) }
    var surpriseTried by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (Native.supportsTts) {
                TextButton(onClick = {
                    val speech = buildString {
                        append("Today. ")
                        append(if (overdue.isEmpty()) "" else "${overdue.size} overdue: ${overdue.joinToString(". ") { it.title }}. ")
                        append(if (dueToday.isEmpty()) "" else "${dueToday.size} due today: ${dueToday.joinToString(". ") { it.title }}. ")
                        append(if (pendingHabits.isEmpty()) "All habits checked in." else "${pendingHabits.size} habits waiting: ${pendingHabits.joinToString(". ") { it.name }}.")
                    }
                    Native.speak(speech)
                }) { Text("🔊 Read aloud") }
            }
        }
        weather?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))

        if (overdue.isEmpty() && dueToday.isEmpty() && pendingHabits.isEmpty() && also.isEmpty()) {
            Text(
                "Clear. Nothing due today.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (overdue.isNotEmpty()) {
                item {
                    Section("OVERDUE", MaterialTheme.colorScheme.error) {
                        overdue.forEach { task ->
                            TaskRow(task, showDue = true) { checked ->
                                val i = tasks.indexOfFirst { it.id == task.id }
                                if (i >= 0) { tasks[i] = task.copy(status = if (checked) "done" else "not_started", completedDate = if (checked) today().toString() else ""); persistTasks() }
                            }
                        }
                    }
                }
            }
            if (dueToday.isNotEmpty()) {
                item {
                    Section("DUE TODAY", MaterialTheme.colorScheme.primary) {
                        dueToday.forEach { task ->
                            TaskRow(task, showDue = false) { checked ->
                                val i = tasks.indexOfFirst { it.id == task.id }
                                if (i >= 0) { tasks[i] = task.copy(status = if (checked) "done" else "not_started", completedDate = if (checked) today().toString() else ""); persistTasks() }
                            }
                        }
                    }
                }
            }
            if (pendingHabits.isNotEmpty()) {
                item {
                    Section("HABITS", MaterialTheme.colorScheme.onSurfaceVariant) {
                        pendingHabits.forEach { habit ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("🔥 ${habit.streak}", modifier = Modifier.padding(end = 10.dp))
                                Text(habit.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                TextButton(onClick = {
                                    val i = habits.indexOfFirst { it.name == habit.name }
                                    if (i >= 0) { habits[i] = habit.copy(checkins = habit.checkins + today()); persistHabits() }
                                }) { Text("Check in") }
                            }
                        }
                    }
                }
            }
            if (also.isNotEmpty()) {
                item {
                    Section("ALSO DUE", MaterialTheme.colorScheme.onSurfaceVariant) {
                        also.take(8).forEach { d ->
                            Row(
                                Modifier.fillMaxWidth().clickable { Nav.open(d.moduleId) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(d.icon, modifier = Modifier.padding(end = 8.dp))
                                Text(d.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    d.meta, style = MaterialTheme.typography.labelMedium,
                                    color = if (d.urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (echoes.isNotEmpty()) {
                item {
                    Section("ON THIS DAY", MaterialTheme.colorScheme.onSurfaceVariant) {
                        echoes.take(6).forEach {
                            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { surprise = surprisePool().randomOrNull(); surpriseTried = true }) {
                        Text("🎲 Surprise me")
                    }
                    val pick = surprise
                    when {
                        pick != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${pick.kind}: ${pick.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { Nav.open(pick.moduleId) }) { Text("Go →") }
                        }
                        // An empty pool is a real answer, not a broken button — say so.
                        surpriseTried -> Text(
                            "Nothing in the queue — add a want-to-go place, an unread book, an untried recipe, or an idea.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, color: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = color)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun TaskRow(task: Task, showDue: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = task.done, onCheckedChange = onToggle)
        Text(task.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        task.dueDate()?.let { d ->
            if (showDue) {
                Text(
                    relativeLabel(d),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// Everything you've told the app you mean to get to, in one list.
private data class Pick(val kind: String, val title: String, val moduleId: String)

private fun surprisePool(): List<Pick> = buildList {
    com.alekpeed.lifeos.places.loadPlaces().let { p ->
        p.places.filter { it.listType == "wantToGo" }.forEach { add(Pick("Place to visit", it.name.ifBlank { "(untitled)" }, "places")) }
        p.bucket.filter { !it.done }.forEach { add(Pick("Bucket-list goal", it.title.ifBlank { "(untitled)" }, "places")) }
    }
    com.alekpeed.lifeos.books.loadBooks().books.filter { it.status == "to_read" }
        .forEach { add(Pick("Book to read", it.title.ifBlank { "(untitled)" }, "books")) }
    com.alekpeed.lifeos.recipes.loadRecipes().recipes.filter { it.cookLogs.isEmpty() }
        .forEach { add(Pick("Recipe to try", it.title.ifBlank { "(untitled)" }, "recipes")) }
    loadIdeas().ideas.filter { !it.archived }
        .forEach { add(Pick("Idea", it.text.ifBlank { "(untitled)" }, "ideas")) }
}
