package com.alekpeed.lifeos.platform

import java.util.zip.ZipInputStream

// The only platform-specific half of the ebook reader: pull a zip apart in memory.
actual fun unzipEntries(bytes: ByteArray): Map<String, ByteArray> = try {
    val out = HashMap<String, ByteArray>()
    ZipInputStream(bytes.inputStream()).use { zip ->
        var e = zip.nextEntry
        while (e != null) {
            if (!e.isDirectory) out[e.name] = zip.readBytes()
            e = zip.nextEntry
        }
    }
    out
} catch (e: Exception) {
    emptyMap()
}
