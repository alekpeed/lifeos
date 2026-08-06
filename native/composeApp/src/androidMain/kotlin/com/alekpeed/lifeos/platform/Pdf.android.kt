package com.alekpeed.lifeos.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

// Android PDF rasterizer backed by the framework PdfRenderer. The base64 bytes are
// written to a private cache file (PdfRenderer needs a seekable file descriptor),
// opened once, and each page rendered to a white-backed ARGB bitmap on demand.
actual object PdfReader {
    private var renderer: android.graphics.pdf.PdfRenderer? = null
    private var pfd: android.os.ParcelFileDescriptor? = null
    private var tmp: java.io.File? = null

    actual fun open(base64: String): Int {
        close()
        return try {
            val ctx = NativeHost.ctx() ?: return 0
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            val f = java.io.File(ctx.cacheDir, "reader.pdf")
            java.io.FileOutputStream(f).use { it.write(bytes) }
            val d = android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val r = android.graphics.pdf.PdfRenderer(d)
            tmp = f; pfd = d; renderer = r
            r.pageCount
        } catch (e: Exception) {
            close()
            0
        }
    }

    actual fun render(page: Int, targetWidthPx: Int): ImageBitmap? {
        val r = renderer ?: return null
        if (page < 0 || page >= r.pageCount) return null
        return try {
            val p = r.openPage(page)
            val w = targetWidthPx.coerceIn(240, 2400)
            val h = (w.toFloat() * p.height / p.width).toInt().coerceIn(1, 6000)
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            p.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            p.close()
            bmp.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    actual fun close() {
        try { renderer?.close() } catch (e: Exception) {}
        try { pfd?.close() } catch (e: Exception) {}
        try { tmp?.delete() } catch (e: Exception) {}
        renderer = null; pfd = null; tmp = null
    }
}
