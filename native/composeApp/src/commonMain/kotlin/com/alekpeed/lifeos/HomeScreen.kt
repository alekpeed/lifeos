package com.alekpeed.lifeos

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.calendar.datedWorklist
import com.alekpeed.lifeos.data.today

// The home launcher.
//
// Forty-one modules across eight domains, and exactly two ways to reach one: type a few
// letters, or open the domain it lives in. Pinned and Recent rows were built and then
// removed — with a search box and every domain already on the screen they were a third
// and fourth route to the same place, and two rows of duplicated navigation pushed the
// real content down a screen that has a notch eating the top of it.
//
// It follows the app theme rather than committing to its own dark palette, which is what
// the version before this did — that put a visible seam at the first tap.
//
// One line reads live data — what is owed today — and only that one. A launcher that
// summarises everything becomes a worse Briefing; one that says nothing at all makes you
// open Briefing to find out whether you needed to. It is wrapped so a slow or broken read
// costs the line and not the first screen of the app.
//
// Type does the work rather than ornament: monospace for chrome, the theme's own face for
// names. No artwork here by design — a graphical interface attaches through the
// Interfaces registry, and this is the functional one holding the fort until it does.

private val MONO = FontFamily.Monospace

@Composable
fun HomeScreen(modules: List<Module>, onOpen: (Module) -> Unit) {
    var query by remember { mutableStateOf("") }
    var openDomains by remember { mutableStateOf(setOf<String>()) }

    // The one live read. Once per composition, and a failure is silence rather than a
    // crash on the first screen of the app.
    val owed = remember { runCatching { datedWorklist() }.getOrDefault(emptyList()) }
    val overdue = remember(owed) { owed.count { it.isOverdue() } }

    val results = remember(query, modules) { searchModules(modules, query) }
    val searching = query.isNotBlank()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Header(moduleCount = modules.size, owed = owed.size, overdue = overdue) {
                modules.firstOrNull { it.id == "briefing" }?.let(onOpen)
            }
            Spacer(Modifier.height(12.dp))
            // Only after a crash, and only until dismissed. The home screen is the one
            // place it is guaranteed to be seen, since a crash returns you here. It
            // composes nothing at all when there is no report, so it costs no layout.
            com.alekpeed.lifeos.diag.CrashBanner()
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search modules") },
                trailingIcon = {
                    if (searching) TextButton(onClick = { query = "" }) { Text("Clear") }
                },
            )
            Spacer(Modifier.height(14.dp))
        }

        if (searching) {
            item {
                Label("${results.size} match${if (results.size == 1) "" else "es"}")
                Spacer(Modifier.height(8.dp))
                if (results.isEmpty()) {
                    Text(
                        "Nothing by that name.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TileGrid(results, onOpen)
                }
                Spacer(Modifier.height(24.dp))
            }
            return@LazyColumn
        }

        for (group in MODULE_GROUPS) {
            val mods = modules.filter { it.group == group }
            if (mods.isEmpty()) continue
            item {
                val isOpen = group in openDomains
                DomainRow(group, mods, isOpen) {
                    // Several at once: closing one to open another means losing your place
                    // every time you compare two domains.
                    openDomains = if (isOpen) openDomains - group else openDomains + group
                }
                if (isOpen) {
                    Spacer(Modifier.height(10.dp))
                    TileGrid(mods, onOpen)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Header(moduleCount: Int, owed: Int, overdue: Int, onOpenBriefing: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f)) {
                Text(
                    "life", fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    ".", fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "os", fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                "$moduleCount modules",
                fontFamily = MONO, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .clickable(enabled = owed > 0, onClick = onOpenBriefing)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                today().toString(),
                fontFamily = MONO, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    owed == 0 -> "nothing owed"
                    overdue > 0 -> "$owed need you · $overdue overdue"
                    else -> "$owed need you"
                },
                fontFamily = MONO, fontSize = 11.sp,
                color = if (overdue > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        fontFamily = MONO,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
    )
}

@Composable
private fun DomainRow(group: String, mods: List<Module>, isOpen: Boolean, onToggle: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (isOpen) "▾" else "▸",
                fontFamily = MONO, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(16.dp),
            )
            Text(
                group,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(110.dp),
            )
            Text(
                mods.take(7).joinToString(" ") { it.icon },
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${mods.size}",
                fontFamily = MONO, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        )
    }
}

@Composable
private fun TileGrid(mods: List<Module>, onOpen: (Module) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in mods.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in row) {
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onOpen(m) }
                            .padding(vertical = 13.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(m.icon, fontSize = 20.sp)
                        Text(
                            m.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (m.ready) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.size < 3) for (i in row.size until 3) Spacer(Modifier.weight(1f))
            }
        }
    }
}
