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
import com.alekpeed.lifeos.platform.loadImageAsset
import com.alekpeed.lifeos.system.scanCode
import com.alekpeed.lifeos.system.scanWithCamera
import kotlin.math.atan2
import kotlin.math.sqrt

const val NEXUS = "nexus"
private const val ART = "nexus-home.png"

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
    val art = remember { loadImageAsset(ART) }
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

    BoxWithConstraints(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF05060A))) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        val topInset = Native.cutoutTopPx().toFloat()
        val bottomInset = Native.navBottomPx().toFloat()
        val safeH = (vh - topInset - bottomInset).coerceAtLeast(1f)
        val scale = minOf(vw / art.width.toFloat(), safeH / art.height.toFloat())
        val dw = art.width * scale
        val dh = art.height * scale
        val ox = (vw - dw) / 2f
        val oy = topInset + (safeH - dh) / 2f
        val density = LocalDensity.current

        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(with(density) { ox.toDp() }, with(density) { oy.toDp() })
                .size(with(density) { dw.toDp() }, with(density) { dh.toDp() }),
        )

        Box(
            Modifier.fillMaxSize().pointerInput(art, ox, oy, dw, dh) {
                detectTapGestures { tap ->
                    when (val hit = hitRegion(tap, ox, oy, dw, dh)) {
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

private fun hitRegion(tap: Offset, ox: Float, oy: Float, w: Float, h: Float): String? {
    val x = (tap.x - ox) / w
    val y = (tap.y - oy) / h
    if (x < 0f || x > 1f || y < 0f || y > 1f) return null

    if (x in 0.77f..0.86f && y in 0.015f..0.085f) return "bell"

    if (y in 0.88f..0.985f) {
        return when {
            x in 0.04f..0.20f -> "voice"
            x in 0.20f..0.38f -> "note"
            x in 0.38f..0.62f -> "camera"
            x in 0.62f..0.80f -> "barcode"
            x in 0.80f..0.96f -> "ai"
            else -> null
        }
    }

    val cx = 0.5f
    val cy = 0.44f
    val dx = x - cx
    val dy = y - cy
    val r = sqrt(dx * dx + dy * dy)
    if (r <= 0.11f) return "core"
    if (r < 0.15f || r > 0.31f) return null

    var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
    if (deg < 0f) deg += 360f
    val index = (((deg + 22.5f) % 360f) / 45f).toInt()
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
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xE605070B)).clickable(onClick = onDismiss),
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
            modules.forEach { m ->
                Box(
                    Modifier.fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { onPick(m.id) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                ) {
                    Text("${m.icon}   ${m.label}", color = androidx.compose.ui.graphics.Color(0xFFEDEFF2), fontSize = 16.sp)
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
