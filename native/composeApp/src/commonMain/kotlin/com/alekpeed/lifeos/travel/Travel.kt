package com.alekpeed.lifeos.travel

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.attach.Attachment
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.integrations.CurrencyClient
import com.alekpeed.lifeos.places.loadPlaces
import com.alekpeed.lifeos.data.today
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Travel (§5.1). A trip is the container; reservations are the substance.
//
// Deliberately wide, per the spec's instruction to build wide and pare down later
// rather than under-build. What it does NOT do is duplicate: a traveller is a Contacts
// id, a travel document is a Documents id, and a reservation's place is a Places id.
// Documents already own expiry dates and the expiry alert, so a passport lives there
// and a trip links to it — copying the expiry here would give it two owners and two
// answers.

@Serializable
enum class TripStatus { PLANNING, BOOKED, ACTIVE, PAST }

@Serializable
data class Trip(
    val id: Long,
    val name: String,
    val destinations: List<String> = emptyList(),
    val startDate: String = "",
    val endDate: String = "",
    // Status is derived from the dates. This holds an explicit override — a trip can be
    // booked long before it starts, and only the person knows that.
    val statusOverride: TripStatus? = null,
    val notes: String = "",
    val coverPhotoBlob: String = "",
    // Contacts ids. Names are resolved live, so renaming a contact renames the traveller.
    val travelerIds: List<Long> = emptyList(),
    // Documents ids — passport, visa, insurance. Checked for expiry against the trip.
    val documentIds: List<Long> = emptyList(),
    val budgetEstimate: Double = 0.0,
    val currency: String = "USD",
    // Photos album for the trip. The photos live in Photos, not here — this is the link.
    val albumId: Long? = null,
) {
    fun start(): LocalDate? = parseDateOrNull(startDate)
    fun end(): LocalDate? = parseDateOrNull(endDate)

    fun status(from: LocalDate = today()): TripStatus {
        statusOverride?.let { return it }
        val s = start()
        val e = end() ?: s
        return when {
            s == null -> TripStatus.PLANNING
            e != null && e < from -> TripStatus.PAST
            s <= from -> TripStatus.ACTIVE
            else -> TripStatus.PLANNING
        }
    }

    // Days until departure, negative once under way, null with no start date.
    fun daysAway(from: LocalDate = today()): Int? =
        start()?.let { s -> from.daysUntilCompat(s) }
}

@Serializable
enum class ReservationType { FLIGHT, LODGING, RAIL, BUS, CAR, FERRY, TOUR, RESTAURANT, EVENT, OTHER }

@Serializable
enum class ReservationStatus { HELD, CONFIRMED, CANCELLED }

@Serializable
data class Reservation(
    val id: Long,
    val tripId: Long,
    val type: ReservationType = ReservationType.OTHER,
    val provider: String = "",
    val confirmationNumber: String = "",
    val status: ReservationStatus = ReservationStatus.CONFIRMED,
    // Stored as "yyyy-MM-ddTHH:mm" where a time is set (M-01a). A flight departing at
    // 07:25 is the reason Travel waited on that: a date alone cannot describe one.
    val startDateTime: String = "",
    val endDateTime: String = "",
    val location: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val placeId: Long? = null,
    val contactId: Long? = null,
    val contactName: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    // The confirmation email, the airline's manage page, the booking site.
    val externalLink: String = "",
    val cost: Double = 0.0,
    val currency: String = "USD",
    val paid: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val notes: String = "",
) {
    fun startDate(): LocalDate? = parseDateOrNull(startDateTime)
    fun endDate(): LocalDate? = parseDateOrNull(endDateTime)
}

@Serializable
data class TravelData(
    val trips: List<Trip> = emptyList(),
    val reservations: List<Reservation> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadTravel(): TravelData {
    val raw = Storage.read("Travel")
    if (raw.isNullOrBlank()) return TravelData()
    return runCatching { json.decodeFromString<TravelData>(raw) }.getOrElse { TravelData() }
}

fun saveTravel(data: TravelData) {
    Storage.write("Travel", json.encodeToString(data))
}

fun reservationsFor(data: TravelData, tripId: Long): List<Reservation> =
    data.reservations.filter { it.tripId == tripId }
        .sortedWith(compareBy({ it.startDateTime.ifBlank { "9999" } }, { it.provider.lowercase() }))

// Estimated against what the reservations actually add up to.
//
// Foreign-currency bookings are converted through the Tools rates, but kept apart from
// the native total rather than folded silently into it. A rate is today's, and a booking
// was paid at whatever the rate was on the day — merging the two produces one confident
// number that was never true. So the totals report both: what was actually spent in the
// trip's own currency, and what the rest comes to at today's rate, labelled as such.
data class TripBudget(
    val estimate: Double,
    // Costs already in the trip's currency — real, not derived.
    val booked: Double,
    val paid: Double,
    val currency: String,
    // Foreign costs converted at today's rate, and what they were before converting.
    val convertedBooked: Double,
    val convertedPaid: Double,
    val foreign: Map<String, Double>,
    // Currencies with no rate loaded — counted nowhere, so they are named instead.
    val unconvertible: Set<String>,
) {
    val totalBooked: Double get() = booked + convertedBooked
    val totalPaid: Double get() = paid + convertedPaid
    val outstanding: Double get() = totalBooked - totalPaid
    val overEstimate: Boolean get() = estimate > 0 && totalBooked > estimate
    val hasForeign: Boolean get() = foreign.isNotEmpty()
}

fun tripBudget(data: TravelData, trip: Trip): TripBudget {
    val rs = reservationsFor(data, trip.id).filter { it.status != ReservationStatus.CANCELLED }
    val home = trip.currency.ifBlank { "USD" }

    val native = rs.filter { (it.currency.ifBlank { home }) == home }
    val abroad = rs.filter { (it.currency.ifBlank { home }) != home }

    var convBooked = 0.0
    var convPaid = 0.0
    val foreign = mutableMapOf<String, Double>()
    val stuck = mutableSetOf<String>()
    abroad.forEach { r ->
        val code = r.currency.ifBlank { home }
        foreign[code] = (foreign[code] ?: 0.0) + r.cost
        val c = CurrencyClient.convert(r.cost, code, home)
        if (c == null) {
            stuck += code
        } else {
            convBooked += c
            if (r.paid) convPaid += c
        }
    }

    return TripBudget(
        estimate = trip.budgetEstimate,
        booked = native.sumOf { it.cost },
        paid = native.filter { it.paid }.sumOf { it.cost },
        currency = home,
        convertedBooked = convBooked,
        convertedPaid = convPaid,
        foreign = foreign,
        unconvertible = stuck,
    )
}

// Places whose visit dates fall inside the trip, and bucket-list entries worth doing
// while there — the "what did I actually see" and "what should I plan" halves of §5.1's
// Places integration. Matching is by destination name, which is what the person typed.
data class TripPlaces(val visited: List<String>, val suggestions: List<String>)

fun tripPlaces(trip: Trip): TripPlaces {
    val start = trip.start()
    val end = trip.end() ?: start
    val data = loadPlaces()

    val visited = if (start == null || end == null) emptyList() else data.places.filter { p ->
        p.visitDates.any { d -> parseDateOrNull(d)?.let { it >= start && it <= end } == true }
    }.map { it.name }

    val where = trip.destinations.map { it.trim().lowercase() }.filter { it.isNotBlank() }
    fun matches(text: String): Boolean {
        val t = text.lowercase()
        return where.any { w -> t.contains(w) || w.contains(t) }
    }

    val wantToGo = data.places
        .filter { it.listType == "wantToGo" && (matches(it.name) || matches(it.address)) }
        .map { it.name }
    val bucket = data.bucket.filter { !it.done && matches(it.title) }.map { it.title }

    return TripPlaces(visited = visited.distinct(), suggestions = (wantToGo + bucket).distinct())
}

// Whole days from this date to that one; negative if it already passed.
private fun LocalDate.daysUntilCompat(other: LocalDate): Int =
    other.toEpochDays() - this.toEpochDays()
