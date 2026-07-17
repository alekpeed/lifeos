package com.alekpeed.lifeos.platform

import androidx.compose.ui.graphics.ImageBitmap

// The on-device blob store: binary attachments (images) live as their own files,
// referenced from a record by an opaque id, so the record's JSON stays small.
// Device-local — blobs are not part of the JSON backup or sync (like the web
// app's separate attachment binaries).

// Persist a base64-encoded image; returns an id to keep on the record, or null if
// it couldn't be saved / the platform has no blob store.
expect fun saveBlob(base64: String): String?

// Delete a stored blob by id (no-op if missing / already gone).
expect fun deleteBlob(id: String)

// Decode a stored blob into an ImageBitmap for display, or null if missing.
expect fun loadBlobImage(id: String): ImageBitmap?

// Read a stored blob back as a base64 string (for re-sending to a vision API),
// or null if missing.
expect fun readBlobBase64(id: String): String?
