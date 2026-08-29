package com.alekpeed.lifeos.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Wrapping and pagination for the printed Daily Paper.
//
// These fail quietly by nature: a wrap that loses the last word of a paragraph, or a
// paginator that drops a line at every page boundary, produces a document that looks
// perfectly normal until somebody reads it closely and finds a sentence missing.
// So the invariant worth asserting hardest is not the layout — it is that every line
// that went in comes out.
class DesktopPdfTest {

    // One unit per character: the real measure comes from FontMetrics, and a fake one
    // makes the expected output something a reader can verify by counting.
    private val measure: (String) -> Int = { it.length }

    // ---- wrapping -------------------------------------------------------------------

    @Test
    fun `words break onto the next line at the measure`() {
        assertEquals(
            listOf("the quick", "brown fox"),
            wrapText("the quick brown fox", 10, measure),
        )
    }

    @Test
    fun `a word longer than the line is kept, not dropped`() {
        // A long URL is still readable running past the margin. Discarding it, or
        // splitting it mid-word, loses information the reader needed.
        val out = wrapText("see https://example.com/a/very/long/path now", 10, measure)
        assertTrue(out.any { it == "https://example.com/a/very/long/path" })
        assertTrue(out.any { "now" in it })
    }

    @Test
    fun `blank lines survive as paragraph breaks`() {
        // The Daily Paper is prose with paragraphs. Collapsing the gaps would run the
        // whole issue together into one block.
        assertEquals(listOf("one", "", "two"), wrapText("one\n\ntwo", 40, measure))
    }

    @Test
    fun `no word is lost, whatever the measure`() {
        val text = "alpha beta gamma delta epsilon zeta eta theta iota kappa"
        for (width in listOf(3, 7, 11, 20, 500)) {
            val joined = wrapText(text, width, measure).joinToString(" ").split(" ").filter { it.isNotEmpty() }
            assertEquals(text.split(" "), joined, "width $width lost or reordered a word")
        }
    }

    @Test
    fun `runs of spaces do not become empty words`() {
        assertEquals(listOf("a b"), wrapText("a    b", 40, measure))
    }

    // ---- pagination -----------------------------------------------------------------

    @Test
    fun `the first page holds fewer lines because the title is on it`() {
        val lines = (1..10).map { "line $it" }
        val pages = paginate(lines, perPage = 4, firstPage = 2)
        assertEquals(listOf(2, 4, 4), pages.map { it.size })
    }

    @Test
    fun `every line lands on exactly one page`() {
        // The one that matters. An off-by-one here silently deletes a line per page.
        val lines = (1..37).map { "line $it" }
        val pages = paginate(lines, perPage = 5, firstPage = 3)
        assertEquals(lines, pages.flatten())
    }

    @Test
    fun `an exact fit does not produce a trailing empty page`() {
        val lines = (1..7).map { "l$it" }
        val pages = paginate(lines, perPage = 4, firstPage = 3)
        assertEquals(2, pages.size)
        assertEquals(lines, pages.flatten())
    }

    @Test
    fun `empty text still yields one page rather than none`() {
        // A print job reporting zero pages is rejected by the toolkit; a blank sheet is
        // the honest outcome of printing nothing.
        assertEquals(1, paginate(emptyList(), perPage = 5, firstPage = 3).size)
    }

    @Test
    fun `a degenerate page size cannot loop forever`() {
        // A tiny paper size or a huge font can drive the computed lines-per-page to zero
        // or below; taking zero lines per page would never advance.
        val pages = paginate((1..5).map { "l$it" }, perPage = 0, firstPage = 0)
        assertEquals(5, pages.size)
        assertEquals(5, pages.flatten().size)
    }

    @Test
    fun `printing is unavailable in a headless test jvm`() {
        // Same guard as notifications: the flag gates the PDF button, and it must not
        // claim a capability the machine does not have.
        assertTrue(!DesktopPdf.available)
        assertTrue(!Native.supportsPdfExport)
    }
}
