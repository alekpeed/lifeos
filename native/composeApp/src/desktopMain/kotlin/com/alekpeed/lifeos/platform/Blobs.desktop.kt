package com.alekpeed.lifeos.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.io.File
import java.util.Base64
import java.util.UUID

// Desktop blob store — the same shape as Android's: bytes live in a folder beside the
// settings files, and records only carry the id. Until this existed, every attachment
// path on desktop quietly did nothing (saveBlob returned null), so photos, screenshots
// and Sharebox files had nowhere to go.
private fun blobsDir(): File? = try {
    File(System.getProperty("user.home"), ".lifeos/blobs").apply { if (!exists()) mkdirs() }
} catch (e: Exception) {
    null
}

actual fun saveBlob(base64: String): String? = try {
    val dir = blobsDir()
    if (dir == null) {
        null
    } else {
        val bytes = Base64.getDecoder().decode(base64.trim())
        val id = "blob_" + UUID.randomUUID().toString()
        File(dir, "$id.bin").writeBytes(bytes)
        id
    }
} catch (e: Exception) {
    null
}

actual fun deleteBlob(id: String) {
    if (id.isBlank()) return
    try {
        blobsDir()?.let { dir ->
            File(dir, "$id.bin").takeIf { it.exists() }?.delete()
            File(dir, "$id.txt").takeIf { it.exists() }?.delete()
        }
    } catch (e: Exception) {
        // best-effort
    }
}

actual fun readBlobBase64(id: String): String? = try {
    if (id.isBlank()) {
        null
    } else {
        val f = blobsDir()?.let { File(it, "$id.bin") }
        if (f == null || !f.exists()) null else Base64.getEncoder().encodeToString(f.readBytes())
    }
} catch (e: Exception) {
    null
}

actual fun loadBlobImage(id: String): ImageBitmap? = try {
    if (id.isBlank()) {
        null
    } else {
        val f = blobsDir()?.let { File(it, "$id.bin") }
        if (f == null || !f.exists()) null else Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
    }
} catch (e: Exception) {
    null
}

actual fun saveTextBlob(text: String): String? = try {
    val dir = blobsDir()
    if (dir == null) {
        null
    } else {
        val id = "text_" + UUID.randomUUID().toString()
        File(dir, "$id.txt").writeText(text)
        id
    }
} catch (e: Exception) {
    null
}

actual fun readTextBlob(id: String): String? = try {
    if (id.isBlank()) null else blobsDir()?.let { File(it, "$id.txt") }?.takeIf { it.exists() }?.readText()
} catch (e: Exception) {
    null
}

// --- Durability (R-01) ---------------------------------------------------------

// Blobs are named "<id>.bin" (images) or "<id>.txt" (text), and the id itself carries
// the kind in its prefix, so one helper covers both directions.
private fun blobFileName(id: String) = if (id.startsWith("text_")) "$id.txt" else "$id.bin"

actual fun blobIds(): List<String> = try {
    blobsDir()?.listFiles()
        ?.filter { it.isFile }
        ?.mapNotNull { f ->
            when {
                f.name.endsWith(".bin") -> f.name.removeSuffix(".bin")
                f.name.endsWith(".txt") -> f.name.removeSuffix(".txt")
                else -> null
            }
        }
        ?.sorted()
        .orEmpty()
} catch (e: Exception) {
    emptyList()
}

actual fun blobBytes(id: String): Long = try {
    if (id.isBlank()) 0L
    else blobsDir()?.let { File(it, blobFileName(id)) }?.takeIf { it.exists() }?.length() ?: 0L
} catch (e: Exception) {
    0L
}

actual fun readAnyBlobBase64(id: String): String? = try {
    if (id.isBlank()) null
    else blobsDir()
        ?.let { File(it, blobFileName(id)) }
        ?.takeIf { it.exists() }
        ?.let { Base64.getEncoder().encodeToString(it.readBytes()) }
} catch (e: Exception) {
    null
}

actual fun restoreBlob(id: String, base64: String): Boolean = try {
    val dir = blobsDir()
    if (dir == null || id.isBlank()) {
        false
    } else {
        File(dir, blobFileName(id)).writeBytes(Base64.getDecoder().decode(base64.trim()))
        true
    }
} catch (e: Exception) {
    false
}
