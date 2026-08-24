package com.alekpeed.lifeos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The home screen's one decision: what search returns.
//
// A launcher fails quietly — drop a module out of the results over a stray capital and
// nobody files a bug, they just stop trusting the box and go back to hunting through
// domains. So the part that decides what appears is pure, and tested.
//
// The pin and recent tests that used to live here went with the rows themselves: with a
// search box and all eight domains already on the screen, they were a third and fourth
// route to the same place.
class HomeLayoutTest {

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
        // Empty returns nothing rather than everything: the screen shows the domain list
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
}
