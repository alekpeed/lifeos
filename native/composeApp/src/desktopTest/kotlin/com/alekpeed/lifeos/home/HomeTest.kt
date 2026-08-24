package com.alekpeed.lifeos.home

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.core.isBackupKey
import com.alekpeed.lifeos.history.History
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §13.3 — the Home Assistant bridge.
//
// Nothing here can be exercised against a real hub from a test, so what is tested is
// everything that happens either side of the wire: the address somebody typed, the JSON
// a hub sends back, which service call flips which kind of thing, and the order the list
// comes out in. Those are the parts that would fail silently — a wrong service name
// produces a 400 nobody reads, and a mis-parsed state produces a switch that lies about
// whether a light is on.
class HomeTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    // ---- the address ----------------------------------------------------------------

    @Test
    fun `every way somebody writes their hub means the same hub`() {
        val expected = "http://192.168.1.40:8123"
        assertEquals(expected, normalizeBaseUrl("192.168.1.40:8123"))
        assertEquals(expected, normalizeBaseUrl("http://192.168.1.40:8123"))
        assertEquals(expected, normalizeBaseUrl("http://192.168.1.40:8123/"))
        assertEquals(expected, normalizeBaseUrl("  http://192.168.1.40:8123/api  "))
        assertEquals(expected, normalizeBaseUrl("http://192.168.1.40:8123/lovelace"))
    }

    @Test
    fun `a bare hostname gets the default port, an explicit one is left alone`() {
        assertEquals("http://homeassistant.local:8123", normalizeBaseUrl("homeassistant.local"))
        assertEquals("http://ha.lan:9000", normalizeBaseUrl("ha.lan:9000"))
    }

    @Test
    fun `https is not downgraded`() {
        // A Nabu Casa or Tailscale address is https, and rewriting it to http would send
        // a long-lived token in the clear.
        assertEquals("https://abc.ui.nabu.casa", normalizeBaseUrl("https://abc.ui.nabu.casa"))
    }

    @Test
    fun `nothing typed is nothing stored`() {
        assertEquals("", normalizeBaseUrl(""))
        assertEquals("", normalizeBaseUrl("   "))
        assertFalse(HomeConfig("", "token").configured)
        assertFalse(HomeConfig("http://h:8123", "").configured)
        assertTrue(HomeConfig("http://h:8123", "token").configured)
    }

    @Test
    fun `the token never enters a backup`() {
        // A long-lived Home Assistant token is a key to the locks and cameras in a
        // house, and a backup is shared through the OS share sheet on purpose.
        assertFalse(isBackupKey("HomeToken"))
        // The address is not a secret and stays, so a restore is not a re-setup.
        assertTrue(isBackupKey("HomeUrl"))
    }

    // ---- what the hub says ------------------------------------------------------------

    private val states = """
        [
          {"entity_id":"light.porch","state":"on","attributes":{"friendly_name":"Porch light","brightness":128}},
          {"entity_id":"light.hall_lamp","state":"off","attributes":{}},
          {"entity_id":"switch.kettle","state":"off","attributes":{"friendly_name":"Kettle"}},
          {"entity_id":"sensor.outside_temp","state":"11.4","attributes":{"friendly_name":"Outside","unit_of_measurement":"°C"}},
          {"entity_id":"scene.night","state":"unknown","attributes":{"friendly_name":"Night mode"}},
          {"entity_id":"light.shed","state":"unavailable","attributes":{"friendly_name":"Shed"}}
        ]
    """.trimIndent()

    @Test
    fun `a hub's answer becomes a list`() {
        val list = parseStates(states)
        assertEquals(6, list.size)
        val porch = list.first { it.entityId == "light.porch" }
        assertEquals("Porch light", porch.name)
        assertTrue(porch.on)
        assertEquals("light", porch.domain)
    }

    @Test
    fun `brightness arrives as a percentage, not as 0 to 255`() {
        // Every conversion between the two is a place to be off by one, so it happens
        // once, here, on the way in.
        assertEquals(50, parseStates(states).first { it.entityId == "light.porch" }.brightness)
        assertNull(parseStates(states).first { it.entityId == "switch.kettle" }.brightness)
    }

    @Test
    fun `an unnamed entity falls back to something recognisable`() {
        // "(unnamed)" repeated forty times is not a list. "Hall lamp" is.
        assertEquals("Hall lamp", parseStates(states).first { it.entityId == "light.hall_lamp" }.name)
        assertEquals("Outside temp", prettyId("sensor.outside_temp"))
    }

    @Test
    fun `not answering is not the same as off`() {
        // The difference between "off" and "the hub has lost this device" is the whole
        // reason to look at the screen.
        val shed = parseStates(states).first { it.entityId == "light.shed" }
        assertTrue(shed.unavailable)
        assertFalse(shed.on)
        assertFalse(parseStates(states).first { it.entityId == "switch.kettle" }.unavailable)
    }

    @Test
    fun `a sensor keeps its reading and its unit`() {
        val t = parseStates(states).first { it.entityId == "sensor.outside_temp" }
        assertEquals("11.4", t.state)
        assertEquals("°C", t.unit)
        assertFalse(t.domain in CONTROLLABLE)
    }

    @Test
    fun `a hub that answers with rubbish costs a list, not a crash`() {
        for (bad in listOf("", "not json", "{}", "null", "[1,2,3]", """[{"state":"on"}]""", """[{"entity_id":"broken"}]""")) {
            assertEquals(emptyList(), parseStates(bad), "for: $bad")
        }
    }

    @Test
    fun `one malformed entity does not cost the others`() {
        val mixed = """[{"entity_id":"nope"},{"entity_id":"light.good","state":"on","attributes":{}}]"""
        assertEquals(listOf("light.good"), parseStates(mixed).map { it.entityId })
    }

    // ---- acting on it -------------------------------------------------------------------

    @Test
    fun `lights switches and fans all answer to one service`() {
        assertEquals("homeassistant" to "turn_on", serviceFor("light.porch", true))
        assertEquals("homeassistant" to "turn_off", serviceFor("switch.kettle", false))
        assertEquals("homeassistant" to "turn_on", serviceFor("fan.office", true))
    }

    @Test
    fun `things with their own verbs get their own verbs`() {
        // Calling homeassistant.turn_on on a lock is a 400 nobody reads.
        assertEquals("lock" to "unlock", serviceFor("lock.front", true))
        assertEquals("lock" to "lock", serviceFor("lock.front", false))
        assertEquals("cover" to "open_cover", serviceFor("cover.garage", true))
        assertEquals("cover" to "close_cover", serviceFor("cover.garage", false))
        assertEquals("media_player" to "media_pause", serviceFor("media_player.tv", false))
    }

    @Test
    fun `a scene is run, never turned off`() {
        assertEquals("scene" to "turn_on", serviceFor("scene.night", true))
        assertEquals("scene" to "turn_on", serviceFor("scene.night", false))
        assertEquals("script" to "turn_on", serviceFor("script.bedtime", true))
    }

    @Test
    fun `nothing is offered for something that cannot be switched`() {
        assertNull(serviceFor("sensor.outside_temp", true))
        assertNull(serviceFor("weather.home", true))
        assertNull(serviceFor("notanentity", true))
    }

    // ---- the order of the list ------------------------------------------------------

    @Test
    fun `favourites first, then what you can act on, then the readings`() {
        // A house has hundreds of entities and four you touch.
        val ordered = ordered(parseStates(states), setOf("switch.kettle"))
        assertEquals("switch.kettle", ordered.first().entityId)
        assertEquals("sensor.outside_temp", ordered.last().entityId)
    }

    @Test
    fun `favourites survive being written and read back`() {
        assertTrue(favourites().isEmpty())
        toggleFavourite("light.porch")
        assertEquals(setOf("light.porch"), favourites())
        toggleFavourite("switch.kettle")
        assertEquals(setOf("light.porch", "switch.kettle"), favourites())
        toggleFavourite("light.porch")
        assertEquals(setOf("switch.kettle"), favourites())
    }

    @Test
    fun `no arrival scene means the geofence does what it always did`() {
        // The default for something that turns lights on in a house is off.
        assertEquals("", arrivalScene())
        setArrivalScene("scene.night")
        assertEquals("scene.night", arrivalScene())
        setArrivalScene("")
        assertEquals("", arrivalScene())
    }

    @Test
    fun `the config round-trips through the store, normalized`() {
        saveHomeConfig("  192.168.1.40:8123/  ", "  abc123  ")
        val cfg = loadHomeConfig()
        assertEquals("http://192.168.1.40:8123", cfg.baseUrl)
        assertEquals("abc123", cfg.token)
        assertTrue(cfg.configured)
        assertEquals("http://192.168.1.40:8123", Storage.read("HomeUrl"))
    }
}
