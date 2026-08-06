package com.alekpeed.lifeos.places

import androidx.compose.ui.graphics.ImageBitmap
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.net.httpGetImageBase64
import com.alekpeed.lifeos.platform.deleteBlob
import com.alekpeed.lifeos.platform.loadBlobImage
import com.alekpeed.lifeos.platform.saveBlob
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.sinh

// Map tiles for the Places map — standard OpenStreetMap raster tiles, fetched once and
// kept on the device. This is what gives the map streets and labels; the app only draws
// the pins on top.
//
// Three layers of cache, cheapest first: an in-memory map for this session, the local
// blob store for tiles fetched on any previous run, then the network. A tile already on
// disk means the places you look at keep working with no connection at all.
//
// Tiles are bytes, so they go in the blob store, which is never synced and never lands in
// a backup export — the index below is the only thing written to a record key, and it is
// tiny. OSM's tile policy asks for an identifying User-Agent and no bulk downloading;
// personal browsing of your own places is exactly what it permits, and the caching here
// is what keeps it to that.

private const val TILE_PX = 256
const val MAX_ZOOM = 18
private const val DISK_CAP = 600
private const val MEM_CAP = 240
private const val INDEX_KEY = "MapTileIndex"
private const val UA = "LifeOS/1.0 (personal life-management app; single user)"

@Serializable
private data class TileIndex(val order: List<String> = emptyList(), val blobs: Map<String, String> = emptyMap())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// ---- Web Mercator, in normalised world units where the whole world is 0..1 ----

fun lonToWorldX(lon: Double): Double = (lon + 180.0) / 360.0

fun latToWorldY(lat: Double): Double {
    val clamped = lat.coerceIn(-85.05112878, 85.05112878)
    val s = sin(clamped * PI / 180.0)
    return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
}

fun worldXToLon(x: Double): Double = x * 360.0 - 180.0

fun worldYToLat(y: Double): Double = atan(sinh(PI * (1 - 2 * y))) * 180.0 / PI

// Pixels across the whole world at a given (fractional) zoom.
fun worldPxAt(zoom: Float): Double = TILE_PX * pow2(zoom)

private fun pow2(e: Float): Double = 2.0.pow(e.toDouble())

object MapTiles {
    private val memory = LinkedHashMap<String, ImageBitmap>()
    private val missing = HashSet<String>()   // tiles the server had nothing for; don't re-ask
    private var index: TileIndex? = null

    private fun key(z: Int, x: Int, y: Int) = "$z/$x/$y"

    private fun loadIndex(): TileIndex {
        index?.let { return it }
        val raw = Storage.read(INDEX_KEY)
        val i = if (raw.isNullOrBlank()) TileIndex()
        else runCatching { json.decodeFromString<TileIndex>(raw) }.getOrElse { TileIndex() }
        index = i
        return i
    }

    private fun saveIndex(i: TileIndex) {
        index = i
        Storage.write(INDEX_KEY, json.encodeToString(i))
    }

    // Already in memory? Used by the draw pass, which can't suspend.
    fun cached(z: Int, x: Int, y: Int): ImageBitmap? = memory[key(z, x, y)]

    fun knownMissing(z: Int, x: Int, y: Int): Boolean = key(z, x, y) in missing

    // Fetch one tile, cheapest source first. Returns null if it isn't available at all.
    suspend fun fetch(z: Int, x: Int, y: Int): ImageBitmap? {
        val k = key(z, x, y)
        memory[k]?.let { return it }
        if (k in missing) return null

        val i = loadIndex()
        i.blobs[k]?.let { blobId ->
            val img = loadBlobImage(blobId)
            if (img != null) { remember(k, img); return img }
        }

        val url = "https://tile.openstreetmap.org/$z/$x/$y.png"
        val b64 = httpGetImageBase64(url, mapOf("User-Agent" to UA))
        if (b64.isNullOrBlank()) { missing.add(k); return null }

        val blobId = saveBlob(b64)
        if (blobId != null) {
            val order = i.order + k
            val blobs = i.blobs + (k to blobId)
            // Evict the oldest tiles past the cap, deleting their bytes as we go.
            if (order.size > DISK_CAP) {
                val drop = order.take(order.size - DISK_CAP)
                drop.forEach { old -> blobs[old]?.let { deleteBlob(it) } }
                saveIndex(TileIndex(order.drop(drop.size), blobs - drop.toSet()))
            } else {
                saveIndex(TileIndex(order, blobs))
            }
        }
        val img = blobId?.let { loadBlobImage(it) }
        if (img != null) remember(k, img) else missing.add(k)
        return img
    }

    private fun remember(k: String, img: ImageBitmap) {
        memory[k] = img
        if (memory.size > MEM_CAP) {
            val oldest = memory.keys.firstOrNull() ?: return
            memory.remove(oldest)
        }
    }

    // Settings-facing: how much map is on disk, and a way to be rid of it.
    fun cachedTileCount(): Int = loadIndex().order.size

    fun clearCache() {
        val i = loadIndex()
        i.blobs.values.forEach { deleteBlob(it) }
        memory.clear()
        missing.clear()
        saveIndex(TileIndex())
    }
}

// A place's coordinates as a map link, for handing off to whatever the machine uses for
// maps. The Google form is understood by the Google Maps app on Android and by every
// desktop browser, which is the widest single answer.
fun mapsLink(lat: Double, lng: Double): String =
    "https://www.google.com/maps/search/?api=1&query=$lat,$lng"

// Zoom that fits a set of pins, so opening the map lands on your places rather than the
// whole planet. Returns null when there's nothing to fit.
fun fitFor(pins: List<MapPin>): Triple<Double, Double, Float>? {
    if (pins.isEmpty()) return null
    val xs = pins.map { lonToWorldX(it.lng) }
    val ys = pins.map { latToWorldY(it.lat) }
    val cx = (xs.min() + xs.max()) / 2
    val cy = (ys.min() + ys.max()) / 2
    val spanX = (xs.max() - xs.min()).coerceAtLeast(1e-6)
    val spanY = (ys.max() - ys.min()).coerceAtLeast(1e-6)
    val span = maxOf(spanX, spanY)
    // World is 1.0 wide; a span of `span` fills the view at 2^z tiles across.
    var z = 1f
    while (z < 15f && span * pow2(z + 1) < 0.9) z += 1f
    return Triple(cx, cy, if (pins.size == 1) 13f else z)
}
