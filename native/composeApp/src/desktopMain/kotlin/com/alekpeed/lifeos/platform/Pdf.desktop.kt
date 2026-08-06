package com.alekpeed.lifeos.platform

import androidx.compose.ui.graphics.ImageBitmap

// Desktop has no bundled PDF engine — open() reports "unsupported" (0 pages) and
// the reader falls back to opening the PDF in the system viewer.
actual object PdfReader {
    actual fun open(base64: String): Int = 0
    actual fun render(page: Int, targetWidthPx: Int): ImageBitmap? = null
    actual fun close() {}
}
