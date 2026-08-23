package com.alekpeed.lifeos.timecapsules

import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.History
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// §5.4. The module's one job is to bring a note back years later, and both surfacing
// mechanisms hang on being able to tell an unopened capsule from one already read — get
// that wrong and they either nag forever or go quiet at the moment they matter.
class CapsuleSurfacingTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private fun capsule(id: Long, days: Int, readAt: String = "") = TimeCapsule(
        id = id,
        title = "For later",
        body = "hello from the past",
        sealedUntil = today().plusDays(days).toString(),
        createdAt = today().toString(),
        readAt = readAt,
    )

    @Test
    fun `a capsule opens on its date, not the day after`() {
        assertTrue(isSealed(capsule(1, days = 1)))
        assertFalse(isSealed(capsule(2, days = 0)), "the day it names is the day it opens")
        assertFalse(isSealed(capsule(3, days = -30)))
    }

    @Test
    fun `unread means opened and not yet looked at`() {
        assertFalse(isUnread(capsule(1, days = 5)), "still sealed is not waiting for you")
        assertTrue(isUnread(capsule(2, days = -1)))
        assertFalse(isUnread(capsule(3, days = -1, readAt = today().toString())))
    }

    @Test
    fun `the surfacing list is the unread ones, oldest first`() {
        saveCapsules(
            TimeCapsulesData(
                listOf(
                    capsule(1, days = -2),
                    capsule(2, days = -30),
                    capsule(3, days = 10),
                    capsule(4, days = -5, readAt = today().toString()),
                ),
            ),
        )
        assertEquals(listOf(2L, 1L), unreadCapsules().map { it.id })
        assertEquals(2, unreadCapsuleCount())
    }

    @Test
    fun `reading one resolves it, which is what stops both mechanisms nagging`() {
        val data = TimeCapsulesData(listOf(capsule(1, days = -1)))
        assertEquals(1, unreadCapsules(data).size)

        val after = markCapsuleRead(data, 1)
        assertEquals(today().toString(), after.capsules.single().readAt)
        assertTrue(unreadCapsules(after).isEmpty())
    }

    @Test
    fun `reading a second time does not overwrite when it was first read`() {
        val longAgo = today().plusDays(-40).toString()
        val data = TimeCapsulesData(listOf(capsule(1, days = -60, readAt = longAgo)))
        assertEquals(longAgo, markCapsuleRead(data, 1).capsules.single().readAt)
    }

    @Test
    fun `alarm ids are stable per capsule and clear of Finance's range`() {
        assertEquals(capsuleReminderId(7), capsuleReminderId(7))
        assertTrue(capsuleReminderId(1) != capsuleReminderId(2))
        // Bills hash their name into the ordinary int range; capsules sit above 900,000
        // so the two modules cannot land on the same alarm slot.
        assertTrue(capsuleReminderId(0) >= 900_000)
        assertTrue(capsuleReminderId(89_999) < 1_000_000)
    }

    @Test
    fun `a capsule written before readAt existed still decodes, and reads as unread`() {
        com.alekpeed.lifeos.Storage.write(
            "Time Capsules",
            """{"capsules":[{"id":1,"title":"Old","body":"b","sealedUntil":"${today().plusDays(-3)}",
               "createdAt":"2020-01-01","photoBlob":""}]}""".trimIndent(),
        )
        val c = loadCapsules().capsules.single()
        assertEquals("Old", c.title)
        assertEquals("", c.readAt)
        assertTrue(isUnread(c), "an old capsule that has opened should surface, not stay silent")
    }

    @Test
    fun `the old flat text store still migrates`() {
        com.alekpeed.lifeos.Storage.write("Time Capsules", "A note\t${today().plusDays(-1)}\n")
        val c = loadCapsules().capsules.single()
        assertEquals("A note", c.body)
        assertTrue(isUnread(c))
    }
}
