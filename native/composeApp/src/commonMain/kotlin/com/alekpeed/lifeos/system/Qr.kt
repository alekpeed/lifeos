package com.alekpeed.lifeos.system

// A square QR module grid: `size` × `size` cells, `modules` row-major (true = dark).
// Rendered by QrCode() with Compose Canvas — no image dependency needed.
data class QrMatrix(val size: Int, val modules: BooleanArray)

// Encode text into a QR module grid. Both targets are JVM, so the actuals use the
// zxing encoder. Returns null if the text can't be encoded.
expect fun encodeQr(text: String): QrMatrix?
