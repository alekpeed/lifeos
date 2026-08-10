package com.alekpeed.lifeos.platform

import androidx.compose.ui.graphics.asImageBitmap

actual fun loadTextAsset(name: String): String? = try {
    NativeHost.ctx()?.assets?.open(name)?.use { it.readBytes().decodeToString() }
} catch (e: Exception) {
    null
}

actual fun loadImageAsset(name: String): androidx.compose.ui.graphics.ImageBitmap? = try {
    NativeHost.ctx()?.assets?.open(name)?.use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
    }
} catch (e: Exception) {
    null
}

actual fun loadBase64ImageAsset(parts: List<String>): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val ctx = NativeHost.ctx() ?: return null
        val encoded = buildString {
            parts.forEach { name ->
                append(ctx.assets.open(name).use { it.readBytes().decodeToString() })
            }
        }
        val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
