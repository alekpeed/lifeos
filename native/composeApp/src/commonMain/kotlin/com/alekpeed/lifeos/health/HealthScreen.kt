package com.alekpeed.lifeos.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage

private data class Reading(val metric: String, val value: Double, val unit: String)

private fun num(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else ((v * 100).toLong() / 100.0).toString()

private fun load(): List<Reading> =
    Storage.read("Health")?.lines()?.filter { it.isNotBlank() }?.map { line ->
        val p = line.split("\t")
        Reading(p.getOrElse(0) { line }, p.getOrElse(1) { "0" }.toDoubleOrNull() ?: 0.0, p.getOrElse(2) { "" })
    } ?: emptyList()

private fun save(readings: List<Reading>) {
    Storage.write("Health", readings.joinToString("\n") { "${it.metric}\t${it.value}\t${it.unit}" })
}

// Structured health readings — metric, value, unit — grouped by metric with the
// latest value, a change arrow vs the previous reading, and a small bar trend of
// recent values. Readings are kept in entry order (oldest → newest) per metric.
@Composable
fun HealthScreen() {
    val readings = remember { mutableStateListOf<Reading>().apply { addAll(load()) } }
    fun persist() = save(readings)
    var metric by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }

    // Preserve first-seen order of metric names.
    val grouped = LinkedHashMap<String, MutableList<Reading>>()
    readings.forEach { grouped.getOrPut(it.metric) { mutableListOf() }.add(it) }
    val groups = grouped.entries.toList()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Health", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = metric,
            onValueChange = { metric = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Metric (e.g. Weight, Resting HR)") },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("Value") },
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                modifier = Modifier.width(90.dp),
                singleLine = true,
                placeholder = { Text("Unit") },
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val m = metric.trim().replace("\t", " ").replace("\n", " ")
                val v = value.trim().toDoubleOrNull()
                val u = unit.trim().replace("\t", " ").replace("\n", " ")
                if (m.isNotEmpty() && v != null) {
                    readings.add(Reading(m, v, u))
                    persist()
                    value = ""
                }
            }) { Text("Log") }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(groups.size) { i ->
                val (name, series) = groups[i]
                val latest = series.last()
                val prev = series.getOrNull(series.size - 2)
                val delta = prev?.let { latest.value - it.value } ?: 0.0

                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${num(latest.value)} ${latest.unit}".trim(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (prev != null && delta != 0.0) {
                            Text(
                                (if (delta > 0) "  ▲ " else "  ▼ ") + num(kotlin.math.abs(delta)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            val idx = readings.indexOfLast { it.metric == name }
                            if (idx >= 0) { readings.removeAt(idx); persist() }
                        }) { Text("✕") }
                    }
                    Spacer(Modifier.height(6.dp))
                    val recent = series.takeLast(10)
                    val max = recent.maxOf { it.value }
                    val min = recent.minOf { it.value }
                    val span = (max - min).takeIf { it > 0 } ?: 1.0
                    Row(
                        Modifier.fillMaxWidth().height(40.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        recent.forEach { r ->
                            val frac = (0.2 + 0.8 * ((r.value - min) / span)).toFloat()
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(frac)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                    Text(
                        "${series.size} reading${if (series.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
