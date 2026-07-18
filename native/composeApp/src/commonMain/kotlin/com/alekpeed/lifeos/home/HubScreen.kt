package com.alekpeed.lifeos.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val bgDeep = Color(0xFF0B0D13)
private val cyan = Color(0xFF57E6D9)
private val violet = Color(0xFF8B7BF0)
private val pink = Color(0xFFF06FB0)

// Section metadata the hub bars render: icon, label, and a halo tint per group id
// (matches MODULE_GROUPS from Modules.kt).
private data class SectionMeta(val id: String, val icon: String, val label: String, val halo: Color)

private val SECTIONS = listOf(
    SectionMeta("Core", "🗓", "Core", cyan),
    SectionMeta("Health", "🔥", "Health", Color(0xFFFF7A8A)),
    SectionMeta("Insight", "📋", "Insight", Color(0xFF5C9CE0)),
    SectionMeta("Memory", "📚", "Memory", pink),
    SectionMeta("People", "👤", "People", violet),
    SectionMeta("System", "⚙", "System", Color(0xFF7B8AA8)),
)

// The Nocturne hub: greeting, today's ring + stats, six section bars, a mood
// check-in, and a live ticker. Replaces the old flat module list as the app's
// home screen. Filled in section by section below.
@Composable
fun HubScreen(modules: List<Module>, onOpenSection: (String) -> Unit) {
    val scope = rememberCoroutineScope()
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
        val totalHabits = habits.size.coerceAtLeast(1)
        val doneHabits = habits.count { it.checkedInToday }
        (doneHabits * 100 / totalHabits).coerceIn(0, 100)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(bgDeep)
    ) {
        AuroraBackground()

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .padding(top = 30.dp, bottom = 20.dp),
        ) {
            Greeting()
            Spacer(Modifier.height(16.dp))
            HeroCard(ringPct, tasksDue, habitsToCheck, streak)

            // Section bars sit in the middle band and split the free space evenly,
            // so there's no dead void between the hero and the mood bar.
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp, Alignment.CenterVertically),
            ) {
                SECTIONS.forEach { s -> SectionBar(s) { onOpenSection(s.id) } }
            }

            MoodBar(onCommit = { score -> scope.launch { appendMood(score) } })
            Spacer(Modifier.height(11.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                CornerButton("⚙") { Nav.open("settings") }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Ticker(weather, btc, djia)
                }
                CornerButton("🔍") { Nav.open("search") }
            }
        }
    }
}

// The drifting aurora — three soft radial blobs (teal, violet, pink) that slowly
// wander behind everything. This is the atmosphere the hub is built on.
@Composable
private fun AuroraBackground() {
    val t = rememberInfiniteTransition(label = "aurora")
    val dx by t.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraDx",
    )
    val dy by t.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraDy",
    )
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun blob(cx: Float, cy: Float, r: Float, color: Color) {
            drawCircle(
                brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r),
                radius = r, center = Offset(cx, cy),
            )
        }
        blob(w * 0.16f + dx * 42f, h * 0.06f + dy * 32f, w * 0.78f, cyan.copy(alpha = 0.20f))
        blob(w * 0.92f + dx * 52f, h * 0.12f - dy * 30f, w * 0.82f, violet.copy(alpha = 0.26f))
        blob(w * 0.60f - dx * 44f, h * 1.02f + dy * 24f, w * 0.95f, pink.copy(alpha = 0.18f))
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private fun greetingWord(hour: Int): String = when {
    hour < 5 -> "night"
    hour < 12 -> "morning"
    hour < 17 -> "afternoon"
    hour < 21 -> "evening"
    else -> "night"
}

// Date line + the breathing-gradient greeting phrase (whole phrase animates, not
// just the name — matches the mockup's flowing text).
@Composable
private fun Greeting() {
    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    val dateLine = "${WEEKDAYS.getOrElse(now.dayOfWeek.ordinal) { "" }} · ${now.dayOfMonth} ${MONTHS.getOrElse(now.monthNumber - 1) { "" }}"
    val greeting = "Good ${greetingWord(now.hour)}, Alek."

    val transition = rememberInfiniteTransition(label = "greet")
    val shift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "greetShift",
    )

    Column {
        Text(
            dateLine.uppercase(),
            color = Color(0xFF8EA2CC),
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            greeting,
            fontSize = 26.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall.copy(
                brush = Brush.linearGradient(
                    colors = listOf(cyan, violet, pink, cyan),
                    start = Offset(shift * 260f, 0f),
                    end = Offset(shift * 260f + 260f, 0f),
                    tileMode = TileMode.Mirror,
                ),
            ),
        )
    }
}

// The raised glass card up top: a rotating-gradient ring showing today's
// completion, plus three quick stats beside it.
@Composable
private fun HeroCard(pct: Int, tasksDue: Int, habitsToCheck: Int, streak: Int) {
    RaisedGlass {
        Row(
            Modifier.fillMaxWidth().padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DayRing(pct)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
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
        Text(value, fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 11.5.sp, color = Color(0xFF9AA8CC))
    }
}

// A conic-swept progress ring that slowly rotates its gradient, plus a soft
// pulsing glow behind it — the "breathing" effect from the mockup.
@Composable
private fun DayRing(pct: Int) {
    val transition = rememberInfiniteTransition(label = "ring")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "ringRotation",
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "ringGlow",
    )
    val ringColors = listOf(cyan, violet, pink, cyan)

    Box(Modifier.size(94.dp), contentAlignment = Alignment.Center) {
        // Soft glowing ring behind the crisp one — a blurred stroked circle whose
        // colors rotate with the ring, breathing in and out.
        Canvas(
            Modifier
                .size(88.dp)
                .blur(16.dp)
                .alpha(glow)
        ) {
            rotate(rotation) {
                drawArc(
                    brush = Brush.sweepGradient(ringColors),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(13.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Canvas(Modifier.size(94.dp)) {
            val stroke = 11.dp.toPx()
            rotate(rotation) {
                // Faint full-gradient track so the ring always reads as a colored
                // ring — even at 0% — with the colors slowly rotating around it.
                drawArc(
                    brush = Brush.sweepGradient(ringColors),
                    startAngle = -90f - rotation, sweepAngle = 360f, useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round), alpha = 0.30f,
                )
                // Bright progress arc, held at the top while its colors rotate.
                if (pct > 0) {
                    drawArc(
                        brush = Brush.sweepGradient(ringColors),
                        startAngle = -90f - rotation, sweepAngle = 360f * (pct / 100f), useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pct%", fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("TODAY", fontSize = 8.sp, letterSpacing = 1.5.sp, color = Color(0xFF9DB0D8))
        }
    }
}

// The shared "raised glass" card look used by the hero, section bars, mood bar,
// ticker, and corner buttons — a soft translucent panel with a top highlight.
@Composable
private fun RaisedGlass(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.05f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
    ) {
        content()
    }
}

// One alphabetical section bar: icon, name, chevron, with a soft colored halo
// bleeding from the top-right corner.
@Composable
private fun SectionBar(section: SectionMeta, onClick: () -> Unit) {
    RaisedGlass(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val c = Offset(size.width - 20f, size.height / 2f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(section.halo.copy(alpha = 0.60f), Color.Transparent),
                            center = c,
                            radius = size.height * 2.1f,
                        ),
                        radius = size.height * 2.1f,
                        center = c,
                    )
                }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(section.icon, fontSize = 22.sp)
            }
            Spacer(Modifier.width(15.dp))
            Text(
                section.label,
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text("›", fontSize = 22.sp, color = Color(0xFF8592B4))
        }
    }
}

// Maps a 1..10 mood value to a 0..1 fraction along the track, inset so the thumb
// never clips either end (matches the mockup's 2%..98% range).
private fun fractionOf(value: Int): Float = (0.02f + (value - 1) / 9f * 0.96f)

// Maps a pointer x (0..width px) back to the nearest 1..10 stop.
private fun valueFromX(x: Float, width: Float): Int {
    if (width <= 0f) return 1
    val f = (x / width).coerceIn(0f, 1f)
    return (1 + (((f - 0.02f) / 0.96f).coerceIn(0f, 1f)) * 9f).let { kotlin.math.round(it).toInt() }.coerceIn(1, 10)
}

// The mood check-in. Drag it and it snaps into ten stops; each new stop writes a
// timestamped entry to the Mood journal (via onCommit) and flashes the app "Saved"
// pill. The fill runs the same LED-flowing gradient as the day ring.
@Composable
private fun MoodBar(modifier: Modifier = Modifier, onCommit: (Int) -> Unit) {
    var value by remember { mutableStateOf(latestMood()?.score ?: 6) }

    val transition = rememberInfiniteTransition(label = "mood")
    val flow by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "moodFlow",
    )

    fun commit(v: Int) {
        if (v != value) {
            value = v
            onCommit(v)
            com.alekpeed.lifeos.ui.SaveToast.show("mood $v logged ✓")
        }
    }

    RaisedGlass(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 9.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("How are you feeling right now?", fontSize = 11.sp, color = Color(0xFFC3CDE4))
                Text(
                    "$value",
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium.copy(
                        brush = Brush.linearGradient(
                            colors = listOf(cyan, violet, pink, cyan),
                            start = Offset(flow * 60f, 0f),
                            end = Offset(flow * 60f + 60f, 0f),
                            tileMode = TileMode.Mirror,
                        ),
                    ),
                )
            }
            Spacer(Modifier.height(9.dp))
            MoodTrack(value, flow, onCommit = ::commit)
        }
    }
}

// The interactive track: rail, LED-flowing fill, ten notches, and a glowing thumb.
// A tap or drag anywhere maps to the nearest stop and commits it.
@Composable
private fun MoodTrack(value: Int, flow: Float, onCommit: (Int) -> Unit) {
    var widthPx by remember { mutableStateOf(1f) }
    val fillFraction = fractionOf(value)

    Box(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onCommit(valueFromX(offset.x, widthPx)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> onCommit(valueFromX(change.position.x, widthPx)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // rail
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.09f))
        )
        // LED-flowing fill
        Box(
            Modifier
                .fillMaxWidth(fillFraction)
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(cyan, violet, pink, cyan),
                        start = Offset(flow * 90f, 0f),
                        end = Offset(flow * 90f + 90f, 0f),
                        tileMode = TileMode.Mirror,
                    ),
                )
        )
        // thumb
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset((fillFraction * widthPx - 10 * density).toInt(), 0) }
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFFD7E0FF), violet),
                    ),
                )
        )
    }
}

private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()

// The live info window: weather, BTC, DJIA — each fills in as its fetch returns,
// showing a "—" placeholder until then.
@Composable
private fun Ticker(weather: WeatherNow?, btc: Double?, djia: Double?, modifier: Modifier = Modifier) {
    RaisedGlass(modifier = modifier, shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            TickerCell("NYC", weather?.let { "☀ ${it.tempF}°" } ?: "—")
            TickerDivider()
            TickerCell("BTC", btc?.let { "$${it.toInt().grouped()}" } ?: "—")
            TickerDivider()
            TickerCell("DJIA", djia?.let { it.toInt().grouped() } ?: "—")
        }
    }
}

@Composable
private fun TickerCell(key: String, value: String) {
    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, fontSize = 8.sp, letterSpacing = 1.sp, color = Color(0xFF8EA2CC))
        Spacer(Modifier.width(5.dp))
        Text(value, fontSize = 11.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}

@Composable
private fun TickerDivider() {
    Box(Modifier.height(16.dp).width(1.dp).background(Color.White.copy(alpha = 0.10f)))
}

// A round glass corner control (gear / magnifier).
@Composable
private fun CornerButton(glyph: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    RaisedGlass(modifier = modifier.size(42.dp).clickable(onClick = onClick), shape = RoundedCornerShape(15.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(glyph, fontSize = 18.sp, color = Color(0xFFC6CFE4))
        }
    }
}
