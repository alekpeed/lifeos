package com.alekpeed.lifeos.attach

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Plain zip read/write, no app knowledge — the byte-level half of record
// export/import (attach/RecordPackage.kt, commonMain). java.util.zip, so the
// actual lives here rather than commonMain, same reasoning as EbookParser /
// FilteredTextReader — but written ONCE and shared by both real targets rather
// than duplicated, since jvmShared sits on the path to both.
actual object ZipPackage {
    actual fun zip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    actual fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) out[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return out
    }
}
