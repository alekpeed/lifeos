package com.alekpeed.lifeos.platform

// EPUB/TXT → readable plain text, shared by every build.
//
// This used to live inside Android's MainActivity, which is why desktop could pick a
// file but never read one: the parser wasn't reachable from there. Only the ten lines
// of zip extraction are platform-specific now — both targets are JVM, but the project
// declares no shared JVM source set, so the split is drawn at `unzipEntries` and
// everything above it is common.

// Entry name -> bytes for every non-directory entry. Empty if the bytes aren't a
// readable zip.
expect fun unzipEntries(bytes: ByteArray): Map<String, ByteArray>

// The marker wrapping a chapter title in the returned text; the reader splits on it to
// build its table of contents. A private-use codepoint so real book text can't collide.
const val EBOOK_CHAPTER_MARK = '\uE000'

// Turn a picked ebook's bytes into readable plain text. A .txt (no PK zip magic)
// decodes directly; an EPUB is unzipped, its OPF read for the real reading order
// (spine → manifest hrefs), and each chapter's XHTML stripped to text. Falls back to
// name-sorted XHTML entries when there's no usable OPF.
fun parseEbook(bytes: ByteArray): String {
    if (bytes.size < 2 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
        return bytes.decodeToString().trim().ifBlank { "(Empty file.)" }
    }
    val entries = unzipEntries(bytes)
    if (entries.isEmpty()) return "(Couldn't read this file as an EPUB.)"

    val opfName = entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
    val order: List<String> = if (opfName != null) {
        val opf = entries.getValue(opfName).decodeToString()
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

    // Each spine item becomes a chapter, delimited by the private-use marker the reader
    // parses into a table of contents. The title comes from the item's first heading /
    // <title>, else "Chapter N".
    val sb = StringBuilder()
    var chapterNum = 0
    for (href in order) {
        val path = href.substringBefore('#')
        val data = entries[path] ?: entries[path.substringAfterLast('/')] ?: continue
        val body = htmlToText(data.decodeToString())
        if (body.isBlank()) continue
        chapterNum++
        val title = ebookChapterTitle(data.decodeToString()) ?: "Chapter $chapterNum"
        sb.append(EBOOK_CHAPTER_MARK).append(title).append(EBOOK_CHAPTER_MARK).append('\n')
        sb.append(body).append("\n\n")
    }
    return sb.toString().trim().ifBlank { "(Couldn't extract readable text from this EPUB.)" }
}

// The chapter heading for a spine document: its first h1–h3, else its <title>, stripped
// of tags and clamped to a sane length. Null if nothing usable.
private fun ebookChapterTitle(html: String): String? {
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
