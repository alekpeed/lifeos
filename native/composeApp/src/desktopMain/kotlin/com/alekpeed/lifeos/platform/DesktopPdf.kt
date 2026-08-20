package com.alekpeed.lifeos.platform

import java.io.ByteArrayOutputStream

// A minimal PDF writer, so desktop can export the Daily Paper the way Android does.
//
// No PDF library ships with the JVM and the project takes no new dependencies for this,
// so this emits the format directly: one Helvetica text object per page, A4 at 72dpi.
// Standard-14 fonts need no embedding, which is what keeps this small enough to be worth
// writing by hand.
internal object DesktopPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40
    private const val BODY_SIZE = 12.0
    private const val TITLE_SIZE = 18.0
    private const val LEADING = 15.0
    private const val MAX_PAGES = 300

    // Helvetica advance widths (units per 1000) for printable ASCII. Wrapping against the
    // real metrics is what keeps a line from running off the page — measuring with an
    // arbitrary system font would only approximate the font the PDF actually names.
    private val HELVETICA = intArrayOf(
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278, // 32-47
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556, // 48-63
        1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, // 64-79
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556, // 80-95
        333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556, // 96-111
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584,       // 112-126
    )

    // Unicode -> WinAnsi (CP1252) byte for the punctuation this app actually emits. The
    // editorial prose is full of em-dashes and curly quotes, and htmlToText hands back
    // more of them; without this mapping every one of them exported as "?".
    private val WIN_ANSI_EXTRAS = mapOf(
        '\u20AC' to 0x80, '\u201A' to 0x82, '\u0192' to 0x83, '\u201E' to 0x84,
        '\u2026' to 0x85, '\u2020' to 0x86, '\u2021' to 0x87, '\u02C6' to 0x88,
        '\u2030' to 0x89, '\u2039' to 0x8B, '\u2018' to 0x91, '\u2019' to 0x92,
        '\u201C' to 0x93, '\u201D' to 0x94, '\u2022' to 0x95, '\u2013' to 0x96,
        '\u2014' to 0x97, '\u02DC' to 0x98, '\u2122' to 0x99, '\u203A' to 0x9B,
    )

    // Helvetica widths for the mapped codes above, so an em-dash (1000 units, nearly
    // twice the fallback) doesn't quietly push a line past the margin.
    private val EXTRA_WIDTHS = mapOf(
        0x80 to 556, 0x82 to 222, 0x83 to 556, 0x84 to 333, 0x85 to 1000, 0x86 to 556,
        0x87 to 556, 0x88 to 333, 0x89 to 1000, 0x8B to 333, 0x91 to 222, 0x92 to 222,
        0x93 to 333, 0x94 to 333, 0x95 to 350, 0x96 to 556, 0x97 to 1000, 0x98 to 333,
        0x99 to 1000, 0x9B to 333,
    )

    // One WinAnsi byte per character. Tabs and other control characters become spaces;
    // anything with no WinAnsi equivalent degrades to "?" rather than corrupting the run.
    private fun toWinAnsi(s: String): IntArray {
        val out = IntArray(s.length)
        for ((i, c) in s.withIndex()) {
            out[i] = when {
                c.code in 32..126 -> c.code
                c.code in 160..255 -> c.code
                c.code < 32 -> 32
                else -> WIN_ANSI_EXTRAS[c] ?: '?'.code
            }
        }
        return out
    }

    private fun codeWidth(code: Int): Int {
        val i = code - 32
        return when {
            code in 32..126 && i in HELVETICA.indices -> HELVETICA[i]
            else -> EXTRA_WIDTHS[code] ?: 556
        }
    }

    // Bold runs wider than regular at the same size; the title is the only bold run and a
    // flat 1.08 keeps it inside the margin without carrying a second width table.
    private fun textWidth(s: String, size: Double, bold: Boolean): Double {
        var units = 0
        for (code in toWinAnsi(s)) units += codeWidth(code)
        val w = units / 1000.0 * size
        return if (bold) w * 1.08 else w
    }

    private fun wrap(text: String, maxWidth: Double, size: Double, bold: Boolean): List<String> {
        val out = ArrayList<String>()
        for (paragraph in text.split("\n")) {
            if (paragraph.isBlank()) { out.add(""); continue }
            var line = StringBuilder()
            for (word in paragraph.split(" ").filter { it.isNotEmpty() }) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (textWidth(candidate, size, bold) <= maxWidth) {
                    line = StringBuilder(candidate)
                } else {
                    if (line.isNotEmpty()) out.add(line.toString())
                    // A single word longer than the line gets hard-split rather than
                    // silently overflowing the page.
                    var rest = word
                    while (textWidth(rest, size, bold) > maxWidth && rest.length > 1) {
                        var cut = rest.length
                        while (cut > 1 && textWidth(rest.take(cut), size, bold) > maxWidth) cut--
                        out.add(rest.take(cut))
                        rest = rest.drop(cut)
                    }
                    line = StringBuilder(rest)
                }
            }
            out.add(line.toString())
        }
        return out
    }

    // PDF string literals escape backslash and both parens; everything else goes through
    // as WinAnsi bytes, with anything unmappable degraded rather than dropped.
    private fun escape(s: String): String {
        val sb = StringBuilder()
        for (code in toWinAnsi(s)) {
            when (code) {
                '\\'.code -> sb.append("\\\\")
                '('.code -> sb.append("\\(")
                ')'.code -> sb.append("\\)")
                else -> sb.append(code.toChar())
            }
        }
        return sb.toString()
    }

    fun build(title: String, text: String): ByteArray {
        val usableWidth = (PAGE_W - MARGIN * 2).toDouble()
        val titleLines = wrap(title, usableWidth, TITLE_SIZE, bold = true)
        val bodyLines = wrap(text, usableWidth, BODY_SIZE, bold = false)

        // First page loses room to the title block.
        val firstPageRows = ((PAGE_H - MARGIN * 2) - (titleLines.size * 22 + 12)) / LEADING
        val laterPageRows = (PAGE_H - MARGIN * 2) / LEADING
        val pages = ArrayList<List<String>>()
        var i = 0
        while (i < bodyLines.size && pages.size < MAX_PAGES) {
            val room = (if (pages.isEmpty()) firstPageRows else laterPageRows).toInt().coerceAtLeast(1)
            pages.add(bodyLines.subList(i, minOf(i + room, bodyLines.size)))
            i += room
        }
        if (pages.isEmpty()) pages.add(emptyList())

        val contents = pages.mapIndexed { index, lines ->
            val sb = StringBuilder()
            sb.append("BT\n")
            var y = PAGE_H - MARGIN
            if (index == 0) {
                sb.append("/F2 ").append(TITLE_SIZE.toInt()).append(" Tf\n")
                for (t in titleLines) {
                    y -= 22
                    sb.append("1 0 0 1 ").append(MARGIN).append(' ').append(y).append(" Tm\n")
                    sb.append('(').append(escape(t)).append(") Tj\n")
                }
                y -= 12
            }
            sb.append("/F1 ").append(BODY_SIZE.toInt()).append(" Tf\n")
            for (line in lines) {
                y -= LEADING.toInt()
                if (line.isNotEmpty()) {
                    sb.append("1 0 0 1 ").append(MARGIN).append(' ').append(y).append(" Tm\n")
                    sb.append('(').append(escape(line)).append(") Tj\n")
                }
            }
            sb.append("ET")
            sb.toString()
        }

        // Object layout: 1 catalog, 2 pages, 3+4 fonts, then per page a page object and
        // its content stream.
        val pageCount = pages.size
        val firstPageObj = 5
        val objects = ArrayList<String>()
        objects.add("<< /Type /Catalog /Pages 2 0 R >>")
        val kids = (0 until pageCount).joinToString(" ") { "${firstPageObj + it * 2} 0 R" }
        objects.add("<< /Type /Pages /Kids [$kids] /Count $pageCount >>")
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        for (p in 0 until pageCount) {
            val contentObj = firstPageObj + p * 2 + 1
            objects.add(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_W $PAGE_H] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents $contentObj 0 R >>",
            )
            objects.add("STREAM:${contents[p]}")
        }

        val out = ByteArrayOutputStream()
        fun emit(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        emit("%PDF-1.4\n")
        val offsets = IntArray(objects.size + 1)
        objects.forEachIndexed { idx, obj ->
            offsets[idx + 1] = out.size()
            val num = idx + 1
            if (obj.startsWith("STREAM:")) {
                val body = obj.removePrefix("STREAM:")
                val bytes = body.toByteArray(Charsets.ISO_8859_1)
                emit("$num 0 obj\n<< /Length ${bytes.size} >>\nstream\n")
                out.write(bytes)
                emit("\nendstream\nendobj\n")
            } else {
                emit("$num 0 obj\n$obj\nendobj\n")
            }
        }
        val xref = out.size()
        emit("xref\n0 ${objects.size + 1}\n")
        emit("0000000000 65535 f \n")
        for (idx in 1..objects.size) emit(offsets[idx].toString().padStart(10, '0') + " 00000 n \n")
        emit("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}
