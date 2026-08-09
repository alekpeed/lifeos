package com.alekpeed.lifeos.interfaces.nexus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.lifeOsModules
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.system.scanCode
import com.alekpeed.lifeos.system.scanWithCamera
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

const val NEXUS = "nexus"

private val DOMAINS = listOf(
    "Operations", "Archive", "Logistics", "Discovery",
    "Management", "Intelligence", "People", "System",
)
private val ICONS = listOf("◎", "▤", "◇", "⌁", "▥", "♧", "◉", "⚙")
private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
private val BG = Color(0xFF030409)
private val PANEL = Color(0xD80A0910)
private val PINK = Color(0xFFFF3F8E)
private val HOT = Color(0xFFFF79B4)
private val PALE = Color(0xFFFFC9DE)
private val WHITE = Color(0xFFF5EEF2)
private val MUTED = Color(0xFFBFA7B2)
private val GREEN = Color(0xFF45C65A)

private data class Frame(
    val w: Float, val safeTop: Float, val safeBottom: Float,
    val cx: Float, val cy: Float, val outerR: Float,
    val innerR: Float, val coreR: Float,
    val cardsTop: Float, val cardsBottom: Float,
    val barTop: Float, val barBottom: Float,
)

@Composable
fun NexusHome() {
    val modules = remember { lifeOsModules() }
    val scope = rememberCoroutineScope()
    var openDomain by remember { mutableStateOf("") }
    var clock by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        Native.setImmersive(true)
        onDispose { Native.setImmersive(false) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val h12 = when { now.hour == 0 -> 12; now.hour > 12 -> now.hour - 12; else -> now.hour }
            clock = "$h12:${now.minute.toString().padStart(2, '0')} ${if (now.hour < 12) "AM" else "PM"}"
            date = "${MONTHS[now.monthNumber - 1]} ${now.dayOfMonth}, ${now.year}"
            delay(20_000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val t = (Clock.System.now().toEpochMilliseconds() % 3600L) / 3600f
            pulse = 0.5f - 0.5f * cos(2f * PI.toFloat() * t)
            delay(32)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val safeTop = Native.cutoutTopPx().toFloat() + 6f
        val safeBottom = h - Native.navBottomPx().toFloat() - 8f
        val safeH = safeBottom - safeTop
        val outerR = min(w * 0.40f, safeH * 0.255f)
        val frame = Frame(
            w, safeTop, safeBottom,
            w * 0.50f, safeTop + safeH * 0.385f,
            outerR, outerR * 0.47f, outerR * 0.31f,
            safeTop + safeH * 0.735f, safeTop + safeH * 0.855f,
            safeTop + safeH * 0.875f, safeBottom - 8f,
        )
        val density = LocalDensity.current

        Canvas(
            Modifier.fillMaxSize().pointerInput(frame) {
                detectTapGestures { tap ->
                    when (val hit = frameHit(tap, frame)) {
                        null, "core" -> Unit
                        "bell" -> Nav.open("notifications")
                        "voice" -> Nav.open("command")
                        "note" -> Nav.open("ideas")
                        "camera" -> scanWithCamera(scope)
                        "barcode" -> scanCode(scope)
                        "ai" -> Nav.open("ai-assistant")
                        else -> if (hit.startsWith("domain:")) openDomain = hit.removePrefix("domain:")
                    }
                }
            },
        ) {
            drawRect(BG)
            for (i in 0..11) {
                val x = w * (i / 11f)
                val extra = safeH * (0.18f + ((i * 37) % 6) * 0.022f)
                drawLine(PINK.copy(alpha = 0.08f), Offset(x, frame.cy - outerR * 0.75f), Offset(x, frame.cy + outerR + extra), 1.2f)
            }
            val planetTop = safeTop + safeH * 0.095f
            val planetBox = Size(w * 1.16f, outerR * 1.55f)
            drawArc(PINK.copy(alpha = 0.16f), 198f, 144f, false, Offset(-w * 0.08f, planetTop), planetBox, style = Stroke(20f), blendMode = BlendMode.Plus)
            drawArc(HOT.copy(alpha = 0.78f), 198f, 144f, false, Offset(-w * 0.08f, planetTop), planetBox, style = Stroke(2.5f))

            drawCircle(PINK.copy(alpha = 0.10f), outerR * 1.12f, Offset(frame.cx, frame.cy), style = Stroke(1.5f))
            drawCircle(PINK.copy(alpha = 0.28f), outerR, Offset(frame.cx, frame.cy), style = Stroke(2.4f))
            drawCircle(PINK.copy(alpha = 0.16f), frame.innerR, Offset(frame.cx, frame.cy), style = Stroke(1.4f))

            repeat(8) { i ->
                val centerDeg = -90f + i * 45f
                val path = annularSectorPath(frame.cx, frame.cy, frame.innerR, outerR, centerDeg - 20.5f, centerDeg + 20.5f)
                drawPath(path, Color(0x1A2D0718))
                drawPath(path, PINK.copy(alpha = 0.38f), style = Stroke(1.7f))
            }

            val glow = 0.18f + pulse * 0.16f
            drawCircle(PINK.copy(alpha = glow), frame.coreR * 1.45f, Offset(frame.cx, frame.cy), blendMode = BlendMode.Plus)
            drawCircle(PINK.copy(alpha = 0.30f), frame.coreR * 1.18f, Offset(frame.cx, frame.cy), style = Stroke(11f), blendMode = BlendMode.Plus)
            drawCircle(HOT.copy(alpha = 0.92f), frame.coreR * 1.10f, Offset(frame.cx, frame.cy), style = Stroke(3f))
            drawCircle(Color(0xFF180914), frame.coreR, Offset(frame.cx, frame.cy))
            drawCircle(PINK.copy(alpha = 0.40f), frame.coreR, Offset(frame.cx, frame.cy), style = Stroke(1.5f))

            val baseY = frame.cardsTop - safeH * 0.055f
            drawLine(PINK.copy(alpha = 0.42f), Offset(frame.cx, frame.cy + outerR), Offset(frame.cx, baseY), 2f)
            repeat(4) { j ->
                val ww = w * (0.48f - j * 0.07f)
                drawOval(PINK.copy(alpha = 0.28f - j * 0.04f), Offset(frame.cx - ww / 2f, baseY + j * 4f), Size(ww, 24f - j * 3f), style = Stroke(if (j == 0) 2f else 1f))
            }

            val gap = w * 0.018f
            val cardW = (w - gap * 4f) / 3f
            repeat(3) { i ->
                val x = gap + i * (cardW + gap)
                drawRoundRect(PANEL, Offset(x, frame.cardsTop), Size(cardW, frame.cardsBottom - frame.cardsTop), CornerRadius(20f, 20f))
                drawRoundRect(PINK.copy(alpha = 0.42f), Offset(x, frame.cardsTop), Size(cardW, frame.cardsBottom - frame.cardsTop), CornerRadius(20f, 20f), style = Stroke(1.4f))
            }

            drawRoundRect(PANEL, Offset(w * 0.035f, frame.barTop), Size(w * 0.93f, frame.barBottom - frame.barTop), CornerRadius(34f, 34f))
            drawRoundRect(PINK.copy(alpha = 0.48f), Offset(w * 0.035f, frame.barTop), Size(w * 0.93f, frame.barBottom - frame.barTop), CornerRadius(34f, 34f), style = Stroke(1.5f))
            val camCx = w * 0.50f
            val camCy = (frame.barTop + frame.barBottom) / 2f - 4f
            val camR = (frame.barBottom - frame.barTop) * 0.38f
            drawCircle(PINK.copy(alpha = 0.18f), camR * 1.20f, Offset(camCx, camCy), blendMode = BlendMode.Plus)
            drawCircle(PINK, camR, Offset(camCx, camCy), style = Stroke(2.2f))
        }

        Text("⬡  NEXUS", color = HOT, fontSize = 25.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp,
            modifier = Modifier.offset(20.dp, with(density) { (safeTop + 20f).toDp() }))
        Text("LIFE OS", color = PALE, fontSize = 11.sp, letterSpacing = 3.sp,
            modifier = Modifier.offset(49.dp, with(density) { (safeTop + 54f).toDp() }))
        Text(clock, color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.offset(with(density) { (w * 0.34f).toDp() }, with(density) { (safeTop + 21f).toDp() }).width(with(density) { (w * 0.32f).toDp() }))
        Text(date, color = HOT, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.offset(with(density) { (w * 0.33f).toDp() }, with(density) { (safeTop + 48f).toDp() }).width(with(density) { (w * 0.34f).toDp() }))
        Text("♢", color = PALE, fontSize = 23.sp,
            modifier = Modifier.offset(with(density) { (w * 0.77f).toDp() }, with(density) { (safeTop + 20f).toDp() }))
        Text("◔", color = HOT, fontSize = 38.sp,
            modifier = Modifier.offset(with(density) { (w * 0.87f).toDp() }, with(density) { (safeTop + 7f).toDp() }))

        DOMAINS.forEachIndexed { i, domain ->
            val a = (-90f + i * 45f) * PI.toFloat() / 180f
            val r = (frame.innerR + frame.outerR) / 2f
            val x = frame.cx + cos(a) * r
            val y = frame.cy + sin(a) * r
            Text(ICONS[i], color = HOT, fontSize = 24.sp, textAlign = TextAlign.Center,
                modifier = Modifier.offset(with(density) { (x - w * 0.06f).toDp() }, with(density) { (y - 30f).toDp() }).width(with(density) { (w * 0.12f).toDp() }))
            Text(domain.uppercase(), color = PALE, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.7.sp, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.offset(with(density) { (x - w * 0.12f).toDp() }, with(density) { (y + 8f).toDp() }).width(with(density) { (w * 0.24f).toDp() }))
        }

        Text("NEXUS", color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp, textAlign = TextAlign.Center,
            modifier = Modifier.offset(with(density) { (frame.cx - frame.coreR).toDp() }, with(density) { (frame.cy - 24f).toDp() }).width(with(density) { (frame.coreR * 2f).toDp() }))
        Text("CORE", color = HOT, fontSize = 10.sp, letterSpacing = 3.sp, textAlign = TextAlign.Center,
            modifier = Modifier.offset(with(density) { (frame.cx - frame.coreR).toDp() }, with(density) { (frame.cy + 18f).toDp() }).width(with(density) { (frame.coreR * 2f).toDp() }))

        Text("FOCUS  •  BALANCE  •  MOMENTUM", color = PALE, fontSize = 9.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center,
            modifier = Modifier.offset(0.dp, with(density) { (frame.cardsTop - 35f).toDp() }).fillMaxWidth())

        val gap = w * 0.018f
        val cardW = (w - gap * 4f) / 3f
        val cards = listOf(
            Triple("LIFE SCORE", "—", "not calculated"),
            Triple("SYNC STATUS", "READY", "local data active"),
            Triple("UP NEXT", "TODAY", "open Today for details"),
        )
        cards.forEachIndexed { i, card ->
            val x = gap + i * (cardW + gap)
            Text(card.first, color = HOT, fontSize = 9.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center,
                modifier = Modifier.offset(with(density) { x.toDp() }, with(density) { (frame.cardsTop + 14f).toDp() }).width(with(density) { cardW.toDp() }))
            Text(card.second, color = WHITE, fontSize = if (i == 0) 25.sp else 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.offset(with(density) { x.toDp() }, with(density) { (frame.cardsTop + 42f).toDp() }).width(with(density) { cardW.toDp() }))
            Text(card.third, color = if (i == 1) GREEN else MUTED, fontSize = 8.sp, textAlign = TextAlign.Center,
                modifier = Modifier.offset(with(density) { x.toDp() }, with(density) { (frame.cardsTop + 72f).toDp() }).width(with(density) { cardW.toDp() }))
        }

        val bottom = listOf(
            Triple(0.12f, "MIC", "VOICE LINK"),
            Triple(0.30f, "NOTE", "QUICK NOTE"),
            Triple(0.50f, "CAM", "CAMERA"),
            Triple(0.70f, "BAR", "BARCODE READER"),
            Triple(0.88f, "AI", "AI ASSIST"),
        )
        bottom.forEach { (fraction, icon, label) ->
            val x = w * fraction
            Text(icon, color = if (fraction == 0.50f) WHITE else HOT, fontSize = if (fraction == 0.50f) 14.sp else 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.offset(with(density) { (x - w * 0.07f).toDp() }, with(density) { (frame.barTop + 34f).toDp() }).width(with(density) { (w * 0.14f).toDp() }))
            Text(label, color = PALE, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.offset(with(density) { (x - w * 0.10f).toDp() }, with(density) { (frame.barTop + 61f).toDp() }).width(with(density) { (w * 0.20f).toDp() }))
        }

        if (openDomain.isNotBlank()) {
            DomainSheet(
                domain = openDomain,
                modules = modules.filter { it.group == openDomain },
                onPick = { Nav.open(it); openDomain = "" },
                onDismiss = { openDomain = "" },
            )
        }
    }
}

private fun frameHit(tap: Offset, f: Frame): String? {
    if (tap.x in f.w * 0.73f..f.w * 0.83f && tap.y in f.safeTop..f.safeTop + (f.safeBottom - f.safeTop) * 0.08f) return "bell"
    if (tap.y in f.barTop..f.barBottom) {
        return when {
            tap.x < f.w * 0.21f -> "voice"
            tap.x < f.w * 0.39f -> "note"
            tap.x < f.w * 0.61f -> "camera"
            tap.x < f.w * 0.79f -> "barcode"
            else -> "ai"
        }
    }
    val dx = tap.x - f.cx
    val dy = tap.y - f.cy
    val r = sqrt(dx * dx + dy * dy)
    if (r <= f.coreR * 1.10f) return "core"
    if (r < f.innerR || r > f.outerR) return null
    var angle = atan2(dy, dx) * 180f / PI.toFloat()
    if (angle < 0f) angle += 360f
    val fromTopClockwise = (angle + 90f) % 360f
    val index = (((fromTopClockwise + 22.5f) % 360f) / 45f).toInt()
    val sectorCenter = index * 45f
    var delta = fromTopClockwise - sectorCenter
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    if (kotlin.math.abs(delta) > 20.5f) return null
    return "domain:${DOMAINS[index]}"
}

private fun annularSectorPath(cx: Float, cy: Float, innerR: Float, outerR: Float, startDeg: Float, endDeg: Float): Path {
    val p = Path()
    val steps = 18
    fun point(r: Float, deg: Float): Offset {
        val a = deg * PI.toFloat() / 180f
        return Offset(cx + cos(a) * r, cy + sin(a) * r)
    }
    val startOuter = point(outerR, startDeg)
    p.moveTo(startOuter.x, startOuter.y)
    for (s in 1..steps) {
        val d = startDeg + (endDeg - startDeg) * (s / steps.toFloat())
        val q = point(outerR, d)
        p.lineTo(q.x, q.y)
    }
    for (s in steps downTo 0) {
        val d = startDeg + (endDeg - startDeg) * (s / steps.toFloat())
        val q = point(innerR, d)
        p.lineTo(q.x, q.y)
    }
    p.close()
    return p
}

@Composable
private fun DomainSheet(
    domain: String,
    modules: List<com.alekpeed.lifeos.Module>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xEE05060A)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(domain.uppercase(), color = HOT, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
            modules.forEach { m ->
                Box(
                    Modifier.fillMaxWidth().background(Color(0x14FFFFFF), RoundedCornerShape(12.dp)).clickable { onPick(m.id) }.padding(horizontal = 16.dp, vertical = 15.dp),
                ) {
                    Text("${m.icon}   ${m.label}", color = WHITE, fontSize = 16.sp)
                }
            }
            Text("tap anywhere to close", color = Color(0x8899A0AA), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

fun registerNexus() {
    com.alekpeed.lifeos.interfaces.Interfaces.registerHome(NEXUS) { NexusHome() }
}
