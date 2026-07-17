package com.alekpeed.lifeos.orrery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.DATA_SOURCES
import com.alekpeed.lifeos.data.countOf
import com.alekpeed.lifeos.sync.SyncMeta
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.data.parseDateOrNull
import kotlinx.datetime.Clock

// The Orrery — the web app renders your life as an animated solar system where
// orbit = neglect, planet size = how much lives there, speed = this week's
// activity, and a pulsing ring = something overdue. Native shows the same real
// signals as a flight log (this app doesn't hand-build the animated graphics):
// each area with how much lives there, how long since it was touched, its orbit
// band (inner = fresh, outer = drifting), and any overdue count. Sorted
// most-neglected (outermost) first.

private const val DAY_MS = 86_400_000L

private data class Planet(val label: String, val count: Int, val days: Int?, val overdue: Int)

private fun band(days: Int?): String = when {
    days == null -> "Outer dark"
    days <= 6 -> "Inner"
    days <= 29 -> "Mid"
    else -> "Outer"
}

private fun bandColor(days: Int?): Color = when {
    days == null -> Color(0xFF8A94A3)
    days <= 6 -> Color(0xFF2F9E57)
    days <= 29 -> Color(0xFFC98A1A)
    else -> Color(0xFFE05C5C)
}

@Composable
fun OrreryScreen() {
    val planets = remember {
        val now = Clock.System.now().toEpochMilliseconds()
        val todayStr = today().toString()
        val tasksOverdue = loadTasks().count { t ->
            !t.done && t.due.isNotBlank() && (parseDateOrNull(t.due)?.toString() ?: "9999") < todayStr
        }
        DATA_SOURCES
            .filter { it.key !in setOf("Orrery", "Entropy") && countOf(it.key) > 0 }
            .map { ds ->
                val ts = SyncMeta.metaOf(ds.key)?.updatedAt
                val days = ts?.let { ((now - it) / DAY_MS).toInt().coerceAtLeast(0) }
                Planet(ds.label, countOf(ds.key), days, if (ds.key == "Tasks") tasksOverdue else 0)
            }
            .sortedByDescending { it.days ?: Int.MAX_VALUE }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("The Orrery", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Your life as a solar system: inner planets were touched recently, outer ones are drifting; the count is how much lives there. Most-neglected first.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        if (planets.isEmpty()) {
            Text("Nothing in orbit yet — add data in a few modules.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(planets) { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(bandColor(p.days)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${band(p.days)} orbit · ${p.count} record${if (p.count == 1) "" else "s"}" +
                                if (p.overdue > 0) " · ${p.overdue} overdue" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (p.overdue > 0) Color(0xFFE05C5C) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        when (val d = p.days) { null -> "no data"; 0 -> "today"; 1 -> "1d ago"; else -> "${d}d ago" },
                        style = MaterialTheme.typography.bodyMedium, color = bandColor(p.days),
                    )
                }
            }
        }
    }
}
