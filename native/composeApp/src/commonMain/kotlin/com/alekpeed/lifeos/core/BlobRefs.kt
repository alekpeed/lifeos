package com.alekpeed.lifeos.core

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.platform.blobBytes
import com.alekpeed.lifeos.platform.blobIds

// Which records point at which blobs — the missing half of the blob store.
//
// Blobs are written by id and read by id, and nothing ever asked the reverse question:
// *who still refers to this?* Without that, a backup cannot tell an attachment worth
// keeping from a map tile worth regenerating, and a cleanup cannot tell a genuinely
// unreferenced file from one whose owner it simply failed to look at.
//
// Rather than teach this layer every module's schema, it scans the stored text of each
// key for blob ids. Ids are distinctive enough to match unambiguously, and a scan means
// a new module gets covered for free instead of being silently omitted the way the
// DATA_SOURCES-driven backup omitted every key nobody remembered to add.
private val BLOB_ID = Regex("(?:blob|text)_[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

// Keys whose blobs are a regenerable cache, not user data.
//
// This distinction is load-bearing in both directions. The Places map keeps up to 600
// tiles in the blob store, so treating them as attachments would bloat every backup with
// data that redownloads itself — and treating them as unreferenced would delete a live
// cache, because MapTileIndex is not in DATA_SOURCES and so is invisible to anything
// that walks that list.
private val CACHE_KEYS = setOf("MapTileIndex")

// Keys never written to a backup.
//
// Internal keys (`__syncmeta`, `__lastsync`) describe this device's sync position;
// restoring another device's copy would make the engine believe it had already pushed
// changes it has not. The three credentials are excluded because a backup is shared
// through the OS share sheet — the whole point is to hand it to something else.
private const val INTERNAL_PREFIX = "__"
private val SECRET_KEYS = setOf("ApiKey", "GeminiKey", "OpenAiKey", "HomeToken")

fun isBackupKey(key: String): Boolean =
    !key.startsWith(INTERNAL_PREFIX) && key !in SECRET_KEYS && key !in CACHE_KEYS

// Every blob id mentioned by a key's stored text.
private fun referencedBy(key: String): Set<String> =
    Storage.read(key)?.let { text -> BLOB_ID.findAll(text).map { it.value }.toSet() }.orEmpty()

data class BlobAudit(
    // Referenced by a real record, and present. These are what a backup must carry.
    val documents: List<String>,
    // Referenced only by a cache index. Present, wanted, but regenerable.
    val cache: List<String>,
    // Present but referenced by nothing — leftovers from deletes that missed a blob.
    val orphans: List<String>,
    // Referenced by a record but absent from the store. Broken attachments: this is
    // what a synced record looks like on a second device today, since the reference
    // travels and the bytes do not.
    val dangling: List<String>,
) {
    val documentBytes: Long get() = documents.sumOf { blobBytes(it) }
    val cacheBytes: Long get() = cache.sumOf { blobBytes(it) }
    val orphanBytes: Long get() = orphans.sumOf { blobBytes(it) }
}

fun auditBlobs(): BlobAudit {
    val present = blobIds().toSet()

    val documentRefs = mutableSetOf<String>()
    val cacheRefs = mutableSetOf<String>()
    for (key in Storage.keys()) {
        if (key.startsWith(INTERNAL_PREFIX)) continue
        val refs = referencedBy(key)
        if (refs.isEmpty()) continue
        if (key in CACHE_KEYS) cacheRefs += refs else documentRefs += refs
    }

    // A blob referenced by both a record and a cache index is a document: the record's
    // claim is the one that would lose data if ignored.
    val cacheOnly = cacheRefs - documentRefs

    return BlobAudit(
        documents = (documentRefs intersect present).sorted(),
        cache = (cacheOnly intersect present).sorted(),
        orphans = (present - documentRefs - cacheOnly).sorted(),
        dangling = (documentRefs - present).sorted(),
    )
}

// Human-readable size, for the Settings readout.
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${(bytes / 100_000_000) / 10.0} GB"
    bytes >= 1_000_000 -> "${(bytes / 100_000) / 10.0} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
