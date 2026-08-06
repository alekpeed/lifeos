package com.alekpeed.lifeos.places

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.platform.Native
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.roundToInt

// The Places map — real OpenStreetMap tiles with your places pinned on top. Streets and
// labels come from the tiles; everything this file draws is the pins and the chrome.
//
// The view is a centre point in normalised world coordinates plus a fractional zoom, which
// is what lets a pinch feel continuous while tiles are only ever fetched at whole zoom
// levels: tiles are drawn from floor(zoom) and scaled by the fraction. Panning moves the
// centre, not a pixel offset, so nothing can drift out of range or invert a clamp.
//
// Tapping a pin selects it and shows its name; tapping the same pin again opens the place.
// The selected pin also offers a handoff to the system map, which is the right place for
// directions — this map answers "where are my places", not "navigate me there".

data class MapPin(
    val id: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
    val wantToGo: Boolean,
)

fun mapPins(places: List<Place>): List<MapPin> = places.mapNotNull { p ->
    val lat = p.lat ?: return@mapNotNull null
    val lng = p.lng ?: return@mapNotNull null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@mapNotNull null
    MapPin(p.id, p.name, lat, lng, p.listType == "wantToGo")
}

@Composable
fun WorldMapView(pins: List<MapPin>, onPick: (Long) -> Unit) {
    // Open on your places, not the middle of the Atlantic.
    val start = remember(pins) { fitFor(pins) }
    var cx by remember { mutableStateOf(start?.first ?: 0.5) }
    var cy by remember { mutableStateOf(start?.second ?: 0.5) }
    var zoom by remember { mutableStateOf(start?.third ?: 2f) }
    var selected by remember { mutableStateOf<MapPin?>(null) }
    // Bumped whenever a tile lands, purely to ask the canvas to draw again.
    var tick by remember { mutableStateOf(0) }

    val visitedColor = MaterialTheme.colorScheme.primary
    val wantColor = Color(0xFFE0A25C)
    val pinRim = Color(0xFFFFFFFF)
    val backdrop = MaterialTheme.colorScheme.surfaceVariant

    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()

        // World -> screen for the current view.
        val worldPx = worldPxAt(zoom)
        fun sx(worldX: Double) = (vw / 2 + (worldX - cx) * worldPx).toFloat()
        fun sy(worldY: Double) = (vh / 2 + (worldY - cy) * worldPx).toFloat()

        val zi = floor(zoom).toInt().coerceIn(0, MAX_ZOOM)
        val n = 1 shl zi
        val tilePx = (worldPx / n).toFloat()

        // Which tiles cover the viewport right now.
        val tiles = remember(cx, cy, zoom, vw, vh) {
            if (vw <= 0f || vh <= 0f || tilePx <= 0f) emptyList()
            else {
                val leftWorld = cx - (vw / 2) / worldPx
                val topWorld = cy - (vh / 2) / worldPx
                val x0 = floor(leftWorld * n).toInt() - 1
                val y0 = floor(topWorld * n).toInt() - 1
                val cols = (vw / tilePx).toInt() + 3
                val rows = (vh / tilePx).toInt() + 3
                buildList {
                    for (ty in y0 until y0 + rows) {
                        if (ty < 0 || ty >= n) continue
                        for (tx in x0 until x0 + cols) {
                            // Wrap east-west so panning across the date line keeps working.
                            val wrapped = ((tx % n) + n) % n
                            add(Triple(wrapped, ty, tx))
                        }
                    }
                }
            }
        }

        // Fetch whatever isn't in memory yet, then nudge the canvas. One at a time and in
        // a single coroutine: it keeps the request rate polite (OSM's tile policy asks for
        // exactly that) and it cancels the moment you pan somewhere else.
        LaunchedEffect(tiles, zi) {
            tiles.forEach { (tx, ty, _) ->
                if (MapTiles.cached(zi, tx, ty) == null && !MapTiles.knownMissing(zi, tx, ty)) {
                    if (MapTiles.fetch(zi, tx, ty) != null) tick++
                }
            }
        }

        // The tiles that are actually ready to paint, resolved in composition rather than
        // inside the draw pass so a landed tile (tick) is what triggers the repaint.
        val ready = remember(tiles, zi, tick) {
            tiles.mapNotNull { (tx, ty, rawX) ->
                MapTiles.cached(zi, tx, ty)?.let { Triple(it, rawX, ty) }
            }
        }

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val px = worldPxAt(zoom)
                        // Zoom about the pinch centroid: convert it to world, change the
                        // scale, then move the centre so that world point stays put.
                        val beforeX = cx + (centroid.x - vw / 2) / px
                        val beforeY = cy + (centroid.y - vh / 2) / px
                        val next = (zoom + log2(gestureZoom.coerceAtLeast(0.0001f))).coerceIn(1f, MAX_ZOOM.toFloat())
                        val npx = worldPxAt(next)
                        cx = (beforeX - (centroid.x - vw / 2) / npx - pan.x / npx)
                        cy = (beforeY - (centroid.y - vh / 2) / npx - pan.y / npx).coerceIn(0.0, 1.0)
                        // Longitude wraps; latitude does not.
                        cx = ((cx % 1.0) + 1.0) % 1.0
                        zoom = next
                    }
                }
                .pointerInput(pins, cx, cy, zoom) {
                    detectTapGestures { tap ->
                        var best: MapPin? = null
                        var bestD = 34f * 34f
                        pins.forEach { p ->
                            val d = (Offset(sx(lonToWorldX(p.lng)), sy(latToWorldY(p.lat))) - tap)
                                .getDistanceSquared()
                            if (d < bestD) { bestD = d; best = p }
                        }
                        val hit = best
                        when {
                            hit == null -> selected = null
                            selected?.id == hit.id -> { selected = null; onPick(hit.id) }
                            else -> selected = hit
                        }
                    }
                },
        ) {
            drawRect(backdrop)
            ready.forEach { (img, rawX, ty) ->
                // Draw at the unwrapped column so wrapped tiles land in the right place.
                val left = (vw / 2 + (rawX.toDouble() / n - cx) * worldPx).toFloat()
                val top = (vh / 2 + (ty.toDouble() / n - cy) * worldPx).toFloat()
                drawImage(
                    image = img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(tilePx.roundToInt() + 1, tilePx.roundToInt() + 1),
                )
            }
            pins.forEach { p ->
                val x = sx(lonToWorldX(p.lng))
                val y = sy(latToWorldY(p.lat))
                if (x in -30f..vw + 30f && y in -30f..vh + 30f) {
                    val c = if (p.wantToGo) wantColor else visitedColor
                    val on = selected?.id == p.id
                    val r = if (on) 9f else 7f
                    drawCircle(Color(0x55000000), radius = r + 3f, center = Offset(x, y + 1f))
                    drawCircle(pinRim, radius = r, center = Offset(x, y))
                    drawCircle(c, radius = r - 2.5f, center = Offset(x, y))
                }
            }
        }

        // Chrome sits above the map in its own layer, so nothing bleeds through it.
        Column(Modifier.align(Alignment.TopStart).padding(8.dp)) {
            selected?.let { p ->
                Row(
                    Modifier.clip(RoundedCornerShape(9.dp))
                        .background(Color(0xE6101319)).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        p.name.ifBlank { "(untitled)" },
                        color = Color(0xFFF2F4F6),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "Open place",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable { onPick(p.id) },
                    )
                    Text(
                        "Directions ↗",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable { Native.openUrl(mapsLink(p.lat, p.lng)) },
                    )
                }
            }
        }

        // Zoom controls, for a mouse with no pinch.
        Column(
            Modifier.align(Alignment.CenterEnd).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("+" to 1f, "−" to -1f).forEach { (label, delta) ->
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xE6101319))
                        .clickable { zoom = (zoom + delta).coerceIn(1f, MAX_ZOOM.toFloat()) }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) { Text(label, color = Color(0xFFF2F4F6), fontSize = 17.sp) }
            }
        }

        // Required attribution, and the only always-on text on the map.
        Text(
            if (pins.isEmpty()) "No places have coordinates yet · © OpenStreetMap"
            else "© OpenStreetMap contributors",
            color = Color(0xFFDDE1E6),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                .clip(RoundedCornerShape(6.dp)).background(Color(0x99000000))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
