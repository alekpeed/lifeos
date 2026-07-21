package com.alekpeed.lifeos.platform

import androidx.compose.ui.graphics.ImageBitmap

// A tiny PDF page-rasterizer for the in-app reader. Android renders pages natively
// via PdfRenderer; desktop has no built-in engine, so open() returns 0 and the
// reader falls back to opening the file externally.
//
// Stateful and single-document by design: open() caches one PDF (the book being
// read), render() rasterizes a page from it, close() releases it. Only one reader
// is on screen at a time. Call render() off the main thread.
expect object PdfReader {
    // Load a PDF from its base64 bytes and return the page count (0 if it can't be
    // opened / the platform has no renderer). Replaces any previously open doc.
    fun open(base64: String): Int

    // Rasterize a page (0-based) scaled to targetWidthPx, preserving aspect ratio,
    // as an ImageBitmap — or null if out of range / render failed.
    fun render(page: Int, targetWidthPx: Int): ImageBitmap?

    // Release the open document.
    fun close()
}
