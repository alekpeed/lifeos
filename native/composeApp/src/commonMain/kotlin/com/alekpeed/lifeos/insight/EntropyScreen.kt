package com.alekpeed.lifeos.insight

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.DATA_SOURCES
import com.alekpeed.lifeos.data.countOf
import com.alekpeed.lifeos.sync.SyncMeta
import kotlinx.datetime.Clock
import kotlin.math.roundToInt

// Entropy — a computed neglect dashboard, not a list. For each area that holds
// data, how long since it was last touched (from the per-record timestamps the
// sync layer tracks), most-neglected first, with an overall average. Stores
// nothing itself.
private data class Area(val label: String, val days: Int?)

private const val DAY_MS = 86_400_000L

private fun fresh() = Color(0xFF2F9E57)
private fun stale() = Color(0xFFC98A1A)
private fun neglected() = Color(0xFFE05C5C)
private fun unknown() = Color(0xFF8A94A3)

private fun sevColor(days: Int?): Color = when {
    days == null -> unknown()
    days <= 6 -> fresh()
    days <= 29 -> stale()
    else -> neglected()
}

private fun sevLabel(days: Int?): String = when {
    days == null -> "no timestamp yet"
    days == 0 -> "today"
    days == 1 -> "1 day ago"
    else -> "$days days ago"
}

@Composable
fun EntropyScreen() {
    val areas = remember {
        val now = Clock.System.now().toEpochMilliseconds()
        DATA_SOURCES
            .filter { it.key != "Entropy" && countOf(it.key) > 0 }
            .map { ds ->
                val ts = SyncMeta.metaOf(ds.key)?.updatedAt
                val days = ts?.let { ((now - it) / DAY_MS).toInt().coerceAtLeast(0) }
                Area(ds.label, days)
            }
            .sortedByDescending { it.days ?: -1 }
    }
    val known = areas.mapNotNull { it.days }
    val overall = if (known.isNotEmpty()) known.average().roundToInt() else null

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Entropy", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "How long since each area was last touched — most neglected first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(sevColor(overall).copy(alpha = 0.14f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Overall", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                if (overall == null) "—" else "$overall days avg.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = sevColor(overall),
            )
        }
        Spacer(Modifier.height(12.dp))

        if (areas.isEmpty()) {
            Text(
                "Nothing tracked yet — add data in a few modules and check back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(areas) { a ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(sevColor(a.days)))
                        Spacer(Modifier.width(12.dp))
                        Text(a.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            sevLabel(a.days),
                            style = MaterialTheme.typography.bodyMedium,
                            color = sevColor(a.days),
                        )
                    }
                }
            }
        }
    }
}
