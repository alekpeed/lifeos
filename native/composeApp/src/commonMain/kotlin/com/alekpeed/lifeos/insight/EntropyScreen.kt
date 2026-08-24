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
import com.alekpeed.lifeos.data.StaleLevel
import com.alekpeed.lifeos.data.StaleRule
import com.alekpeed.lifeos.data.agoLabel
import com.alekpeed.lifeos.data.countOf
import com.alekpeed.lifeos.data.daysSinceMillis
import com.alekpeed.lifeos.data.levelFor
import com.alekpeed.lifeos.data.worstFirst
import com.alekpeed.lifeos.sync.SyncMeta
import kotlin.math.roundToInt

// Entropy — a computed neglect dashboard, not a list. For each area that holds
// data, how long since it was last touched (from the per-record timestamps the
// sync layer tracks), most-neglected first, with an overall average. Stores
// nothing itself.
//
// The arithmetic is the shared staleness utility (§12.1.2) — the same one Rabbit Holes
// counts cold days with, and the one Contacts cadence and the unused-subscription flag
// will use. Only the thresholds and the colours are this screen's.
private data class Area(val label: String, val days: Int?)

// A week to want attention, a month to count as left. These numbers are Entropy's own:
// a module untouched for ten days is fine, a contact unspoken to for ten days may not be.
private val AREA_RULE = StaleRule(staleAfter = 7, neglectedAfter = 30)

private fun sevColor(days: Int?): Color = when (levelFor(days, AREA_RULE)) {
    StaleLevel.UNKNOWN -> Color(0xFF8A94A3)
    StaleLevel.FRESH -> Color(0xFF2F9E57)
    StaleLevel.STALE -> Color(0xFFC98A1A)
    StaleLevel.NEGLECTED -> Color(0xFFE05C5C)
}

@Composable
fun EntropyScreen() {
    val areas = remember {
        val list = DATA_SOURCES
            .filter { it.key != "Entropy" && countOf(it.key) > 0 }
            .map { ds -> Area(ds.label, daysSinceMillis(SyncMeta.metaOf(ds.key)?.updatedAt)) }
        // Worst first, and an area with no timestamp yet sorts last rather than first —
        // it is the thing we cannot say anything about, not the most neglected thing.
        worstFirst(list) { it.days }
    }
    val known = areas.mapNotNull { it.days }
    val overall = if (known.isNotEmpty()) known.average().roundToInt() else null

    Column(Modifier.fillMaxSize().padding(20.dp)) {
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
                            agoLabel(a.days),
                            style = MaterialTheme.typography.bodyMedium,
                            color = sevColor(a.days),
                        )
                    }
                }
            }
        }
    }
}
