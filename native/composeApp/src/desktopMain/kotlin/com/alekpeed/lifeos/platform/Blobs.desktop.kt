package com.alekpeed.lifeos.platform

import androidx.compose.ui.graphics.ImageBitmap

// Desktop has no camera/attachment flow yet; blobs are a no-op here.
actual fun saveBlob(base64: String): String? = null
actual fun deleteBlob(id: String) {}
actual fun loadBlobImage(id: String): ImageBitmap? = null
actual fun readBlobBase64(id: String): String? = null
