package com.alekpeed.lifeos.platform

import java.util.zip.ZipInputStream

// Turn a picked ebook's bytes into readable plain text. A .txt (no PK zip magic)
// decodes directly; an EPUB is unzipped, its OPF read for the real reading order
// (spine → manifest hrefs), and each chapter's XHTML stripped to text. Falls back to
// name-sorted XHTML entries when there's no usable OPF.
//
// Plain java.util.zip + regex — no Android API — which is why it lives here rather
// than duplicated per platform: Android and desktop both pick a file their own way,
// then hand the bytes to this.
object EbookParser {
    fun parse(bytes: ByteArray): String {
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
            entries.keys.filter { val l = it.lowercase(); l.endsWith(".xhtml") || l.endsWith(".html") || l.endsWith(".htm") }.sorted()
        }
        // Each spine item becomes a chapter, delimited by a private-use marker
        // (U+E000titleU+E000) the reader parses into a table of contents. The title
        // comes from the item's first heading / <title>, else "Chapter N".
        val sb = StringBuilder()
        var chapterNum = 0
        for (href in order) {
            val path = href.substringBefore('#')
            val data = entries[path] ?: entries[path.substringAfterLast('/')] ?: continue
            val html = data.decodeToString()
            val body = htmlToText(html)
            if (body.isBlank()) continue
            chapterNum++
            val title = chapterTitle(html) ?: "Chapter $chapterNum"
            sb.append('\uE000').append(title).append('\uE000').append('\n')
            sb.append(body).append("\n\n")
        }
        return sb.toString().trim().ifBlank { "(Couldn't extract readable text from this EPUB.)" }
    }

    // The chapter heading for a spine document: its first h1–h3, else its <title>,
    // stripped of tags and clamped to a sane length. Null if nothing usable.
    private fun chapterTitle(html: String): String? {
        val raw = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>").find(html)?.groupValues?.get(1)
            ?: Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)
            ?: return null
        val text = Regex("<[^>]+>").replace(raw, "").replace(Regex("\\s+"), " ").trim()
        return text.takeIf { it.isNotBlank() && it.length <= 80 }
    }

    private fun htmlToText(html: String): String {
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
}
