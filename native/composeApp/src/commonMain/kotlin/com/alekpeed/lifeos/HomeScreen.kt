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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The home launcher, "Console" style: a personal control panel. Pinned modules up
// top, then every one of the six domains — tap a domain to reveal its modules
// inline. Pure layout: no storage, clock, or network reads, so it just draws.
// Commits to its own dark, monospaced look; module screens still follow the theme.

private val BG = Color(0xFF0F1216)
private val PANEL = Color(0xFF151A20)
private val TEXT = Color(0xFFDFE4E6)
private val MUTED = Color(0xFF8B939A)
private val ICON_MUTED = Color(0xFF9AA2A8)
private val ACCENT = Color(0xFF5EC7A6)
private val HAIR = Color(0xFF23282E)
private val MONO = FontFamily.Monospace

private val PINNED_IDS = listOf("today", "tasks", "briefing", "finance", "command", "ask")

@Composable
fun HomeScreen(modules: List<Module>, onOpen: (Module) -> Unit) {
    val pinned = remember(modules) { PINNED_IDS.mapNotNull { id -> modules.firstOrNull { it.id == id } } }
    var expanded by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().background(BG).padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Row(Modifier.weight(1f)) {
                    Text("life", color = TEXT, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(".", color = ACCENT, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("os", color = TEXT, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
                Text("${modules.size} modules", color = MUTED, fontFamily = MONO, fontSize = 11.sp)
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("PINNED")
            Spacer(Modifier.height(8.dp))
            TileGrid(pinned, onOpen)
            Spacer(Modifier.height(18.dp))
            SectionLabel("DOMAINS")
        }
        for (group in MODULE_GROUPS) {
            val mods = modules.filter { it.group == group }
            if (mods.isEmpty()) continue
            item {
                val isOpen = expanded == group
                DomainRow(group, mods, isOpen) { expanded = if (isOpen) "" else group }
                if (isOpen) {
                    Spacer(Modifier.height(8.dp))
                    TileGrid(mods, onOpen)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = ACCENT, fontFamily = MONO, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
}

@Composable
private fun DomainRow(group: String, mods: List<Module>, isOpen: Boolean, onToggle: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(group, color = TEXT, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(116.dp))
            Text(
                mods.take(6).joinToString(" ") { it.icon },
                color = ICON_MUTED, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text("${mods.size}", color = ACCENT, fontFamily = MONO, fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(HAIR))
    }
}

@Composable
private fun TileGrid(mods: List<Module>, onOpen: (Module) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in mods.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in row) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(PANEL)
                            .clickable { onOpen(m) }.padding(vertical = 13.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(m.icon, fontSize = 20.sp)
                        Text(
                            m.label, color = if (m.ready) TEXT else MUTED,
                            fontFamily = MONO, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.size < 3) for (i in row.size until 3) Spacer(Modifier.weight(1f))
            }
        }
    }
}
