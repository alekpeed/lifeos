package com.alekpeed.lifeos.platform

actual fun loadTextAsset(name: String): String? = try {
    object {}.javaClass.classLoader?.getResourceAsStream(name)?.use { it.readBytes().decodeToString() }
} catch (e: Exception) {
    null
}
