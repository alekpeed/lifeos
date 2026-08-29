package com.alekpeed.lifeos.platform

import java.awt.EventQueue
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob

// Paginated text out to paper or a PDF, on desktop.
//
// The Android side renders a PDF itself and hands it to the system print sheet, which
// offers "Save as PDF" alongside any real printer. Desktop reaches the same place from
// the other direction: `PrinterJob` with a `Printable`, and the OS print dialog on both
// Windows and Linux offers Print to File / Save as PDF. That is deliberately not a
// second-best — it is the same destination, and it costs no PDF library. Adding one to
// hand-roll the file would be a few megabytes of dependency to reimplement a dialog
// the OS already has.
//
// The layout is the plain kind on purpose: this prints the Daily Paper, which is prose,
// and prose wants a readable serif at a sane measure rather than a faithful copy of
// what the screen was doing.
object DesktopPdf {

    val available: Boolean by lazy {
        runCatching { !GraphicsEnvironment.isHeadless() && PrinterJob.lookupPrintServices().isNotEmpty() }
            .getOrDefault(false)
    }

    fun export(title: String, text: String) {
        if (!available) return
        // The print dialog is a modal AWT window, so it belongs on the event dispatch
        // thread. Compose Desktop runs its own; showing it from there is how you get a
        // dialog that never paints.
        EventQueue.invokeLater {
            runCatching {
                val job = PrinterJob.getPrinterJob()
                job.setJobName(title)
                job.setPrintable(TextPrintable(title, text))
                // Cancelled dialog returns false, and that is a normal outcome rather
                // than a failure — somebody looked at the preview and changed their mind.
                if (job.printDialog()) job.print()
            }
        }
    }
}

// Wraps the text to the page's printable width and pages it.
//
// `print` is called repeatedly by the toolkit, out of order and often more than once
// per page, so nothing here may depend on call sequence: the layout is computed once
// against the first PageFormat seen and reused. Recomputing per call would be slow and,
// worse, could disagree with itself between the measuring pass and the drawing pass.
private class TextPrintable(private val title: String, private val text: String) : Printable {

    private val titleFont = Font(Font.SERIF, Font.BOLD, 14)
    private val bodyFont = Font(Font.SERIF, Font.PLAIN, 10)

    private var pages: List<List<String>>? = null

    override fun print(graphics: Graphics, pf: PageFormat, pageIndex: Int): Int {
        val g = graphics as Graphics2D
        val laidOut = pages ?: layout(g, pf).also { pages = it }
        if (pageIndex >= laidOut.size) return Printable.NO_SUCH_PAGE

        g.translate(pf.imageableX, pf.imageableY)
        val lineHeight = g.getFontMetrics(bodyFont).height
        var y = 0

        // The title heads the first page only; repeating it on every page would read as
        // a header the document does not have.
        if (pageIndex == 0) {
            g.font = titleFont
            y += g.getFontMetrics(titleFont).height
            g.drawString(title, 0, y)
            y += lineHeight
        }

        g.font = bodyFont
        for (line in laidOut[pageIndex]) {
            y += lineHeight
            g.drawString(line, 0, y)
        }
        return Printable.PAGE_EXISTS
    }

    private fun layout(g: Graphics2D, pf: PageFormat): List<List<String>> {
        val fm = g.getFontMetrics(bodyFont)
        val wrapped = wrapText(text, pf.imageableWidth.toInt()) { fm.stringWidth(it) }
        val perPage = ((pf.imageableHeight.toInt() / fm.height) - 1).coerceAtLeast(1)
        // The first page gives two lines up to the title and the gap under it.
        return paginate(wrapped, perPage = perPage, firstPage = (perPage - 2).coerceAtLeast(1))
    }
}

// Wrapping and pagination, separated from the toolkit so they can be tested.
//
// `measure` is the only thing that needs a real Graphics2D, and passing it in costs one
// lambda. Without this split these two would be untestable, and they are exactly the
// kind of code that fails quietly: a wrap that drops the last word of a paragraph, or a
// paginator that loses a line at every page boundary, produces a document that looks
// fine until someone reads it closely.

// Greedy word wrap. A single word longer than the measure is emitted on its own
// oversized line rather than dropped or split mid-word — a URL is still readable
// running off the margin, and is gone entirely if we discard it.
internal fun wrapText(text: String, maxWidth: Int, measure: (String) -> Int): List<String> {
    val out = mutableListOf<String>()
    for (raw in text.split('\n')) {
        if (raw.isBlank()) { out.add(""); continue }
        var line = ""
        for (word in raw.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (measure(candidate) <= maxWidth || line.isEmpty()) {
                line = candidate
            } else {
                out.add(line)
                line = word
            }
        }
        out.add(line)
    }
    return out
}

// Chop wrapped lines into pages. The first page holds fewer, because the title sits on
// it. Never returns an empty list: a print job with no pages is a job the toolkit will
// reject rather than a blank sheet.
internal fun paginate(lines: List<String>, perPage: Int, firstPage: Int): List<List<String>> {
    val first = firstPage.coerceAtLeast(1)
    val rest = perPage.coerceAtLeast(1)
    val out = mutableListOf<List<String>>()
    var i = 0
    var take = first
    while (i < lines.size) {
        val end = minOf(i + take, lines.size)
        out.add(lines.subList(i, end).toList())
        i = end
        take = rest
    }
    return if (out.isEmpty()) listOf(emptyList()) else out
}
