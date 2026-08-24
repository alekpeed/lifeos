package com.alekpeed.lifeos.archive

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.calendar.DatedKind
import com.alekpeed.lifeos.calendar.datedItems
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.History
import com.alekpeed.lifeos.milestones.Milestone
import com.alekpeed.lifeos.milestones.MilestonesData
import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.milestones.saveMilestones
import com.alekpeed.lifeos.timecapsules.TimeCapsule
import com.alekpeed.lifeos.timecapsules.TimeCapsulesData
import com.alekpeed.lifeos.timecapsules.loadCapsules
import com.alekpeed.lifeos.timecapsules.saveCapsules
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// §12.1.4 — one shape for Archive's two single-moment records.
//
// The thing to protect is that this stayed a shape and did not become a merge: the two
// modules keep their own fields, their own storage and their own semantics, and the
// only thing shared is how a moment is read. So the tests check both that the shared
// view is right and that nothing about either record changed on disk.
class MomentTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private val when1 = today().plusDays(-3).toString()
    private val when2 = today().plusDays(5).toString()

    private fun seed() {
        saveMilestones(
            MilestonesData(
                listOf(Milestone(id = 1, title = "Ran a marathon", date = when1, notes = "in the rain", photoBlob = "blob-a")),
            ),
        )
        saveCapsules(
            TimeCapsulesData(
                listOf(TimeCapsule(id = 2, title = "For my 30th", body = "Hello future me", sealedUntil = when2, photoBlob = "blob-b")),
            ),
        )
    }

    @Test
    fun `a milestone reads through the shared shape without renaming its own fields`() {
        seed()
        val m = loadMilestones().milestones.single()
        assertEquals(when1, m.date)
        assertEquals("in the rain", m.note)
        // The module's own name for it is untouched.
        assertEquals("in the rain", m.notes)
        assertEquals(MomentKind.HAPPENED, m.kind)
        assertEquals("blob-a", m.photoBlob)
    }

    @Test
    fun `a capsule's moment is the day it opens, and its note is its body`() {
        seed()
        val c = loadCapsules().capsules.single()
        assertEquals(when2, c.date)
        assertEquals(when2, c.sealedUntil)
        assertEquals("Hello future me", c.note)
        assertEquals("Hello future me", c.body)
        assertEquals(MomentKind.OPENS, c.kind)
    }

    @Test
    fun `the shared view returns both, and each knows where it lives`() {
        seed()
        val moments = archiveMoments()
        assertEquals(setOf("Ran a marathon", "For my 30th"), moments.map { it.title }.toSet())
        assertEquals(
            setOf("milestones", "time-capsules"),
            moments.map { it.kind.moduleId }.toSet(),
        )
    }

    @Test
    fun `nothing about how either record is stored changed`() {
        // The interface is getters over existing fields, so the JSON must still hold
        // exactly what it held before — an added field would strand every saved record.
        seed()
        val milestone = Storage.read("Milestones")!!
        assertTrue(milestone.contains("\"notes\":\"in the rain\""), milestone)
        assertTrue(!milestone.contains("\"note\""), milestone)
        val capsule = Storage.read("Time Capsules")!!
        assertTrue(capsule.contains("\"body\":\"Hello future me\""), capsule)
        assertTrue(!capsule.contains("\"date\""), capsule)
    }

    @Test
    fun `both still reach the calendar, with their own kinds`() {
        seed()
        val items = datedItems(today().plusDays(-30), today().plusDays(30))
        val milestone = items.single { it.title == "Ran a marathon" }
        val capsule = items.single { it.title == "For my 30th" }
        assertEquals(DatedKind.EVENT, milestone.kind)
        assertEquals(DatedKind.UNSEAL, capsule.kind)
        assertEquals("opens", capsule.note)
        assertEquals("", milestone.note)
    }

    @Test
    fun `the calendar keys stay distinct between the two modules`() {
        // They share a numbering space now that one loop produces both; two records with
        // the same id must not collide into one row.
        saveMilestones(MilestonesData(listOf(Milestone(id = 7, title = "A", date = when1))))
        saveCapsules(TimeCapsulesData(listOf(TimeCapsule(id = 7, title = "B", body = "", sealedUntil = when1))))
        val keys = datedItems(today().plusDays(-30), today().plusDays(30))
            .filter { it.title == "A" || it.title == "B" }
            .map { it.key }
        assertEquals(2, keys.toSet().size)
    }

    @Test
    fun `a moment with no date is on no day`() {
        saveMilestones(MilestonesData(listOf(Milestone(id = 1, title = "Undated"))))
        assertTrue(datedItems(today().plusDays(-3650), today().plusDays(3650)).none { it.title == "Undated" })
        assertTrue(momentsOn("").isEmpty())
    }

    @Test
    fun `momentsOn finds both kinds on the same day`() {
        saveMilestones(MilestonesData(listOf(Milestone(id = 1, title = "A", date = when1))))
        saveCapsules(TimeCapsulesData(listOf(TimeCapsule(id = 2, title = "B", body = "", sealedUntil = when1))))
        assertEquals(setOf("A", "B"), momentsOn(when1).map { it.title }.toSet())
    }
}
