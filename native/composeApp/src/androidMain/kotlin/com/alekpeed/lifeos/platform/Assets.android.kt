package com.alekpeed.lifeos.platform

actual fun loadTextAsset(name: String): String? = try {
    NativeHost.ctx()?.assets?.open(name)?.use { it.readBytes().decodeToString() }
} catch (e: Exception) {
    null
}
