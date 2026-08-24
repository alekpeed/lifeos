package com.alekpeed.lifeos.health

import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.data.minusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.History
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §11.2 — the mood log and the medication schedule.
//
// Both are small, and both have one way to be quietly wrong. A mood is a number the
// Almanac will correlate against, so it has to reach healthSeries under a name the
// engine reads and survive an Apple Health import that knows nothing about it. A
// medication log is a claim about whether somebody took their medicine, so a day
// nobody answered must never be counted as a day they missed.
class MoodAndMedsTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private fun ago(days: Int) = today().minusDays(days).toString()

    // ---- mood ---------------------------------------------------------------------

    @Test
    fun `a mood reaches the stats layer under the name the engine reads`() {
        saveHealth(HealthData(logs = listOf(DailyLog(date = ago(1), mood = 4, sleepHours = 7.5))))
        val points = healthSeries()
        assertEquals(4.0, points.single { it.metric == "Mood" }.value)
        assertEquals(ago(1), points.single { it.metric == "Mood" }.date)
        // And it does not disturb what was already there.
        assertEquals(7.5, points.single { it.metric == "Sleep" }.value)
    }

    @Test
    fun `a day with no mood contributes no mood point`() {
        // Not a zero. A mood of zero is not on the scale, and the correlation engine
        // would happily fit a line through it.
        saveHealth(HealthData(logs = listOf(DailyLog(date = ago(1), sleepHours = 8.0))))
        assertTrue(healthSeries().none { it.metric == "Mood" })
    }

    @Test
    fun `a row with only a mood on it is not empty`() {
        // isEmpty decides whether an imported day is worth writing; a mood-only day is
        // a real day and must not be discarded as blank.
        assertFalse(DailyLog(date = ago(0), mood = 3).isEmpty)
        assertTrue(DailyLog(date = ago(0)).isEmpty)
    }

    @Test
    fun `an import cannot wipe a mood it knows nothing about`() {
        // Apple Health has no mood field, so a merged day must keep the one you logged.
        val existing = listOf(DailyLog(date = ago(2), mood = 5, sleepHours = 6.0))
        val imported = listOf(DailyLog(date = ago(2), sleepHours = 7.0, waterOz = 64.0))
        val (merged, result) = mergeImportedDays(existing, imported)
        assertEquals(5, merged.single().mood)
        assertEquals(7.0, merged.single().sleepHours)
        assertEquals(1, result.updated)
    }

    @Test
    fun `the faces line up with the scale`() {
        assertEquals("😖", moodFace(1))
        assertEquals("😄", moodFace(5))
        // Off the scale is nothing, not the nearest face.
        assertEquals("", moodFace(0))
        assertEquals("", moodFace(6))
        assertEquals("", moodFace(null))
    }

    // ---- medications --------------------------------------------------------------

    private fun withMed(vararg log: MedEvent) =
        HealthData(medications = listOf(Medication(id = 1, name = "Thing", log = log.toList())))

    @Test
    fun `answering a day records it`() {
        val next = markMedication(withMed(), 1, ago(0), taken = true)
        assertEquals(true, medEventOn(next.medications.single(), ago(0))?.taken)
    }

    @Test
    fun `changing your mind corrects the day rather than adding a second one`() {
        var data = markMedication(withMed(), 1, ago(0), taken = false)
        data = markMedication(data, 1, ago(0), taken = true)
        assertEquals(1, data.medications.single().log.size)
        assertEquals(true, medEventOn(data.medications.single(), ago(0))?.taken)
    }

    @Test
    fun `a day nobody answered is missing, not missed`() {
        // The whole point. Two answered days out of thirty elapsed is 100% adherence on
        // what was answered — saying 7% would claim they skipped 28 doses they may well
        // have taken.
        val data = withMed(MedEvent(ago(1), true), MedEvent(ago(2), true))
        val a = adherence(data.medications.single(), ago(29), ago(0))
        assertEquals(2, a.answered)
        assertEquals(2, a.taken)
        assertEquals(100, a.percent)
        assertNull(medEventOn(data.medications.single(), ago(5)))
    }

    @Test
    fun `adherence counts the skips it was told about`() {
        val data = withMed(MedEvent(ago(1), true), MedEvent(ago(2), false), MedEvent(ago(3), true))
        val a = adherence(data.medications.single(), ago(29), ago(0))
        assertEquals(3, a.answered)
        assertEquals(2, a.taken)
        assertEquals(66, a.percent)
    }

    @Test
    fun `an unanswered medication has no percentage at all`() {
        // Null rather than zero: nothing is known yet, and a 0% on a new medication
        // would be an accusation.
        assertNull(adherence(withMed().medications.single(), ago(29), ago(0)).percent)
    }

    @Test
    fun `the window is a window`() {
        val data = withMed(MedEvent(ago(1), true), MedEvent(ago(100), true))
        assertEquals(1, adherence(data.medications.single(), ago(29), ago(0)).answered)
    }

    @Test
    fun `marking a medication that has gone changes nothing`() {
        val data = withMed()
        assertEquals(data, markMedication(data, 99, ago(0), taken = true))
    }

    @Test
    fun `medications round-trip through the store`() {
        saveHealth(withMed(MedEvent(ago(1), true)))
        val back = loadHealth().medications.single()
        assertEquals("Thing", back.name)
        assertEquals(listOf(MedEvent(ago(1), true)), back.log)
    }
}
