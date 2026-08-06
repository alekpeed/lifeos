package com.alekpeed.lifeos.platform

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

// Stream a picked file (or the first .xml/.csv entry of a .zip, detected by magic
// bytes) line-by-line, keeping lines that contain any of the filter substrings,
// capped at ~8 MB of kept text. Built for the Apple Health export, whose export.xml
// runs to hundreds of MB — the whole file never sits in memory at once.
//
// Plain java.io / java.util.zip — shared between Android and desktop rather than
// duplicated, since neither platform's version of "open a file and read bytes" has
// anything to do with this part.
object FilteredTextReader {
    fun read(raw: InputStream, filter: List<String>): String? {
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
}
