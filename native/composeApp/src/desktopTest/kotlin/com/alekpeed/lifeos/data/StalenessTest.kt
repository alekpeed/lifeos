package com.alekpeed.lifeos.data

import com.alekpeed.lifeos.rabbitholes.COLD_AFTER_DAYS
import com.alekpeed.lifeos.rabbitholes.RabbitHole
import com.alekpeed.lifeos.rabbitholes.daysCold
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §12.1.2 — one staleness computation, four callers.
//
// The failure this prevents is quiet: two modules answering "how long since?" with
// different numbers for the same record because each grew its own arithmetic. What has
// to hold is that the edges agree — an unstamped record is not fresh, a future date is
// not negative neglect, and the unknowns sort last rather than to the top of a
// worst-first list.
class StalenessTest {

    private val rule = StaleRule(staleAfter = 7, neglectedAfter = 30)
    private val anchor = today()

    private fun ago(days: Int) = anchor.minusDays(days).toString()

    @Test
    fun `days since counts whole days back to the stamp`() {
        assertEquals(0, daysSinceDate(ago(0), anchor))
        assertEquals(1, daysSinceDate(ago(1), anchor))
        assertEquals(400, daysSinceDate(ago(400), anchor))
    }

    @Test
    fun `no stamp is not the same as touched today`() {
        // The commonest case on a fresh install. Reading it as zero would make a neglect
        // dashboard render entirely green and mean nothing.
        assertNull(daysSinceDate(null))
        assertNull(daysSinceDate(""))
        assertNull(daysSinceDate("   "))
        assertNull(daysSinceDate("last tuesday"))
        assertEquals(StaleLevel.UNKNOWN, levelFor(null, rule))
    }

    @Test
    fun `a date in the future is a clock skew, not negative neglect`() {
        assertEquals(0, daysSinceDate(anchor.plusDays(5).toString(), anchor))
    }

    @Test
    fun `millis and dates answer the same question the same way`() {
        val day = 86_400_000L
        val now = 1_700_000_000_000L
        assertEquals(0, daysSinceMillis(now, now))
        assertEquals(3, daysSinceMillis(now - 3 * day, now))
        assertNull(daysSinceMillis(null, now))
        // A timestamp from the future — two devices, one clock off — clamps rather than
        // going negative, exactly as the date path does.
        assertEquals(0, daysSinceMillis(now + 5 * day, now))
    }

    @Test
    fun `the thresholds are inclusive at the bottom edge`() {
        assertEquals(StaleLevel.FRESH, levelFor(6, rule))
        assertEquals(StaleLevel.STALE, levelFor(7, rule))
        assertEquals(StaleLevel.STALE, levelFor(29, rule))
        assertEquals(StaleLevel.NEGLECTED, levelFor(30, rule))
    }

    @Test
    fun `stale and neglected are both stale, and only one is neglected`() {
        assertFalse(stalenessOf(3, rule).isStale)
        assertTrue(stalenessOf(10, rule).isStale)
        assertFalse(stalenessOf(10, rule).isNeglected)
        assertTrue(stalenessOf(90, rule).isNeglected)
        assertTrue(stalenessOf(90, rule).isStale)
        // Unknown is not stale. It is unknown, and a nag built on it would be a guess.
        assertFalse(stalenessOf(null, rule).isStale)
        assertFalse(stalenessOf(null, rule).known)
    }

    @Test
    fun `the label reads the same wherever it appears`() {
        assertEquals("today", agoLabel(0))
        assertEquals("1 day ago", agoLabel(1))
        assertEquals("34 days ago", agoLabel(34))
        assertEquals("no timestamp yet", agoLabel(null))
        assertEquals("never spoken", agoLabel(null, unknown = "never spoken"))
    }

    @Test
    fun `worst first puts the questions after the answers`() {
        val items = listOf("a" to 3, "b" to null, "c" to 90, "d" to 12)
        assertEquals(listOf("c", "d", "a", "b"), worstFirst(items) { it.second }.map { it.first })
    }

    @Test
    fun `worst first on an empty or all-unknown list does not throw`() {
        assertTrue(worstFirst(emptyList<Pair<String, Int?>>()) { it.second }.isEmpty())
        val unknowns = listOf("a" to null, "b" to null)
        assertEquals(2, worstFirst(unknowns) { it.second }.size)
    }

    @Test
    fun `stalenessSince reads a stored date straight through`() {
        assertEquals(StaleLevel.NEGLECTED, stalenessSince(ago(45), rule, anchor).level)
        assertEquals(45, stalenessSince(ago(45), rule, anchor).days)
        assertEquals(StaleLevel.UNKNOWN, stalenessSince("", rule, anchor).level)
    }

    // ---- the callers ----------------------------------------------------------------

    @Test
    fun `a rabbit hole counts cold days through the shared utility`() {
        val hole = RabbitHole(id = 1, topic = "t", startedDate = ago(30), touchedDate = ago(9))
        // The touched date wins over the started date — every edit stamps it, so
        // "untouched for N days" means what it says.
        assertEquals(9, daysCold(hole))
        assertEquals(30, daysCold(hole.copy(touchedDate = "")))
        assertNull(daysCold(hole.copy(startedDate = "", touchedDate = "")))
    }

    @Test
    fun `a thread only reads cold once it is past this module's own threshold`() {
        // The thresholds differ per domain on purpose — three weeks is a cold thread,
        // sixty days is an unused subscription. Only the arithmetic is shared.
        val hole = RabbitHole(id = 1, topic = "t", startedDate = ago(20), touchedDate = ago(20))
        assertFalse(daysCold(hole)!! >= COLD_AFTER_DAYS)
        assertTrue(daysCold(hole.copy(touchedDate = ago(21)))!! >= COLD_AFTER_DAYS)
    }
}
