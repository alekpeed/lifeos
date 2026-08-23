package com.alekpeed.lifeos.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.data.relativeLabelOf
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.links.loadLinks
import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks
import com.alekpeed.lifeos.ui.DateField
import com.alekpeed.lifeos.ui.SaveToast
import com.alekpeed.lifeos.ui.TagField

// Projects. A list of the things you are actually working on, and behind each one the
// tasks that belong to it plus whatever else it has gathered.

@Composable
fun ProjectsScreen() {
    var data by remember { mutableStateOf(loadProjects()) }
    var tasks by remember { mutableStateOf(loadTasks()) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var showClosed by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    fun persist(next: ProjectsData) {
        data = next
        saveProjects(next)
        SaveToast.show()
    }

    fun persistTasks(next: List<Task>) {
        tasks = next
        saveTasks(next)
        SaveToast.show()
    }

    // Re-read both after an operation that writes them behind our back (delete releases
    // tasks; complete stamps the project).
    fun reload() {
        data = loadProjects()
        tasks = loadTasks()
    }

    val current = openId?.let { id -> data.projects.firstOrNull { it.id == id } }
    if (current != null) {
        ProjectDetail(
            project = current,
            tasks = tasks,
            onBack = { openId = null },
            onPatch = { change ->
                persist(data.copy(projects = data.projects.map { if (it.id == current.id) change(it) else it }))
            },
            onTasks = { persistTasks(it) },
            onComplete = { completeProject(current.id); reload(); SaveToast.show("Project finished") },
            onDelete = {
                val freed = deleteProject(current.id)
                reload()
                openId = null
                SaveToast.show(if (freed > 0) "Deleted · $freed task(s) released" else "Deleted")
            },
        )
        return
    }

    val shown = data.projects
        .filter { showClosed || it.open }
        .sortedWith(compareBy({ !it.open }, { it.target()?.toString() ?: "9999" }, { it.name.lowercase() }))

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Button({ adding = true }, Modifier.fillMaxWidth()) { Text("+ New project") }
        Spacer(Modifier.height(10.dp))

        val closed = data.projects.count { !it.open }
        if (closed > 0) {
            FilterChip(showClosed, { showClosed = !showClosed }, { Text("Show finished ($closed)") })
            Spacer(Modifier.height(8.dp))
        }

        if (shown.isEmpty()) {
            Text(
                if (data.projects.isEmpty()) {
                    "No projects yet. A project gathers the tasks, documents, links and " +
                        "people that belong to one piece of work."
                } else {
                    "Nothing open."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { p ->
                ProjectCard(p, projectProgress(p, tasks)) { openId = p.id }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false; newName = "" },
            title = { Text("New project") },
            text = {
                OutlinedTextField(
                    newName, { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("What are you working on?") },
                )
            },
            confirmButton = {
                TextButton({
                    val n = newName.trim().replace("\n", " ")
                    if (n.isNotEmpty()) {
                        val id = (data.projects.maxOfOrNull { it.id } ?: 0L) + 1
                        persist(data.copy(projects = data.projects + Project(id, n)))
                        openId = id
                    }
                    adding = false
                    newName = ""
                }) { Text("Create") }
            },
            dismissButton = { TextButton({ adding = false; newName = "" }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectCard(p: Project, progress: ProjectProgress, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpen() }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textDecoration = if (p.status == ProjectStatus.DONE) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            if (p.targetDate.isNotBlank()) {
                Text(
                    relativeLabelOf(p.targetDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (p.overdue()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val bits = buildList {
            if (p.status != ProjectStatus.ACTIVE) add(projectStatusLabel(p.status))
            if (progress.total > 0) add("${progress.done}/${progress.total} tasks")
            p.tags.forEach { add("#$it") }
        }
        if (bits.isNotEmpty()) {
            Text(
                bits.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (progress.total > 0) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectDetail(
    project: Project,
    tasks: List<Task>,
    onBack: () -> Unit,
    onPatch: ((Project) -> Project) -> Unit,
    onTasks: (List<Task>) -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    var newTask by remember(project.id) { mutableStateOf("") }
    var confirmDelete by remember(project.id) { mutableStateOf(false) }

    val mine = tasksIn(project, tasks).sortedWith(compareBy({ it.done }, { it.due.ifBlank { "9999" } }))
    val progress = projectProgress(project, tasks)

    // Read the other modules once for this project, not on every keystroke in the notes
    // field — four file reads and four JSON parses per recomposition is not free.
    val documentOptions = remember(project.id) { loadDocuments().documents.map { it.id to it.title } }
    val linkOptions = remember(project.id) { loadLinks().links.map { it.id to it.title.ifBlank { it.url } } }
    val contactOptions = remember(project.id) { loadContacts().contacts.map { it.id to it.name } }
    val milestoneOptions = remember(project.id) { loadMilestones().milestones.map { it.id to it.title } }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        TextButton(onBack) { Text("← All projects") }

        OutlinedTextField(
            project.name,
            { v -> onPatch { it.copy(name = v.replace("\n", " ")) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Name") },
        )
        Spacer(Modifier.height(6.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProjectStatus.entries.forEach { st ->
                FilterChip(
                    project.status == st,
                    { onPatch { it.copy(status = st) } },
                    { Text(projectStatusLabel(st)) },
                )
            }
        }

        if (progress.total > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${progress.done} of ${progress.total} done" +
                    if (progress.complete) " · everything on it is finished" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Label("Started")
                DateField(project.startDate) { v -> onPatch { it.copy(startDate = v) } }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Label("Target")
                DateField(project.targetDate) { v -> onPatch { it.copy(targetDate = v) } }
            }
        }
        if (project.overdue()) {
            Text(
                "Past its target date and still open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Label("Tags")
        TagField(project.tags, "home, 2027") { v -> onPatch { it.copy(tags = v) } }

        Label("Notes")
        OutlinedTextField(
            project.notes,
            { v -> onPatch { it.copy(notes = v) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            placeholder = { Text("What this is, and where it stands") },
        )

        // ---- tasks ----
        Label("Tasks (${mine.size})")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                newTask, { newTask = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Add a task to this project") },
            )
            Spacer(Modifier.width(8.dp))
            Button({
                val t = newTask.trim().replace("\n", " ")
                if (t.isNotEmpty()) {
                    val id = (tasks.maxOfOrNull { it.id } ?: 0L) + 1
                    onTasks(tasks + Task(id, t, projectId = project.id))
                    newTask = ""
                }
            }) { Text("Add") }
        }
        if (mine.isEmpty()) {
            Text(
                "Nothing on it yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        mine.forEach { t ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = t.done,
                    onCheckedChange = { on ->
                        onTasks(
                            tasks.map {
                                if (it.id != t.id) {
                                    it
                                } else if (on) {
                                    it.copy(status = "done", completedDate = today().toString())
                                } else {
                                    it.copy(status = "not_started", completedDate = "")
                                }
                            },
                        )
                    },
                )
                Text(
                    t.title,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f).clickable { Nav.open("tasks") },
                )
                if (t.due.isNotBlank()) {
                    Text(
                        relativeLabelOf(t.due),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- what else belongs to it ----
        LinkedSection(
            label = "Documents",
            chosen = project.documentIds,
            options = documentOptions,
            moduleId = "documents",
        ) { ids -> onPatch { it.copy(documentIds = ids) } }

        LinkedSection(
            label = "Links",
            chosen = project.linkIds,
            options = linkOptions,
            moduleId = "links",
        ) { ids -> onPatch { it.copy(linkIds = ids) } }

        LinkedSection(
            label = "People",
            chosen = project.contactIds,
            options = contactOptions,
            moduleId = "contacts",
        ) { ids -> onPatch { it.copy(contactIds = ids) } }

        LinkedSection(
            label = "Milestones",
            chosen = project.milestoneIds,
            options = milestoneOptions,
            moduleId = "milestones",
        ) { ids -> onPatch { it.copy(milestoneIds = ids) } }

        Spacer(Modifier.height(16.dp))
        Row {
            if (project.open) {
                Button({ onComplete() }) { Text("Mark finished") }
                Spacer(Modifier.width(8.dp))
            }
            TextButton({ confirmDelete = true }) { Text("Delete project") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${project.name}?") },
            text = {
                Text(
                    if (progress.total > 0) {
                        "Its ${progress.total} task(s) stay — they just stop belonging to a project."
                    } else {
                        "Nothing else is affected."
                    },
                )
            },
            confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep it") } },
        )
    }
}

// A pick-list of records from another module. Links, never copies: the project holds
// ids, and the titles are resolved live so renaming a document renames it here.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkedSection(
    label: String,
    chosen: List<Long>,
    options: List<Pair<Long, String>>,
    moduleId: String,
    onChange: (List<Long>) -> Unit,
) {
    if (options.isEmpty() && chosen.isEmpty()) return
    var picking by remember { mutableStateOf(false) }

    Label("$label (${chosen.size})")
    if (chosen.isEmpty()) {
        Text(
            "None attached.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chosen.forEach { id ->
                val name = options.firstOrNull { it.first == id }?.second
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { Nav.open(moduleId) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        // A link whose record has since been deleted shouldn't vanish
                        // silently — say so, so it can be cleared.
                        name ?: "(deleted)",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (name == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
    if (options.isNotEmpty()) {
        TextButton({ picking = !picking }) { Text(if (picking) "Done" else "Attach $label") }
    }
    if (picking) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (id, title) ->
                FilterChip(
                    id in chosen,
                    { onChange(if (id in chosen) chosen - id else chosen + id) },
                    { Text(title.take(40)) },
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
