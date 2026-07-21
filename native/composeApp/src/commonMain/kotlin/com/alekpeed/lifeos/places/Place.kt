package com.alekpeed.lifeos.places

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.today
import kotlinx.datetime.daysUntil
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Places — ported from the web app's Places view. A place lives on one of two
// lists (visited / want-to-go), carries a category, star rating, revisit flag,
// visit dates, address + coordinates, freeform notes, and geofenced
// notes-to-self. Plus a separate Bucket List of dated goals. Persists as one
// JSON blob under the "Places" key; old plain-line stubs migrate to visited
// places so existing entries survive.

@Serializable
data class Place(
    val id: Long,
    val name: String,
    val listType: String = "visited",   // visited | wantToGo
    val category: String = "",
    val rating: Int = 0,                 // 0-5, visited only
    val revisit: Boolean = false,
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val notes: String = "",
    val visitDates: List<String> = emptyList(),
    val notesToSelf: List<String> = emptyList(),
    val photoBlob: String = "",          // blob-store id of the primary attached photo, if any
    val attachments: List<com.alekpeed.lifeos.attach.Attachment> = emptyList(), // more photos / files
)

@Serializable
data class BucketItem(
    val id: Long,
    val title: String,
    val done: Boolean = false,
    val targetDate: String = "",
)

@Serializable
data class PlacesData(
    val places: List<Place> = emptyList(),
    val bucket: List<BucketItem> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadPlaces(): PlacesData {
    val raw = Storage.read("Places")
    if (raw.isNullOrBlank()) return PlacesData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<PlacesData>(raw) }.getOrElse { PlacesData() }
    }
    // Migrate the old SimpleListScreen stub (one place name per line).
    val places = raw.lines().filter { it.isNotBlank() }
        .mapIndexed { i, line -> Place(id = i + 1L, name = line.trim(), listType = "visited") }
    return PlacesData(places = places)
}

fun savePlaces(data: PlacesData) {
    Storage.write("Places", json.encodeToString(data))
}

// ---------- Nearby nudges (ported from the web's foreground "check nearby" flow) ----------

const val NEARBY_RADIUS_METERS = 1000.0
const val STALE_REVISIT_DAYS = 90

data class NearbyNudge(val place: Place, val distanceMeters: Double, val reason: String)

private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = (lat2 - lat1) * kotlin.math.PI / 180
    val dLng = (lng2 - lng1) * kotlin.math.PI / 180
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1 * kotlin.math.PI / 180) * cos(lat2 * kotlin.math.PI / 180) * sin(dLng / 2) * sin(dLng / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

// Want-to-go spots, stale revisits (90+ days since last visit), and standing
// notes-to-self within 1km of the given position — a manual "check nearby"
// action, same as the web (foreground-only; native has no passive background
// geofence sweep over every place, only the single-place arrival alert).
fun findNearbyNudges(places: List<Place>, lat: Double, lng: Double): List<NearbyNudge> {
    val out = mutableListOf<NearbyNudge>()
    val now = today()
    for (place in places) {
        val plat = place.lat ?: continue
        val plng = place.lng ?: continue
        val distance = haversineMeters(lat, lng, plat, plng)
        if (distance > NEARBY_RADIUS_METERS) continue

        if (place.listType == "wantToGo") {
            out.add(NearbyNudge(place, distance, "Want to go"))
        } else if (place.listType == "visited" && place.revisit) {
            val lastVisitDate = place.visitDates.maxOrNull()?.let { parseDateOrNull(it) }
            val daysSince = lastVisitDate?.daysUntil(now)
            if (daysSince == null || daysSince >= STALE_REVISIT_DAYS) {
                out.add(NearbyNudge(place, distance, if (daysSince != null) "Haven't been back in $daysSince days" else "Never marked as visited"))
            }
        }
        place.notesToSelf.forEach { note -> out.add(NearbyNudge(place, distance, "📝 $note")) }
    }
    return out.sortedBy { it.distanceMeters }
}
