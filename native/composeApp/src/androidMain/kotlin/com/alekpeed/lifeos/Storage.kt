package com.alekpeed.lifeos

import android.content.Context
import java.io.File

actual object Storage {
    // Set from MainActivity before any UI reads/writes.
    lateinit var appContext: Context

    private fun file(name: String) = File(appContext.filesDir, "$name.txt")

    actual fun read(name: String): String? = try {
        if (!::appContext.isInitialized) null
        else file(name).takeIf { it.exists() }?.readText()
    } catch (e: Exception) {
        null
    }

    actual fun write(name: String, text: String) {
        try {
            if (::appContext.isInitialized) {
                // Read before overwriting: the mutation log diffs the two to work out
                // which records actually changed (R-02/R-03). Only for keys it tracks —
                // the map tile cache should not pay for a second full read per write.
                val previous = if (com.alekpeed.lifeos.history.History.tracks(name)) read(name) else null
                file(name).writeText(text)
                com.alekpeed.lifeos.sync.SyncMeta.record(name)
                com.alekpeed.lifeos.history.History.onWrite(name, previous, text)
            }
        } catch (e: Exception) {
            // best-effort
        }
    }

    actual fun keys(): List<String> = try {
        if (!::appContext.isInitialized) emptyList()
        else appContext.filesDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.map { it.name.removeSuffix(".txt") }
            ?.sorted()
            .orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    actual fun remove(name: String) {
        try {
            if (::appContext.isInitialized) {
                val previous = if (com.alekpeed.lifeos.history.History.tracks(name)) read(name) else null
                file(name).takeIf { it.exists() }?.delete()
                com.alekpeed.lifeos.sync.SyncMeta.tombstone(name)
                com.alekpeed.lifeos.history.History.onRemove(name, previous)
            }
        } catch (e: Exception) {
            // best-effort
        }
    }
}
