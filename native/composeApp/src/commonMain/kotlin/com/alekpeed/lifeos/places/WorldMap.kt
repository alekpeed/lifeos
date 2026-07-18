package com.alekpeed.lifeos.places

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.platform.loadTextAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The Places map — an offline vector world map, not a tile service. Land
// polygons come from the bundled world_land.txt (Natural Earth 1:50m via
// world-atlas, public domain), parsed once into a single Skia Path in
// equirectangular projection space (x = lon+180 in [0,360], y = 90−lat in
// [0,180]) and drawn under the theme's colors. Pinch/drag to zoom and pan;
// pins mark every place with coordinates (visited vs want-to-go colors); tap a
// pin to jump to that place's editor. Street-level detail is out of scope by
// design — this answers "where are my places", not "navigate me there".

private const val WORLD_W = 360f
private const val WORLD_H = 180f

// One value per place the map needs — precomputed world coords + list color key.
data class MapPin(val id: Long, val name: String, val x: Float, val y: Float, val wantToGo: Boolean)

fun mapPins(places: List<Place>): List<MapPin> = places.mapNotNull { p ->
    val lat = p.lat ?: return@mapNotNull null
    val lng = p.lng ?: return@mapNotNull null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@mapNotNull null
    MapPin(p.id, p.name, (lng + 180).toFloat(), (90 - lat).toFloat(), p.listType == "wantToGo")
}

// Parse "lon,lat lon,lat …;lon,lat …" rings into a filled Path in world space.
private fun parseLandPath(text: String): Path {
    val path = Path()
    text.splitToSequence(';').forEach { ring ->
        var first = true
        ring.splitToSequence(' ').forEach { pt ->
            val comma = pt.indexOf(',')
            if (comma > 0) {
                val lon = pt.substring(0, comma).toFloatOrNull()
                val lat = pt.substring(comma + 1).toFloatOrNull()
                if (lon != null && lat != null) {
                    val x = lon + 180f
                    val y = 90f - lat
                    if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                }
            }
        }
        if (!first) path.close()
    }
    return path
}

@Composable
fun WorldMapView(pins: List<MapPin>, onPick: (Long) -> Unit) {
    // Parse off the main thread; ~60k points takes a beat on a phone.
    val landPath by produceState<Path?>(null) {
        value = withContext(Dispatchers.Default) {
            loadTextAsset("world_land.txt")?.let { parseLandPath(it) }
        }
    }

    var zoom by remember { mutableStateOf(0f) }        // px per world unit; 0 = fit on first draw
    var offset by remember { mutableStateOf(Offset.Zero) }
    var tapped by remember { mutableStateOf<MapPin?>(null) }

    val sea = MaterialTheme.colorScheme.surface
    val land = MaterialTheme.colorScheme.surfaceVariant
    val coast = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val visitedColor = MaterialTheme.colorScheme.primary
    val wantColor = Color(0xFFE0A25C)
    val pinRim = MaterialTheme.colorScheme.surface

    Column(Modifier.fillMaxSize()) {
        Text(
            "Pinch to zoom, drag to pan. ● visited · ● want to go — tap a pin to open it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tapped?.let {
            Text(
                "${it.name} — tap again to open",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val fit = size.width / WORLD_W
                        val z = if (zoom <= 0f) fit else zoom
                        val off = if (zoom <= 0f) Offset(0f, (size.height - WORLD_H * fit) / 2f) else offset
                        val newZoom = (z * gestureZoom).coerceIn(fit, fit * 60f)
                        // Keep the point under the centroid fixed while zooming, then pan.
                        var next = centroid - (centroid - off) * (newZoom / z) + pan
                        // Loose clamp so the world can't be flung away entirely.
                        val w = WORLD_W * newZoom
                        val h = WORLD_H * newZoom
                        next = Offset(
                            next.x.coerceIn(size.width - w - 80f, 80f),
                            next.y.coerceIn(size.height - h - 80f, 80f),
                        )
                        zoom = newZoom
                        offset = next
                    }
                }
                .pointerInput(pins) {
                    detectTapGestures { tap ->
                        val fit = size.width / WORLD_W
                        val z = if (zoom <= 0f) fit else zoom
                        val off = if (zoom <= 0f) Offset(0f, (size.height - WORLD_H * fit) / 2f) else offset
                        var best: MapPin? = null
                        var bestD = 28f * 28f
                        pins.forEach { p ->
                            val sp = off + Offset(p.x * z, p.y * z)
                            val d = (sp - tap).getDistanceSquared()
                            if (d < bestD) { bestD = d; best = p }
                        }
                        val hit = best
                        when {
                            hit == null -> tapped = null
                            tapped?.id == hit.id -> { tapped = null; onPick(hit.id) }
                            else -> tapped = hit
                        }
                    }
                },
        ) {
            val fit = size.width / WORLD_W
            val z = if (zoom <= 0f) fit else zoom
            val off = if (zoom <= 0f) Offset(0f, (size.height - WORLD_H * fit) / 2f) else offset
            drawRect(sea)
            withTransform({
                translate(off.x, off.y)
                scale(z, z, pivot = Offset.Zero)
            }) {
                // Graticule every 30° under the land.
                for (lon in 0..360 step 30) {
                    drawLine(grid, Offset(lon.toFloat(), 0f), Offset(lon.toFloat(), WORLD_H), strokeWidth = 1f / z)
                }
                for (lat in 0..180 step 30) {
                    drawLine(grid, Offset(0f, lat.toFloat()), Offset(WORLD_W, lat.toFloat()), strokeWidth = 1f / z)
                }
                landPath?.let {
                    drawPath(it, land)
                    drawPath(it, coast, style = Stroke(width = 1.2f / z))
                }
            }
            // Pins in screen space so they keep a constant size at any zoom.
            pins.forEach { p ->
                val sp = off + Offset(p.x * z, p.y * z)
                if (sp.x in -20f..size.width + 20f && sp.y in -20f..size.height + 20f) {
                    val c = if (p.wantToGo) wantColor else visitedColor
                    val selectedPin = tapped?.id == p.id
                    drawCircle(pinRim, radius = if (selectedPin) 9f else 7f, center = sp)
                    drawCircle(c, radius = if (selectedPin) 7f else 5f, center = sp)
                }
            }
        }
    }
}
