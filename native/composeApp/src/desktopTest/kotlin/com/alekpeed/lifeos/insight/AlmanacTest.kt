package com.alekpeed.lifeos.insight

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §7 D-4 — the floors, and the sample beside every figure.
//
// Both halves of D-4 fail silently if they regress. A floor that gets lowered to make
// a demo look better produces a confident decimal on six days of data; a sample size
// that stops being printed leaves the number looking exactly as authoritative as one
// drawn through a year. Neither shows up as a crash, so they are pinned here.
class AlmanacTest {

    private fun line(n: Int, slope: Double = 1.0): List<Pair<Double, Double>> =
        (0 until n).map { it.toDouble() to it * slope }

    @Test
    fun `the raised floors are the raised floors`() {
        // D-4's table, as merged. If one of these moves, the change was deliberate and
        // the doc moves with it.
        assertEquals(21, CORR_MIN)
        assertEquals(21, TREND_MIN)
        assertEquals(6, MONTHS_MIN)
        assertEquals(42, WEEKDAY_MIN_DAYS)
        assertEquals(6, LINREG_MIN)
        assertEquals(4, READING_MIN)
    }

    @Test
    fun `nothing fits on two points`() {
        // The defect D-4 named outright: a straight line through two points is not a
        // prediction, it is the line between them extended.
        assertNull(linregress(line(2)))
        assertNull(linregress(line(5)))
        assertNotNull(linregress(line(6)))
        assertNull(pearson(line(5)))
        assertNotNull(pearson(line(6)))
    }

    @Test
    fun `a perfect line reads as one, and its slope is right`() {
        val fit = linregress(line(10, slope = 2.0))
        assertNotNull(fit)
        assertTrue(abs(fit.slope - 2.0) < 1e-9)
        assertTrue(abs(fit.intercept) < 1e-9)
        assertTrue(abs(pearson(line(10, slope = 2.0))!! - 1.0) < 1e-9)
    }

    @Test
    fun `a flat series produces no correlation rather than zero`() {
        // Every y identical means the question has no answer, not that the answer is
        // "no relationship" — and a printed r = 0.0 would say the second thing.
        assertNull(pearson((0 until 30).map { it.toDouble() to 5.0 }))
        assertNull(linregress((0 until 30).map { 5.0 to it.toDouble() }))
    }

    @Test
    fun `strength says which direction as well as how much`() {
        assertEquals("strong positive", strength(0.8))
        assertEquals("strong negative", strength(-0.8))
        assertEquals("moderate positive", strength(0.4))
        assertEquals("weak negative", strength(-0.25))
        assertEquals("little positive", strength(0.05))
    }

    @Test
    fun `the sample reads like English beside the figure`() {
        assertEquals(" · 34 days", sample(34, "day"))
        assertEquals(" · 1 day", sample(1, "day"))
        assertEquals(" · 8 months", sample(8, "month"))
        assertEquals(" · 4 reading logs", sample(4, "reading log"))
    }

    @Test
    fun `a correlation padded with zeros says how many days were real`() {
        // A hundred sleep days of which five had a completed task is not a hundred-day
        // finding, and the second number is what lets a reader see that.
        assertEquals(" · 100 days, 5 with a task", sampleWithActive(100, 5, "day"))
    }

    @Test
    fun `a weekday claim carries the count behind it`() {
        // Six kept Mondays out of twelve is a different claim from six out of six.
        val perWd = intArrayOf(3, 9, 9, 9, 9, 9, 9)
        val gap = worstWeekday(perWd, spanDays = 84)
        assertNotNull(gap)
        assertEquals(0, gap.weekday)
        assertEquals("Monday", WEEKDAY_NAMES[gap.weekday])
        assertEquals(3, gap.kept)
        assertEquals(12, gap.elapsed)
    }

    @Test
    fun `a habit younger than the floor names no weekday at all`() {
        assertNull(worstWeekday(intArrayOf(1, 5, 5, 5, 5, 5, 5), spanDays = 41))
        assertNotNull(worstWeekday(intArrayOf(1, 5, 5, 5, 5, 5, 5), spanDays = 42))
    }

    @Test
    fun `a partial week has not produced another weekday to have skipped`() {
        // 45 days is six full weeks and three days: six Mondays, not seven.
        assertEquals(6, worstWeekday(intArrayOf(1, 9, 9, 9, 9, 9, 9), spanDays = 45)!!.elapsed)
    }
}
