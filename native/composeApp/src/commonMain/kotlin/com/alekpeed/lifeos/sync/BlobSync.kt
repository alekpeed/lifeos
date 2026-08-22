package com.alekpeed.lifeos.sync

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.core.auditBlobs
import com.alekpeed.lifeos.net.httpGetBytesBase64
import com.alekpeed.lifeos.net.httpSendBytes
import com.alekpeed.lifeos.platform.readAnyBlobBase64
import com.alekpeed.lifeos.platform.restoreBlob

// Attachment transport (R-01, second half).
//
// SyncEngine moves Storage keys, which are text. An attachment is not text: the record
// carries only a blob id, so syncing records alone delivers a reference whose bytes stay
// on the device that made them. That is what a photo looks like on the second device
// today — present in the record, missing on screen.
//
// Bytes go to a private Storage bucket rather than into `sync_records`, for the same
// reason Sharebox files do: a pull fetches every row it can see, so a base64 photo in a
// row would be re-downloaded on every sync by every device forever.
//
// The queues fall out of the audit rather than needing a manifest:
//
//   * upload  = documents present here but not yet marked uploaded
//   * download = `dangling` — ids a record references that this device does not hold,
//     which after a pull is exactly the set that arrived from somewhere else
//
// So a device asks for precisely what it is missing, self-corrects if an upload failed
// halfway, and never needs to list the bucket.
object BlobSync {
    private const val BUCKET = "attachments"
    private val OBJECT = "${SupabaseConfig.URL}/storage/v1/object"

    // Device-local record of what this device has already sent. The `__` prefix keeps it
    // out of both SyncMeta (so it is never pushed as user data) and the backup.
    private const val K_UPLOADED = "__blobsUploaded"

    // Bound on one run. A first sync over a large library would otherwise hold the whole
    // sync open; the rest go on the next pass, and the caller is told how many are left
    // rather than the cap being silent.
    private const val PER_RUN = 25

    data class BlobSummary(val uploaded: Int, val downloaded: Int, val failed: Int, val remaining: Int)

    private fun uploadedIds(): Set<String> =
        Storage.read(K_UPLOADED)?.lineSequence()?.filter { it.isNotBlank() }?.toSet().orEmpty()

    private fun rememberUploaded(ids: Set<String>) {
        Storage.write(K_UPLOADED, ids.joinToString("\n"))
    }

    private fun headers(contentType: String? = null): Map<String, String> = buildMap {
        put("apikey", SupabaseConfig.ANON_KEY)
        SupabaseAuth.accessToken()?.let { put("Authorization", "Bearer $it") }
        contentType?.let { put("content-type", it) }
    }

    // Send attachments this device holds but has not uploaded. Runs before records are
    // pushed, so a record never reaches another device ahead of its bytes.
    suspend fun push(): BlobSummary {
        val uid = SupabaseAuth.userId() ?: return BlobSummary(0, 0, 0, 0)
        val already = uploadedIds()
        val pending = auditBlobs().documents.filterNot { it in already }
        if (pending.isEmpty()) return BlobSummary(0, 0, 0, 0)

        val batch = pending.take(PER_RUN)
        val sent = mutableSetOf<String>()
        var failed = 0
        for (id in batch) {
            val base64 = readAnyBlobBase64(id)
            if (base64 == null) { failed++; continue }
            // Blob ids are random and their bytes are never rewritten in place — editing a
            // photo writes a new blob and drops the old one — so an upload is idempotent
            // and upsert simply makes a retry after a lost marker succeed instead of 409.
            var res = httpSendBytes(
                "POST", "$OBJECT/$BUCKET/$uid/$id",
                headers("application/octet-stream") + ("x-upsert" to "true"),
                base64,
            )
            if (res.status == 401 && SupabaseAuth.refresh()) {
                res = httpSendBytes(
                    "POST", "$OBJECT/$BUCKET/$uid/$id",
                    headers("application/octet-stream") + ("x-upsert" to "true"),
                    base64,
                )
            }
            if (res.ok) sent += id else failed++
        }
        if (sent.isNotEmpty()) rememberUploaded(already + sent)
        return BlobSummary(sent.size, 0, failed, (pending.size - batch.size).coerceAtLeast(0))
    }

    // Fetch attachments this device's records point at but does not hold. Runs after the
    // record pull, because that pull is what creates the gap.
    suspend fun pull(): BlobSummary {
        val uid = SupabaseAuth.userId() ?: return BlobSummary(0, 0, 0, 0)
        val missing = auditBlobs().dangling
        if (missing.isEmpty()) return BlobSummary(0, 0, 0, 0)

        val batch = missing.take(PER_RUN)
        var got = 0
        var failed = 0
        for (id in batch) {
            val url = "$OBJECT/$BUCKET/$uid/$id"
            val base64 = httpGetBytesBase64(url, headers())
                ?: if (SupabaseAuth.refresh()) httpGetBytesBase64(url, headers()) else null
            when {
                base64 == null -> failed++
                restoreBlob(id, base64) -> got++
                else -> failed++
            }
        }
        return BlobSummary(0, got, failed, (missing.size - batch.size).coerceAtLeast(0))
    }

    // Deliberately absent: deleting remote objects for blobs no longer referenced here.
    // Another device may still hold the record that references them, and its copy could
    // be the older one — removing the bytes on this device's say-so would destroy an
    // attachment that device still expects. An unreferenced object costs storage; a
    // deleted one cannot be recovered. Same call Sharebox makes for the same reason.
}
