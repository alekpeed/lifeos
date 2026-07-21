package com.alekpeed.lifeos.education

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.ui.DateField
import com.alekpeed.lifeos.ui.usDate
import com.alekpeed.lifeos.ui.SaveToast

private val OVERDUE = Color(0xFFE05C5C)

@Composable
fun EducationScreen() {
    var data by remember { mutableStateOf(loadEducation()) }
    var counter by remember {
        mutableStateOf(
            maxOf(
                data.semesters.maxOfOrNull { it.id } ?: 0L,
                data.courses.maxOfOrNull { it.id } ?: 0L,
                data.assignments.maxOfOrNull { it.id } ?: 0L,
                data.assignments.flatMap { it.progressLogs }.maxOfOrNull { it.id } ?: 0L,
            ),
        )
    }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: EducationData) { data = d; saveEducation(d); SaveToast.show() }

    var tab by remember { mutableStateOf("coursework") }
    var semesterId by remember { mutableStateOf<Long?>(null) }
    var courseId by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Education", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == "coursework", onClick = { tab = "coursework" }, label = { Text("Coursework") })
            FilterChip(selected = tab == "summary", onClick = { tab = "summary" }, label = { Text("GPA & Time") })
        }
        Spacer(Modifier.height(12.dp))

        when {
            tab == "summary" -> SummaryView(data)
            courseId != null -> AssignmentsView(
                data, ::save, ::freshId, courseId!!,
                semesterName = data.semesters.firstOrNull { it.id == semesterId }?.name ?: "",
                onBack = { courseId = null },
            )
            semesterId != null -> CoursesView(
                data, ::save, ::freshId, semesterId!!,
                onBack = { semesterId = null },
                onOpenCourse = { courseId = it },
            )
            else -> SemestersView(data, ::save, ::freshId, onOpen = { semesterId = it })
        }
    }
}

// ---------- Semesters ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SemestersView(
    data: EducationData,
    save: (EducationData) -> Unit,
    freshId: () -> Long,
    onOpen: (Long) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val countBySem = data.courses.groupingBy { it.semesterId }.eachCount()

    QuickAdd("+ New semester", input, { input = it }) {
        val name = input.trim()
        if (name.isNotEmpty()) {
            save(data.copy(semesters = data.semesters + Semester(freshId(), name)))
            input = ""
        }
    }
    Spacer(Modifier.height(12.dp))

    if (data.semesters.isEmpty()) {
        Muted("No semesters yet.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(data.semesters, key = { it.id }) { s ->
            Card(onClick = { onOpen(s.id) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name.ifBlank { "(untitled semester)" }, style = MaterialTheme.typography.bodyLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val range = listOf(s.startDate, s.endDate).filter { it.isNotBlank() }.map { usDate(it) }
                            if (range.isNotEmpty()) Chip(range.joinToString(" – "))
                            val n = countBySem[s.id] ?: 0
                            Chip("$n course${if (n == 1) "" else "s"}")
                        }
                    }
                    DeleteBtn {
                        save(data.copy(semesters = data.semesters.filterNot { it.id == s.id }))
                    }
                }
            }
        }
    }
}

// ---------- Courses ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoursesView(
    data: EducationData,
    save: (EducationData) -> Unit,
    freshId: () -> Long,
    semesterId: Long,
    onBack: () -> Unit,
    onOpenCourse: (Long) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Long?>(null) }
    val semester = data.semesters.firstOrNull { it.id == semesterId }
    val courses = data.courses.filter { it.semesterId == semesterId }
    val doneByCourse = data.assignments.groupBy { it.courseId }

    Toolbar("← Semesters", semester?.name ?: "Semester", onBack)
    Spacer(Modifier.height(8.dp))
    QuickAdd("+ New course", input, { input = it }) {
        val name = input.trim()
        if (name.isNotEmpty()) {
            save(data.copy(courses = data.courses + Course(freshId(), semesterId, name)))
            input = ""
        }
    }
    Spacer(Modifier.height(12.dp))

    if (courses.isEmpty()) { Muted("No courses in this semester yet."); return }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(courses, key = { it.id }) { c ->
            val all = doneByCourse[c.id].orEmpty()
            val done = all.count { it.done }
            Column {
                Card(onClick = { selected = if (selected == c.id) null else c.id }) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name.ifBlank { "(untitled course)" }, style = MaterialTheme.typography.bodyLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (c.grade.isNotBlank()) Chip(c.grade)
                            Chip("${trimNum(c.credits)} cr")
                            if (all.isNotEmpty()) Chip("$done/${all.size} done")
                        }
                    }
                }
                if (selected == c.id) CourseDetail(data, save, c, onOpenCourse) { selected = null }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseDetail(
    data: EducationData,
    save: (EducationData) -> Unit,
    course: Course,
    onOpenAssignments: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    fun patch(f: (Course) -> Course) =
        save(data.copy(courses = data.courses.map { if (it.id == course.id) f(it) else it }))

    Panel {
        Label("Name")
        EditField(course.name, "Course name") { v -> patch { it.copy(name = v.replace("\n", " ")) } }
        Label("Credits")
        EditField(if (course.credits == 0.0) "" else trimNum(course.credits), "3") { v ->
            patch { it.copy(credits = v.toDoubleOrNull() ?: 0.0) }
        }
        Label("Final grade")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GRADE_OPTIONS.forEach { g ->
                FilterChip(
                    selected = course.grade == g,
                    onClick = { patch { it.copy(grade = g) } },
                    label = { Text(if (g.isBlank()) "—" else g) },
                )
            }
        }
        Label("Reading-list tag (tag Links entries to build this course's list)")
        EditField(course.readingListTag, "e.g. econ101") { v -> patch { it.copy(readingListTag = v.trim()) } }
        val readingTag = course.readingListTag.trim()
        if (readingTag.isNotBlank()) {
            val tagged = remember(readingTag) {
                com.alekpeed.lifeos.links.loadLinks().links.filter { l -> l.tags.any { it.equals(readingTag, ignoreCase = true) } }
            }
            if (tagged.isEmpty()) {
                Text("No Links tagged \"$readingTag\" yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                tagged.forEach { l ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("• ${l.title.ifBlank { l.url }}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        if (l.url.isNotBlank()) TextButton(onClick = { com.alekpeed.lifeos.platform.Native.openUrl(l.url) }) { Text("Open") }
                    }
                }
            }
        }
        Label("Notes")
        EditField(course.notes, "Course notes", singleLine = false) { v -> patch { it.copy(notes = v) } }

        Label("Key dates")
        course.keyDates.sortedBy { it.date }.forEach { kd ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${kd.label}: ${usDate(kd.date)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { patch { it.copy(keyDates = it.keyDates.filterNot { k -> k == kd }) } }) { Text("×") }
            }
        }
        KeyDateAdder { label, date -> patch { it.copy(keyDates = it.keyDates + KeyDate(label, date)) } }

        Spacer(Modifier.height(10.dp))
        Button(onClick = { onOpenAssignments(course.id) }) { Text("View assignments →") }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = {
            save(data.copy(courses = data.courses.filterNot { it.id == course.id }))
            onDeleted()
        }) { Text("Delete course", color = OVERDUE) }
    }
}

@Composable
private fun KeyDateAdder(onAdd: (String, String) -> Unit) {
    var label by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    Column {
        DateField(date) { v -> date = v }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(label, { label = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Label") })
            Spacer(Modifier.width(6.dp))
            Button(onClick = {
                if (label.isNotBlank() && parseDateOrNull(date) != null) { onAdd(label.trim(), date); label = ""; date = "" }
            }) { Text("+") }
        }
    }
}

// ---------- Assignments ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignmentsView(
    data: EducationData,
    save: (EducationData) -> Unit,
    freshId: () -> Long,
    courseId: Long,
    semesterName: String,
    onBack: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Long?>(null) }
    val course = data.courses.firstOrNull { it.id == courseId }
    val assignments = data.assignments.filter { it.courseId == courseId }
        .sortedBy { it.dueDate.ifBlank { "9999" } }

    Toolbar("← Courses", listOf(semesterName, course?.name ?: "Course").filter { it.isNotBlank() }.joinToString(" · "), onBack)
    Spacer(Modifier.height(8.dp))
    QuickAdd("+ New assignment", input, { input = it }) {
        val title = input.trim()
        if (title.isNotEmpty()) {
            save(data.copy(assignments = data.assignments + Assignment(freshId(), courseId, title)))
            input = ""
        }
    }
    Spacer(Modifier.height(12.dp))

    if (assignments.isEmpty()) { Muted("No assignments yet."); return }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(assignments, key = { it.id }) { a ->
            fun patch(f: (Assignment) -> Assignment) =
                save(data.copy(assignments = data.assignments.map { if (it.id == a.id) f(it) else it }))
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { selected = if (selected == a.id) null else a.id }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = a.done, onCheckedChange = { c -> patch { it.copy(status = if (c) "done" else "not_started") } })
                    Text(
                        a.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (a.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                    )
                }
                val chips = buildList {
                    a.dueDate.takeIf { it.isNotBlank() }?.let { add(usDate(it) to (parseDateOrNull(it)?.let { d -> d < today() } == true && !a.done)) }
                    if (a.status == "in_progress") add("${a.percentComplete}%" to false)
                    if (a.timeSpentMinutes > 0) add("${trimNum(a.timeSpentMinutes / 60.0)}h" to false)
                    a.grade?.let { add("$it%" to false) }
                    if (!a.done) pacingStatusFor(a)?.takeIf { it.gap > 0 }?.let {
                        add("${it.gap} ${a.pacingUnit} behind" to true)
                    }
                }
                if (chips.isNotEmpty()) {
                    FlowRow(Modifier.padding(start = 44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEach { (t, warn) -> Chip(t, if (warn) OVERDUE else null) }
                    }
                }
                if (selected == a.id) AssignmentDetail(data, save, a) { selected = null }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignmentDetail(
    data: EducationData,
    save: (EducationData) -> Unit,
    a: Assignment,
    onDeleted: () -> Unit,
) {
    fun patch(f: (Assignment) -> Assignment) =
        save(data.copy(assignments = data.assignments.map { if (it.id == a.id) f(it) else it }))

    Panel {
        Label("Title")
        EditField(a.title, "Title") { v -> patch { it.copy(title = v.replace("\n", " ")) } }
        Label("Due date")
        DateField(a.dueDate) { v -> patch { it.copy(dueDate = v) } }
        Label("Status")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ASSIGNMENT_STATUSES.forEach { (v, lbl) ->
                FilterChip(selected = a.status == v, onClick = { patch { it.copy(status = v) } }, label = { Text(lbl) })
            }
        }
        Label("% complete: ${a.percentComplete}%")
        Slider(
            value = a.percentComplete.toFloat(), onValueChange = { patch { c -> c.copy(percentComplete = it.toInt()) } },
            valueRange = 0f..100f,
        )
        Label("Time spent (minutes)")
        EditField(if (a.timeSpentMinutes == 0) "" else a.timeSpentMinutes.toString(), "minutes") { v ->
            patch { it.copy(timeSpentMinutes = v.toIntOrNull() ?: 0) }
        }
        Label("Grade (%)")
        EditField(a.grade?.toString() ?: "", "0-100") { v ->
            patch { it.copy(grade = v.toIntOrNull()) }
        }

        Spacer(Modifier.height(10.dp))
        Text("Academic pacing", style = MaterialTheme.typography.titleSmall)
        PacingSection(a) { f -> patch(f) }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            save(data.copy(assignments = data.assignments.filterNot { it.id == a.id }))
            onDeleted()
        }) { Text("Delete assignment", color = OVERDUE) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PacingSection(a: Assignment, patch: ((Assignment) -> Assignment) -> Unit) {
    val unit = a.pacingUnit
    Label("Total due")
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = a.pacingTarget?.toString() ?: "", onValueChange = { v -> patch { it.copy(pacingTarget = v.toIntOrNull()) } },
            modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Total $unit") },
        )
        Spacer(Modifier.width(8.dp))
        listOf("pages", "words").forEach { u ->
            FilterChip(selected = unit == u, onClick = { patch { it.copy(pacingUnit = u) } }, label = { Text(u) })
            Spacer(Modifier.width(4.dp))
        }
    }

    val status = pacingStatusFor(a)
    if (status != null) {
        val msg = if (status.gap > 0)
            "You wanted ${status.checkpoint.targetByThen} $unit done by ${usDate(status.checkpoint.date)} — you've logged ${status.loggedTotal}. ${status.gap} $unit short. Still on track, or just forgot to log?"
        else
            "On track — ${status.loggedTotal} $unit logged, ${status.checkpoint.targetByThen} was the target by ${usDate(status.checkpoint.date)}."
        Spacer(Modifier.height(4.dp))
        Text(msg, style = MaterialTheme.typography.bodySmall, color = if (status.gap > 0) OVERDUE else MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Label("Checkpoints (your own intention)")
    a.paceCheckpoints.sortedBy { it.date }.forEach { cp ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("By ${usDate(cp.date)}: ${cp.targetByThen} $unit", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { patch { it.copy(paceCheckpoints = it.paceCheckpoints.filterNot { c -> c == cp }) } }) { Text("×") }
        }
    }
    CheckpointAdder(unit) { date, target -> patch { it.copy(paceCheckpoints = it.paceCheckpoints + Checkpoint(date, target)) } }

    Label("Progress log — ${a.progressLogs.sumOf { it.unitsAdded }} $unit logged so far")
    a.progressLogs.sortedByDescending { it.date }.forEach { log ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("+${log.unitsAdded} $unit · ${usDate(log.date)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { patch { it.copy(progressLogs = it.progressLogs.filterNot { l -> l.id == log.id } ) } }) { Text("×") }
        }
    }
    LogAdder(unit) { units ->
        patch {
            val id = (it.progressLogs.maxOfOrNull { l -> l.id } ?: 0L) + 1
            it.copy(progressLogs = it.progressLogs + ProgressLog(id, today().toString(), units))
        }
    }
}

@Composable
private fun CheckpointAdder(unit: String, onAdd: (String, Int) -> Unit) {
    var date by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    Column {
        DateField(date) { v -> date = v }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(target, { target = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("$unit by then") })
            Spacer(Modifier.width(6.dp))
            Button(onClick = {
                val t = target.toIntOrNull()
                if (parseDateOrNull(date) != null && t != null) { onAdd(date, t); date = ""; target = "" }
            }) { Text("+") }
        }
    }
}

@Composable
private fun LogAdder(unit: String, onLog: (Int) -> Unit) {
    var units by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(units, { units = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("$unit added today") })
        Spacer(Modifier.width(8.dp))
        Button(onClick = { units.toIntOrNull()?.takeIf { it >= 1 }?.let { onLog(it); units = "" } }) { Text("Log") }
    }
}

// ---------- GPA & Time summary ----------

@Composable
private fun SummaryView(data: EducationData) {
    Column(Modifier.fillMaxSize()) {
        val gpa = runningGpa(data.courses)
        if (gpa != null) {
            Text("Running GPA: ${format2(gpa.first)}", style = MaterialTheme.typography.titleLarge)
            Muted("Across ${gpa.second} graded course${if (gpa.second == 1) "" else "s"}, weighted by credits.")
        } else {
            Muted("No final grades entered yet — set one in a course's detail view.")
        }
        Spacer(Modifier.height(16.dp))
        Text("Time invested vs. grade", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))

        val courseById = data.courses.associateBy { it.id }
        val rows = data.assignments.filter { it.grade != null }
            .sortedByDescending { it.timeSpentMinutes }
        if (rows.isEmpty()) {
            Muted("No graded assignments with time logged yet.")
            return
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            HeaderCell("Assignment", 2f); HeaderCell("Course", 2f); HeaderCell("Time (h)", 1f); HeaderCell("Grade", 1f)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    BodyCell(r.title.ifBlank { "(untitled)" }, 2f)
                    BodyCell(courseById[r.courseId]?.name ?: "(unknown)", 2f)
                    BodyCell(trimNum(r.timeSpentMinutes / 60.0), 1f)
                    BodyCell("${r.grade}%", 1f)
                }
            }
        }
    }
}

// ---------- Shared bits ----------

@Composable
private fun QuickAdd(placeholder: String, value: String, onChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value, onChange, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text(placeholder) })
        Spacer(Modifier.width(10.dp))
        Button(onClick = onAdd) { Text("Add") }
    }
}

@Composable
private fun Toolbar(back: String, title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text(back) }
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Card(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) { content() }
}

@Composable
private fun Chip(text: String, color: Color? = null) {
    Text(
        text, style = MaterialTheme.typography.labelSmall,
        color = color ?: MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DeleteBtn(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("×") }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun EditField(value: String, placeholder: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine, placeholder = { Text(placeholder) },
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(weight))
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BodyCell(text: String, weight: Float) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(weight))
}

private fun trimNum(n: Double): String {
    val r = ((n * 10).toLong()).toDouble() / 10.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}

private fun format2(n: Double): String {
    val scaled = (n * 100).toLong()
    val whole = scaled / 100
    val frac = (scaled % 100).let { if (it < 0) -it else it }
    return "$whole.${frac.toString().padStart(2, '0')}"
}
