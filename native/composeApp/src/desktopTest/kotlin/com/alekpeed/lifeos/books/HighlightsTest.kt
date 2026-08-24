package com.alekpeed.lifeos.books

import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.History
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// §11.5 — reading highlights, and the export that makes capturing them worth it.
class HighlightsTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private val epub = BookFile(1, "Ebook", "text", "blob-1")
    private val pdf = BookFile(2, "The PDF", "pdf", "blob-2")
    private fun book() = Book(id = 1, title = "The Book", author = "A. Writer")

    @Test
    fun `a saved passage keeps where it came from`() {
        val b = addHighlight(book(), "  A line worth keeping.  ", "why", epub, "34%")
        val h = b.highlights.single()
        assertEquals("A line worth keeping.", h.text)
        assertEquals("why", h.note)
        assertEquals("Ebook", h.fileName)
        assertEquals(1L, h.fileId)
        assertEquals("34%", h.where)
        assertEquals(today().toString(), h.date)
    }

    @Test
    fun `a blank passage is not a highlight`() {
        assertTrue(addHighlight(book(), "   ", "", epub, "34%").highlights.isEmpty())
    }

    @Test
    fun `ids do not collide and the newest is first on the list`() {
        var b = addHighlight(book(), "First", "", epub, "10%")
        b = addHighlight(b, "Second", "", epub, "20%")
        assertEquals(listOf("Second", "First"), b.highlights.map { it.text })
        assertEquals(listOf(2L, 1L), b.highlights.map { it.id })
    }

    @Test
    fun `a text file and a PDF each say what they honestly know`() {
        // An EPUB has no pages and a PDF has no percentage of the whole; inventing
        // either from the other would be a made-up reference on a real quotation.
        var b = addHighlight(book(), "From the ebook", "", epub, "34%")
        b = addHighlight(b, "From the PDF", "", pdf, "p. 143")
        assertEquals(setOf("34%", "p. 143"), b.highlights.map { it.where }.toSet())
    }

    @Test
    fun `nothing captured exports nothing at all`() {
        // Not an empty document with a title on it — there is nothing to share.
        assertEquals("", exportHighlights(book()))
    }

    @Test
    fun `the export leads with the book and counts what is in it`() {
        var b = addHighlight(book(), "One", "", epub, "10%")
        b = addHighlight(b, "Two", "", epub, "20%")
        val out = exportHighlights(b)
        assertTrue(out.startsWith("# The Book"), out.take(40))
        assertTrue(out.contains("*A. Writer*"))
        assertTrue(out.contains("2 highlights"))
    }

    @Test
    fun `it reads in the order they were captured, not newest first`() {
        // The list is newest-first because that is what you just saved; a document meant
        // to be read straight through is not.
        var b = addHighlight(book(), "First", "", epub, "10%")
        b = addHighlight(b, "Second", "", epub, "20%")
        val out = exportHighlights(b)
        assertTrue(out.indexOf("First") < out.indexOf("Second"))
    }

    @Test
    fun `each passage is quoted, with its reference and any note`() {
        val out = exportHighlights(addHighlight(book(), "A line", "matters because", epub, "34%"))
        assertTrue(out.contains("> A line"))
        assertTrue(out.contains("— 34% · ${today()}"))
        assertTrue(out.contains("matters because"))
    }

    @Test
    fun `a passage that spans lines stays quoted on every line`() {
        // Otherwise the second line reads as prose about the quote rather than as part
        // of it, in every destination that renders Markdown.
        val out = exportHighlights(addHighlight(book(), "First line\nSecond line", "", epub, ""))
        assertTrue(out.contains("> First line\n> Second line"), out)
    }

    @Test
    fun `one source needs no heading, two get one each`() {
        val single = exportHighlights(addHighlight(book(), "Only", "", epub, ""))
        assertTrue(!single.contains("## Ebook"))
        var b = addHighlight(book(), "From ebook", "", epub, "")
        b = addHighlight(b, "From pdf", "", pdf, "")
        val both = exportHighlights(b)
        assertTrue(both.contains("## Ebook"))
        assertTrue(both.contains("## The PDF"))
    }

    @Test
    fun `highlights round-trip through the store`() {
        val b = addHighlight(book(), "Kept", "note", epub, "34%")
        saveBooks(BooksData(listOf(b)))
        assertEquals(b.highlights, loadBooks().books.single().highlights)
    }
}
