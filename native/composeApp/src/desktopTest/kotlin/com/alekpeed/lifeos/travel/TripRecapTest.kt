package com.alekpeed.lifeos.travel

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.History
import com.alekpeed.lifeos.photos.Album
import com.alekpeed.lifeos.photos.Caption
import com.alekpeed.lifeos.photos.PhotosData
import com.alekpeed.lifeos.photos.savePhotos
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// §11.6. The recap's promise is that every number in it came from a record — the model
// is only ever handed this list, so if a stat is wrong or missing, the prose is too.
class TripRecapTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private fun pastTrip() = Trip(
        id = 1,
        name = "Iceland",
        destinations = listOf("Reykjavik"),
        startDate = today().plusDays(-10).toString(),
        endDate = today().plusDays(-3).toString(),
        budgetEstimate = 2000.0,
        currency = "USD",
        albumId = 5,
    )

    @Test
    fun `a recap only appears once the trip is over`() {
        assertTrue(tripIsOver(pastTrip()))
        assertFalse(tripIsOver(pastTrip().copy(endDate = today().plusDays(3).toString())))
        // Under way today is not over.
        assertFalse(tripIsOver(pastTrip().copy(startDate = today().toString(), endDate = today().toString())))
        // No dates at all: nothing to recap.
        assertFalse(tripIsOver(Trip(9, "Someday")))
    }

    @Test
    fun `days away counts both end days`() {
        assertEquals(8, tripRecap(pastTrip(), TravelData(listOf(pastTrip()))).days)
    }

    @Test
    fun `photos come from the linked album and are named`() {
        savePhotos(
            PhotosData(
                listOf(Album(5, "Iceland 2026", captions = listOf(Caption(1, "a"), Caption(2, "b")))),
            ),
        )
        val r = tripRecap(pastTrip(), TravelData(listOf(pastTrip())))
        assertEquals(2, r.photos)
        assertEquals("Iceland 2026", r.albumName)
        assertTrue(tripRecapStats(r).any { it.first == "Photos" && it.second == "2 in Iceland 2026" })
    }

    @Test
    fun `a cancelled booking is not counted or charged for`() {
        val trip = pastTrip()
        val data = TravelData(
            trips = listOf(trip),
            reservations = listOf(
                Reservation(1, 1, type = ReservationType.FLIGHT, cost = 500.0, currency = "USD"),
                Reservation(2, 1, type = ReservationType.LODGING, cost = 900.0, currency = "USD"),
                Reservation(
                    3, 1, type = ReservationType.TOUR, cost = 300.0, currency = "USD",
                    status = ReservationStatus.CANCELLED,
                ),
            ),
        )
        val r = tripRecap(trip, data)
        assertEquals(2, r.bookings)
        assertEquals(1400.0, r.budget.totalBooked)
        assertTrue(r.bookingsByType.any { it == "Flights" to 1 })
        assertTrue(r.bookingsByType.none { it.first == "Tours" })
    }

    @Test
    fun `under budget reads as under, not as a negative overspend`() {
        val trip = pastTrip()
        val data = TravelData(
            trips = listOf(trip),
            reservations = listOf(Reservation(1, 1, cost = 1400.0, currency = "USD")),
        )
        val stats = tripRecapStats(tripRecap(trip, data))
        assertTrue(stats.any { it.first == "Under budget by" && it.second == "USD 600.0" })
        assertTrue(stats.none { it.first == "Over budget by" })
    }

    @Test
    fun `places visited are the ones dated inside the trip`() {
        Storage.write(
            "Places",
            """{"places":[
              {"id":1,"name":"Blue Lagoon","visitDates":["${today().plusDays(-5)}"]},
              {"id":2,"name":"Somewhere else","visitDates":["${today().plusDays(-100)}"]}
            ]}""".trimIndent(),
        )
        val r = tripRecap(pastTrip(), TravelData(listOf(pastTrip())))
        assertEquals(listOf("Blue Lagoon"), r.placesVisited)
    }

    @Test
    fun `a trip with nothing logged says so rather than inventing a recap`() {
        val r = tripRecap(pastTrip(), TravelData(listOf(pastTrip())))
        assertFalse(r.hasAnything)
    }

    @Test
    fun `the model is handed the stats and nothing else`() {
        val trip = pastTrip()
        val data = TravelData(listOf(trip), listOf(Reservation(1, 1, cost = 100.0, currency = "USD")))
        val context = buildTripRecapContext(tripRecap(trip, data))

        assertTrue(context.contains("Trip: Iceland"))
        assertTrue(context.contains("Destinations: Reykjavik"))
        assertTrue(context.contains("Spent: USD 100.0"))
        // No stat, no line — the prompt cannot mention what it was never given.
        assertFalse(context.contains("Travelled with"))
    }

    @Test
    fun `narratives are kept per trip`() {
        saveTripNarrative(1, "Iceland was cold.")
        saveTripNarrative(2, "Lisbon was not.")
        assertEquals("Iceland was cold.", tripNarrative(1))
        assertEquals("Lisbon was not.", tripNarrative(2))
        assertEquals("", tripNarrative(3))
    }
}
