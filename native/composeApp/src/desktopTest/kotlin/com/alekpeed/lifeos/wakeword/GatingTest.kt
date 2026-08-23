package com.alekpeed.lifeos.wakeword

import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.history.History
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §7 D-2 — when the wake word is allowed to listen.
//
// This is the only part of a microphone service that can be tested without a device,
// and it is the part where being wrong is expensive in a way nothing reports: a gate
// that never closes costs a day of battery and looks exactly like a gate that works.
class GatingTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private val plugged = DeviceState(charging = true, screenOn = false)
    private val awake = DeviceState(charging = false, screenOn = true)
    private val pocket = DeviceState(charging = false, screenOn = false)
    private val midday = LocalTime(13, 0)
    private val night = LocalTime(3, 0)

    private fun listens(gates: WakeGates, state: DeviceState, now: LocalTime = midday) =
        wakeDecision(gates, state, now).listening

    @Test
    fun `charging only listens on the charger and nowhere else`() {
        val g = WakeGates(power = WakePower.CHARGING)
        assertTrue(listens(g, plugged))
        assertFalse(listens(g, awake))
        assertFalse(listens(g, pocket))
    }

    @Test
    fun `charging or screen adds the phone you are holding`() {
        val g = WakeGates(power = WakePower.CHARGING_OR_SCREEN)
        assertTrue(listens(g, plugged))
        assertTrue(listens(g, awake))
        // A phone in a pocket is the case that costs all day and captures nothing.
        assertFalse(listens(g, pocket))
    }

    @Test
    fun `always means always, and that is the expensive one`() {
        val g = WakeGates(power = WakePower.ALWAYS)
        assertTrue(listens(g, pocket))
    }

    @Test
    fun `the hours gate outranks the power gate`() {
        // A phone charging overnight is the exact case the hours window exists for, so
        // it must not be the case that plugging in defeats it.
        val g = WakeGates(power = WakePower.ALWAYS)
        val d = wakeDecision(g, plugged, night)
        assertFalse(d.listening)
        assertEquals(WakeBlock.HOURS, d.block)
        assertEquals(LocalTime(7, 0), d.opensAt)
    }

    @Test
    fun `it says which gate closed, because the notification says so`() {
        assertEquals(WakeBlock.POWER, wakeDecision(WakeGates(), pocket, midday).block)
        assertEquals(WakeBlock.NONE, wakeDecision(WakeGates(), plugged, midday).block)
        assertTrue(blockReason(wakeDecision(WakeGates(), pocket, midday)).contains("plug in"))
        assertTrue(blockReason(wakeDecision(WakeGates(), plugged, night)).contains("07:00"))
        assertEquals("", blockReason(wakeDecision(WakeGates(), plugged, midday)))
    }

    @Test
    fun `a daytime window includes its start and excludes its end`() {
        val from = LocalTime(7, 0)
        val until = LocalTime(22, 0)
        assertTrue(inWindow(from, until, LocalTime(7, 0)))
        assertTrue(inWindow(from, until, LocalTime(21, 59)))
        assertFalse(inWindow(from, until, LocalTime(22, 0)))
        assertFalse(inWindow(from, until, LocalTime(6, 59)))
    }

    @Test
    fun `a window that runs past midnight is a window, not an empty one`() {
        // Somebody who works nights should be able to set 22:00–07:00 and have it mean
        // what it says rather than "never".
        val from = LocalTime(22, 0)
        val until = LocalTime(7, 0)
        assertTrue(inWindow(from, until, LocalTime(23, 30)))
        assertTrue(inWindow(from, until, LocalTime(2, 0)))
        assertFalse(inWindow(from, until, LocalTime(12, 0)))
    }

    @Test
    fun `a zero-width window reads as all day, not as never`() {
        // "07:00 to 07:00" is nobody's way of switching listening off — the switch
        // above it is, and reading it as "never" would silently disable the feature.
        assertTrue(inWindow(LocalTime(7, 0), LocalTime(7, 0), LocalTime(3, 0)))
        assertTrue(listens(WakeGates(from = LocalTime(7, 0), until = LocalTime(7, 0)), plugged, night))
    }

    @Test
    fun `the next flip is the far edge of wherever you are`() {
        val g = WakeGates(from = LocalTime(7, 0), until = LocalTime(22, 0))
        assertEquals(LocalTime(22, 0), nextHoursFlip(g, LocalTime(13, 0)))
        assertEquals(LocalTime(7, 0), nextHoursFlip(g, LocalTime(2, 0)))
        assertEquals(LocalTime(7, 0), nextHoursFlip(g, LocalTime(23, 0)))
    }

    @Test
    fun `with hours off there is nothing to schedule`() {
        assertNull(nextHoursFlip(WakeGates(hoursEnabled = false), midday))
        assertNull(nextHoursFlip(WakeGates(from = LocalTime(9, 0), until = LocalTime(9, 0)), midday))
    }

    @Test
    fun `gates survive being written and read back`() {
        val g = WakeGates(power = WakePower.CHARGING, hoursEnabled = false, from = LocalTime(6, 30), until = LocalTime(23, 15))
        saveWakeGates(g)
        assertEquals(g, loadWakeGates())
    }

    @Test
    fun `a phone that has never opened the screen is still gated`() {
        // The default has to be the gated one. Defaulting to "always" would mean every
        // existing install keeps the old always-on cost until somebody notices.
        val d = loadWakeGates()
        assertEquals(WakePower.CHARGING_OR_SCREEN, d.power)
        assertTrue(d.hoursEnabled)
        assertFalse(listens(d, pocket))
        assertFalse(listens(d, plugged, night))
    }

    @Test
    fun `nonsense in the store falls back rather than throwing`() {
        Storage.write("WakeGatePower", "banana")
        Storage.write("WakeGateFrom", "25:99")
        Storage.write("WakeGateUntil", "")
        val g = loadWakeGates()
        assertEquals(WakePower.CHARGING_OR_SCREEN, g.power)
        assertEquals(LocalTime(7, 0), g.from)
        assertEquals(LocalTime(22, 0), g.until)
    }
}
