package com.alekpeed.lifeos.skilltrees

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.ui.DateField
import com.alekpeed.lifeos.ui.SaveToast

// Skill Trees (§5.2). One module, two tiers, two vocabularies kept apart on purpose.
//
// Standings count what you have been doing and advance in RANKS. Skills are what you
// have declared you are learning; they move in LEVELS, and only when a benchmark you
// wrote is met. The wall between them is the whole point: both used to say "Level 4"
// while meaning different things, one accumulated and one earned.

@Composable
fun SkillTreesScreen() {
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0 }, { Text("Standings") })
            FilterChip(tab == 1, { tab = 1 }, { Text("Skills") })
        }
        Spacer(Modifier.height(14.dp))
        if (tab == 0) StandingsBand() else SkillsBand()
    }
}

// ---- Tier 1 ------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StandingsBand() {
    var data by remember { mutableStateOf(loadStandings()) }
    var editing by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val counts = remember(data) { activityCounts() }

    fun persist(next: StandingsData) {
        data = next
        saveStandings(next)
        SaveToast.show()
    }

    val open = editing?.let { id -> data.standings.firstOrNull { it.id == id } }
    if (open != null) {
        StandingEditor(
            standing = open,
            counts = counts,
            onBack = { editing = null },
            onPatch = { change ->
                persist(data.copy(standings = data.standings.map { if (it.id == open.id) change(it) else it }))
            },
            onDelete = {
                persist(data.copy(standings = data.standings.filterNot { it.id == open.id }))
                editing = null
            },
        )
        return
    }

    val total = data.standings.sumOf { rankOf(standingXp(it, counts)) }

    Column {
        Text(
            "What you have been doing, counted. A standing cannot show that you got " +
                "better at anything — only that you did more of it. That is why it never " +
                "shares a word with a skill's level.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text("Standing total $total", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(data.standings, key = { it.id }) { s ->
                StandingBar(s, counts) { editing = s.id }
            }
            item {
                Spacer(Modifier.height(8.dp))
                TextButton({ adding = true }) { Text("+ New standing") }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false; newName = "" },
            title = { Text("New standing") },
            text = {
                OutlinedTextField(
                    newName, { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Cook, Explorer, Archivist…") },
                )
            },
            confirmButton = {
                TextButton({
                    val n = newName.trim().replace("\n", " ")
                    if (n.isNotEmpty()) {
                        val id = nextStandingId(data)
                        persist(data.copy(standings = data.standings + Standing(id, n)))
                        editing = id
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
private fun StandingBar(s: Standing, counts: ActivityCounts, onOpen: () -> Unit) {
    val xp = standingXp(s, counts)
    val rank = rankOf(xp)
    val pct = rankProgress(xp)

    Row(
        Modifier.fillMaxWidth().clickable { onOpen() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.icon, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(rankLabel(s, rank), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier.fillMaxWidth(pct).height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                standingBlurb(s, counts),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StandingEditor(
    standing: Standing,
    counts: ActivityCounts,
    onBack: () -> Unit,
    onPatch: ((Standing) -> Standing) -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TextButton(onBack) { Text("← Standings") }
        OutlinedTextField(
            standing.name, { v -> onPatch { it.copy(name = v.replace("\n", " ")) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") },
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            standing.icon, { v -> onPatch { it.copy(icon = v.take(2)) } },
            modifier = Modifier.width(110.dp), singleLine = true, label = { Text("Icon") },
        )

        Spacer(Modifier.height(12.dp))
        Text("Counts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Pick what feeds it, and what each one is worth. Removing a module means " +
                "reweighting here rather than losing a branch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SourceKind.entries.forEach { kind ->
            val current = standing.sources.firstOrNull { it.kind == kind }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = current != null,
                    onCheckedChange = { on ->
                        onPatch { s ->
                            if (on) s.copy(sources = s.sources + SourceWeight(kind, 10))
                            else s.copy(sources = s.sources.filterNot { it.kind == kind })
                        }
                    },
                )
                Column(Modifier.weight(1f)) {
                    Text(sourceLabel(kind), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${counts[kind]} so far",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (current != null) {
                    OutlinedTextField(
                        current.xp.toString(),
                        { v ->
                            val n = v.filter { c -> c.isDigit() }.take(4).toIntOrNull() ?: 0
                            onPatch { s ->
                                s.copy(sources = s.sources.map { if (it.kind == kind) it.copy(xp = n) else it })
                            }
                        },
                        modifier = Modifier.width(86.dp),
                        singleLine = true,
                        label = { Text("XP") },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Rung names", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            standing.rankNames.joinToString(", "),
            { v -> onPatch { it.copy(rankNames = v.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Novice, Steady, Reliable… (optional)") },
        )

        Spacer(Modifier.height(16.dp))
        TextButton(onDelete) { Text("Delete standing") }
        Spacer(Modifier.height(24.dp))
    }
}

// ---- Tier 2 ---------------------------------------------------------------------------------

@Composable
private fun SkillsBand() {
    var data by remember { mutableStateOf(loadSkills()) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var showPaused by remember { mutableStateOf(false) }

    fun persist(next: SkillsData) {
        data = next
        saveSkills(next)
        SaveToast.show()
    }

    val open = openId?.let { id -> data.skills.firstOrNull { it.id == id } }
    if (open != null) {
        SkillDetail(
            skill = open,
            data = data,
            onBack = { openId = null },
            onData = { persist(it) },
            onDelete = { persist(deleteSkill(data, open.id)); openId = null },
        )
        return
    }

    val summaries = remember(data) { skillSummaries(data) }
    val cold = remember(data) { goneCold(data) }
    val roots = remember(data, showPaused) {
        skillRoots(data).filter { showPaused || it.active }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "What you are actually learning. A level moves when a benchmark you wrote is " +
                "met — never because you finished tasks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        if (data.skills.isEmpty()) {
            Text(
                "No skills yet. Add one — an instrument, a language, a craft — then log " +
                    "practice against it and write down what its next level actually means.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button({ adding = true }) { Text("+ Add a skill") }
        } else {
            if (cold.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
                ) {
                    Text(
                        if (cold.size == 1) "1 skill has gone cold" else "${cold.size} skills have gone cold",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    cold.take(4).forEach { s ->
                        Text(
                            "${s.skill.name} — ${s.freshness.daysSince ?: 0} days since practice",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            val paused = data.skills.count { !it.active }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ adding = true }) { Text("+ Add a skill") }
                if (paused > 0) {
                    Spacer(Modifier.width(8.dp))
                    FilterChip(showPaused, { showPaused = !showPaused }, { Text("Paused ($paused)") })
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(roots, key = { it.id }) { root ->
                    val summary = summaries.firstOrNull { it.skill.id == root.id }
                    if (summary != null) {
                        SkillCard(summary, indent = 0) { openId = root.id }
                        summary.children.filter { showPaused || it.active }.forEach { child ->
                            summaries.firstOrNull { it.skill.id == child.id }?.let {
                                SkillCard(it, indent = 1) { openId = child.id }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false; newName = "" },
            title = { Text("New skill") },
            text = {
                OutlinedTextField(
                    newName, { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Guitar, Japanese, welding…") },
                )
            },
            confirmButton = {
                TextButton({
                    val n = newName.trim().replace("\n", " ")
                    if (n.isNotEmpty()) {
                        val id = nextSkillId(data)
                        persist(
                            data.copy(
                                skills = data.skills + Skill(id, n, startedDate = today().toString()),
                            ),
                        )
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
private fun SkillCard(s: SkillSummary, indent: Int, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = (indent * 16).dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpen() }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (indent > 0) Text("↳ ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.skill.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                s.skill.levelName(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val bits = buildList {
            add("${(s.hours.hours * 10).toLong() / 10.0}h")
            if (s.streak > 0) add("${s.streak}-day streak")
            if (s.benchmarksTotal > 0) add("${s.benchmarksMet}/${s.benchmarksTotal} benchmarks")
            if (!s.skill.active) add("paused")
            else if (s.freshness.stale) add("cold — ${s.freshness.daysSince} days")
            if (s.skill.domain.isNotBlank()) add(s.skill.domain)
        }
        Text(
            bits.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = if (s.freshness.stale && s.skill.active) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        s.next?.let {
            Text(
                "Next: ${it.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillDetail(
    skill: Skill,
    data: SkillsData,
    onBack: () -> Unit,
    onData: (SkillsData) -> Unit,
    onDelete: () -> Unit,
) {
    var logDate by remember(skill.id) { mutableStateOf(today().toString()) }
    var logMinutes by remember(skill.id) { mutableStateOf("30") }
    var logFocus by remember(skill.id) { mutableStateOf("") }
    var logQuality by remember(skill.id) { mutableStateOf(3) }
    var newBenchmark by remember(skill.id) { mutableStateOf("") }
    var confirmDelete by remember(skill.id) { mutableStateOf(false) }

    fun patch(f: (Skill) -> Skill) {
        onData(data.copy(skills = data.skills.map { if (it.id == skill.id) f(it) else it }))
    }

    val hours = remember(skill, data) { hoursFor(skill, data) }
    val fresh = remember(skill, data) { freshnessOf(skill, data) }
    val streak = remember(skill, data) { practiceStreak(skill, data) }
    val logs = remember(skill, data) { logsFor(skill.id, data) }
    val benchmarks = remember(skill, data) { benchmarksFor(skill.id, data) }
    val books = remember(skill) { evidenceBooks(skill) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TextButton(onBack) { Text("← Skills") }
        OutlinedTextField(
            skill.name, { v -> patch { it.copy(name = v.replace("\n", " ")) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") },
        )
        Spacer(Modifier.height(6.dp))
        Row {
            OutlinedTextField(
                skill.domain, { v -> patch { it.copy(domain = v.replace("\n", " ")) } },
                modifier = Modifier.weight(1f), singleLine = true,
                label = { Text("Domain") }, placeholder = { Text("music, language…") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                skill.active,
                { patch { it.copy(active = !it.active) } },
                { Text(if (skill.active) "Active" else "Paused") },
            )
        }

        // ---- where it stands ----
        Label("Where it stands")
        Text(
            skill.levelName(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            buildString {
                append("${(hours.hours * 10).toLong() / 10.0} hours")
                if (hours.habits > 0) append(" · ${hours.habits / 60}h from linked habits")
                if (hours.courses > 0) append(" · ${hours.courses / 60}h from courses")
                if (streak > 0) append(" · $streak-day streak")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            when {
                fresh.exempt -> "Paused, so it isn't going cold."
                fresh.lastPracticed == null -> "No practice logged yet."
                fresh.stale -> "Cold — ${fresh.daysSince} days since practice, past its ${fresh.freshness.intervalDays}-day rung."
                else -> "Fresh — last practised ${fresh.daysSince} days ago, holds ${fresh.freshness.intervalDays} days."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (fresh.stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- the ladder ----
        Label("Level scale")
        OutlinedTextField(
            skill.levelScale.joinToString(", "),
            { v -> patch { it.copy(levelScale = v.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("A1, A2, B1, B2, C1, C2 — or belts, or grades") },
        )
        Text(
            "Your ladder, not the app's. A level only moves when a benchmark below is met.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- benchmarks ----
        Label("Benchmarks (${benchmarks.count { it.achieved }}/${benchmarks.size})")
        Text(
            "What the next level concretely means. \"Play the F barre chord cleanly at 80bpm\", " +
                "not a progress bar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        benchmarks.forEach { b ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = b.achieved,
                    onCheckedChange = { on ->
                        if (on) {
                            onData(achieveBenchmark(data, b.id))
                        } else {
                            onData(
                                data.copy(
                                    benchmarks = data.benchmarks.map {
                                        if (it.id == b.id) it.copy(achieved = false, achievedDate = "") else it
                                    },
                                ),
                            )
                        }
                    },
                )
                Column(Modifier.weight(1f)) {
                    Text(b.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "for level ${b.targetLevel}" + if (b.achieved && b.achievedDate.isNotBlank()) " · met ${b.achievedDate}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton({
                    onData(data.copy(benchmarks = data.benchmarks.filterNot { it.id == b.id }))
                }) { Text("×") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                newBenchmark, { newBenchmark = it },
                modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("What must be true for the next level?") },
            )
            Spacer(Modifier.width(8.dp))
            Button({
                val label = newBenchmark.trim().replace("\n", " ")
                if (label.isNotEmpty()) {
                    onData(
                        data.copy(
                            benchmarks = data.benchmarks + Benchmark(
                                id = nextBenchmarkId(data),
                                skillId = skill.id,
                                label = label,
                                targetLevel = skill.currentLevel + 1,
                            ),
                        ),
                    )
                    newBenchmark = ""
                }
            }) { Text("Add") }
        }

        // ---- practice ----
        Label("Log practice")
        Row {
            Column(Modifier.weight(1f)) {
                Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DateField(logDate) { v -> logDate = v }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                logMinutes, { v -> logMinutes = v.filter { c -> c.isDigit() }.take(4) },
                modifier = Modifier.width(110.dp), singleLine = true, label = { Text("Minutes") },
            )
        }
        OutlinedTextField(
            logFocus, { logFocus = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Focus") }, placeholder = { Text("What did you actually work on?") },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quality", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { q ->
                    FilterChip(logQuality == q, { logQuality = q }, { Text("$q") })
                }
            }
        }
        Button({
            val mins = logMinutes.toIntOrNull() ?: 0
            if (mins > 0) {
                onData(
                    data.copy(
                        logs = data.logs + PracticeLog(
                            id = nextLogId(data),
                            skillId = skill.id,
                            date = logDate,
                            minutes = mins,
                            focus = logFocus.trim(),
                            quality = logQuality,
                        ),
                    ),
                )
                logFocus = ""
            }
        }) { Text("Log it") }

        if (logs.isNotEmpty()) {
            Label("Sessions (${logs.size})")
            logs.take(15).forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            l.focus.ifBlank { "Practice" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${l.date} · ${l.minutes}m · quality ${l.quality}/5",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton({
                        onData(data.copy(logs = data.logs.filterNot { it.id == l.id }))
                    }) { Text("×") }
                }
            }
        }

        // ---- what feeds it ----
        Label("Feeds from")
        Text(
            "A linked habit's check-ins count as practice, a linked course contributes its " +
                "minutes, and a book stands as evidence. Nothing is counted twice: a day that " +
                "already has a session logged here ignores the habit check-in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            skill.habitNames.joinToString(", "),
            { v -> patch { it.copy(habitNames = v.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Habits") }, placeholder = { Text("Exact habit names, comma separated") },
        )
        OutlinedTextField(
            skill.minutesPerCheckin.toString(),
            { v -> patch { it.copy(minutesPerCheckin = v.filter { c -> c.isDigit() }.take(3).toIntOrNull() ?: 0) } },
            modifier = Modifier.width(160.dp), singleLine = true,
            label = { Text("Minutes per check-in") },
        )
        if (books.isNotEmpty()) {
            Text(
                "Evidence: " + books.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Label("Notes")
        OutlinedTextField(
            skill.notes, { v -> patch { it.copy(notes = v) } },
            modifier = Modifier.fillMaxWidth(), singleLine = false,
        )

        Spacer(Modifier.height(16.dp))
        TextButton({ confirmDelete = true }) { Text("Delete skill") }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${skill.name}?") },
            text = {
                Text(
                    "Its ${logs.size} session(s) and ${benchmarks.size} benchmark(s) go with it — " +
                        "they mean nothing without the skill. Sub-skills are kept and promoted.",
                )
            },
            confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
