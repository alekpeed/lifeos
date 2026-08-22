package com.alekpeed.lifeos.travel

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.attach.Attachment
import com.alekpeed.lifeos.data.parseDateOrNull
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

// Estimated against what the reservations actually add up to. Mixed currencies are
// reported rather than converted: converting silently at today's rate would state a
// number the trip never cost.
data class TripBudget(
    val estimate: Double,
    val booked: Double,
    val paid: Double,
    val currency: String,
    val mixedCurrencies: Set<String>,
) {
    val outstanding: Double get() = booked - paid
    val overEstimate: Boolean get() = estimate > 0 && booked > estimate
}

fun tripBudget(data: TravelData, trip: Trip): TripBudget {
    val rs = reservationsFor(data, trip.id).filter { it.status != ReservationStatus.CANCELLED }
    val others = rs.map { it.currency.ifBlank { trip.currency } }.filter { it != trip.currency }.toSet()
    val inTripCurrency = rs.filter { (it.currency.ifBlank { trip.currency }) == trip.currency }
    return TripBudget(
        estimate = trip.budgetEstimate,
        booked = inTripCurrency.sumOf { it.cost },
        paid = inTripCurrency.filter { it.paid }.sumOf { it.cost },
        currency = trip.currency,
        mixedCurrencies = others,
    )
}

// Whole days from this date to that one; negative if it already passed.
private fun LocalDate.daysUntilCompat(other: LocalDate): Int =
    other.toEpochDays() - this.toEpochDays()
