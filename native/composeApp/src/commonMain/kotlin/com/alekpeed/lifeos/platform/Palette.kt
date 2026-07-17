package com.alekpeed.lifeos.platform

// Extract a small dominant-color palette (hex "#RRGGBB") from a base64 JPEG.
// Android decodes the image and quantizes its pixels; desktop has no bitmap layer
// and returns an empty list. Backs Theme-from-Photo.
expect fun paletteFromBase64(base64: String): List<String>
