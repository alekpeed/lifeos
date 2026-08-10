package com.alekpeed.lifeos.operations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.loadImageAsset
import com.alekpeed.lifeos.system.scanCode
import com.alekpeed.lifeos.system.scanWithCamera
import kotlinx.coroutines.CoroutineScope
import kotlin.math.PI
import kotlin.math.sin

private const val ART = "operations-2170.png"
private const val SOURCE_WIDTH = 941f
private const val SOURCE_HEIGHT = 1672f

private data class HitRect(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    fun contains(px: Float, py: Float): Boolean =
        px in x..(x + width) && py in y..(y + height)
}

private val HIT_RECTS = listOf(
    HitRect("home", 132f, 31f, 252f, 103f),
    HitRect("bell", 627f, 55f, 73f, 79f),
    HitRect("status", 704f, 45f, 104f, 101f),
    HitRect("notifications", 650f, 277f, 258f, 163f),
    // Small utility controls precede the large panels because their artwork
    // touches the Command/Briefing frames by a few source pixels.
    HitRect("voice", 287f, 1234f, 70f, 121f),
    HitRect("quick-note", 359f, 1234f, 69f, 121f),
    HitRect("camera", 429f, 1226f, 79f, 137f),
    HitRect("barcode", 510f, 1234f, 70f, 121f),
    HitRect("ai-assist", 582f, 1234f, 71f, 121f),
    HitRect("daily-paper", 30f, 617f, 282f, 329f),
    HitRect("tasks", 640f, 620f, 282f, 333f),
    HitRect("today", 317f, 669f, 330f, 485f),
    HitRect("command", 26f, 930f, 282f, 315f),
    HitRect("briefing", 644f, 935f, 284f, 305f),
)

@Composable
fun OperationsScreen() {
    val art = remember { loadImageAsset(ART) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        Native.setImmersive(true)
        onDispose { Native.setImmersive(false) }
    }

    if (art == null) {
        OperationsFallback()
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        val topInset = Native.cutoutTopPx().toFloat()
        val bottomInset = Native.navBottomPx().toFloat()
        val safeH = (viewportH - topInset - bottomInset).coerceAtLeast(1f)
        val scale = minOf(viewportW / SOURCE_WIDTH, safeH / SOURCE_HEIGHT)
        val drawW = SOURCE_WIDTH * scale
        val drawH = SOURCE_HEIGHT * scale
        val originX = (viewportW - drawW) / 2f
        val originY = topInset + (safeH - drawH) / 2f
        val density = LocalDensity.current

        val frameModifier = Modifier
            .offset(
                x = with(density) { originX.toDp() },
                y = with(density) { originY.toDp() },
            )
            .size(
                width = with(density) { drawW.toDp() },
                height = with(density) { drawH.toDp() },
            )

        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = frameModifier,
        )

        CityAmbientLayer(frameModifier)

        Box(
            Modifier.fillMaxSize().pointerInput(art, originX, originY, drawW, drawH) {
                detectTapGestures { tap ->
                    val sourceX = (tap.x - originX) / scale
                    val sourceY = (tap.y - originY) / scale
                    val hit = HIT_RECTS.firstOrNull { it.contains(sourceX, sourceY) }?.id
                    routeHit(hit, scope)
                }
            },
        )
    }
}

@Composable
private fun CityAmbientLayer(modifier: Modifier) {
    val transition = rememberInfiniteTransition()
    val traffic by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )
    val buzz by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    Canvas(modifier) {
        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)

        // Tiny localized pulses over existing signs. Their low alpha keeps the
        // photograph dominant while making the city feel electrically alive.
        val lights = listOf(
            Triple(0.157f, 0.255f, 0.08f),
            Triple(0.236f, 0.310f, 0.43f),
            Triple(0.314f, 0.258f, 0.72f),
            Triple(0.522f, 0.277f, 0.20f),
            Triple(0.637f, 0.229f, 0.58f),
            Triple(0.785f, 0.321f, 0.86f),
            Triple(0.875f, 0.278f, 0.34f),
        )
        lights.forEachIndexed { index, (x, y, phase) ->
            val wave = (0.5f + 0.5f * sin((buzz + phase) * 2f * PI.toFloat()))
            val center = point(x, y)
            val color = if (index % 2 == 0) Color(0xFF7CEBFF) else Color(0xFFFF65D0)
            drawCircle(color.copy(alpha = 0.025f + wave * 0.055f), size.minDimension * 0.020f, center)
            drawCircle(Color.White.copy(alpha = 0.05f + wave * 0.11f), size.minDimension * 0.0022f, center)
        }

        data class Track(val start: Offset, val end: Offset, val phase: Float, val color: Color)
        val tracks = listOf(
            Track(point(0.25f, 0.389f), point(0.73f, 0.338f), 0.00f, Color(0xFFFFE1AE)),
            Track(point(0.11f, 0.404f), point(0.47f, 0.347f), 0.38f, Color(0xFFFF6B9D)),
            Track(point(0.88f, 0.392f), point(0.57f, 0.347f), 0.67f, Color(0xFF9FE9FF)),
        )
        tracks.forEach { track ->
            val t = (traffic + track.phase) % 1f
            val head = Offset(
                x = track.start.x + (track.end.x - track.start.x) * t,
                y = track.start.y + (track.end.y - track.start.y) * t,
            )
            val tailT = (t - 0.035f).coerceAtLeast(0f)
            val tail = Offset(
                x = track.start.x + (track.end.x - track.start.x) * tailT,
                y = track.start.y + (track.end.y - track.start.y) * tailT,
            )
            drawLine(
                color = track.color.copy(alpha = 0.34f),
                start = tail,
                end = head,
                strokeWidth = size.minDimension * 0.0025f,
                cap = StrokeCap.Round,
            )
            drawCircle(track.color.copy(alpha = 0.55f), size.minDimension * 0.0022f, head)
        }
    }
}

private fun routeHit(hit: String?, scope: CoroutineScope) {
    when (hit) {
        "home" -> Nav.goHome()
        "bell", "notifications" -> Nav.open("notifications")
        "status" -> Nav.open("settings")
        "daily-paper" -> Nav.open("daily-paper")
        "tasks" -> Nav.open("tasks")
        "today" -> Nav.open("today")
        "command", "voice" -> Nav.open("command")
        "briefing" -> Nav.open("briefing")
        "quick-note" -> Nav.open("ideas")
        "camera" -> scanWithCamera(scope)
        "barcode" -> scanCode(scope)
        "ai-assist" -> Nav.open("ai-assistant")
    }
}

@Composable
private fun OperationsFallback() {
    val modules = listOf(
        "Today" to "today",
        "Daily Paper" to "daily-paper",
        "Tasks" to "tasks",
        "Command" to "command",
        "Briefing" to "briefing",
        "Notifications" to "notifications",
    )
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080B12)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("OPERATIONS", color = Color(0xFFEAF8FF), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        modules.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, id) ->
                    Box(
                        modifier = Modifier.weight(1f).background(Color(0xFF152131), RoundedCornerShape(14.dp))
                            .pointerInput(id) { detectTapGestures { Nav.open(id) } }.padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = Color(0xFFBCEEFF), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
