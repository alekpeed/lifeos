package com.alekpeed.lifeos.platform

import androidx.compose.ui.graphics.ImageBitmap

// Read a bundled image asset (androidMain/assets) as an ImageBitmap, or null if
// missing / undecodable. Backs a graphical interface's full-screen artwork. Only
// Android supplies real bitmaps; desktop returns null and callers fall back to the
// built-in functional screen.
expect fun loadImageAsset(name: String): ImageBitmap?

// Decode one image whose base64 payload is split across bundled text assets. This
// keeps generated binary artwork reproducible through text-only repository writes.
expect fun loadBase64ImageAsset(parts: List<String>): ImageBitmap?
