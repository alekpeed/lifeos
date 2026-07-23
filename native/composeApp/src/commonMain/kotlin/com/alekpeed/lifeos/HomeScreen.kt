package com.alekpeed.lifeos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.integrations.WeatherClient
import com.alekpeed.lifeos.tasks.loadTasks
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// The home launcher, "Console" style: a personal control panel. A live status
// line greets you with today's real signals (tasks due / overdue, habit streak,
// weather), the modules you reach for most sit pinned up top, and every one of
// the six domains is a tap away — tap a domain to reveal its modules inline.
//
// This screen deliberately commits to its own dark, monospaced look rather than
// following the app theme: it IS the home identity. Module screens still theme.

private val BG = Color(0xFF0F1216)
private val PANEL = Color(0xFF151A20)
private val TEXT = Color(0xFFDFE4E6)
private val MUTED = Color(0xFF8B939A)
private val ICON_MUTED = Color(0xFF9AA2A8)
private val ACCENT = Color(0xFF5EC7A6)
private val WARN = Color(0xFFE0A15C)
private val HAIR = Color(0x14FFFFFF)
private val MONO = FontFamily.Monospace

// Modules surfaced at the top, most-reached-for first. (Fixed for now; a natural
// spot to make user-editable later.)
private val PINNED_IDS = listOf("today", "tasks", "briefing", "finance", "command", "ask")

@Composable
fun HomeScreen(modules: List<Module>, onOpen: (Module) -> Unit) {
    // Live signals. Loaded OFF the first frame and fully guarded: this is the
    // landing screen, so a bad record in any store must never keep the home from
    // drawing — worst case the counts stay at zero.
    var dueToday by remember { mutableStateOf(0) }
    var overdue by remember { mutableStateOf(0) }
    var streak by remember { mutableStateOf(0) }
    var weather by remember { mutableStateOf<String?>(null) }

    val greeting = remember {
        runCatching {
            when (Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour) {
                in 0..11 -> "Good morning"
                in 12..17 -> "Good afternoon"
                else -> "Good evening"
            }
        }.getOrDefault("Welcome back")
    }
    val dateStamp = remember { runCatching { today().toString().replace('-', '·') }.getOrDefault("") }

    LaunchedEffect(Unit) {
        runCatching {
            val tasks = loadTasks()
            overdue = tasks.count { val d = it.dueDate(); !it.done && d != null && d < today() }
            dueToday = tasks.count { !it.done && it.dueDate() == today() }
            streak = loadHabits().maxOfOrNull { it.streak } ?: 0
        }
        // Weather: reuses the city saved in Tools; quiet when unset or offline.
        runCatching {
            val city = Storage.read("WeatherCity")?.trim().orEmpty()
            if (city.isNotEmpty()) {
                WeatherClient.forCity(city).onSuccess { w ->
                    weather = "${w.tempF}°F · ${w.description} · ${w.place}"
                }
            }
        }
    }

    val pinned = remember(modules) { PINNED_IDS.mapNotNull { id -> modules.firstOrNull { it.id == id } } }
    var expanded by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(BG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 28.dp),
    ) {
        // Header: wordmark + date.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                buildAnnotatedString {
                    append("life")
                    withStyle(SpanStyle(color = ACCENT)) { append(".") }
                    append("os")
                },
                color = TEXT, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 21.sp,
                modifier = Modifier.weight(1f),
            )
            Text(dateStamp, color = MUTED, fontFamily = MONO, fontSize = 11.sp)
        }
        Spacer(Modifier.height(14.dp))

        // Live status line.
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PANEL)
                .border(1.dp, HAIR, RoundedCornerShape(12.dp))
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ACCENT)) { append("▸ ") }
                    append(greeting)
                },
                color = TEXT, fontFamily = MONO, fontSize = 12.sp,
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ACCENT, fontWeight = FontWeight.Bold)) { append("$dueToday") }
                    append(" due · ")
                    withStyle(SpanStyle(color = if (overdue > 0) WARN else MUTED, fontWeight = FontWeight.Bold)) { append("$overdue") }
                    append(" overdue")
                    if (streak > 0) {
                        append("  ·  🔥 ")
                        withStyle(SpanStyle(color = ACCENT, fontWeight = FontWeight.Bold)) { append("${streak}d") }
                    }
                },
                color = TEXT, fontFamily = MONO, fontSize = 12.sp,
            )
            weather?.let { Text(it, color = MUTED, fontFamily = MONO, fontSize = 11.sp) }
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel("Pinned")
        Spacer(Modifier.height(8.dp))
        TileGrid(pinned, onOpen)

        Spacer(Modifier.height(18.dp))
        SectionLabel("Domains")
        Spacer(Modifier.height(2.dp))
        for (group in MODULE_GROUPS) {
            val mods = modules.filter { it.group == group }
            if (mods.isEmpty()) continue
            val isOpen = expanded == group
            DomainRow(group, mods, isOpen) { expanded = if (isOpen) null else group }
            if (isOpen) {
                Spacer(Modifier.height(8.dp))
                TileGrid(mods, onOpen)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = ACCENT, fontFamily = MONO, fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp, letterSpacing = 3.sp,
    )
}

@Composable
private fun DomainRow(group: String, mods: List<Module>, isOpen: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(group, color = TEXT, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.width(76.dp))
        Text(
            mods.take(6).joinToString(" ") { it.icon },
            color = ICON_MUTED, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(if (isOpen) "▾" else "${mods.size}", color = ACCENT, fontFamily = MONO, fontSize = 11.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(HAIR))
}

// A 3-column grid of module tiles; taps open the module.
@Composable
private fun TileGrid(mods: List<Module>, onOpen: (Module) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in mods.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in row) {
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PANEL)
                            .border(1.dp, HAIR, RoundedCornerShape(12.dp))
                            .clickable { onOpen(m) }
                            .padding(vertical = 13.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(m.icon, fontSize = 20.sp)
                        Text(
                            m.label,
                            color = if (m.ready) TEXT else MUTED,
                            fontFamily = MONO, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Pad a short final row so tiles keep their column width.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
