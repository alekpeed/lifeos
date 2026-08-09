package com.alekpeed.lifeos.interfaces.nexus

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.HomeScreen
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.lifeOsModules
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.loadBase64ImageAsset
import com.alekpeed.lifeos.platform.loadImageAsset
import com.alekpeed.lifeos.system.scanCode
import com.alekpeed.lifeos.system.scanWithCamera
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

const val NEXUS = "nexus"
private const val ART = "nexus-home.png"
private const val CANONICAL_WIDTH = 1080f
private const val CANONICAL_HEIGHT = 2400f
private val GENERATED_ART = (0..8).map { "nexus-home-$it.b64" }

private val DOMAINS = listOf(
    "Operations",
    "Archive",
    "Logistics",
    "Discovery",
    "Management",
    "Intelligence",
    "People",
    "System",
)

@Composable
fun NexusHome() {
    val art = remember {
        loadBase64ImageAsset(GENERATED_ART) ?: loadImageAsset(ART)
    }
    if (art == null) {
        HomeScreen(remember { lifeOsModules() }) { Nav.open(it.id) }
        return
    }

    val modules = remember { lifeOsModules() }
    val scope = rememberCoroutineScope()
    var openDomain by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        Native.setImmersive(true)
        onDispose { Native.setImmersive(false) }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF05060A)),
    ) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        val topInset = Native.cutoutTopPx().toFloat()
        val bottomInset = Native.navBottomPx().toFloat()
        val safeH = (viewportH - topInset - bottomInset).coerceAtLeast(1f)

        // The interaction frame is always exactly 1080x2400 in normalized geometry.
        // Device aspect-ratio differences are letterboxed; the artwork is fitted to
        // this canonical frame rather than allowed to redefine it.
        val scale = minOf(viewportW / CANONICAL_WIDTH, safeH / CANONICAL_HEIGHT)
        val drawW = CANONICAL_WIDTH * scale
        val drawH = CANONICAL_HEIGHT * scale
        val originX = (viewportW - drawW) / 2f
        val originY = topInset + (safeH - drawH) / 2f
        val density = LocalDensity.current

        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(
                    with(density) { originX.toDp() },
                    with(density) { originY.toDp() },
                )
                .size(
                    with(density) { drawW.toDp() },
                    with(density) { drawH.toDp() },
                ),
        )

        // Invisible interaction layer. Coordinates below are the canonical JSON
        // frame values and intentionally do not alter the artwork.
        Box(
            Modifier.fillMaxSize().pointerInput(art, originX, originY, drawW, drawH) {
                detectTapGestures { tap ->
                    when (val hit = hitRegion(tap, originX, originY, drawW, drawH)) {
                        null, "core" -> Unit
                        "bell" -> Nav.open("notifications")
                        "voice" -> Nav.open("command")
                        "quick_note" -> Nav.open("ideas")
                        "camera" -> scanWithCamera(scope)
                        "barcode" -> scanCode(scope)
                        "ai_assist" -> Nav.open("ai-assistant")
                        else -> if (hit.startsWith("domain:")) {
                            openDomain = hit.removePrefix("domain:")
                        }
                    }
                }
            },
        )

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

private fun hitRegion(
    tap: Offset,
    originX: Float,
    originY: Float,
    drawW: Float,
    drawH: Float,
): String? {
    val x = (tap.x - originX) / drawW
    val y = (tap.y - originY) / drawH
    if (x !in 0f..1f || y !in 0f..1f) return null

    if (x in 0.82f..0.92f && y in 0.035f..0.09f) return "bell"

    if (x in 0.045f..0.205f && y in 0.845f..0.955f) return "voice"
    if (x in 0.205f..0.385f && y in 0.845f..0.955f) return "quick_note"
    if (x in 0.385f..0.615f && y in 0.825f..0.965f) return "camera"
    if (x in 0.615f..0.795f && y in 0.845f..0.955f) return "barcode"
    if (x in 0.795f..0.955f && y in 0.845f..0.955f) return "ai_assist"

    val dxPx = (x - 0.5f) * drawW
    val dyPx = (y - 0.365f) * drawH
    val radiusPx = sqrt(dxPx * dxPx + dyPx * dyPx)

    val coreRadius = 0.105f * drawW
    if (radiusPx <= coreRadius) return "core"

    val innerRadius = 0.17f * drawW
    val outerRadius = 0.405f * drawW
    if (radiusPx < innerRadius || radiusPx > outerRadius) return null

    var angleDeg = atan2(dyPx, dxPx) * 180f / PI.toFloat()
    if (angleDeg < 0f) angleDeg += 360f
    val clockwiseFromTop = (angleDeg + 90f) % 360f
    val index = (((clockwiseFromTop + 22.5f) % 360f) / 45f).toInt()
    return "domain:${DOMAINS[index]}"
}

@Composable
private fun DomainSheet(
    domain: String,
    modules: List<com.alekpeed.lifeos.Module>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xE605070B))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                domain.uppercase(),
                color = androidx.compose.ui.graphics.Color(0xFFFF88B9),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            modules.forEach { module ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Color(0x14FFFFFF),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { onPick(module.id) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                ) {
                    Text(
                        "${module.icon}   ${module.label}",
                        color = androidx.compose.ui.graphics.Color(0xFFEDEFF2),
                        fontSize = 16.sp,
                    )
                }
            }
            Text(
                "tap anywhere to close",
                color = androidx.compose.ui.graphics.Color(0x8899A0AA),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

fun registerNexus() {
    com.alekpeed.lifeos.interfaces.Interfaces.registerHome(NEXUS) { NexusHome() }
}
