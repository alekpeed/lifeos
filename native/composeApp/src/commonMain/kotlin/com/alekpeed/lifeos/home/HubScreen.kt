package com.alekpeed.lifeos.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.Module
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.integrations.MarketsClient
import com.alekpeed.lifeos.integrations.WeatherClient
import com.alekpeed.lifeos.integrations.WeatherNow
import com.alekpeed.lifeos.mood.appendMood
import com.alekpeed.lifeos.mood.latestMood
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.ui.SaveToast
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

// Typography: a serif display face for headings and section labels (a clear step
// away from the default sans), monospace for eyebrows and data.
private val Display = FontFamily.Serif
private val Mono = FontFamily.Monospace

private data class SectionMeta(val id: String, val icon: String, val label: String)

// Alphabetical, matching MODULE_GROUPS.
private val SECTIONS = listOf(
    SectionMeta("Core", "🗓", "Core"),
    SectionMeta("Health", "🔥", "Health"),
    SectionMeta("Insight", "📋", "Insight"),
    SectionMeta("Memory", "📚", "Memory"),
    SectionMeta("People", "👤", "People"),
    SectionMeta("System", "⚙", "System"),
)

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private fun greetingWord(hour: Int): String = when {
    hour < 5 -> "night"
    hour < 12 -> "morning"
    hour < 17 -> "afternoon"
    hour < 21 -> "evening"
    else -> "night"
}

// A clean, functional home screen: greeting, today's numbers, one bar per section,
// a mood check-in, and a live ticker. Tap a section bar to open its modules.
@Composable
fun HubScreen(modules: List<Module>, onOpenSection: (String) -> Unit) {
    var weather by remember { mutableStateOf<WeatherNow?>(null) }
    var btc by remember { mutableStateOf<Double?>(null) }
    var djia by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        WeatherClient.forCity("New York").onSuccess { weather = it }
        MarketsClient.crypto(listOf("bitcoin")).onSuccess { list -> btc = list.firstOrNull()?.usd }
        MarketsClient.djia().onSuccess { djia = it }
    }

    val tasksDue = remember { loadTasks().count { !it.done && it.due.isNotBlank() && (it.dueDate()?.let { d -> d <= today() } == true) } }
    val habits = remember { loadHabits() }
    val habitsToCheck = remember { habits.count { !it.checkedInToday } }
    val streak = remember { habits.maxOfOrNull { it.streak } ?: 0 }
    val ringPct = remember {
        val total = habits.size.coerceAtLeast(1)
        (habits.count { it.checkedInToday } * 100 / total).coerceIn(0, 100)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp)
            .padding(top = 28.dp, bottom = 18.dp),
    ) {
        Greeting()
        Spacer(Modifier.height(16.dp))
        HeroCard(ringPct, tasksDue, habitsToCheck, streak)

        Column(
            Modifier.fillMaxWidth().weight(1f).padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            SECTIONS.forEach { s -> SectionBar(s) { onOpenSection(s.id) } }
        }

        MoodCard()
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CornerButton("⚙") { Nav.open("settings") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Ticker(weather, btc, djia)
            }
            CornerButton("🔍") { Nav.open("search") }
        }
    }
}

@Composable
private fun Greeting() {
    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    val dateLine = "${WEEKDAYS.getOrElse(now.dayOfWeek.ordinal) { "" }} · ${now.dayOfMonth} ${MONTHS.getOrElse(now.monthNumber - 1) { "" }}"
    Column {
        Text(
            dateLine.uppercase(),
            fontFamily = Mono,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Good ${greetingWord(now.hour)}, Alek.",
            fontFamily = Display,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun HeroCard(pct: Int, tasksDue: Int, habitsToCheck: Int, streak: Int) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DayRing(pct)
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow("$tasksDue", "tasks due")
                StatRow("$habitsToCheck", "habits to check in")
                StatRow("🔥 $streak", "day streak")
            }
        }
    }
}

@Composable
private fun StatRow(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontFamily = Display, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(8.dp))
        Text(label, fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// A clean single-accent progress ring — no glow, no gradient.
@Composable
private fun DayRing(pct: Int) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(78.dp)) {
            val sw = 7.dp.toPx()
            drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(sw, cap = StrokeCap.Round))
            if (pct > 0) {
                drawArc(color = accent, startAngle = -90f, sweepAngle = 360f * (pct / 100f), useCenter = false, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pct%", fontFamily = Display, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("TODAY", fontFamily = Mono, fontSize = 8.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionBar(section: SectionMeta, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(section.icon, fontSize = 22.sp)
            Spacer(Modifier.width(16.dp))
            Text(
                section.label,
                fontFamily = Display,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// The mood check-in — a snapping 1..10 slider that logs to the Mood journal when
// it settles on a new value.
@Composable
private fun MoodCard() {
    var value by remember { mutableStateOf(latestMood()?.score ?: 6) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("How are you feeling right now?", fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$value", fontFamily = Display, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { value = it.roundToInt().coerceIn(1, 10) },
                onValueChangeFinished = {
                    appendMood(value)
                    SaveToast.show("mood $value logged ✓")
                },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(),
            )
        }
    }
}

@Composable
private fun Ticker(weather: WeatherNow?, btc: Double?, djia: Double?) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TickerCell("NYC", weather?.let { "☀ ${it.tempF}°" } ?: "—")
            TickerCell("BTC", btc?.let { "$${it.toInt().grouped()}" } ?: "—")
            TickerCell("DJIA", djia?.let { it.toInt().grouped() } ?: "—")
        }
    }
}

@Composable
private fun TickerCell(key: String, value: String) {
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, fontFamily = Mono, fontSize = 8.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(5.dp))
        Text(value, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CornerButton(glyph: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(glyph, fontSize = 18.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun Int.grouped(): String = toString().reversed().chunked(3).joinToString(",").reversed()
