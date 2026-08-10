package com.alekpeed.lifeos.interfaces.nexus

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.lifeOsModules
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.sync.SupabaseAuth
import com.alekpeed.lifeos.sync.SupabaseSync
import com.alekpeed.lifeos.sync.SyncEngine
import com.alekpeed.lifeos.sync.SyncMeta
import com.alekpeed.lifeos.system.scanCode
import com.alekpeed.lifeos.system.scanWithCamera
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.priorityRank
import com.alekpeed.lifeos.tasks.statusLabel
import com.alekpeed.lifeos.data.today
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

private val ROOM_DOMAINS = listOf(
    "Operations", "Archive", "Logistics", "Discovery",
    "Management", "Intelligence", "People", "System",
)

private val ROOM_ICONS = mapOf(
    "Operations" to "◇",
    "Archive" to "▱",
    "Logistics" to "⬡",
    "Discovery" to "◎",
    "Management" to "▥",
    "Intelligence" to "✣",
    "People" to "◉",
    "System" to "⬢",
)

private data class RoomLiveState(
    val score: String,
    val scoreCaption: String,
    val syncMain: String,
    val syncLine1: String,
    val syncLine2: String,
    val nextWhen: String,
    val nextTitle: String,
    val nextDetail: String,
    val notificationCount: Int,
)

@Composable
fun NexusCommandRoomHome() {
    val scope = rememberCoroutineScope()
    val modules = remember { lifeOsModules() }
    var openDomain by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        Native.setImmersive(true)
        onDispose { Native.setImmersive(false) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF05080D))) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val topInset = Native.cutoutTopPx().toFloat()
        val bottomInset = Native.navBottomPx().toFloat()
        val safeH = (h - topInset - bottomInset).coerceAtLeast(1f)

        fun Modifier.r(x: Float, y: Float, rw: Float, rh: Float): Modifier =
            offset(
                with(density) { (w * x).toDp() },
                with(density) { (topInset + safeH * y).toDp() },
            ).size(
                with(density) { (w * rw).toDp() },
                with(density) { (safeH * rh).toDp() },
            )

        RoomBackdrop(topInset, safeH)
        RoomChrome(topInset, safeH)
        RoomAnimations(topInset, safeH)

        HeaderLive(Modifier.r(0.03f, 0.018f, 0.94f, 0.07f))

        val left = listOf("Operations", "Archive", "Logistics", "Discovery")
        val right = listOf("Management", "Intelligence", "People", "System")
        val ys = listOf(0.135f, 0.232f, 0.329f, 0.426f)
        left.forEachIndexed { i, domain ->
            DomainTile(
                domain = domain,
                modifier = Modifier.r(0.055f, ys[i], 0.245f, 0.082f),
                onClick = { openDomain = domain },
            )
        }
        right.forEachIndexed { i, domain ->
            DomainTile(
                domain = domain,
                modifier = Modifier.r(0.700f, ys[i], 0.245f, 0.082f),
                onClick = { openDomain = domain },
            )
        }

        CoreConsole(
            modifier = Modifier.r(0.12f, 0.505f, 0.76f, 0.145f),
            onClick = { Nav.open("today") },
        )

        LiveStatsRow(
            modifier = Modifier.r(0.035f, 0.675f, 0.93f, 0.165f),
            onLife = { Nav.open("today") },
            onSync = { Nav.open("settings") },
            onNext = { Nav.open("tasks") },
        )

        BottomRail(
            modifier = Modifier.r(0.035f, 0.865f, 0.93f, 0.105f),
            onVoice = { Nav.open("command") },
            onNote = { Nav.open("ideas") },
            onCamera = { scanWithCamera(scope) },
            onBarcode = { scanCode(scope) },
            onAi = { Nav.open("ai-assistant") },
        )

        // Bell hit target, kept separate from the header so its icon can stay subtle.
        Box(Modifier.r(0.735f, 0.026f, 0.08f, 0.05f).clickable { Nav.open("notifications") })

        if (openDomain.isNotBlank()) {
            RoomDomainSheet(
                domain = openDomain,
                modules = modules.filter { it.group == openDomain },
                onPick = { Nav.open(it); openDomain = "" },
                onDismiss = { openDomain = "" },
            )
        }
    }
}

@Composable
private fun RoomBackdrop(topInset: Float, safeH: Float) {
    val transition = rememberInfiniteTransition(label = "city")
    val twinkle by transition.animateFloat(
        0.28f, 0.82f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
        label = "twinkle",
    )
    val scan by transition.animateFloat(
        0f, 1f,
        animationSpec = infiniteRepeatable(tween(5200), RepeatMode.Restart),
        label = "scan",
    )

    Canvas(Modifier.fillMaxSize()) {
        val y0 = topInset + safeH * 0.10f
        val y1 = topInset + safeH * 0.51f
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF09111B), Color(0xFF0A1722), Color(0xFF081019)),
                startY = y0,
                endY = y1,
            ),
            topLeft = Offset(0f, y0),
            size = Size(size.width, y1 - y0),
        )

        // Window frame.
        drawRoundRect(
            Color(0xFF6CB7DA).copy(alpha = 0.22f),
            topLeft = Offset(size.width * 0.315f, y0 + safeH * 0.015f),
            size = Size(size.width * 0.37f, safeH * 0.37f),
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(2f),
        )

        // Dense 2170 skyline. Geometry stays deterministic; only window light breathes.
        val skylineBottom = y1
        val towers = listOf(
            0.30f to 0.16f, 0.335f to 0.23f, 0.365f to 0.19f, 0.39f to 0.28f,
            0.425f to 0.20f, 0.455f to 0.31f, 0.49f to 0.24f, 0.52f to 0.34f,
            0.55f to 0.27f, 0.585f to 0.30f, 0.62f to 0.21f, 0.655f to 0.25f,
        )
        towers.forEachIndexed { i, (x, frac) ->
            val bw = size.width * (0.022f + (i % 3) * 0.006f)
            val bh = safeH * frac
            val left = size.width * x
            val top = skylineBottom - bh
            drawRect(Color(0xFF111B25), Offset(left, top), Size(bw, bh))
            drawRect(Color(0xFF27445B).copy(alpha = 0.55f), Offset(left, top), Size(bw, 2f))
            val cols = 3 + (i % 2)
            val rows = 9 + (i % 5)
            repeat(cols) { c ->
                repeat(rows) { r ->
                    if ((r + c + i) % 3 != 0) {
                        val lx = left + 4f + c * ((bw - 7f) / cols)
                        val ly = top + 8f + r * ((bh - 15f) / rows)
                        val col = when ((i + c + r) % 5) {
                            0 -> Color(0xFF2BE7FF)
                            1 -> Color(0xFF7E6BFF)
                            2 -> Color(0xFFFFA446)
                            3 -> Color(0xFFFF4E9B)
                            else -> Color(0xFFEAF7FF)
                        }
                        drawRect(col.copy(alpha = 0.30f + twinkle * 0.45f), Offset(lx, ly), Size(2.3f, 4.2f))
                    }
                }
            }
        }

        // Multi-level traffic ribbons.
        repeat(4) { i ->
            val yy = y1 - safeH * (0.035f + i * 0.028f)
            drawLine(Color(0xFF63D7FF).copy(alpha = 0.18f), Offset(size.width * 0.29f, yy), Offset(size.width * 0.71f, yy - 7f), 2f)
            drawLine(Color(0xFFFF9A48).copy(alpha = 0.16f), Offset(size.width * 0.30f, yy + 4f), Offset(size.width * 0.70f, yy - 2f), 1.5f)
        }

        val scanY = y0 + (y1 - y0) * scan
        drawLine(
            Color(0xFF58E7FF).copy(alpha = 0.10f),
            Offset(size.width * 0.31f, scanY),
            Offset(size.width * 0.69f, scanY),
            1.5f,
        )
    }
}

@Composable
private fun RoomChrome(topInset: Float, safeH: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val cyan = Color(0xFF5CCEFF)
        val amber = Color(0xFFFFA653)
        val steel = Color(0xFF344655)
        val top = topInset
        val bottom = topInset + safeH

        drawRect(Color(0xFF05080D), Offset(0f, top), Size(size.width, safeH * 0.10f))
        drawLine(steel.copy(alpha = 0.8f), Offset(0f, top + safeH * 0.095f), Offset(size.width, top + safeH * 0.095f), 2f)
        drawLine(cyan.copy(alpha = 0.35f), Offset(0f, top + safeH * 0.098f), Offset(size.width * 0.42f, top + safeH * 0.098f), 1f)
        drawLine(amber.copy(alpha = 0.30f), Offset(size.width * 0.62f, top + safeH * 0.098f), Offset(size.width, top + safeH * 0.098f), 1f)

        // Architectural rails framing the city and console.
        drawLine(steel, Offset(size.width * 0.02f, top + safeH * 0.11f), Offset(size.width * 0.02f, bottom * 0.84f), 6f)
        drawLine(steel, Offset(size.width * 0.98f, top + safeH * 0.11f), Offset(size.width * 0.98f, bottom * 0.84f), 6f)
        drawLine(cyan.copy(alpha = 0.38f), Offset(size.width * 0.025f, top + safeH * 0.12f), Offset(size.width * 0.025f, top + safeH * 0.53f), 1.5f)
        drawLine(amber.copy(alpha = 0.28f), Offset(size.width * 0.975f, top + safeH * 0.12f), Offset(size.width * 0.975f, top + safeH * 0.53f), 1.5f)
    }
}

@Composable
private fun RoomAnimations(topInset: Float, safeH: Float) {
    val transition = rememberInfiniteTransition(label = "room-live")
    val pulse by transition.animateFloat(
        0f, 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "core-pulse",
    )
    val sweep by transition.animateFloat(
        0f, 1f,
        animationSpec = infiniteRepeatable(tween(3600), RepeatMode.Restart),
        label = "console-sweep",
    )

    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width * 0.50f
        val cy = topInset + safeH * (0.566f - (pulse - 0.5f) * 0.004f)
        val baseR = size.width * 0.050f
        drawCircle(Color(0xFF45DBFF).copy(alpha = 0.08f + pulse * 0.10f), baseR * (1.6f + pulse * 0.22f), Offset(cx, cy))
        drawCircle(Color(0xFF6F79FF).copy(alpha = 0.34f), baseR * (1.0f + pulse * 0.08f), Offset(cx, cy), style = Stroke(2.2f))
        drawCircle(Color(0xFFFFA653).copy(alpha = 0.16f + pulse * 0.08f), baseR * (0.64f + pulse * 0.04f), Offset(cx, cy), style = Stroke(1.5f))

        val y = topInset + safeH * (0.515f + sweep * 0.12f)
        drawLine(
            Color(0xFF67E9FF).copy(alpha = 0.14f),
            Offset(size.width * 0.17f, y),
            Offset(size.width * 0.83f, y),
            1.4f,
        )
    }
}

@Composable
private fun HeaderLive(modifier: Modifier) {
    var clockText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var noticeCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val hour = when { now.hour == 0 -> 12; now.hour > 12 -> now.hour - 12; else -> now.hour }
            clockText = "$hour:${now.minute.toString().padStart(2, '0')} ${if (now.hour < 12) "AM" else "PM"}"
            val months = listOf("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER")
            dateText = "${months[now.monthNumber - 1]} ${now.dayOfMonth}, ${now.year}"
            val due = loadTasks().count { !it.done && it.dueDate() == today() }
            val pending = if (SupabaseAuth.isSignedIn()) SyncEngine.pendingCount() else 0
            noticeCount = due + pending
            delay(1_000)
        }
    }

    Box(modifier.background(Color(0xD9080D14), RoundedCornerShape(18.dp))) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = 18.dp)) {
            Text("⬡  N E X U S", color = Color(0xFFEAF7FF), fontSize = 17.sp, letterSpacing = 2.sp)
            Text("LIFE OS", color = Color(0xFF9FC6DC), fontSize = 9.sp, letterSpacing = 2.sp)
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(clockText, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(dateText, color = Color(0xFFB8CBD8), fontSize = 8.sp, letterSpacing = 1.sp)
        }
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Text(if (noticeCount > 0) "♢ $noticeCount" else "♢", color = Color(0xFFDCE7EE), fontSize = 14.sp)
            Box(
                Modifier.size(35.dp).background(Color(0xFF0B111A), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("◯", color = Color(0xFF81CFFF), fontSize = 27.sp)
            }
        }
    }
}

@Composable
private fun DomainTile(domain: String, modifier: Modifier, onClick: () -> Unit) {
    val accent = when (domain) {
        "Operations" -> Color(0xFF78CFFF)
        "Archive" -> Color(0xFF8299FF)
        "Logistics" -> Color(0xFF55D7D0)
        "Discovery" -> Color(0xFFB58AFF)
        "Management" -> Color(0xFFFFA75A)
        "Intelligence" -> Color(0xFF7D8CFF)
        "People" -> Color(0xFFE06DE8)
        else -> Color(0xFF69CBEF)
    }
    val transition = rememberInfiniteTransition(label = domain)
    val glow by transition.animateFloat(
        0.08f, 0.22f,
        animationSpec = infiniteRepeatable(tween(2600 + domain.length * 60), RepeatMode.Reverse),
        label = "tile-glow",
    )

    Box(
        modifier
            .background(Color(0xD90A1119), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(accent.copy(alpha = 0.55f + glow), cornerRadius = CornerRadius(15f, 15f), style = Stroke(1.5f))
            drawLine(accent.copy(alpha = glow), Offset(0f, 2f), Offset(size.width * 0.55f, 2f), 2f)
        }
        Column(Modifier.align(Alignment.Center).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ROOM_ICONS[domain] ?: "◇", color = accent, fontSize = 21.sp)
            Text(domain.uppercase(), color = Color(0xFFE8F0F5), fontSize = 9.sp, letterSpacing = 1.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CoreConsole(modifier: Modifier, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "core-hover")
    val hover by transition.animateFloat(
        -3f, 3f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "hover",
    )
    val pulse by transition.animateFloat(
        0.985f, 1.015f,
        animationSpec = infiniteRepeatable(tween(2100), RepeatMode.Reverse),
        label = "breathe",
    )

    Box(
        modifier
            .graphicsLayer { translationY = hover; scaleX = pulse; scaleY = pulse }
            .background(Color(0xE60A121B), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(Color(0xFF6BD8FF).copy(alpha = 0.50f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(1.8f))
            val c = Offset(size.width * 0.5f, size.height * 0.47f)
            repeat(5) { i ->
                drawCircle(
                    color = if (i % 2 == 0) Color(0xFF4BDFFF).copy(alpha = 0.20f) else Color(0xFFFFA34E).copy(alpha = 0.10f),
                    radius = size.minDimension * (0.08f + i * 0.055f),
                    center = c,
                    style = Stroke(1f),
                )
            }
            drawLine(Color(0xFF66E8FF).copy(alpha = 0.22f), Offset(size.width * 0.08f, size.height * 0.73f), Offset(size.width * 0.92f, size.height * 0.73f), 1f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⬡", color = Color(0xFF7DE4FF), fontSize = 28.sp)
            Text("N E X U S   C O R E", color = Color(0xFFEAF6FC), fontSize = 10.sp, letterSpacing = 2.sp)
            Text("Unified. Intelligent. Always On.", color = Color(0xFF9DB6C6), fontSize = 7.sp)
        }
    }
}

@Composable
private fun LiveStatsRow(modifier: Modifier, onLife: () -> Unit, onSync: () -> Unit, onNext: () -> Unit) {
    var live by remember { mutableStateOf(readRoomLive(syncing = false)) }
    var syncing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            live = readRoomLive(syncing)
            delay(15_000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            if (SupabaseAuth.isSignedIn()) {
                syncing = true
                live = readRoomLive(true)
                SupabaseSync.syncNow()
                syncing = false
                live = readRoomLive(false)
            }
            delay(5 * 60 * 1_000L)
        }
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        StatPanel(Modifier.weight(1f).fillMaxSize().clickable(onClick = onLife), "LIFE SCORE") {
            Text(live.score, color = Color(0xFFDFF5FF), fontSize = 29.sp, fontWeight = FontWeight.Light)
            Text(live.scoreCaption, color = Color(0xFF83D9FF), fontSize = 7.sp, textAlign = TextAlign.Center)
        }
        StatPanel(Modifier.weight(1f).fillMaxSize().clickable(onClick = onSync), "SYNC STATUS") {
            Text(live.syncMain, color = Color(0xFF76E7E0), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(live.syncLine1, color = Color(0xFFB5C8D3), fontSize = 7.sp, maxLines = 1)
            Text(live.syncLine2, color = Color(0xFF90D4F0), fontSize = 7.sp, maxLines = 1)
        }
        StatPanel(Modifier.weight(1f).fillMaxSize().clickable(onClick = onNext), "UP NEXT") {
            Text(live.nextWhen, color = Color(0xFFFFB05E), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(live.nextTitle, color = Color(0xFFEAF2F6), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(live.nextDetail, color = Color(0xFFA7BAC5), fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatPanel(modifier: Modifier, title: String, content: @Composable () -> Unit) {
    Box(modifier.background(Color(0xE60A1118), RoundedCornerShape(11.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(Color(0xFF6BCDF3).copy(alpha = 0.42f), cornerRadius = CornerRadius(16f, 16f), style = Stroke(1.3f))
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = Color(0xFFB8D9E8), fontSize = 8.sp, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable
private fun BottomRail(
    modifier: Modifier,
    onVoice: () -> Unit,
    onNote: () -> Unit,
    onCamera: () -> Unit,
    onBarcode: () -> Unit,
    onAi: () -> Unit,
) {
    Row(
        modifier.background(Color(0xF0060B11), RoundedCornerShape(18.dp)).padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RailAction(Modifier.weight(1f), "♩", "VOICE LINK", Color(0xFF82AFFF), onVoice)
        RailAction(Modifier.weight(1f), "▤", "QUICK NOTE", Color(0xFFB49CFF), onNote)
        RailAction(Modifier.weight(1.2f), "▣", "CAMERA", Color(0xFFFFA95E), onCamera, hero = true)
        RailAction(Modifier.weight(1f), "▥", "BARCODE", Color(0xFF6DE5E0), onBarcode)
        RailAction(Modifier.weight(1f), "✣", "AI ASSIST", Color(0xFF7FCBFF), onAi)
    }
}

@Composable
private fun RailAction(modifier: Modifier, icon: String, label: String, accent: Color, onClick: () -> Unit, hero: Boolean = false) {
    val transition = rememberInfiniteTransition(label = label)
    val pulse by transition.animateFloat(
        if (hero) 0.15f else 0.03f,
        if (hero) 0.32f else 0.10f,
        animationSpec = infiniteRepeatable(tween(if (hero) 1800 else 3200), RepeatMode.Reverse),
        label = "rail-pulse",
    )
    Box(modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (hero) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(accent.copy(alpha = pulse), size.minDimension * 0.43f, center)
                drawCircle(accent.copy(alpha = 0.70f), size.minDimension * 0.35f, center, style = Stroke(1.5f))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, color = accent, fontSize = if (hero) 25.sp else 21.sp)
            Text(label, color = Color(0xFFC8D4DC), fontSize = 6.5.sp, maxLines = 1)
        }
    }
}

private fun readRoomLive(syncing: Boolean): RoomLiveState {
    val now = Clock.System.now().toEpochMilliseconds()
    val d = today()
    val tasks = loadTasks()
    val habits = loadHabits()
    val due = tasks.filter { it.dueDate() == d }
    val total = due.size + habits.size
    val completed = due.count { it.done } + habits.count { it.checkedInToday }
    val score = if (total == 0) "—" else ((completed * 100f) / total).roundToInt().toString()
    val scoreCaption = if (total == 0) "NO ITEMS TODAY" else "$completed / $total COMPLETE"

    val signedIn = SupabaseAuth.isSignedIn()
    val pending = if (signedIn) SyncEngine.pendingCount() else 0
    val last = SyncMeta.lastSyncAt
    val syncMain = when {
        syncing -> "SYNCING"
        !signedIn -> "LOCAL ONLY"
        pending == 0 -> "SYSTEM SYNCED"
        else -> "$pending PENDING"
    }
    val syncLine1 = when {
        !signedIn -> "Cloud relay off"
        last <= 0L -> "Not synced yet"
        else -> relativeRoomSync(now - last)
    }
    val syncLine2 = when {
        !signedIn -> "Open Settings to sign in"
        pending == 0 -> "Data mesh stable"
        else -> "Changes queued"
    }

    val next = nextRoomTask(tasks, d)
    val nextWhen = when (val nd = next?.dueDate()) {
        null -> if (next == null) "CLEAR" else "ANYTIME"
        d -> "TODAY"
        else -> nd.toString()
    }
    return RoomLiveState(
        score,
        scoreCaption,
        syncMain,
        syncLine1,
        syncLine2,
        nextWhen,
        next?.title ?: "Nothing queued",
        next?.project?.ifBlank { statusLabel(next.status) } ?: "Tasks are clear",
        due.count { !it.done } + pending,
    )
}

private fun nextRoomTask(tasks: List<Task>, d: kotlinx.datetime.LocalDate): Task? =
    tasks.asSequence()
        .filter { !it.done }
        .filter { it.snoozeDate()?.let { s -> s <= d } ?: true }
        .sortedWith(compareBy<Task>({ it.dueDate() == null }, { it.dueDate()?.toString().orEmpty() }, { priorityRank(it.priority) }))
        .firstOrNull()

private fun relativeRoomSync(age: Long): String = when {
    age < 60_000L -> "Synced just now"
    age < 3_600_000L -> "Synced ${age / 60_000L}m ago"
    age < 86_400_000L -> "Synced ${age / 3_600_000L}h ago"
    else -> "Synced ${age / 86_400_000L}d ago"
}

@Composable
private fun RoomDomainSheet(
    domain: String,
    modules: List<com.alekpeed.lifeos.Module>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE805090E)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(domain.uppercase(), color = Color(0xFF8ADFFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
            modules.forEach { module ->
                Box(
                    Modifier.fillMaxWidth()
                        .background(Color(0xE60B131C), RoundedCornerShape(12.dp))
                        .clickable { onPick(module.id) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                ) {
                    Text("${module.icon}   ${module.label}", color = Color(0xFFEAF2F6), fontSize = 16.sp)
                }
            }
        }
    }
}

fun registerNexusCommandRoom() {
    com.alekpeed.lifeos.interfaces.Interfaces.registerHome(NEXUS) { NexusCommandRoomHome() }
}
