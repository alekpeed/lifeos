package com.alekpeed.lifeos.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.relativeLabelOf
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.ui.DateField
import com.alekpeed.lifeos.ui.SaveToast
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import com.alekpeed.lifeos.ui.TagField

private fun dueColor(due: LocalDate?, done: Boolean): Color? {
    if (due == null || done) return null
    val days = today().daysUntil(due)
    return when {
        days < 0 -> Color(0xFFE05C5C)
        days == 0 -> Color(0xFFE0A25C)
        else -> null
    }
}

private fun priorityColor(p: String): Color? = when (p) {
    "urgent" -> Color(0xFFD64545)
    "high" -> Color(0xFFE05C5C)
    "medium" -> Color(0xFFE0A25C)
    "low" -> Color(0xFF5C9CE0)
    else -> null
}

// The chips shown under a task title (project, status, waiting-on, subtask count,
// tags, recurrence). Shared by the list rows and the board cards.
private fun taskMetas(task: Task): List<String> = buildList {
    if (task.project.isNotBlank()) add(task.project)
    if (task.status == "in_progress") add("In progress")
    if (task.status == "waiting") add(if (task.waitingOn.isNotBlank()) "Waiting: ${task.waitingOn}" else "Waiting")
    if (task.subtasks.isNotEmpty()) add("${task.subtasks.count { it.done }}/${task.subtasks.size}")
    task.tags.forEach { add("#$it") }
    if (task.recur.isNotEmpty()) add("⟳ ${task.recur}")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen() {
    val tasks = remember { mutableStateListOf<Task>().apply { addAll(loadTasks()) } }
    fun persist() { saveTasks(tasks); SaveToast.show() }
    // Adding starts from the button, not the keyboard: hit Add and the prompt comes up.
    var adding by remember { mutableStateOf(false) }
    var nextId by remember { mutableStateOf((tasks.maxOfOrNull { it.id } ?: 0L) + 1) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var board by remember { mutableStateOf(false) }
    var projectFilter by remember { mutableStateOf<String?>(null) }
    var hideDone by remember { mutableStateOf(false) }
    var showSnoozed by remember { mutableStateOf(false) }
    var sortByPriority by remember { mutableStateOf(false) }
    // The row checkbox SELECTS; completing and deleting are done to the selection from
    // one universal bar. Keeps the checkbox meaning one thing and makes both actions
    // work on many rows at once.
    var selected by remember { mutableStateOf(setOf<Long>()) }

    fun update(id: Long, f: (Task) -> Task) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i >= 0) { tasks[i] = f(tasks[i]); persist() }
    }
    // Marking a recurring task done spawns its next occurrence from the due date.
    fun spawnRecurrence(task: Task) {
        task.dueDate()?.let { d ->
            nextRecurDate(d, task.recur)?.let { nd ->
                tasks.add(task.copy(id = nextId, status = "not_started", due = nd.toString(), subtasks = task.subtasks.map { it.copy(done = false) }))
                nextId += 1
            }
        }
    }
    fun moveStatus(task: Task, newStatus: String) {
        if (newStatus == "done" && task.status != "done" && task.recur.isNotEmpty()) spawnRecurrence(task)
        // Stamp/clear the completion date so the yearly recap can count real years.
        update(task.id) { it.copy(status = newStatus, completedDate = if (newStatus == "done") today().toString() else "") }
    }
    // Complete the selection — or reopen it, if every selected task is already done, so a
    // mis-complete is undone the same way it was made.
    fun completeSelected() {
        val picked = tasks.filter { it.id in selected }
        if (picked.isEmpty()) return
        val reopen = picked.all { it.done }
        picked.forEach { task ->
            if (!reopen && !task.done && task.recur.isNotEmpty()) spawnRecurrence(task)
            val i = tasks.indexOfFirst { it.id == task.id }
            if (i >= 0) {
                tasks[i] = tasks[i].copy(
                    status = if (reopen) "not_started" else "done",
                    completedDate = if (reopen) "" else today().toString(),
                )
            }
        }
        saveTasks(tasks)
        SaveToast.show(if (reopen) "Reopened ${picked.size}" else "Completed ${picked.size}")
        selected = emptySet()
    }

    fun deleteSelected() {
        val n = tasks.count { it.id in selected }
        if (n == 0) return
        tasks.removeAll { it.id in selected }
        if (expandedId in selected) expandedId = null
        saveTasks(tasks)
        SaveToast.show(if (n == 1) "Deleted 1 task" else "Deleted $n tasks")
        selected = emptySet()
    }

    val projects = tasks.map { it.project.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    fun visible(list: List<Task>) = list.filter { projectFilter == null || it.project.trim() == projectFilter }

    Column(Modifier.fillMaxSize().padding(20.dp)) {

        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Add task")
        }
        Spacer(Modifier.height(10.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = !board, onClick = { board = false }, label = { Text("List") })
            FilterChip(selected = board, onClick = { board = true }, label = { Text("Board") })
            if (!board) {
                FilterChip(selected = hideDone, onClick = { hideDone = !hideDone }, label = { Text("Hide done") })
                val snoozed = visible(tasks).count { !it.done && (it.snoozeDate()?.let { d -> d > today() } == true) }
                if (snoozed > 0) FilterChip(selected = showSnoozed, onClick = { showSnoozed = !showSnoozed }, label = { Text("Snoozed ($snoozed)") })
                FilterChip(selected = sortByPriority, onClick = { sortByPriority = !sortByPriority }, label = { Text(if (sortByPriority) "By priority" else "By due") })
            }
        }
        if (projects.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = projectFilter == null, onClick = { projectFilter = null }, label = { Text("All") })
                projects.forEach { p ->
                    FilterChip(selected = projectFilter == p, onClick = { projectFilter = if (projectFilter == p) null else p }, label = { Text(p) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // One bar for the whole selection: complete (or reopen) and delete.
        if (!board && selected.isNotEmpty()) {
            val shownIds = visible(tasks).map { it.id }.toSet()
            SelectionBar(
                count = selected.size,
                allShownPicked = shownIds.isNotEmpty() && shownIds.all { it in selected },
                onComplete = { completeSelected() },
                onDelete = { deleteSelected() },
                onSelectAll = { selected = if (shownIds.all { it in selected }) emptySet() else shownIds },
                onClear = { selected = emptySet() },
            )
            Spacer(Modifier.height(10.dp))
        }

        if (adding) {
            AddTaskPrompt(
                // Adding while filtered to a project drops the new task into that project.
                project = projectFilter,
                onDismiss = { adding = false },
                onAdd = { title, due ->
                    tasks.add(Task(nextId, title, due = due, project = projectFilter ?: ""))
                    nextId += 1
                    persist()
                    adding = false
                },
            )
        }

        if (board) {
            TaskBoard(
                tasks = visible(tasks),
                onOpen = { id -> board = false; expandedId = id },
                onMove = { task, s -> moveStatus(task, s) },
            )
        } else {
            val shown = visible(tasks)
                .filter { !hideDone || !it.done }
                .filter { showSnoozed || it.done || (it.snoozeDate()?.let { d -> d <= today() } ?: true) }
                .sortedWith(
                    if (sortByPriority) {
                        compareBy({ it.done }, { priorityRank(it.priority) }, { it.dueDate()?.toString() ?: "9999-99-99" })
                    } else {
                        compareBy({ it.done }, { it.dueDate()?.toString() ?: "9999-99-99" }, { priorityRank(it.priority) })
                    },
                )
            if (shown.isEmpty()) {
                Text(
                    if (tasks.isEmpty()) "Nothing on the list. Add a task above."
                    else "Nothing matches the current filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        expanded = expandedId == task.id,
                        picked = task.id in selected,
                        onPick = { on ->
                            selected = if (on) selected + task.id else selected - task.id
                        },
                        onToggleExpand = { expandedId = if (expandedId == task.id) null else task.id },
                        update = { id, f -> update(id, f) },
                        onDelete = { tasks.removeAll { it.id == task.id }; expandedId = null; persist() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(
    task: Task,
    expanded: Boolean,
    picked: Boolean,
    onPick: (Boolean) -> Unit,
    onToggleExpand: () -> Unit,
    update: (Long, (Task) -> Task) -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable { onToggleExpand() }.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Selects, never completes — completing is done from the selection bar.
            Checkbox(checked = picked, onCheckedChange = { onPick(it) })
            priorityColor(task.priority)?.let { c ->
                Text("●", color = c, modifier = Modifier.padding(end = 6.dp))
            }
            Text(
                task.title, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            task.dueDate()?.let { due ->
                Text(
                    relativeLabelOf(task.due), style = MaterialTheme.typography.labelMedium,
                    color = dueColor(due, task.done) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val metas = taskMetas(task)
        if (metas.isNotEmpty()) {
            FlowRow(
                Modifier.padding(start = 40.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                metas.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (expanded) TaskEditor(task, update, onDelete)
    }
}

// Kanban: one column per status, horizontally scrollable, with real drag and drop.
//
// Press and hold a card to pick it up, drag it over another column, let go. The
// column under your finger lights up so you can see where it will land, and dragging
// near either edge scrolls the board — without that, moving a card from Not started
// to Done on a phone would be impossible, since only a column and a half fits on
// screen at once.
//
// Long-press rather than plain drag on purpose: a bare drag would fight the board's
// own horizontal scroll, and a tap still opens the card for a full edit. The ‹ / ›
// buttons stay as the keyboard-and-mouse path and as a fallback if a drag misses.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TaskBoard(tasks: List<Task>, onOpen: (Long) -> Unit, onMove: (Task, String) -> Unit) {
    val statusOrder = TASK_STATUSES.map { it.first }
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    // Everything below is in root coordinates, so a scrolling board and a moving
    // finger are measured against the same origin.
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    var boardWidth by remember { mutableStateOf(0f) }
    val colSpan = remember { mutableStateMapOf<String, ClosedFloatingPointRange<Float>>() }

    var dragTask by remember { mutableStateOf<Task?>(null) }
    var dragAt by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }   // where in the card you grabbed it

    val hovered = if (dragTask == null) null else {
        statusOrder.firstOrNull { s -> colSpan[s]?.contains(dragAt.x) == true }
    }

    // Auto-scroll while a drag sits near an edge. Frame-driven so it moves at a
    // readable speed regardless of how fast the pointer is being sampled.
    LaunchedEffect(dragTask != null) {
        if (dragTask == null) return@LaunchedEffect
        val edge = with(density) { 64.dp.toPx() }
        val step = with(density) { 9.dp.toPx() }
        while (true) {
            withFrameNanos { }
            if (boardWidth <= 0f) continue
            val x = dragAt.x - boardOrigin.x
            val dx = when {
                x < edge -> -step
                x > boardWidth - edge -> step
                else -> 0f
            }
            if (dx != 0f) scroll.scrollBy(dx)
        }
    }

    fun drop() {
        val task = dragTask
        val target = hovered
        dragTask = null
        if (task != null && target != null && target != task.status.ifBlank { "not_started" }) {
            onMove(task, target)
        }
    }

    Box(
        Modifier.weight(1f).fillMaxWidth().onGloballyPositioned {
            boardOrigin = it.positionInRoot()
            boardWidth = it.size.width.toFloat()
        },
    ) {
        Row(
            Modifier.fillMaxSize().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TASK_STATUSES.forEach { (statusVal, label) ->
                val colTasks = tasks.filter { (it.status.ifBlank { "not_started" }) == statusVal }
                    .sortedWith(compareBy({ it.dueDate()?.toString() ?: "9999-99-99" }, { priorityRank(it.priority) }))
                val idx = statusOrder.indexOf(statusVal)
                val isTarget = hovered == statusVal
                Column(
                    Modifier.width(250.dp).fillMaxHeight()
                        .onGloballyPositioned { c ->
                            val left = c.positionInRoot().x
                            colSpan[statusVal] = left..(left + c.size.width)
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            else Color.Transparent,
                        )
                        .padding(4.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "$label · ${colTasks.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (colTasks.isEmpty()) {
                        Text(
                            if (isTarget) "Drop here" else "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    colTasks.forEach { task ->
                        BoardCard(
                            task = task,
                            canPrev = idx > 0,
                            canNext = idx in 0 until statusOrder.lastIndex,
                            lifted = dragTask?.id == task.id,
                            onOpen = { onOpen(task.id) },
                            onPrev = { if (idx > 0) onMove(task, statusOrder[idx - 1]) },
                            onNext = { if (idx in 0 until statusOrder.lastIndex) onMove(task, statusOrder[idx + 1]) },
                            onPickUp = { cardRoot, grab ->
                                dragTask = task
                                dragGrab = grab
                                dragAt = cardRoot + grab
                            },
                            onDragBy = { amount -> dragAt += amount },
                            onDrop = { drop() },
                            onDragCancel = { dragTask = null },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // The card riding under your finger. Drawn last so it floats over the columns,
        // and offset by where you grabbed it so it doesn't jump on pick-up.
        dragTask?.let { task ->
            val x = dragAt.x - boardOrigin.x - dragGrab.x
            val y = dragAt.y - boardOrigin.y - dragGrab.y
            Box(
                Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .width(250.dp)
                    .zIndex(1f),
            ) {
                BoardCard(
                    task = task,
                    canPrev = false,
                    canNext = false,
                    floating = true,
                    onOpen = {},
                    onPrev = {},
                    onNext = {},
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardCard(
    task: Task,
    canPrev: Boolean,
    canNext: Boolean,
    onOpen: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    // Drag state. `lifted` is the gap left behind by a card being dragged; `floating`
    // is the copy riding under the finger. Both are false for an ordinary card, which
    // is why the overlay can reuse this composable unchanged.
    lifted: Boolean = false,
    floating: Boolean = false,
    onPickUp: (cardRoot: Offset, grab: Offset) -> Unit = { _, _ -> },
    onDragBy: (Offset) -> Unit = {},
    onDrop: () -> Unit = {},
    onDragCancel: () -> Unit = {},
) {
    var cardRoot by remember { mutableStateOf(Offset.Zero) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(
                if (floating) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (lifted) 0.35f else 1f),
            )
            .then(if (floating) Modifier.alpha(0.92f) else Modifier)
            .onGloballyPositioned { cardRoot = it.positionInRoot() }
            .then(
                // The floating copy takes no input — the original still owns the gesture.
                if (floating) Modifier
                else Modifier.pointerInput(task.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { grab -> onPickUp(cardRoot, grab) },
                        onDrag = { _, amount -> onDragBy(amount) },
                        onDragEnd = { onDrop() },
                        onDragCancel = { onDragCancel() },
                    )
                },
            )
            .then(if (floating) Modifier else Modifier.clickable { onOpen() })
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            priorityColor(task.priority)?.let { c -> Text("●", color = c, modifier = Modifier.padding(end = 6.dp)) }
            Text(
                task.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
        }
        task.dueDate()?.let { due ->
            Text(
                relativeLabelOf(task.due), style = MaterialTheme.typography.labelSmall,
                color = dueColor(due, task.done) ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Status is already implied by the column, so drop the status chips here.
        val metas = taskMetas(task).filter { it != "In progress" && !it.startsWith("Waiting") }
        if (metas.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                metas.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (canPrev) TextButton(onClick = onPrev) { Text("‹ Back") } else Spacer(Modifier.width(1.dp))
            Spacer(Modifier.weight(1f))
            if (canNext) TextButton(onClick = onNext) { Text("Next ›") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditor(task: Task, update: (Long, (Task) -> Task) -> Unit, onDelete: () -> Unit) {
    var newSub by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().padding(start = 40.dp, top = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) {
        Label("Status")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TASK_STATUSES.forEach { (v, lbl) ->
                FilterChip(
                    selected = task.status == v,
                    onClick = { update(task.id) { it.copy(status = v, completedDate = if (v == "done") today().toString() else "") } },
                    label = { Text(lbl) },
                )
            }
        }
        Label("Priority")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TASK_PRIORITIES.forEach { (v, lbl) ->
                FilterChip(selected = task.priority == v, onClick = { update(task.id) { it.copy(priority = v) } }, label = { Text(lbl) })
            }
        }
        Label("Due")
        // The chips cover the common cases; the field underneath takes any date, which
        // is what everything else in the app already offers.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Today" to today(), "Tomorrow" to today().plusDays(1), "Next week" to today().plusDays(7)).forEach { (lbl, d) ->
                AssistChip(onClick = { update(task.id) { it.copy(due = d.toString()) } }, label = { Text(lbl) })
            }
            TextButton(onClick = { update(task.id) { it.copy(due = "") } }) { Text("Clear") }
        }
        Spacer(Modifier.height(4.dp))
        DateField(task.due, withTime = true) { v -> update(task.id) { it.copy(due = v) } }
        Label("Repeats")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TASK_RECUR.forEach { (v, lbl) ->
                FilterChip(selected = task.recur == v, onClick = { update(task.id) { it.copy(recur = v) } }, label = { Text(lbl) })
            }
        }
        Label("Project")
        val projectOptions = remember { loadTasks().map { it.project.trim() }.filter { it.isNotEmpty() }.distinct().sorted() }
        if (projectOptions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                projectOptions.forEach { p ->
                    FilterChip(
                        selected = task.project.trim() == p,
                        onClick = { update(task.id) { t -> t.copy(project = if (t.project.trim() == p) "" else p) } },
                        label = { Text(p) },
                    )
                }
            }
        }
        EditField(task.project, "e.g. Home renovation") { v -> update(task.id) { it.copy(project = v.replace("\n", " ")) } }
        Label("Tags")
        TagField(task.tags, "errand, work") { v -> update(task.id) { it.copy(tags = v) } }
        if (task.status == "waiting") {
            Label("Waiting on")
            EditField(task.waitingOn, "Who are you waiting on?") { v -> update(task.id) { it.copy(waitingOn = v.replace("\n", " ")) } }
        }
        Label("Notes")
        EditField(task.notes, "Notes", singleLine = false) { v -> update(task.id) { it.copy(notes = v) } }

        Label("Snooze")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { update(task.id) { it.copy(snoozedUntil = today().plusDays(1).toString()) } }, label = { Text("Tomorrow") })
            AssistChip(onClick = { update(task.id) { it.copy(snoozedUntil = today().plusDays(7).toString()) } }, label = { Text("+1 week") })
            if (task.snoozedUntil.isNotEmpty()) TextButton(onClick = { update(task.id) { it.copy(snoozedUntil = "") } }) { Text("Clear") }
        }
        if (task.snoozedUntil.isNotEmpty()) Text("Snoozed → ${task.snoozedUntil}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Label("Checklist")
        task.subtasks.forEach { sub ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sub.done, onCheckedChange = { c ->
                    update(task.id) { t -> t.copy(subtasks = t.subtasks.map { if (it.id == sub.id) it.copy(done = c) else it }) }
                })
                Text(sub.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    textDecoration = if (sub.done) TextDecoration.LineThrough else null)
                TextButton(onClick = { update(task.id) { t -> t.copy(subtasks = t.subtasks.filter { it.id != sub.id }) } }) { Text("×") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newSub, { newSub = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Add checklist item") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val txt = newSub.trim()
                if (txt.isNotEmpty()) {
                    val sid = (task.subtasks.maxOfOrNull { it.id } ?: 0L) + 1
                    update(task.id) { it.copy(subtasks = it.subtasks + Subtask(sid, txt)) }
                    newSub = ""
                }
            }) { Text("Add") }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDelete) { Text("Delete task", color = Color(0xFFD64545)) }
    }
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

// The universal selection bar: acts on every checked task at once. Only shown when
// something is selected, so it stays out of the way the rest of the time.
@Composable
private fun SelectionBar(
    count: Int,
    allShownPicked: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$count selected",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = onSelectAll) { Text(if (allShownPicked) "None" else "All") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onComplete) { Text("✓ Complete") }
        TextButton(onClick = onDelete) { Text("🗑 Delete", color = Color(0xFFD64545)) }
        TextButton(onClick = onClear) { Text("×") }
    }
}

// The add prompt: hit Add and this comes up ready to type, so a new task never starts
// with hunting for a text box. Title is all that's required; the due chips save opening
// the task afterwards just to say "today".
@Composable
private fun AddTaskPrompt(
    project: String?,
    onDismiss: () -> Unit,
    onAdd: (title: String, due: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() {
        val t = title.trim().replace("\n", " ")
        if (t.isNotEmpty()) onAdd(t, due)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (project != null) "New task in $project" else "New task") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    placeholder = { Text("What needs doing?") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Due",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Today" to today().toString(),
                        "Tomorrow" to today().plusDays(1).toString(),
                        "Next week" to today().plusDays(7).toString(),
                    ).forEach { (label, value) ->
                        FilterChip(
                            selected = due == value,
                            onClick = { due = if (due == value) "" else value },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                DateField(due, withTime = true) { v -> due = v }
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
