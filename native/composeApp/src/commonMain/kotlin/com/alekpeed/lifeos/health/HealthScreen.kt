package com.alekpeed.lifeos.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.minusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.ui.DateField
import com.alekpeed.lifeos.ui.SaveToast
import com.alekpeed.lifeos.ui.usDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Common metrics to prefill the free-form field with one tap.
private val QUICK = listOf("Weight" to "lb", "Sleep" to "h", "Workout" to "min", "Resting HR" to "bpm", "Water" to "oz", "Steps" to "", "Mood" to "/10")

private fun num(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else ((v * 100).toLong() / 100.0).toString()

// Health — three tabs. Daily is the web app's structured one-row-per-day log
// (sleep / workout / water / weight / notes) with a rolling 7-day summary;
// Metrics is free-form dated readings with trend bars (kept from the first
// native pass — it logs things the daily shape has no slot for); Import pulls
// an Apple Health export or Garmin Connect CSV into the daily log, preview +
// confirm before anything is written.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthScreen() {
    var data by remember { mutableStateOf(loadHealth()) }
    fun persist(next: HealthData) { data = next; saveHealth(next); SaveToast.show() }
    var tab by remember { mutableStateOf("daily") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Health", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("daily" to "Daily", "workouts" to "Workouts", "metrics" to "Metrics", "import" to "Import").forEach { (v, lbl) ->
                FilterChip(selected = tab == v, onClick = { tab = v }, label = { Text(lbl) })
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            "workouts" -> WorkoutsTab(data) { persist(it) }
            "metrics" -> MetricsTab(data) { persist(it) }
            "import" -> ImportTab(data) { persist(it) }
            else -> DailyTab(data) { persist(it) }
        }
    }
}

// ---- Workout log -------------------------------------------------------------

// A per-session workout log with sport-native pace math — rowing shows a /500m
// split, swimming /100m, runs min/mi, cycling mph — plus per-type totals and
// best pace. Rowing distance is meters, road work miles (editable per session).
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutsTab(data: HealthData, persist: (HealthData) -> Unit) {
    var nextId by remember { mutableStateOf((data.workouts.maxOfOrNull { it.id } ?: 0L) + 1) }
    var type by remember { mutableStateOf("Rowing") }
    var minutes by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(defaultDistanceUnit("Rowing")) }
    var notes by remember { mutableStateOf("") }
    var statType by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WORKOUT_TYPES.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t; unit = defaultDistanceUnit(t) }, label = { Text(t) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                minutes, { minutes = it }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Minutes") },
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                distance, { distance = it }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text(if (unit.isBlank()) "Distance (opt.)" else "Distance ($unit)") },
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(unit, { unit = it.trim() }, modifier = Modifier.width(64.dp), singleLine = true, placeholder = { Text("unit") })
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(notes, { notes = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Notes (10×500m, felt strong…)") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val m = minutes.trim().toDoubleOrNull()
                val d = distance.trim().toDoubleOrNull()
                if (m != null || d != null) {
                    val w = Workout(nextId, today().toString(), type, m, d, if (d != null) unit else "", notes.trim())
                    nextId += 1
                    persist(data.copy(workouts = listOf(w) + data.workouts))
                    minutes = ""; distance = ""; notes = ""
                }
            }) { Text("Log") }
        }
        Spacer(Modifier.height(12.dp))

        if (data.workouts.isEmpty()) {
            Text("No workouts logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        // Per-type rollup: sessions, time, distance, best pace. Tap a chip to
        // filter the session list below.
        val byType = data.workouts.groupBy { it.type }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            byType.entries.sortedByDescending { it.value.size }.forEach { (t, list) ->
                FilterChip(
                    selected = statType == t,
                    onClick = { statType = if (statType == t) null else t },
                    label = { Text("$t · ${list.size}") },
                )
            }
        }
        statType?.let { t ->
            val list = byType[t] ?: emptyList()
            val totalMin = list.sumOf { it.minutes ?: 0.0 }
            val dist = list.filter { it.distance != null }
            val distTotal = dist.sumOf { it.distance ?: 0.0 }
            val distUnit = dist.firstOrNull()?.distanceUnit ?: ""
            val best = list.filter { paceValue(it) != null }.minByOrNull { paceValue(it)!! }
            val bits = buildList {
                add("${list.size} session${if (list.size == 1) "" else "s"}")
                if (totalMin > 0) {
                    add(if (totalMin >= 60) "${(totalMin / 60).toInt()}h ${(totalMin % 60).toInt()}m total" else "${totalMin.toInt()}m total")
                }
                if (distTotal > 0) add("${num(distTotal)}$distUnit total")
                best?.let { b -> paceLabel(b)?.let { add("best $it (${usDate(b.date).ifBlank { b.date }})") } }
            }
            Spacer(Modifier.height(4.dp))
            Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))

        val shown = data.workouts.filter { statType == null || it.type == statType }.sortedByDescending { it.date }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(shown, key = { it.id }) { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.type, style = MaterialTheme.typography.bodyLarge)
                        val meta = buildList {
                            add(usDate(w.date).ifBlank { w.date })
                            w.minutes?.let { add(mmss(it)) }
                            w.distance?.let { add("${num(it)}${w.distanceUnit}") }
                            paceLabel(w)?.let { add(it) }
                            if (w.notes.isNotBlank()) add(w.notes)
                        }
                        Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { persist(data.copy(workouts = data.workouts.filterNot { it.id == w.id })) }) { Text("✕") }
                }
            }
        }
    }
}

// ---- Daily log --------------------------------------------------------------

@Composable
private fun DailyTab(data: HealthData, persist: (HealthData) -> Unit) {
    var selectedDate by remember { mutableStateOf<String?>(null) }
    val logs = data.logs.sortedByDescending { it.date }

    fun upsert(log: DailyLog) {
        val next = if (data.logs.any { it.date == log.date }) {
            data.logs.map { if (it.date == log.date) log else it }
        } else {
            data.logs + log
        }
        persist(data.copy(logs = next))
    }

    // Rolling 7-day summary, same math as the web view.
    val cutoff = today().minusDays(7).toString()
    val recent = logs.filter { it.date >= cutoff }
    val sleeps = recent.mapNotNull { it.sleepHours }
    val waters = recent.mapNotNull { it.waterOz }
    val workouts = recent.count { it.workoutType.isNotBlank() || (it.workoutMinutes ?: 0.0) > 0 }
    val summary = buildList {
        if (sleeps.isNotEmpty()) add("avg sleep ${num((sleeps.sum() / sleeps.size * 10).toLong() / 10.0)}h")
        if (waters.isNotEmpty()) add("avg water ${num((waters.sum() / waters.size).toLong().toDouble())}oz")
        add("$workouts workout${if (workouts == 1) "" else "s"}")
    }.joinToString(" · ")

    Column(Modifier.fillMaxSize()) {
        Button(onClick = {
            val d = today().toString()
            if (data.logs.none { it.date == d }) upsert(DailyLog(date = d))
            selectedDate = d
        }) { Text("+ Log today's entry") }
        Spacer(Modifier.height(8.dp))
        Text("Last 7 days: $summary", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))

        if (logs.isEmpty()) {
            Text("No health logs yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(logs, key = { it.date }) { log ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { selectedDate = if (selectedDate == log.date) null else log.date }
                        .padding(vertical = 4.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(usDate(log.date).ifBlank { log.date }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        val chips = buildList {
                            log.sleepHours?.let { add("😴 ${num(it)}h") }
                            if (log.workoutType.isNotBlank() || (log.workoutMinutes ?: 0.0) > 0) {
                                val t = log.workoutType.ifBlank { "Workout" }
                                add("🏋 $t" + (log.workoutMinutes?.let { " (${num(it)}m)" } ?: ""))
                            }
                            log.waterOz?.let { add("💧 ${num(it)}oz") }
                            log.weightLb?.let { add("⚖ ${num(it)}") }
                        }
                        Text(chips.joinToString("  "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (selectedDate == log.date) {
                        DailyEditor(
                            log = log,
                            onChange = { upsert(it) },
                            onDelete = {
                                persist(data.copy(logs = data.logs.filterNot { it.date == log.date }))
                                selectedDate = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyEditor(log: DailyLog, onChange: (DailyLog) -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) {
        Label("Date")
        DateField(log.date) { v -> if (v.isNotBlank()) onChange(log.copy(date = v)) }
        Row {
            Column(Modifier.weight(1f)) {
                Label("Sleep (hrs)")
                NumField(log.sleepHours?.let { num(it) } ?: "", "7.5") { v -> onChange(log.copy(sleepHours = v)) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Label("Water (oz)")
                NumField(log.waterOz?.let { num(it) } ?: "", "64") { v -> onChange(log.copy(waterOz = v)) }
            }
        }
        Row {
            Column(Modifier.weight(1f)) {
                Label("Workout type")
                OutlinedTextField(
                    log.workoutType, { onChange(log.copy(workoutType = it.replace("\n", " "))) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Run, lift, yoga…") },
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Label("Workout (min)")
                NumField(log.workoutMinutes?.let { num(it) } ?: "", "45") { v -> onChange(log.copy(workoutMinutes = v)) }
            }
        }
        Label("Weight (lb)")
        NumField(log.weightLb?.let { num(it) } ?: "", "optional") { v -> onChange(log.copy(weightLb = v)) }
        Label("Notes")
        OutlinedTextField(
            log.notes, { onChange(log.copy(notes = it)) },
            modifier = Modifier.fillMaxWidth(), singleLine = false, placeholder = { Text("Notes") },
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onDelete) { Text("Delete log", color = Color(0xFFD64545)) }
    }
}

@Composable
private fun NumField(value: String, placeholder: String, onChange: (Double?) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        text,
        { text = it; onChange(it.trim().toDoubleOrNull()) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        placeholder = { Text(placeholder) },
    )
}

// ---- Free-form metrics (the original native pass) ---------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricsTab(data: HealthData, persist: (HealthData) -> Unit) {
    val readings = data.readings
    var nextId by remember { mutableStateOf((readings.maxOfOrNull { it.id } ?: 0L) + 1) }
    var metric by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }

    val grouped = LinkedHashMap<String, MutableList<Reading>>()
    readings.forEach { grouped.getOrPut(it.metric) { mutableListOf() }.add(it) }
    val groups = grouped.entries.toList()

    Column(Modifier.fillMaxSize()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QUICK.forEach { (m, u) ->
                AssistChip(onClick = { metric = m; unit = u }, label = { Text(m) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(metric, { metric = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Metric (e.g. Weight, Resting HR)") })
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value, { value = it }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Value") },
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(unit, { unit = it }, modifier = Modifier.width(90.dp), singleLine = true, placeholder = { Text("Unit") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val m = metric.trim().replace("\n", " ")
                val v = value.trim().toDoubleOrNull()
                val u = unit.trim().replace("\n", " ")
                if (m.isNotEmpty() && v != null) {
                    persist(data.copy(readings = readings + Reading(nextId, m, v, u, today().toString())))
                    nextId += 1
                    value = ""
                }
            }) { Text("Log") }
        }
        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(groups.size, key = { groups[it].key }) { i ->
                val name = groups[i].key
                val series = groups[i].value
                val latest = series.last()
                val prev = series.getOrNull(series.size - 2)
                val delta = prev?.let { latest.value - it.value } ?: 0.0

                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            if (latest.date.isNotBlank()) Text(latest.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${num(latest.value)} ${latest.unit}".trim(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        if (prev != null && delta != 0.0) {
                            Text(
                                (if (delta > 0) "  ▲ " else "  ▼ ") + num(kotlin.math.abs(delta)),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            val target = readings.lastOrNull { it.metric == name }
                            if (target != null) persist(data.copy(readings = readings.filterNot { it.id == target.id }))
                        }) { Text("✕") }
                    }
                    Spacer(Modifier.height(6.dp))
                    val recent = series.takeLast(10)
                    val max = recent.maxOf { it.value }
                    val min = recent.minOf { it.value }
                    val span = (max - min).takeIf { it > 0 } ?: 1.0
                    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        recent.forEach { r ->
                            val frac = (0.2 + 0.8 * ((r.value - min) / span)).toFloat()
                            Box(Modifier.weight(1f).fillMaxHeight(frac).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                    Text("${series.size} reading${if (series.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---- Import (Apple Health / Garmin) -----------------------------------------

@Composable
private fun ImportTab(data: HealthData, persist: (HealthData) -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<DailyLog>?>(null) }
    val scope = rememberCoroutineScope()

    fun runParse(text: String?, parse: (String) -> List<DailyLog>) {
        if (text == null) { busy = false; return }
        scope.launch {
            val days = withContext(Dispatchers.Default) { parse(text) }
            busy = false
            if (days.isEmpty()) {
                error = "No sleep, workout, water, or weight data found in that file."
            } else {
                preview = days
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!Native.supportsFilePick) {
            Text("File import isn't available on this platform.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        Text("Apple Health", style = MaterialTheme.typography.titleMedium)
        Text(
            "Health app → profile picture → Export All Health Data. Pick the export.zip (or export.xml). Sleep, workouts, water, and weight aggregate to one entry per day. One-time import, not a live sync.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                error = null; preview = null; busy = true
                Native.pickFilteredTextFile(APPLE_HEALTH_FILTER) { text -> runParse(text, ::parseAppleHealth) }
            },
            enabled = !busy,
        ) { Text("Choose Apple Health export") }

        Spacer(Modifier.height(16.dp))
        Text("Garmin Connect", style = MaterialTheme.typography.titleMedium)
        Text(
            "A CSV export from Garmin Connect — the Activities list export (workouts), or a sleep/weight report CSV.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                error = null; preview = null; busy = true
                Native.pickTextFile { text -> runParse(text, ::parseGarminCsv) }
            },
            enabled = !busy,
        ) { Text("Choose Garmin CSV") }

        Spacer(Modifier.height(12.dp))
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Reading… large exports can take a moment.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

        preview?.let { days ->
            Spacer(Modifier.height(8.dp))
            val sleeps = days.count { it.sleepHours != null }
            val workouts = days.count { it.workoutType.isNotBlank() || it.workoutMinutes != null }
            val waters = days.count { it.waterOz != null }
            val weights = days.count { it.weightLb != null }
            Text("Found ${days.size} day(s): $sleeps sleep · $workouts workout · $waters water · $weights weight.", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Importing fills matching fields on existing logs for those dates and creates new ones otherwise.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Button(onClick = {
                    val (merged, result) = mergeImportedDays(data.logs, days)
                    persist(data.copy(logs = merged))
                    preview = null
                    status = "Imported: ${result.created} new day(s), ${result.updated} updated."
                }) { Text("Confirm import") }
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = { preview = null }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}
