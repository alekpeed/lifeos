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
