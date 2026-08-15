package com.alekpeed.lifeos.platform

import java.io.ByteArrayOutputStream

// A hand-rolled, minimal PDF writer for exportTextAsPdf — plain paginated text, one
// title, nothing else. Android renders this via the framework's PdfDocument +
// StaticLayout; the JVM has no equivalent bundled, and pulling in a PDF library for
// "print some text" would be a heavy dependency for a small feature. The PDF format
// for exactly this case — unembedded standard fonts, simple text operators — is
// small enough to write directly.
//
// Using Helvetica and Helvetica-Bold, two of the 14 standard PDF fonts every viewer
// already has, means nothing needs embedding. Word-wrap uses Helvetica's real AFM
// character widths (below) rather than measuring with a locally-installed font, so
// the wrap width matches what every viewer will actually render — including on a
// CI runner with no fonts of its own.
internal object PdfWriter {
    private const val PAGE_W = 595   // A4 @ 72dpi, matching the Android export
    private const val PAGE_H = 842
    private const val MARGIN = 40
    private const val BODY_SIZE = 12f
    private const val LEADING = 14f
    private const val TITLE_SIZE = 18f
    private const val MAX_PAGES = 300

    // Helvetica's standard AFM widths, 1/1000 em, for printable ASCII 32 (space)
    // through 126 (~) — the only range this writer's sanitizer lets through, so the
    // table never needs to cover more than that.
    private val WIDTH = intArrayOf(
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
        1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
        333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584,
    )

    private fun width(c: Char, sizePt: Float): Float {
        val code = c.code
        if (code < 32 || code > 126) return WIDTH[0] * sizePt / 1000f
        return WIDTH[code - 32] * sizePt / 1000f
    }

    // Keep the writer to the one range it has metrics for. Common "smart" punctuation
    // maps to its plain-ASCII equivalent; anything else outside 32..126 is dropped
    // rather than mis-measured or corrupting the byte stream (PDF literal strings are
    // raw WinAnsiEncoding bytes).
    private fun sanitize(s: String): String {
        val out = StringBuilder(s.length)
        for (c in s) {
            when {
                c == '‘' || c == '’' -> out.append('\'')
                c == '“' || c == '”' -> out.append('"')
                c == '–' || c == '—' -> out.append('-')
                c == '…' -> out.append("...")
                c == '\t' -> out.append(' ')
                c.code in 32..126 -> out.append(c)
                else -> {}
            }
        }
        return out.toString()
    }

    // Greedy word-wrap to a max width in points, at the given font size. Blank
    // paragraphs (from a blank line in the source) survive as a single empty line.
    private fun wrap(paragraph: String, sizePt: Float, maxWidth: Float): List<String> {
        if (paragraph.isEmpty()) return listOf("")
        val words = paragraph.split(' ')
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        var lineW = 0f
        val spaceW = width(' ', sizePt)
        for (word in words) {
            var w = word
            var wordW = 0f
            for (ch in w) wordW += width(ch, sizePt)
            // A single word wider than the whole line has to be hard-broken, or it
            // would silently vanish off the page edge forever.
            if (wordW > maxWidth && w.isNotEmpty()) {
                if (line.isNotEmpty()) { lines.add(line.toString()); line = StringBuilder(); lineW = 0f }
                var chunk = StringBuilder()
                var chunkW = 0f
                for (ch in w) {
                    val cw = width(ch, sizePt)
                    if (chunkW + cw > maxWidth && chunk.isNotEmpty()) {
                        lines.add(chunk.toString()); chunk = StringBuilder(); chunkW = 0f
                    }
                    chunk.append(ch); chunkW += cw
                }
                w = chunk.toString(); wordW = chunkW
            }
            val addW = (if (line.isEmpty()) 0f else spaceW) + wordW
            if (line.isNotEmpty() && lineW + addW > maxWidth) {
                lines.add(line.toString())
                line = StringBuilder(w); lineW = wordW
            } else {
                if (line.isNotEmpty()) line.append(' ')
                line.append(w); lineW += addW
            }
        }
        lines.add(line.toString())
        return lines
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    fun write(title: String, text: String): ByteArray {
        val safeTitle = sanitize(title)
        val maxWidth = (PAGE_W - MARGIN * 2).toFloat()
        val lines = sanitize(text).split("\n").flatMap { wrap(it, BODY_SIZE, maxWidth) }

        val linesPerPage = ((PAGE_H - MARGIN * 2) / LEADING).toInt().coerceAtLeast(1)
        val firstPageLines = (linesPerPage - ((TITLE_SIZE + 16) / LEADING).toInt() - 1).coerceAtLeast(1)
        val pages = mutableListOf<List<String>>()
        var i = 0
        var first = true
        while (i < lines.size && pages.size < MAX_PAGES) {
            val cap = if (first) firstPageLines else linesPerPage
            pages.add(lines.subList(i, minOf(i + cap, lines.size)))
            i += cap
            first = false
        }
        if (pages.isEmpty()) pages.add(emptyList())

        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()   // index 0 unused; object N's offset at index N
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        fun beginObj(n: Int) { while (offsets.size <= n) offsets.add(0); offsets[n] = out.size(); w("$n 0 obj\n") }

        w("%PDF-1.4\n%âãÏÓ\n")

        val pageCount = pages.size
        val fontRegularObj = 3
        val fontBoldObj = 4
        val firstPageObj = 5
        val firstContentObj = firstPageObj + pageCount

        beginObj(1); w("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        beginObj(2)
        w("<< /Type /Pages /Kids [")
        for (p in 0 until pageCount) w("${firstPageObj + p} 0 R ")
        w("] /Count $pageCount >>\nendobj\n")

        beginObj(fontRegularObj)
        w("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")
        beginObj(fontBoldObj)
        w("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n")

        for (p in 0 until pageCount) {
            beginObj(firstPageObj + p)
            w(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_W $PAGE_H] " +
                    "/Resources << /Font << /F1 $fontRegularObj 0 R /F2 $fontBoldObj 0 R >> >> " +
                    "/Contents ${firstContentObj + p} 0 R >>\nendobj\n",
            )
        }

        for (p in 0 until pageCount) {
            val body = buildString {
                var top = PAGE_H - MARGIN
                if (p == 0) {
                    append("BT /F2 $TITLE_SIZE Tf $MARGIN $top Td (${escape(safeTitle)}) Tj ET\n")
                    top -= (TITLE_SIZE + 16).toInt()
                }
                val pageLines = pages[p]
                if (pageLines.isNotEmpty()) {
                    append("BT /F1 $BODY_SIZE Tf $LEADING TL $MARGIN $top Td\n")
                    pageLines.forEachIndexed { idx, line ->
                        if (idx > 0) append("T*\n")
                        append("(${escape(line)}) Tj\n")
                    }
                    append("ET\n")
                }
            }
            val bytes = body.toByteArray(Charsets.ISO_8859_1)
            beginObj(firstContentObj + p)
            w("<< /Length ${bytes.size} >>\nstream\n")
            out.write(bytes)
            w("\nendstream\nendobj\n")
        }

        val xrefStart = out.size()
        val totalObjs = firstContentObj + pageCount
        w("xref\n0 $totalObjs\n0000000000 65535 f \n")
        for (n in 1 until totalObjs) {
            w(offsets[n].toString().padStart(10, '0') + " 00000 n \n")
        }
        w("trailer\n<< /Size $totalObjs /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF")

        return out.toByteArray()
    }
}
