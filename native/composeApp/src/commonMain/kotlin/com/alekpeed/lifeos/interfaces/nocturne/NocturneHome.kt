package com.alekpeed.lifeos.interfaces.nocturne

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.alekpeed.lifeos.interfaces.Interfaces

const val NOCTURNE = "nocturne"
private const val ART = "nocturne-home.png"

// Normalized hit regions deliberately correspond to physical objects in the scene,
// not floating buttons. The artwork can be replaced without touching navigation as
// long as the same semantic composition is retained.
private data class Region(
    val domain: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

private val HITS = listOf(
    Region("Archive", 0.015f, 0.070f, 0.300f, 0.350f),       // bookshelves / library
    Region("Discovery", 0.705f, 0.070f, 0.990f, 0.350f),    // celestial chart / telescope
    Region("Management", 0.305f, 0.315f, 0.695f, 0.575f),   // central city / planning model
    Region("Logistics", 0.010f, 0.345f, 0.385f, 0.570f),    // illuminated map table
    Region("Operations", 0.620f, 0.345f, 0.995f, 0.585f),   // work desk / control surface
    Region("Intelligence", 0.010f, 0.565f, 0.445f, 0.795f), // analysis desk / reading area
    Region("People", 0.555f, 0.565f, 0.995f, 0.805f),       // lounge / garden seating
    Region("System", 0.245f, 0.785f, 0.755f, 0.985f),       // illuminated floor pool / system core
)

@Composable
fun NocturneHome() {
    val art = remember { loadImageAsset(ART) }
    val modules = remember { lifeOsModules() }
    var domain by remember { mutableStateOf("") }

    // The design is edge-to-edge artwork. Android system chrome is suppressed while
    // this interface is visible; safe sizing still respects physical cutouts.
    DisposableEffect(Unit) {
        Native.setImmersive(true)
        onDispose { Native.setImmersive(false) }
    }

    if (art == null) {
        // Keeps LifeOS functional if the binary art asset is absent from a particular build.
        HomeScreen(modules) { Nav.open(it.id) }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(NocturneColors.Void)) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        val topInset = Native.cutoutTopPx().toFloat()
        val bottomInset = Native.navBottomPx().toFloat()
        val safeH = (viewportH - topInset - bottomInset).coerceAtLeast(1f)

        // Artwork is fitted inside the physical safe area without distortion.
        val sourceAspect = 1024f / 1792f
        val safeAspect = viewportW / safeH
        val artW: Float
        val artH: Float
        if (safeAspect > sourceAspect) {
            artH = safeH
            artW = artH * sourceAspect
        } else {
            artW = viewportW
            artH = artW / sourceAspect
        }
        val originX = (viewportW - artW) / 2f
        val originY = topInset + (safeH - artH) / 2f
        val density = LocalDensity.current

        Image(
            bitmap = art,
            contentDescription = "Nocturne home",
            modifier = Modifier
                .offset(with(density) { originX.toDp() }, with(density) { originY.toDp() })
                .size(with(density) { artW.toDp() }, with(density) { artH.toDp() }),
            contentScale = ContentScale.FillBounds,
        )

        Box(
            Modifier.fillMaxSize().pointerInput(art, originX, originY, artW, artH) {
                detectTapGestures { point ->
                    hitDomain(point, originX, originY, artW, artH)?.let { domain = it }
                }
            },
        )

        if (domain.isNotBlank()) {
            DomainSheet(
                domain = domain,
                modules = modules.filter { it.group == domain },
                onPick = { id -> Nav.open(id); domain = "" },
                onDismiss = { domain = "" },
            )
        }
    }
}

private fun hitDomain(point: Offset, ox: Float, oy: Float, width: Float, height: Float): String? {
    val x = (point.x - ox) / width
    val y = (point.y - oy) / height
    if (x !in 0f..1f || y !in 0f..1f) return null
    return HITS.firstOrNull { x in it.left..it.right && y in it.top..it.bottom }?.domain
}

@Composable
private fun DomainSheet(
    domain: String,
    modules: List<com.alekpeed.lifeos.Module>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(Color(0xD906090D))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                domain.uppercase(),
                color = NocturneColors.Gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            modules.forEach { module ->
                Box(
                    Modifier.fillMaxWidth()
                        .background(NocturneColors.Panel, RoundedCornerShape(12.dp))
                        .clickable { onPick(module.id) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                ) {
                    Text(
                        "${module.icon}   ${module.label}",
                        color = NocturneColors.Ivory,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

object NocturneColors {
    val Void = Color(0xFF05070A)
    val Panel = Color(0xEE10151A)
    val Ivory = Color(0xFFF2E7D2)
    val Gold = Color(0xFFD6A84A)
    val Brass = Color(0xFF9D7132)
    val NightBlue = Color(0xFF0C1A27)
    val Muted = Color(0xFF9B968D)
}

fun registerNocturne() {
    Interfaces.registerHome(NOCTURNE) { NocturneHome() }
}
