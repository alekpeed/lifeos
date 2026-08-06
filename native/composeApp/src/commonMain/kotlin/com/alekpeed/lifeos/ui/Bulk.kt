package com.alekpeed.lifeos.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Multi-select for the lists in the app: tick a few rows, then act on all of them at
// once instead of opening each one and deleting it. Clearing twenty scanned items a row
// at a time was the thing that made a long list feel like a chore.
//
// The shape is deliberately small so a screen can adopt it in a handful of lines and
// keep whatever row layout it already has:
//
//   val bulk = rememberBulk()
//   BulkBar(bulk, shown.map { it.id }, onDelete = { ids -> save(...) })   // above the list
//   Row(Modifier.bulkClickable(bulk, row.id) { open(row.id) }) {
//       BulkTick(bulk, row.id)
//       ...
//   }
//
// Long-pressing any row starts a selection (press-and-hold works with a mouse too);
// while a selection is live a plain tap ticks rather than opens, which is what every
// phone list does. The bar also carries a "Select" button, so it's discoverable without
// knowing to long-press.

private val DANGER = Color(0xFFD64545)

class BulkState {
    var on by mutableStateOf(false)
        private set
    var picked by mutableStateOf<Set<Long>>(emptySet())
        private set

    val count: Int get() = picked.size

    fun has(id: Long): Boolean = id in picked

    // Enter selection mode with nothing ticked yet (the "Select" button).
    fun begin() {
        on = true
    }

    // Long-press a row: start selecting, with that row already ticked.
    fun start(id: Long) {
        on = true
        picked = picked + id
    }

    fun toggle(id: Long) {
        picked = if (id in picked) picked - id else picked + id
    }

    fun selectAll(ids: Collection<Long>) {
        on = true
        picked = ids.toSet()
    }

    fun clear() {
        on = false
        picked = emptySet()
    }

    // Only the ids still present in the list — rows can vanish underneath a selection
    // (a filter change, a sync landing), and an action must never act on a ghost.
    fun resolve(ids: Collection<Long>): Set<Long> = picked.intersect(ids.toSet())
}

@Composable
fun rememberBulk(): BulkState = remember { BulkState() }

// Row behaviour: long-press starts/extends a selection, a tap ticks while selecting and
// opens otherwise. Replaces the row's own .clickable { }.
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bulkClickable(bulk: BulkState, id: Long, onOpen: () -> Unit): Modifier =
    combinedClickable(
        onLongClick = { if (bulk.on) bulk.toggle(id) else bulk.start(id) },
        onClick = { if (bulk.on) bulk.toggle(id) else onOpen() },
    )

// The tick mark at the head of a row. A glyph rather than a Checkbox, so it can't
// swallow the tap meant for the row itself.
@Composable
fun BulkTick(bulk: BulkState, id: Long) {
    if (!bulk.on) return
    val ticked = bulk.has(id)
    Text(
        if (ticked) "☑" else "☐",
        style = MaterialTheme.typography.bodyLarge,
        color = if (ticked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 8.dp),
    )
}

// The bar above a list. Off: a quiet "Select". On: the count, all / none, whatever extra
// actions the screen offers, and Delete behind a confirmation.
//
// `ids` is the ids currently shown, in order, so "All" means "all of what I'm looking
// at" rather than everything the module holds.
@Composable
fun BulkBar(
    bulk: BulkState,
    ids: List<Long>,
    onDelete: (Set<Long>) -> Unit,
    noun: String = "item",
    extra: @Composable RowScope.(Set<Long>) -> Unit = {},
) {
    var confirming by remember { mutableStateOf(false) }

    if (ids.isEmpty()) {
        // Nothing left to act on — drop out of selection mode rather than leaving an
        // empty bar behind. Done as an effect so composition itself stays read-only.
        if (bulk.on) LaunchedEffect(Unit) { bulk.clear() }
        return
    }

    if (!bulk.on) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { bulk.begin() }) {
                Text("☑ Select", style = MaterialTheme.typography.labelMedium)
            }
        }
        return
    }

    val live = bulk.resolve(ids)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            if (live.isEmpty()) "Tap to select" else "${live.size} selected",
            style = MaterialTheme.typography.labelLarge,
            color = if (live.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 4.dp),
        )
        if (live.size < ids.size) {
            TextButton(onClick = { bulk.selectAll(ids) }) {
                Text("All", style = MaterialTheme.typography.labelMedium)
            }
        }
        extra(live)
        Spacer(Modifier.weight(1f))
        if (live.isNotEmpty()) {
            TextButton(onClick = { confirming = true }) {
                Text("Delete", style = MaterialTheme.typography.labelMedium, color = DANGER)
            }
        }
        TextButton(onClick = { bulk.clear() }) { Text("Done", style = MaterialTheme.typography.labelMedium) }
    }

    if (confirming) {
        val n = live.size
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete $n $noun${if (n == 1) "" else "s"}?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDelete(live)
                    bulk.clear()
                }) { Text("Delete $n", color = DANGER) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}
