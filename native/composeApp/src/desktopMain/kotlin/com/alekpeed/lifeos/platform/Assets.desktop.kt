package com.alekpeed.lifeos.platform

actual fun loadTextAsset(name: String): String? = try {
    object {}.javaClass.classLoader?.getResourceAsStream(name)?.use { it.readBytes().decodeToString() }
} catch (e: Exception) {
    null
}

// Desktop has no bundled interface artwork; graphical interfaces are mobile-only
// and callers fall back to the built-in functional screen.
actual fun loadImageAsset(name: String): androidx.compose.ui.graphics.ImageBitmap? = null
