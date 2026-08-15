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
