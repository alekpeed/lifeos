package com.alekpeed.lifeos.platform

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.UUID

private fun blobsDir(): File? {
    val ctx = NativeHost.ctx() ?: return null
    val dir = File(ctx.filesDir, "blobs")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

actual fun saveBlob(base64: String): String? = try {
    val dir = blobsDir() ?: return null
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    val id = "blob_" + UUID.randomUUID().toString()
    File(dir, "$id.bin").writeBytes(bytes)
    id
} catch (e: Exception) {
    null
}

actual fun deleteBlob(id: String) {
    if (id.isBlank()) return
    try {
        blobsDir()?.let { File(it, "$id.bin").takeIf { f -> f.exists() }?.delete() }
    } catch (e: Exception) {
        // best-effort
    }
}

actual fun readBlobBase64(id: String): String? = try {
    if (id.isBlank()) null
    else {
        val dir = blobsDir()
        val f = if (dir != null) File(dir, "$id.bin") else null
        if (f == null || !f.exists()) null else Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }
} catch (e: Exception) {
    null
}

actual fun saveTextBlob(text: String): String? = try {
    val dir = blobsDir() ?: return null
    val id = "text_" + UUID.randomUUID().toString()
    File(dir, "$id.txt").writeBytes(text.encodeToByteArray())
    id
} catch (e: Exception) {
    null
}

actual fun readTextBlob(id: String): String? = try {
    if (id.isBlank()) null
    else {
        val dir = blobsDir()
        val f = if (dir != null) File(dir, "$id.txt") else null
        if (f == null || !f.exists()) null else f.readBytes().decodeToString()
    }
} catch (e: Exception) {
    null
}

actual fun loadBlobImage(id: String): ImageBitmap? = try {
    if (id.isBlank()) null
    else {
        val dir = blobsDir()
        val f = if (dir != null) File(dir, "$id.bin") else null
        if (f == null || !f.exists()) null
        else {
            val bytes = f.readBytes()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
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
        ?.let { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
} catch (e: Exception) {
    null
}

actual fun restoreBlob(id: String, base64: String): Boolean = try {
    val dir = blobsDir()
    if (dir == null || id.isBlank()) {
        false
    } else {
        File(dir, blobFileName(id)).writeBytes(Base64.decode(base64, Base64.DEFAULT))
        true
    }
} catch (e: Exception) {
    false
}
