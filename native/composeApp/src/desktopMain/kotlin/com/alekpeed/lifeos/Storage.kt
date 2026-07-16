package com.alekpeed.lifeos

import java.io.File

actual object Storage {
    private val dir = File(System.getProperty("user.home"), ".lifeos").apply { runCatching { mkdirs() } }

    private fun file(name: String) = File(dir, "$name.txt")

    actual fun read(name: String): String? = try {
        file(name).takeIf { it.exists() }?.readText()
    } catch (e: Exception) {
        null
    }

    actual fun write(name: String, text: String) {
        try {
            file(name).writeText(text)
            com.alekpeed.lifeos.sync.SyncMeta.record(name)
        } catch (e: Exception) {
            // best-effort
        }
    }

    actual fun remove(name: String) {
        try {
            file(name).takeIf { it.exists() }?.delete()
            com.alekpeed.lifeos.sync.SyncMeta.tombstone(name)
        } catch (e: Exception) {
            // best-effort
        }
    }
}
