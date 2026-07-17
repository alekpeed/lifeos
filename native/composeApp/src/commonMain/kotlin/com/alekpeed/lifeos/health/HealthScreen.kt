package com.alekpeed.lifeos.health

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.today
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class Reading(val id: Long, val metric: String, val value: Double, val unit: String = "", val date: String = "")

@Serializable
private data class HealthData(val readings: List<Reading> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// Common metrics to prefill the field with one tap.
private val QUICK = listOf("Weight" to "lb", "Sleep" to "h", "Workout" to "min", "Resting HR" to "bpm", "Water" to "oz", "Steps" to "", "Mood" to "/10")

// Public read-only accessor for the stats layer (The Almanac). Exposes each
// dated reading as (metric, value, date) without leaking the private model.
data class HealthPoint(val metric: String, val value: Double, val date: String)
fun healthSeries(): List<HealthPoint> = load().map { HealthPoint(it.metric, it.value, it.date) }

private fun num(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else ((v * 100).toLong() / 100.0).toString()

private fun load(): List<Reading> {
    val raw = Storage.read("Health")
    if (raw.isNullOrBlank()) return emptyList()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<HealthData>(raw).readings }.getOrElse { emptyList() }
    }
    // Migrate old "metric\tvalue\tunit" lines (undated).
    return raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val p = line.split("\t")
        Reading(i + 1L, p.getOrElse(0) { line }, p.getOrElse(1) { "0" }.toDoubleOrNull() ?: 0.0, p.getOrElse(2) { "" })
    }
}

private fun save(readings: List<Reading>) {
    Storage.write("Health", json.encodeToString(HealthData(readings)))
}

// Structured, dated health readings — metric, value, unit, date — grouped by
// metric with the latest value + when, a change arrow vs the previous reading,
// and a small bar trend of recent values. Quick-metric chips prefill common ones.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthScreen() {
    var readings by remember { mutableStateOf(load()) }
    fun persist(next: List<Reading>) { readings = next; save(next) }
    var nextId by remember { mutableStateOf((readings.maxOfOrNull { it.id } ?: 0L) + 1) }
    var metric by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }

    val grouped = LinkedHashMap<String, MutableList<Reading>>()
    readings.forEach { grouped.getOrPut(it.metric) { mutableListOf() }.add(it) }
    val groups = grouped.entries.toList()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Health", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

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
                    persist(readings + Reading(nextId, m, v, u, today().toString()))
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
                            if (target != null) persist(readings.filterNot { it.id == target.id })
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
