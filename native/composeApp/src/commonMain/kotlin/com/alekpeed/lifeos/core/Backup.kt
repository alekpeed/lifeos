package com.alekpeed.lifeos.core

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.platform.readAnyBlobBase64
import com.alekpeed.lifeos.platform.restoreBlob
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Manual, Drive-independent backup.
//
// Two things were wrong with the original, and both lost data silently:
//
// 1. It walked DATA_SOURCES, so any key not on that list was never backed up — among
//    them SavedZones, PaperEditorialHistory and RecapNarrative. It now walks the store
//    itself, so a new module is covered without anyone remembering to add it.
// 2. Attachments were left out entirely. The file said they "would ride along once the
//    media layer exists" — it exists, so they do. Until now every photo, scan, ebook
//    and PDF was single-device: absent from the backup, absent from sync, and gone with
//    the device.
private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

const val BACKUP_VERSION = 2

@Serializable
data class BackupFile(
    val version: Int = BACKUP_VERSION,
    val records: Map<String, String> = emptyMap(),
    // blob id -> base64 of its bytes. Ids are preserved on restore because every record
    // references its attachment by id.
    val blobs: Map<String, String> = emptyMap(),
)

// A full backup. `includeBlobs` is false for the share-as-text path, which has to stay
// small enough to survive a clipboard; the file export passes true.
//
// Known limit: this builds the whole backup as one in-memory string, and base64 adds a
// third again on top of the bytes. Fine for a normal library of photos and scans; a very
// large one would want streaming straight to the file instead.
fun exportBackup(includeBlobs: Boolean = true): String {
    val records = LinkedHashMap<String, String>()
    Storage.keys()
        .filter { isBackupKey(it) }
        .forEach { key -> Storage.read(key)?.takeIf { it.isNotBlank() }?.let { records[key] = it } }

    val blobs = LinkedHashMap<String, String>()
    if (includeBlobs) {
        // Documents only. Cache blobs redownload themselves and orphans are already
        // unreferenced, so carrying either would only inflate the file.
        auditBlobs().documents.forEach { id -> readAnyBlobBase64(id)?.let { blobs[id] = it } }
    }
    return json.encodeToString(BackupFile(records = records, blobs = blobs))
}

// Kept so the existing Settings button and any backup already sitting in a note still
// work: text-only, no attachments.
fun exportBackupJson(): String = exportBackup(includeBlobs = false)

data class RestoreResult(val records: Int, val blobs: Int, val blobsFailed: Int) {
    val ok: Boolean get() = records >= 0
    companion object {
        val INVALID = RestoreResult(-1, 0, 0)
    }
}

// Restore from a backup. Accepts both the current envelope and the original flat
// {key: value} map, so older backups still import.
fun importBackup(text: String): RestoreResult {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return RestoreResult.INVALID

    // The emptiness check is load-bearing, not a tidy-up. Every field of BackupFile has
    // a default and the parser ignores unknown keys, so a legacy {key: value} backup
    // decodes as a perfectly valid BackupFile with no records at all — and would report
    // success while restoring nothing. Requiring content forces those through the flat-map
    // branch below.
    val parsed = runCatching { json.decodeFromString<BackupFile>(trimmed) }
        .getOrNull()
        ?.takeIf { it.records.isNotEmpty() || it.blobs.isNotEmpty() }
        ?: runCatching { json.decodeFromString<Map<String, String>>(trimmed) }
            .getOrNull()
            ?.let { BackupFile(version = 1, records = it) }
        ?: return RestoreResult.INVALID

    // Blobs first: a record restored before its attachment would render as broken for
    // as long as the write takes, and a failure part-way leaves records pointing at
    // bytes that never arrived.
    var restored = 0
    var failed = 0
    parsed.blobs.forEach { (id, b64) -> if (restoreBlob(id, b64)) restored++ else failed++ }

    var written = 0
    parsed.records.forEach { (k, v) ->
        if (isBackupKey(k)) {
            Storage.write(k, v)
            written++
        }
    }
    return RestoreResult(written, restored, failed)
}

// Restore from a picked file, whose bytes arrive base64-encoded from pickAttachment.
// The decode lives here so the experimental encoding API is opted into in one place
// rather than at every call site.
@OptIn(ExperimentalEncodingApi::class)
fun importBackupFromBase64(base64: String?): RestoreResult {
    val text = base64
        ?.let { runCatching { Base64.decode(it).decodeToString() }.getOrNull() }
        ?: return RestoreResult.INVALID
    return importBackup(text)
}

// Original signature, kept for the clipboard path: module count, or -1 if invalid.
fun importBackupJson(text: String): Int = importBackup(text).records
