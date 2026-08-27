package com.alekpeed.lifeos.diag

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The report is the whole point of the black box, so its shape is pinned.
//
// A crash reporter is only exercised when something has already gone wrong, which is
// the worst moment to discover it drops the causal chain or names the wrong screen.
class CrashReportTest {

    private fun frame(type: String, message: String, n: Int) =
        CrashFrame(type, message, List(n) { "com.alekpeed.lifeos.Thing.method(Thing.kt:$it)" })

    @Test
    fun `the screen is named`() {
        // Without this the report says an exception happened somewhere in a 42-module
        // app, which is barely more than "it crashed".
        val out = crashReport("finance", "main", "2026-08-26T10:00:00Z", listOf(frame("E", "boom", 3)))
        assertContains(out, "screen: finance")
    }

    @Test
    fun `a crash on the home screen says so rather than leaving it blank`() {
        val out = crashReport("", "main", "2026-08-26T10:00:00Z", listOf(frame("E", "boom", 3)))
        assertContains(out, "screen: (home)")
    }

    @Test
    fun `the cause is included, not just the wrapper`() {
        // The one that matters. Compose wraps a screen's failure in its own exception,
        // so a report that stops at the top level names a Compose internal for what is
        // actually a null dereference three frames down in module code.
        val out = crashReport(
            "finance", "main", "2026-08-26T10:00:00Z",
            listOf(
                frame("androidx.compose.Wrapper", "composition failed", 2),
                frame("java.lang.NullPointerException", "art was null", 2),
            ),
        )
        assertContains(out, "caused by java.lang.NullPointerException: art was null")
    }

    @Test
    fun `frames are capped and the remainder is counted, not silently dropped`() {
        // A trace that quietly stops looks like a shallow stack. Saying how many were
        // hidden is the difference between a summary and a wrong answer.
        val out = crashReport("x", "main", "t", listOf(frame("E", "boom", 20)))
        assertContains(out, "… 12 more")
        assertEquals(8, out.lines().count { it.trimStart().startsWith("at ") })
    }

    @Test
    fun `a short trace gets no more-line`() {
        val out = crashReport("x", "main", "t", listOf(frame("E", "boom", 3)))
        assertTrue("more" !in out)
    }

    @Test
    fun `a missing message does not produce the word null`() {
        // `Throwable.message` is null more often than not, and a report reading
        // "NullPointerException: null" has taught people to distrust these.
        val out = crashReport("x", "main", "t", listOf(CrashFrame("E", "", listOf("A.kt:1"))))
        assertTrue("null" !in out)
    }
}
