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
            if (::appContext.isInitialized) file(name).writeText(text)
        } catch (e: Exception) {
            // best-effort
        }
    }
}
