package com.alekpeed.lifeos.travel

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.photos.loadPhotos
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// §11.6 — the end-of-trip recap.
//
// The Yearly Recap's pattern at trip scale: count what actually happened from records
// that already exist, then let the model write it up from those numbers and nothing
// else. Everything here is derived — no new fields on Trip, nothing for the recap to
// disagree with later. It appears once the trip's end date has passed, because a recap
// of a trip you are still on is a status report.

data class TripRecap(
    val trip: Trip,
    val days: Int,
    val photos: Int,
    val albumName: String,
    val placesVisited: List<String>,
    val bookings: Int,
    val bookingsByType: List<Pair<String, Int>>,
    val budget: TripBudget,
    val travellers: List<String>,
    val documentsCarried: List<String>,
) {
    val hasAnything: Boolean
        get() = photos > 0 || placesVisited.isNotEmpty() || bookings > 0 || budget.totalBooked > 0.0
}

// Whether there is a recap to show at all: the trip has to be over.
fun tripIsOver(trip: Trip): Boolean {
    val end = trip.end() ?: trip.start() ?: return false
    return end < today()
}

fun tripRecap(trip: Trip, data: TravelData = loadTravel()): TripRecap {
    val start = trip.start()
    val end = trip.end() ?: start
    val days = if (start != null && end != null) end.toEpochDays() - start.toEpochDays() + 1 else 0

    val album = trip.albumId?.let { id -> loadPhotos().albums.firstOrNull { it.id == id } }

    val live = reservationsFor(data, trip.id).filter { it.status != ReservationStatus.CANCELLED }
    val byType = live.groupBy { it.type }
        .map { (t, rs) -> typeName(t) to rs.size }
        .sortedByDescending { it.second }

    val contacts = loadContacts().contacts
    val travellers = trip.travelerIds.mapNotNull { id -> contacts.firstOrNull { it.id == id }?.name }

    val docs = loadDocuments().documents
    val carried = trip.documentIds.mapNotNull { id -> docs.firstOrNull { it.id == id }?.title }

    return TripRecap(
        trip = trip,
        days = days,
        photos = album?.captions?.size ?: 0,
        albumName = album?.name.orEmpty(),
        placesVisited = tripPlaces(trip).visited,
        bookings = live.size,
        bookingsByType = byType,
        budget = tripBudget(data, trip),
        travellers = travellers,
        documentsCarried = carried,
    )
}

private fun typeName(t: ReservationType) = when (t) {
    ReservationType.FLIGHT -> "Flights"
    ReservationType.LODGING -> "Stays"
    ReservationType.RAIL -> "Trains"
    ReservationType.BUS -> "Buses"
    ReservationType.CAR -> "Car hire"
    ReservationType.FERRY -> "Ferries"
    ReservationType.TOUR -> "Tours"
    ReservationType.RESTAURANT -> "Restaurants"
    ReservationType.EVENT -> "Events"
    ReservationType.OTHER -> "Other bookings"
}

private fun money(v: Double, currency: String): String =
    currency + " " + ((v * 100).toLong() / 100.0).toString()

// The stat lines, in the order they read best. Also exactly what the model is given —
// it can only write about what is on this list.
fun tripRecapStats(r: TripRecap): List<Pair<String, String>> = buildList {
    if (r.days > 0) add("Days away" to r.days.toString())
    if (r.trip.destinations.any { it.isNotBlank() }) {
        add("Destinations" to r.trip.destinations.filter { it.isNotBlank() }.joinToString(", "))
    }
    if (r.travellers.isNotEmpty()) add("Travelled with" to r.travellers.joinToString(", "))
    add("Photos" to if (r.albumName.isNotBlank()) "${r.photos} in ${r.albumName}" else r.photos.toString())
    add("Places visited" to if (r.placesVisited.isEmpty()) "none logged" else r.placesVisited.joinToString(", "))
    add("Bookings" to r.bookings.toString())
    r.bookingsByType.forEach { (name, n) -> add("  $name" to n.toString()) }

    val b = r.budget
    if (b.estimate > 0) add("Budget" to money(b.estimate, b.currency))
    add("Spent" to money(b.totalBooked, b.currency))
    if (b.hasForeign) {
        add("  of which converted" to money(b.convertedBooked, b.currency) + " at today's rate")
    }
    if (b.estimate > 0) {
        val diff = b.totalBooked - b.estimate
        val label = if (diff > 0) "Over budget by" else "Under budget by"
        add(label to money(kotlin.math.abs(diff), b.currency))
    }
    if (b.outstanding > 0) add("Still unpaid" to money(b.outstanding, b.currency))
    if (r.documentsCarried.isNotEmpty()) add("Documents carried" to r.documentsCarried.joinToString(", "))
}

// ---- the written-up version ---------------------------------------------------------

const val TRIP_RECAP_SYSTEM: String =
    "You write a short, warm recap of one trip, for the person who took it, inside their " +
        "life-management app. Use ONLY the STATS provided — never invent a place, a number, " +
        "a meal, or a feeling the data doesn't support. 3-5 sentences, reflective but " +
        "grounded. Output only the prose."

fun buildTripRecapContext(r: TripRecap): String = buildString {
    append("Trip: ${r.trip.name.ifBlank { "(untitled)" }}\n")
    if (r.trip.startDate.isNotBlank()) append("Dates: ${r.trip.startDate} to ${r.trip.endDate.ifBlank { r.trip.startDate }}\n")
    tripRecapStats(r).forEach { append("${it.first.trim()}: ${it.second}\n") }
    if (r.trip.notes.isNotBlank()) append("Notes kept during the trip: ${r.trip.notes}\n")
}

// Narratives are kept per trip, keyed by id, so writing one up for Iceland does not
// overwrite the one for Lisbon. (The Yearly Recap holds a single year's; a person has
// many more trips than years.)
@Serializable
private data class RecapStore(val byTrip: Map<String, String> = emptyMap())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun store(): RecapStore {
    val raw = Storage.read("TripRecaps")
    if (raw.isNullOrBlank()) return RecapStore()
    return runCatching { json.decodeFromString<RecapStore>(raw) }.getOrElse { RecapStore() }
}

fun tripNarrative(tripId: Long): String = store().byTrip[tripId.toString()].orEmpty()

fun saveTripNarrative(tripId: Long, text: String) {
    val s = store()
    Storage.write("TripRecaps", json.encodeToString(s.copy(byTrip = s.byTrip + (tripId.toString() to text))))
}
