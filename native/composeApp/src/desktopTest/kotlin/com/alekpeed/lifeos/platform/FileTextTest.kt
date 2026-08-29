package com.alekpeed.lifeos.platform

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The file parsers, now that both platforms share them.
//
// They moved out of MainActivity so desktop could open a book and import a health
// export. That move is the reason these tests exist: one implementation serving two
// targets is worth pinning, and the EPUB path in particular is regex over a zip —
// the kind of code that keeps working right up until a slightly different file
// arrives.
class FileTextTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, body) ->
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun chapters(text: String) =
        Regex("([^]*)").findAll(text).map { it.groupValues[1] }.toList()

    // ---- ebooks ---------------------------------------------------------------------

    @Test
    fun `a plain text file comes back as itself`() {
        // No PK magic, so no unzipping. The commonest thing anyone will drop in.
        assertEquals("Just some words.", parseEbook("Just some words.".toByteArray()))
    }

    @Test
    fun `an empty file says so rather than returning nothing`() {
        assertEquals("(Empty file.)", parseEbook(ByteArray(0)))
    }

    @Test
    fun `an epub is read in spine order, not alphabetical order`() {
        // The whole reason the OPF is parsed at all. Alphabetically these files are
        // b, c, a; the spine says a, b, c — and a book read in filename order is not
        // the book.
        val opf = """
            <package><manifest>
              <item id="c1" href="a.xhtml"/>
              <item id="c2" href="b.xhtml"/>
              <item id="c3" href="c.xhtml"/>
            </manifest><spine>
              <itemref idref="c3"/><itemref idref="c1"/><itemref idref="c2"/>
            </spine></package>
        """.trimIndent()
        val book = parseEbook(
            zip(
                "content.opf" to opf,
                "a.xhtml" to "<html><body><h1>Alpha</h1><p>first</p></body></html>",
                "b.xhtml" to "<html><body><h1>Beta</h1><p>second</p></body></html>",
                "c.xhtml" to "<html><body><h1>Gamma</h1><p>third</p></body></html>",
            ),
        )
        assertEquals(listOf("Gamma", "Alpha", "Beta"), chapters(book))
        assertTrue(book.indexOf("third") < book.indexOf("first"))
    }

    @Test
    fun `hrefs resolve relative to the opf's own folder`() {
        // Real EPUBs put the OPF under OEBPS/ and reference siblings without that
        // prefix. Getting this wrong yields a book with no chapters at all.
        val opf = """
            <package><manifest><item id="c1" href="ch1.xhtml"/></manifest>
            <spine><itemref idref="c1"/></spine></package>
        """.trimIndent()
        val book = parseEbook(
            zip(
                "OEBPS/content.opf" to opf,
                "OEBPS/ch1.xhtml" to "<html><body><h1>One</h1><p>body text</p></body></html>",
            ),
        )
        assertEquals(listOf("One"), chapters(book))
        assertTrue("body text" in book)
    }

    @Test
    fun `an anchor in an href still finds the file`() {
        val opf = """
            <package><manifest><item id="c1" href="ch1.xhtml#start"/></manifest>
            <spine><itemref idref="c1"/></spine></package>
        """.trimIndent()
        val book = parseEbook(zip("content.opf" to opf, "ch1.xhtml" to "<h1>Anchored</h1><p>x</p>"))
        assertEquals(listOf("Anchored"), chapters(book))
    }

    @Test
    fun `without an opf it falls back to sorted xhtml`() {
        val book = parseEbook(
            zip(
                "b.xhtml" to "<h1>Second</h1><p>two</p>",
                "a.xhtml" to "<h1>First</h1><p>one</p>",
            ),
        )
        assertEquals(listOf("First", "Second"), chapters(book))
    }

    @Test
    fun `a chapter with no heading is numbered rather than left blank`() {
        val book = parseEbook(zip("a.xhtml" to "<p>no heading here</p>"))
        assertEquals(listOf("Chapter 1"), chapters(book))
    }

    @Test
    fun `a zip with nothing readable says so instead of returning empty`() {
        val book = parseEbook(zip("cover.png" to "notreallyapng"))
        assertTrue("Couldn't extract" in book)
    }

    // ---- html to text ---------------------------------------------------------------

    @Test
    fun `script and style content is dropped, not rendered as prose`() {
        val text = htmlToText("<style>p{color:red}</style><script>alert(1)</script><p>Real words</p>")
        assertEquals("Real words", text)
    }

    @Test
    fun `entities become the characters they stand for`() {
        assertEquals("Tom & Jerry's \"day\" — done…", htmlToText("Tom &amp; Jerry&#39;s &quot;day&quot; &mdash; done&hellip;"))
    }

    @Test
    fun `block ends become line breaks so paragraphs survive`() {
        assertEquals("one\ntwo", htmlToText("<p>one</p><p>two</p>"))
    }

    // ---- filtered streaming ---------------------------------------------------------

    @Test
    fun `only matching lines are kept`() {
        // The Apple Health export is hundreds of megabytes; keeping everything would
        // defeat the point of streaming it.
        val input = "alpha\nbeta\ngamma\nbetamax\n".byteInputStream()
        assertEquals("beta\nbetamax\n", readFilteredText(input, listOf("beta")))
    }

    @Test
    fun `an empty filter keeps everything`() {
        assertEquals("a\nb\n", readFilteredText("a\nb\n".byteInputStream(), emptyList()))
    }

    @Test
    fun `a zip is opened to its first xml or csv entry`() {
        // Apple hands you a .zip; detection is by PK magic bytes, not by filename,
        // because the picker gives no guarantee about the extension.
        val z = zip("readme.txt" to "ignore me", "export.xml" to "keep\ndrop\n")
        assertEquals("keep\n", readFilteredText(z.inputStream(), listOf("keep")))
    }

    @Test
    fun `a zip with no xml or csv is a null, not an empty string`() {
        // Null and "" mean different things to the caller: nothing usable in the file
        // versus a file that matched nothing.
        assertNull(readFilteredText(zip("a.png" to "x").inputStream(), listOf("k")))
    }
}
