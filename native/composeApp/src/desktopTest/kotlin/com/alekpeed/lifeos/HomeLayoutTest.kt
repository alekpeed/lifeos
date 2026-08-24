package com.alekpeed.lifeos

import com.alekpeed.lifeos.history.History
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The home screen's decisions.
//
// A launcher fails quietly: it loses a pin, or drops a module out of search over a stray
// capital, and nobody files a bug — they just stop trusting the screen and go back to
// hunting through domains. So the parts that decide what appears are pure, and tested.
class HomeLayoutTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private fun mod(id: String, label: String, group: String = "Operations", ready: Boolean = true) =
        Module(id = id, icon = "●", label = label, group = group, ready = ready) {}

    private val registry = listOf(
        mod("today", "Today"),
        mod("tasks", "Tasks"),
        mod("time-capsules", "Time Capsules", "Archive"),
        mod("rabbit-holes", "Rabbit Holes", "Discovery"),
        mod("finance", "Finance", "Management"),
        mod("vault", "Vault", "System"),
    )

    // ---- search ---------------------------------------------------------------------

    @Test
    fun `search ignores case and matches anywhere in the name`() {
        assertEquals(listOf("Tasks"), searchModules(registry, "ASK").map { it.label })
        assertEquals(listOf("Finance"), searchModules(registry, "nan").map { it.label })
    }

    @Test
    fun `what starts with what you typed comes first`() {
        // "ta" should put Tasks above Time Capsules and Rabbit Holes, both of which
        // contain it. A launcher that ranks by storage order makes you read the list.
        assertEquals("Tasks", searchModules(registry, "ta").first().label)
    }

    @Test
    fun `a domain name finds everything in it`() {
        // "arch" is a reasonable thing to type when you know roughly where something
        // lives but not what it is called.
        assertEquals(listOf("Time Capsules"), searchModules(registry, "arch").map { it.label })
    }

    @Test
    fun `an empty query is not a search`() {
        // Empty returns nothing rather than everything: the screen shows pins and domains
        // in that state, and returning all forty-one would render both.
        assertTrue(searchModules(registry, "").isEmpty())
        assertTrue(searchModules(registry, "   ").isEmpty())
    }

    @Test
    fun `nothing matching is nothing, not a guess`() {
        // Not fuzzy on purpose. A launcher that guesses shows you the wrong thing
        // confidently, which is worse than showing you nothing.
        assertTrue(searchModules(registry, "zzzz").isEmpty())
        assertFalse(moduleMatches(mod("tasks", "Tasks"), "tsks"))
    }

    // ---- pins -----------------------------------------------------------------------

    @Test
    fun `an install that has never touched this gets the six it always had`() {
        // The defaults are the previous hardcoded list, so upgrading does not rearrange
        // somebody's home screen underneath them.
        assertEquals(DEFAULT_PINS, loadPins())
    }

    @Test
    fun `pins survive being written and read back`() {
        savePins(listOf("vault", "finance"))
        assertEquals(listOf("vault", "finance"), loadPins())
    }

    @Test
    fun `unpinning the last one leaves it empty rather than restoring the defaults`() {
        // The bug this prevents: an empty list reading as "never set", so the six defaults
        // come back on the next open and the app appears to argue with you.
        savePins(emptyList())
        assertTrue(loadPins().isEmpty())
    }

    @Test
    fun `a new pin goes to the end`() {
        // The row you have learned the shape of should not reshuffle when you add one.
        assertEquals(listOf("a", "b", "c"), togglePin(listOf("a", "b"), "c"))
        assertEquals(listOf("a", "c"), togglePin(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun `a pin for a module that no longer exists simply disappears`() {
        // Modules have been removed twice this month. A stale pin must not leave a gap or
        // take the screen down with it.
        savePins(listOf("tasks", "notifications", "vault"))
        assertEquals(listOf("Tasks", "Vault"), pinnedModules(registry).map { it.label })
    }

    @Test
    fun `pins keep the order they were set in, not the registry's`() {
        savePins(listOf("vault", "today"))
        assertEquals(listOf("Vault", "Today"), pinnedModules(registry).map { it.label })
    }

    // ---- recents --------------------------------------------------------------------

    @Test
    fun `the last thing opened is first`() {
        noteOpened("tasks")
        noteOpened("vault")
        assertEquals(listOf("vault", "tasks"), recentIds())
    }

    @Test
    fun `opening something again moves it rather than repeating it`() {
        noteOpened("tasks")
        noteOpened("vault")
        noteOpened("tasks")
        assertEquals(listOf("tasks", "vault"), recentIds())
    }

    @Test
    fun `recents are capped`() {
        listOf("a", "b", "c", "d", "e", "f", "g", "h").forEach { noteOpened(it) }
        assertEquals(6, recentIds().size)
        assertEquals("h", recentIds().first())
    }

    @Test
    fun `recents do not repeat what is already pinned`() {
        // A row that duplicates the row above it is half a screen saying nothing.
        savePins(listOf("tasks"))
        noteOpened("tasks")
        noteOpened("vault")
        assertEquals(listOf("Vault"), recentModules(registry).map { it.label })
    }

    @Test
    fun `a recent for a module that has gone is dropped`() {
        noteOpened("notifications")
        noteOpened("vault")
        assertEquals(listOf("Vault"), recentModules(registry, pins = emptyList()).map { it.label })
    }

    @Test
    fun `an empty id is not an open`() {
        noteOpened("tasks")
        noteOpened("")
        assertEquals(listOf("tasks"), recentIds())
    }
}
