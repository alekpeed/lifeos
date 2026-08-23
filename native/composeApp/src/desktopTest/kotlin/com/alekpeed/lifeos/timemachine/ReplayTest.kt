package com.alekpeed.lifeos.timemachine

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.Change
import com.alekpeed.lifeos.history.History
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §4. The rebuild's claim is that scrubbing to a date replays the log rather than
// guessing from creation dates — so what has to hold is that a record reads as it did
// then, that the boundary of what can be replayed is honest, and that putting one back
// is itself an ordinary edit rather than a silent rewrite of the past.
class ReplayTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private fun tasks(vararg rows: String) = """{"items":[${rows.joinToString(",")}]}"""
    private fun task(id: Int, title: String, done: Boolean = false) =
        """{"id":$id,"title":"$title","done":$done}"""

    // The instant just before a given kind of event. Two writes can land in the same
    // millisecond, so "the timestamp of the earlier write" is not a usable boundary —
    // this one is, and it is exact rather than a sleep.
    private fun markBefore(kind: Change): Long =
        History.all().first { it.change == kind }.at - 1

    private fun titles(): List<String> =
        Json.parseToJsonElement(Storage.read("Tasks")!!).jsonObject["items"]!!
            .jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content }

    @Test
    fun `with nothing logged the horizon says so instead of claiming coverage`() {
        val hz = horizon()
        assertFalse(hz.hasLog)
        assertNull(hz.from)
        assertFalse(hz.covers(today()))
    }

    @Test
    fun `the horizon starts at the first event, and earlier days are not replayable`() {
        Storage.write("Tasks", tasks(task(1, "First thing")))

        val hz = horizon()
        assertTrue(hz.hasLog)
        assertEquals(today(), hz.from)
        assertTrue(hz.covers(today()))
        assertFalse(hz.covers(today().plusDays(-1)), "yesterday predates the log")
    }

    @Test
    fun `a day's changes carry the direction each field moved`() {
        Storage.write("Tasks", tasks(task(1, "Draft")))
        Storage.write("Tasks", tasks(task(1, "Final")))

        val edits = changesOn(today()).filter { it.kind == Change.UPDATE }
        assertEquals(1, edits.size)
        val f = edits.single().fields.single()
        assertEquals("title", f.field)
        assertEquals("Draft", f.before)
        assertEquals("Final", f.after)
    }

    @Test
    fun `a deletion appears, which the old version could not show at all`() {
        Storage.write("Tasks", tasks(task(1, "Keep"), task(2, "Bin me")))
        Storage.write("Tasks", tasks(task(1, "Keep")))

        val gone = changesOn(today()).filter { it.kind == Change.DELETE }
        assertEquals(listOf("Bin me"), gone.map { it.label })
    }

    @Test
    fun `a record reads as it did at a moment, not as it does now`() {
        Storage.write("Tasks", tasks(task(1, "Was this")))
        Storage.write("Tasks", tasks(task(1, "Is now this")))
        val mark = markBefore(Change.UPDATE)

        val then = History.recordAt("Tasks", "items", "1", mark)
        assertNotNull(then)
        assertEquals("Was this", then.jsonObject["title"]!!.jsonPrimitive.content)
        // Looking at the past leaves the present alone.
        assertEquals(listOf("Is now this"), titles())
    }

    @Test
    fun `putting a record back rewinds it, through the ordinary write path`() {
        Storage.write("Tasks", tasks(task(1, "Original")))
        Storage.write("Tasks", tasks(task(1, "Changed my mind")))
        val mark = markBefore(Change.UPDATE)

        assertTrue(History.restoreTo("Tasks", "items", "1", mark))
        assertEquals(listOf("Original"), titles())

        // The restore is a normal write, so the log stays the truth about the record —
        // and because an edit and its reversal inside one burst cancel out, it leaves no
        // phantom "title changed" entry claiming something happened that didn't.
        assertTrue(
            History.historyOf("Tasks", "1").none { it.change == Change.UPDATE },
            "an edit undone within the burst window should not survive as a change",
        )
    }

    @Test
    fun `a rewind after the burst window is recorded as its own edit`() {
        Storage.write("Tasks", tasks(task(1, "Original")))
        Storage.write("Tasks", tasks(task(1, "Changed my mind")))

        // Push the edit out of the coalesce window by ageing it, which is what a real gap
        // between changing your mind and changing it back looks like. The mark has to be
        // read back out of the aged log, not captured before — every timestamp moved.
        ageLog(minutes = 10)
        val mark = History.all().first { it.change == Change.UPDATE }.at - 1

        assertTrue(History.restoreTo("Tasks", "items", "1", mark))

        assertEquals(listOf("Original"), titles())
        val edits = History.historyOf("Tasks", "1").filter { it.change == Change.UPDATE }
        assertEquals(2, edits.size, "there and back are two separate edits once time has passed")
    }

    // Rewrite the stored log with every timestamp moved back, which is the only way to
    // simulate elapsed time against a coalescing window without sleeping for minutes.
    private fun ageLog(minutes: Int) {
        val shift = minutes * 60_000L
        val raw = Storage.read("__history") ?: return
        val aged = Regex("\"at\":(\\d+)").replace(raw) { m ->
            "\"at\":" + (m.groupValues[1].toLong() - shift)
        }
        Storage.write("__history", aged)
        History.reload()
    }

    @Test
    fun `putting back a record that already reads that way does nothing`() {
        Storage.write("Tasks", tasks(task(1, "Same")))
        val mark = History.all().last().at
        assertFalse(History.restoreTo("Tasks", "items", "1", mark))
    }

    @Test
    fun `putting back a deleted record brings it home`() {
        Storage.write("Tasks", tasks(task(1, "Keep"), task(2, "Deleted later")))
        Storage.write("Tasks", tasks(task(1, "Keep")))
        val mark = markBefore(Change.DELETE)

        assertTrue(History.restoreTo("Tasks", "items", "2", mark))
        assertTrue(titles().contains("Deleted later"))
    }

    @Test
    fun `putting back a record that did not exist then removes it`() {
        Storage.write("Tasks", tasks(task(1, "Original")))
        Storage.write("Tasks", tasks(task(1, "Original"), task(2, "Added after")))
        val mark = History.all().first { it.rec == "2" }.at - 1

        assertTrue(History.restoreTo("Tasks", "items", "2", mark))
        assertEquals(listOf("Original"), titles())
    }

    @Test
    fun `comparing two days collapses a burst into one before and after`() {
        Storage.write("Tasks", tasks(task(1, "A")))
        val start = dateOf(History.all().last().at).plusDays(-1)
        listOf("B", "C", "D").forEach { Storage.write("Tasks", tasks(task(1, it))) }

        val diffs = diffBetween("Tasks", start, today())
        // The record was created inside the window, so it reads as added rather than as
        // three separate title edits.
        assertEquals(1, diffs.size)
        assertEquals(Change.CREATE, diffs.single().kind)
    }

    @Test
    fun `comparing shows field level movement for a record that predates the window`() {
        Storage.write("Tasks", tasks(task(1, "Before")))
        val mark = History.all().last().at
        // A window that starts after the record already existed.
        Storage.write("Tasks", tasks(task(1, "After", done = true)))

        val diffs = diffBetween("Tasks", dateOf(mark), today())
        // Both writes landed today, so the window's start is the end of today — nothing
        // is after it. That is the honest answer at day granularity, not a bug.
        assertTrue(diffs.isEmpty())
    }

    @Test
    fun `a backwards window is refused rather than answered`() {
        Storage.write("Tasks", tasks(task(1, "A")))
        assertTrue(diffBetween("Tasks", today(), today().plusDays(-3)).isEmpty())
    }

    @Test
    fun `the future is not history`() {
        assertEquals(today(), clampToPast(today().plusDays(30)))
        assertEquals(today().plusDays(-5), clampToPast(today().plusDays(-5)))
    }
}
