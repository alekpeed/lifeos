package com.alekpeed.lifeos.attach

import kotlinx.serialization.Serializable

// A file attached to a record — an image, a PDF, or any document. The bytes live
// in the device-local blob store (keyed by blobId, same store as photos/ebooks,
// kept OUT of the JSON backup and sync); the record only carries this small
// descriptor. This is the shared multi-file attachment primitive used by
// Documents, Books, Finance bills, Sharebox, etc.
@Serializable
data class Attachment(
    val blobId: String,        // id returned by saveBlob()
    val name: String,          // display filename ("lease.pdf")
    val mime: String = "",     // best-effort mime type
    val addedAt: String = "",  // ISO date it was attached
) {
    private val extLower: String get() = name.substringAfterLast('.', "").lowercase()

    val isImage: Boolean
        get() = mime.startsWith("image/") ||
            extLower in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp")

    val isPdf: Boolean get() = mime == "application/pdf" || extLower == "pdf"

    // A short type badge for the row ("PDF", "DOCX", "FILE").
    val badge: String get() = extLower.uppercase().ifBlank { if (isImage) "IMG" else "FILE" }
}
