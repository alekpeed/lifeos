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

// Persist a (possibly large) UTF-8 text blob device-locally — like image blobs,
// it stays out of the JSON backup and cross-device sync. Backs the Books reader's
// extracted ebook text. Returns an id, or null if there's no blob store.
expect fun saveTextBlob(text: String): String?

// Read a stored text blob back, or null if missing.
expect fun readTextBlob(id: String): String?

// Write a blob's bytes directly at a GIVEN id rather than minting a new one —
// what importing a record package (attach/RecordPackage.kt) needs: the id came
// from another device, and the record's JSON already references it, so nothing
// should change except the bytes now also existing here. A no-op success if a
// blob already sits at that id (re-importing the same package is harmless).
// False only if the write itself failed.
expect fun restoreBlob(id: String, bytes: ByteArray): Boolean

// Same, for a text blob.
expect fun restoreTextBlob(id: String, text: String): Boolean
