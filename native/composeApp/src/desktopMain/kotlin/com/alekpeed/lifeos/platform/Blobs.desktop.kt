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
