package com.alekpeed.lifeos.interfaces.machiya

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.HomeScreen
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.lifeOsModules
import com.alekpeed.lifeos.interfaces.Interfaces
import com.alekpeed.lifeos.platform.loadImageAsset

const val MACHIYA = "machiya"
private const val ART = "machiya-hub.jpg"
private const val ART_W = 1672f
private const val ART_H = 941f

private data class Hotspot(
    val domain: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

private val HOTSPOTS = listOf(
    Hotspot("Archive", 0.015f, 0.13f, 0.285f, 0.47f),
    Hotspot("Logistics", 0.245f, 0.43f, 0.335f, 0.21f),
    Hotspot("People", 0.655f, 0.14f, 0.165f, 0.43f),
    Hotspot("System", 0.835f, 0.13f, 0.155f, 0.50f),
    Hotspot("Operations", 0.005f, 0.61f, 0.365f, 0.38f),
    Hotspot("Discovery", 0.365f, 0.64f, 0.255f, 0.35f),
    Hotspot("Intelligence", 0.59f, 0.52f, 0.255f, 0.22f),
    Hotspot("Management", 0.64f, 0.74f, 0.35f, 0.25f),
)

fun registerMachiyaHome() {
    Interfaces.registerHome(MACHIYA) { MachiyaHome() }
}

@Composable
private fun MachiyaHome() {
    val art = remember { loadImageAsset(ART) }
    val modules = remember { lifeOsModules() }
    var openDomain by remember { mutableStateOf<String?>(null) }
    var soundOn by remember { mutableStateOf(true) }
    val rainAudio = remember { RainAudio() }

    if (art == null) {
        HomeScreen(modules) { Nav.open(it.id) }
        return
    }

    DisposableEffect(soundOn) {
        if (soundOn) rainAudio.start() else rainAudio.stop()
        onDispose { rainAudio.stop() }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        val scale = minOf(viewportW / ART_W, viewportH / ART_H)
        val drawW = ART_W * scale
        val drawH = ART_H * scale
        val originX = (viewportW - drawW) / 2f
        val originY = (viewportH - drawH) / 2f
        val density = LocalDensity.current

        fun Modifier.artRect(x: Float, y: Float, width: Float, height: Float): Modifier =
            offset(
                with(density) { (originX + drawW * x).toDp() },
                with(density) { (originY + drawH * y).toDp() },
            ).size(
                with(density) { (drawW * width).toDp() },
                with(density) { (drawH * height).toDp() },
            )

        Image(
            bitmap = art,
            contentDescription = "Dark machiya LifeOS hub",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.artRect(0f, 0f, 1f, 1f),
        )

        RainLayer(Modifier.artRect(0.305f, 0.105f, 0.335f, 0.39f))

        HOTSPOTS.forEach { spot ->
            Box(
                Modifier
                    .artRect(spot.x, spot.y, spot.width, spot.height)
                    .clickable { openDomain = spot.domain },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xA016120E))
                .clickable { soundOn = !soundOn }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                if (soundOn) "RAIN SOUND  ON" else "RAIN SOUND  OFF",
                color = Color(0xFFD0AD75),
                fontFamily = FontFamily.Serif,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
        }

        AnimatedVisibility(
            visible = openDomain != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val domain = openDomain
            if (domain != null) {
                DomainDrawer(
                    domain = domain,
                    modules = modules.filter { it.group == domain },
                    onOpen = { Nav.open(it); openDomain = null },
                    onClose = { openDomain = null },
                )
            }
        }
    }
}

@Composable
private fun RainLayer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "machiya rain")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "rainfall",
    )
    Canvas(modifier) {
        repeat(72) { index ->
            val seed = ((index * 73) % 101) / 101f
            val x = ((index * 47) % 103) / 103f * size.width
            val speed = 0.65f + ((index * 19) % 31) / 62f
            val yUnit = (seed + phase * speed) % 1f
            val length = 7f + ((index * 13) % 17)
            val alpha = 0.08f + ((index * 11) % 17) / 130f
            val start = Offset(x, yUnit * (size.height + length) - length)
            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = start,
                end = Offset(start.x - length * 0.28f, start.y + length),
                strokeWidth = if (index % 5 == 0) 1.4f else 0.8f,
                cap = StrokeCap.Round,
            )
            if (yUnit > 0.94f && index % 6 == 0) {
                val splash = (yUnit - 0.94f) / 0.06f
                drawCircle(
                    color = Color(0xFFBDD2DC).copy(alpha = 0.12f * (1f - splash)),
                    radius = 2f + splash * 7f,
                    center = Offset(x, size.height - 2f),
                )
            }
        }
    }
}

@Composable
private fun DomainDrawer(
    domain: String,
    modules: List<com.alekpeed.lifeos.Module>,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.28f)
            .background(Color(0xF21A1510))
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                domain.uppercase(),
                color = Color(0xFFE0BE82),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = 2.sp,
            )
            Text(
                "CLOSE",
                color = Color(0xFF9C8769),
                fontSize = 10.sp,
                modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
            )
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).size(width = 200.dp, height = 1.dp).background(Color(0xFF70583B)))
        modules.forEach { module ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onOpen(module.id) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(module.icon, fontSize = 15.sp, modifier = Modifier.padding(end = 10.dp))
                Text(
                    module.label,
                    color = Color(0xFFE5D7BD),
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
