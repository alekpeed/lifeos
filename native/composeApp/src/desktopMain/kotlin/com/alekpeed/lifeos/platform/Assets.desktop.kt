package com.alekpeed.lifeos.platform

import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Image

actual fun loadTextAsset(name: String): String? = try {
    object {}.javaClass.classLoader?.getResourceAsStream(name)?.use { it.readBytes().decodeToString() }
} catch (e: Exception) {
    null
}

actual fun loadImageAsset(name: String): androidx.compose.ui.graphics.ImageBitmap? = try {
    object {}.javaClass.classLoader?.getResourceAsStream(name)?.use { stream ->
        Image.makeFromEncoded(stream.readBytes()).asImageBitmap()
    }
} catch (e: Exception) {
    null
}

actual fun loadBase64ImageAsset(parts: List<String>): androidx.compose.ui.graphics.ImageBitmap? = null
