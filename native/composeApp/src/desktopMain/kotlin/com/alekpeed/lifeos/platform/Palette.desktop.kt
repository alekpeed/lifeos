package com.alekpeed.lifeos.platform

// Desktop has no bitmap/camera layer; Theme-from-Photo falls back to the presets.
actual fun paletteFromBase64(base64: String): List<String> = emptyList()
