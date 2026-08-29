package com.alekpeed.lifeos.platform

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

// Turning picked files into text — the half of the file pickers that is pure JVM.
//
// This lived in `MainActivity` and was therefore Android-only, which is why the
// desktop build could not open a book or import a health export: not a platform
// limit, just code in the wrong source set. Android and desktop are both JVM, so it
// belongs here, called by both, fixed once.
//
// Nothing here touches a UI toolkit or an Android class. The picking is the
// platform's job; the parsing is the same everywhere.

// Stream a picked file — or the first .xml/.csv entry of a .zip, detected by magic
// bytes rather than by filename — line by line, keeping lines that contain any of the
// filter substrings.
//
// Built for the Apple Health export, whose export.xml runs to hundreds of megabytes:
// the whole file never sits in memory at once, and the kept text is capped so a filter
// that matches everything cannot exhaust the heap either.
fun readFilteredText(raw: InputStream, filter: List<String>): String? {
    val stream = BufferedInputStream(raw, 1 shl 16)
    stream.mark(4)
    val magic = ByteArray(2)
    val n = stream.read(magic)
    stream.reset()
    val source: InputStream? = if (n == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) {
        val zip = ZipInputStream(stream)
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name.lowercase()
            if (!entry.isDirectory && (name.endsWith(".xml") || name.endsWith(".csv"))) break
            entry = zip.nextEntry
        }
        if (entry == null) null else zip
    } else {
        stream
    }
    if (source == null) return null
    val out = StringBuilder()
    source.bufferedReader().forEachLine { line ->
        if (out.length > 8_000_000) return@forEachLine
        if (filter.isEmpty() || filter.any { line.contains(it) }) {
            out.append(line).append('\n')
        }
    }
    return out.toString()
}

// Turn a picked ebook's bytes into readable plain text. A .txt (no PK zip magic)
// decodes directly; an EPUB is unzipped, its OPF read for the real reading order
// (spine → manifest hrefs), and each chapter's XHTML stripped to text. Falls back to
// name-sorted XHTML entries when there is no usable OPF.
fun parseEbook(bytes: ByteArray): String {
    if (bytes.size < 2 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
        return bytes.decodeToString().trim().ifBlank { "(Empty file.)" }
    }
    val entries = HashMap<String, ByteArray>()
    ZipInputStream(bytes.inputStream()).use { zip ->
        var e = zip.nextEntry
        while (e != null) {
            if (!e.isDirectory) entries[e.name] = zip.readBytes()
            e = zip.nextEntry
        }
    }
    val opfName = entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
    val order: List<String> = if (opfName != null) {
        val opf = entries[opfName]!!.decodeToString()
        val base = opfName.substringBeforeLast('/', "")
        val manifest = Regex("(?is)<item\\b[^>]*>").findAll(opf).mapNotNull { m ->
            val id = Regex("id=\"([^\"]*)\"").find(m.value)?.groupValues?.get(1)
            val href = Regex("href=\"([^\"]*)\"").find(m.value)?.groupValues?.get(1)
            if (id != null && href != null) id to href else null
        }.toMap()
        Regex("idref=\"([^\"]*)\"").findAll(opf).mapNotNull { manifest[it.groupValues[1]] }
            .map { if (base.isEmpty()) it else "$base/$it" }.toList()
    } else {
        entries.keys.filter {
            val l = it.lowercase()
            l.endsWith(".xhtml") || l.endsWith(".html") || l.endsWith(".htm")
        }.sorted()
    }
    // Each spine item becomes a chapter, delimited by a private-use marker
    // (U+E000titleU+E000) the reader parses into a table of contents. The title comes
    // from the item's first heading / <title>, else "Chapter N".
    val sb = StringBuilder()
    var chapterNum = 0
    for (href in order) {
        val path = href.substringBefore('#')
        val data = entries[path] ?: entries[path.substringAfterLast('/')] ?: continue
        val html = data.decodeToString()
        val body = htmlToText(html)
        if (body.isBlank()) continue
        chapterNum++
        val title = ebookChapterTitle(html) ?: "Chapter $chapterNum"
        sb.append('').append(title).append('').append('\n')
        sb.append(body).append("\n\n")
    }
    return sb.toString().trim().ifBlank { "(Couldn't extract readable text from this EPUB.)" }
}

// The chapter heading for a spine document: its first h1–h3, else its <title>,
// stripped of tags and clamped to a sane length. Null if nothing usable.
fun ebookChapterTitle(html: String): String? {
    val raw = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>").find(html)?.groupValues?.get(1)
        ?: Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)
        ?: return null
    val text = Regex("<[^>]+>").replace(raw, "").replace(Regex("\\s+"), " ").trim()
    return text.takeIf { it.isNotBlank() && it.length <= 80 }
}

fun htmlToText(html: String): String {
    var s = html
    s = s.replace(Regex("(?is)<(script|style|head)\\b[^>]*>.*?</\\1>"), " ")
    s = s.replace(Regex("(?i)<(br|/p|/div|/h[1-6]|/li|/tr)\\b[^>]*>"), "\n")
    s = s.replace(Regex("<[^>]+>"), "")
    s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&#39;", "'").replace("&apos;", "'").replace("&quot;", "\"")
        .replace("&mdash;", "—").replace("&ndash;", "–")
        .replace("&rsquo;", "’").replace("&lsquo;", "‘")
        .replace("&ldquo;", "“").replace("&rdquo;", "”").replace("&hellip;", "…")
    s = s.replace(Regex("[ \\t]+"), " ").replace(Regex("\\n[ \\t]+"), "\n").replace(Regex("\\n{3,}"), "\n\n")
    return s.trim()
}
