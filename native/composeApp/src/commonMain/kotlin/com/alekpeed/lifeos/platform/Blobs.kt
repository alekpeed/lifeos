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

// --- Durability (R-01) ---------------------------------------------------------
//
// The store was addressable only by an id someone already held, so nothing could ask
// what it contained. That is the whole reason attachments never reached a backup: an
// exporter had no way to enumerate them, and a restore had no way to put one back
// under the id its record still points at.

// Every blob id currently held, image and text alike.
expect fun blobIds(): List<String>

// Size on disk of one blob in bytes, or 0 if it is missing.
expect fun blobBytes(id: String): Long

// Read any blob — image or text — as base64 of its raw bytes. readBlobBase64 only
// ever looked at image blobs, so an exporter using it would silently drop ebooks.
expect fun readAnyBlobBase64(id: String): String?

// Write a blob back under a SPECIFIC id. Restoring must preserve ids: every record
// references its attachment by id, so a fresh id would leave the record pointing at
// nothing. Returns false if it could not be written.
expect fun restoreBlob(id: String, base64: String): Boolean
