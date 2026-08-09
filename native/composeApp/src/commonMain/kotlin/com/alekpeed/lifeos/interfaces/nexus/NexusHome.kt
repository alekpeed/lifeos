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
import kotlinx.coroutines.launch
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

private val domains = listOf(
    "Operations",
    "Archive",
    "Logistics",
    "Discovery",
    "Management",
    "Intelligence",
    "People",
    "System",
)

private val months = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val pink = Color(0xFFFF4F93)
private val hotPink = Color(0xFFFF88B9)
private val palePink = Color(0xFFFFC6DB)
private val cyan = Color(0xFF65E6E6)
private val panel = Color(0xCC090A10)
private val bg = Color(0xFF05060A)
private val white = Color(0xFFF4F0F3)
private val muted = Color(0xFFB9A6B0)

private data class BottomAction(val id: String, val label: String)
private val bottomActions = listOf(
    BottomAction("voice", "VOICE LINK"),
    BottomAction("note", "QUICK NOTE"),
    BottomAction("camera", "CAMERA"),
    BottomAction("barcode", "BARCODE"),
    BottomAction("ai", "AI ASSIST"),
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
            val h12 = when {
                now.hour == 0 -> 12
                now.hour > 12 -> now.hour - 12
                else -> now.hour
            }
            clock = "$h12:${now.minute.toString().padStart(2, '0')} ${if (now.hour < 12) "AM" else "PM"}"
            date = "${months[now.monthNumber - 1]} ${now.dayOfMonth}, ${now.year}"
            delay(20_000)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val t = (Clock.System.now().toEpochMilliseconds() % 4200L) / 4200f
            pulse = 0.5f - 0.5f * cos(2f * PI.toFloat() * t)
            delay(32)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(bg)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val topInset = Native.cutoutTopPx().toFloat()
        val bottomInset = Native.navBottomPx().toFloat()
        val safeTop = topInset + 8f
        val safeBottom = h - bottomInset - 8f
        val wheelCx = w / 2f
        val wheelCy = safeTop + (safeBottom - safeTop) * 0.39f
        val wheelR = min(w * 0.43f, (safeBottom - safeTop) * 0.27f)
        val coreR = wheelR * 0.34f
        val cardTop = safeTop + (safeBottom - safeTop) * 0.72f
        val bottomBarTop = safeTop + (safeBottom - safeTop) * 0.855f
        val bottomBarBottom = safeBottom - 10f

        Canvas(
            Modifier.fillMaxSize().pointerInput(w, h, wheelR) {
                detectTapGestures { tap ->
                    val hit = hitTest(
                        tap = tap,
                        w = w,
                        h = h,
                        safeTop = safeTop,
                        safeBottom = safeBottom,
                        wheelCx = wheelCx,
                        wheelCy = wheelCy,
                        wheelR = wheelR,
                        coreR = coreR,
                        bottomBarTop = bottomBarTop,
                        bottomBarBottom = bottomBarBottom,
                    ) ?: return@detectTapGestures
                    when {
                        hit.startsWith("domain:") -> openDomain = hit.removePrefix("domain:")
                        hit == "bell" -> Nav.open("notifications")
                        hit == "voice" -> Nav.open("command")
                        hit == "note" -> Nav.open("ideas")
                        hit == "camera" -> scanWithCamera(scope)
                        hit == "barcode" -> scanCode(scope)
                        hit == "ai" -> Nav.open("ai-assistant")
                    }
                }
            },
        ) {
            drawRect(bg)

            // Faint cyberpunk city rails / perspective floor.
            val horizon = safeTop + (safeBottom - safeTop) * 0.61f
            for (i in -6..6) {
                val x = w / 2f + i * w * 0.10f
                drawLine(pink.copy(alpha = 0.08f), Offset(w / 2f, horizon), Offset(x, safeBottom), 1f)
            }
            for (i in 0..7) {
                val yy = horizon + (safeBottom - horizon) * (i / 8f)
                drawLine(pink.copy(alpha = 0.06f), Offset(0f, yy), Offset(w, yy), 1f)
            }

            // Planet horizon behind the wheel.
            drawArc(
                color = pink.copy(alpha = 0.28f),
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(-w * 0.12f, safeTop + 40f),
                size = Size(w * 1.24f, wheelR * 1.45f),
                style = Stroke(width = 2.5f),
            )
            drawArc(
                color = hotPink.copy(alpha = 0.13f),
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(-w * 0.12f, safeTop + 37f),
                size = Size(w * 1.24f, wheelR * 1.45f),
                style = Stroke(width = 14f),
                blendMode = BlendMode.Plus,
            )

            // Radial wheel.
            drawCircle(pink.copy(alpha = 0.13f), wheelR * 1.08f, Offset(wheelCx, wheelCy), style = Stroke(1.5f))
            drawCircle(pink.copy(alpha = 0.22f), wheelR, Offset(wheelCx, wheelCy), style = Stroke(2f))
            drawCircle(pink.copy(alpha = 0.16f), wheelR * 0.72f, Offset(wheelCx, wheelCy), style = Stroke(1.5f))

            repeat(8) { i ->
                val a = Math.toRadians((-112.5 + i * 45.0)).toFloat()
                val inner = Offset(
                    wheelCx + cos(a) * coreR * 1.18f,
                    wheelCy + sin(a) * coreR * 1.18f,
                )
                val outer = Offset(
                    wheelCx + cos(a) * wheelR,
                    wheelCy + sin(a) * wheelR,
                )
                drawLine(pink.copy(alpha = 0.38f), inner, outer, 1.5f)
            }

            val glow = 0.35f + pulse * 0.35f
            drawCircle(pink.copy(alpha = 0.10f + pulse * 0.08f), coreR * 1.22f, Offset(wheelCx, wheelCy), blendMode = BlendMode.Plus)
            drawCircle(pink.copy(alpha = glow), coreR * 1.05f, Offset(wheelCx, wheelCy), style = Stroke(11f), blendMode = BlendMode.Plus)
            drawCircle(palePink.copy(alpha = 0.85f), coreR, Offset(wheelCx, wheelCy), style = Stroke(3f))

            // Projection beam / dais.
            val beamBottom = cardTop - 45f
            drawLine(pink.copy(alpha = 0.25f), Offset(wheelCx, wheelCy + wheelR), Offset(wheelCx, beamBottom), 3f)
            drawLine(hotPink.copy(alpha = 0.42f), Offset(wheelCx, wheelCy + wheelR), Offset(wheelCx, beamBottom), 1f)
            for (j in 0..3) {
                drawOval(
                    color = pink.copy(alpha = 0.20f - j * 0.03f),
                    topLeft = Offset(w * (0.20f + j * 0.035f), beamBottom - 10f + j * 4f),
                    size = Size(w * (0.60f - j * 0.07f), 28f - j * 3f),
                    style = Stroke(width = if (j == 0) 2f else 1f),
                )
            }

            // Information cards.
            val gap = w * 0.018f
            val cardW = (w - gap * 4f) / 3f
            val cardH = (bottomBarTop - cardTop) * 0.82f
            repeat(3) { i ->
                drawRoundRect(
                    color = panel,
                    topLeft = Offset(gap + i * (cardW + gap), cardTop),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(18f, 18f),
                )
                drawRoundRect(
                    color = pink.copy(alpha = 0.35f),
                    topLeft = Offset(gap + i * (cardW + gap), cardTop),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(18f, 18f),
                    style = Stroke(1.4f),
                )
            }

            // Bottom action bar.
            drawRoundRect(
                color = panel,
                topLeft = Offset(w * 0.035f, bottomBarTop),
                size = Size(w * 0.93f, bottomBarBottom - bottomBarTop),
                cornerRadius = CornerRadius(30f, 30f),
            )
            drawRoundRect(
                color = pink.copy(alpha = 0.32f),
                topLeft = Offset(w * 0.035f, bottomBarTop),
                size = Size(w * 0.93f, bottomBarBottom - bottomBarTop),
                cornerRadius = CornerRadius(30f, 30f),
                style = Stroke(1.5f),
            )
            val camCx = w * 0.50f
            val camCy = (bottomBarTop + bottomBarBottom) / 2f - 2f
            val camR = (bottomBarBottom - bottomBarTop) * 0.36f
            drawCircle(pink.copy(alpha = 0.16f), camR * 1.18f, Offset(camCx, camCy), blendMode = BlendMode.Plus)
            drawCircle(pink, camR, Offset(camCx, camCy), style = Stroke(2.4f))
        }

        // Header and live values.
        Text(
            "NEXUS",
            color = hotPink,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 5.sp,
            modifier = Modifier.offset(24.dp, with(LocalDensity.current) { (safeTop + 18f).toDp() }),
        )
        Text(
            "LIFE OS",
            color = palePink,
            fontSize = 11.sp,
            letterSpacing = 3.sp,
            modifier = Modifier.offset(26.dp, with(LocalDensity.current) { (safeTop + 53f).toDp() }),
        )
        Text(
            clock,
            color = white,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(
                x = with(LocalDensity.current) { (w * 0.34f).toDp() },
                y = with(LocalDensity.current) { (safeTop + 19f).toDp() },
            ).width(with(LocalDensity.current) { (w * 0.32f).toDp() }),
        )
        Text(
            date,
            color = hotPink,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(
                x = with(LocalDensity.current) { (w * 0.33f).toDp() },
                y = with(LocalDensity.current) { (safeTop + 47f).toDp() },
            ).width(with(LocalDensity.current) { (w * 0.34f).toDp() }),
        )
        Text(
            "♧",
            color = palePink,
            fontSize = 24.sp,
            modifier = Modifier.offset(
                x = with(LocalDensity.current) { (w * 0.76f).toDp() },
                y = with(LocalDensity.current) { (safeTop + 16f).toDp() },
            ),
        )

        // Domain labels around the wheel.
        domains.forEachIndexed { i, domain ->
            val a = Math.toRadians((-90.0 + i * 45.0)).toFloat()
            val r = wheelR * 0.79f
            val x = wheelCx + cos(a) * r
            val y = wheelCy + sin(a) * r
            Text(
                domain.uppercase(),
                color = palePink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(
                    x = with(LocalDensity.current) { (x - w * 0.12f).toDp() },
                    y = with(LocalDensity.current) { (y - 12f).toDp() },
                ).width(with(LocalDensity.current) { (w * 0.24f).toDp() }),
            )
        }

        Text(
            "NEXUS",
            color = white,
            fontSize = 26.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 4.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(
                x = with(LocalDensity.current) { (wheelCx - coreR).toDp() },
                y = with(LocalDensity.current) { (wheelCy - 20f).toDp() },
            ).width(with(LocalDensity.current) { (coreR * 2f).toDp() }),
        )
        Text(
            "CORE",
            color = hotPink,
            fontSize = 11.sp,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(
                x = with(LocalDensity.current) { (wheelCx - coreR).toDp() },
                y = with(LocalDensity.current) { (wheelCy + 22f).toDp() },
            ).width(with(LocalDensity.current) { (coreR * 2f).toDp() }),
        )

        Text(
            "FOCUS  •  BALANCE  •  MOMENTUM",
            color = palePink,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(
                x = 0.dp,
                y = with(LocalDensity.current) { (cardTop - 31f).toDp() },
            ).fillMaxWidth(),
        )

        val gap = w * 0.018f
        val cardW = (w - gap * 4f) / 3f
        listOf(
            Triple("LIFE SCORE", "—", "not calculated"),
            Triple("SYNC STATUS", "READY", "local data active"),
            Triple("UP NEXT", "TODAY", "open Today for details"),
        ).forEachIndexed { i, card ->
            val x = gap + i * (cardW + gap)
            Text(
                card.first,
                color = hotPink,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(
                    x = with(LocalDensity.current) { x.toDp() },
                    y = with(LocalDensity.current) { (cardTop + 14f).toDp() },
                ).width(with(LocalDensity.current) { cardW.toDp() }),
            )
            Text(
                card.second,
                color = white,
                fontSize = if (i == 0) 27.sp else 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(
                    x = with(LocalDensity.current) { x.toDp() },
                    y = with(LocalDensity.current) { (cardTop + 43f).toDp() },
                ).width(with(LocalDensity.current) { cardW.toDp() }),
            )
            Text(
                card.third,
                color = muted,
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(
                    x = with(LocalDensity.current) { x.toDp() },
                    y = with(LocalDensity.current) { (cardTop + 76f).toDp() },
                ).width(with(LocalDensity.current) { cardW.toDp() }),
            )
        }

        val centers = listOf(0.12f, 0.30f, 0.50f, 0.70f, 0.88f)
        val iconText = listOf("MIC", "NOTE", "CAM", "BAR", "AI")
        bottomActions.forEachIndexed { i, action ->
            val x = w * centers[i]
            val y = bottomBarTop + (bottomBarBottom - bottomBarTop) * 0.25f
            Text(
                iconText[i],
                color = if (i == 2) white else hotPink,
                fontSize = if (i == 2) 13.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(
                    x = with(LocalDensity.current) { (x - w * 0.08f).toDp() },
                    y = with(LocalDensity.current) { y.toDp() },
                ).width(with(LocalDensity.current) { (w * 0.16f).toDp() }),
            )
            Text(
                action.label,
                color = palePink,
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(
                    x = with(LocalDensity.current) { (x - w * 0.095f).toDp() },
                    y = with(LocalDensity.current) { (y + 28f).toDp() },
                ).width(with(LocalDensity.current) { (w * 0.19f).toDp() }),
            )
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

private fun hitTest(
    tap: Offset,
    w: Float,
    h: Float,
    safeTop: Float,
    safeBottom: Float,
    wheelCx: Float,
    wheelCy: Float,
    wheelR: Float,
    coreR: Float,
    bottomBarTop: Float,
    bottomBarBottom: Float,
): String? {
    if (tap.x > w * 0.72f && tap.y < safeTop + h * 0.07f) return "bell"

    if (tap.y in bottomBarTop..bottomBarBottom) {
        val centers = floatArrayOf(0.12f, 0.30f, 0.50f, 0.70f, 0.88f)
        val ids = arrayOf("voice", "note", "camera", "barcode", "ai")
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in centers.indices) {
            val d = kotlin.math.abs(tap.x - w * centers[i])
            if (d < bestD) { bestD = d; best = i }
        }
        if (best >= 0 && bestD < w * 0.11f) return ids[best]
    }

    val dx = tap.x - wheelCx
    val dy = tap.y - wheelCy
    val r = sqrt(dx * dx + dy * dy)
    if (r <= coreR) return null
    if (r > wheelR) return null
    var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
    if (deg < 0f) deg += 360f
    val index = (((deg + 22.5f) % 360f) / 45f).toInt()
    return "domain:${domains[index]}"
}

@Composable
private fun DomainSheet(
    domain: String,
    modules: List<com.alekpeed.lifeos.Module>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xF205060A)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                domain.uppercase(),
                color = hotPink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            modules.forEach { m ->
                Box(
                    Modifier.fillMaxWidth()
                        .background(Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { onPick(m.id) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                ) {
                    Text("${m.icon}   ${m.label}", color = white, fontSize = 16.sp)
                }
            }
            Text(
                "tap anywhere to close",
                color = Color(0x8899A0AA),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

fun registerNexus() {
    com.alekpeed.lifeos.interfaces.Interfaces.registerHome(NEXUS) { NexusHome() }
}
