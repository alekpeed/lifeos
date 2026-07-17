package com.alekpeed.lifeos.places

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
