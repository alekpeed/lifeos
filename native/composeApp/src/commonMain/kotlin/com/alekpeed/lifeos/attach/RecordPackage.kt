package com.alekpeed.lifeos.attach

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.platform.readBlobBase64
import com.alekpeed.lifeos.platform.readTextBlob
import com.alekpeed.lifeos.platform.restoreBlob
import com.alekpeed.lifeos.platform.restoreTextBlob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Export one record (with its photos/files) as a single file you can move to
// another device by hand — a USB stick, a synced folder, however you'd move any
// file — and import it there. This exists as the middle ground between two
// options that both have real costs: automatic cross-device blob sync (uploads
// every photo to Supabase Storage — money and a privacy property this app doesn't
// currently have) and leaving blobs device-local forever (a record that syncs
// its text can end up pointing at a photo that only exists on the device it was
// taken on, with the app quietly unable to show it). This is a snapshot, not
// live sync: re-export and re-import to pick up a later change.
//
// One registration per module (ModuleDescriptor) is all that's needed — export
// and import both work generically off it, because blobs are found by SCANNING
// the record's JSON for anything shaped like a blob id, not by hand-listing which
// field holds one per module. Every blob id this app mints looks like
// "blob_<uuid>" or "text_<uuid>" (see platform/Blobs.kt), which is distinctive
// enough to find reliably at any nesting depth — a flat photoBlob field, an
// attachments[] list, Books' files[], Photos' captions nested inside an album,
// all fall out of the same scan with no per-shape code.

data class ModuleDescriptor(
    val storageKey: String,   // the Storage key this module's data lives under
    val listPath: String,     // the JSON array key inside that data holding records
    val label: String,        // singular noun for messages, e.g. "document"
)

// One named constant per exportable list, so a screen references e.g.
// DOCUMENTS_MODULE rather than retyping the storage key / list path / label
// (and risking a typo the compiler can't catch, since these are plain strings).
val DOCUMENTS_MODULE = ModuleDescriptor("Documents", "documents", "document")
val LINKS_MODULE = ModuleDescriptor("Links", "links", "link")
val BOOKS_MODULE = ModuleDescriptor("Books", "books", "book")
val COLLECTIONS_MODULE = ModuleDescriptor("Collections", "collections", "collection")
val PLACES_MODULE = ModuleDescriptor("Places", "places", "place")
val RECIPES_MODULE = ModuleDescriptor("Recipes", "recipes", "recipe")
val MILESTONES_MODULE = ModuleDescriptor("Milestones", "milestones", "milestone")
val CONTACTS_MODULE = ModuleDescriptor("Contacts", "contacts", "contact")
val QUARTERMASTER_MODULE = ModuleDescriptor("Quartermaster", "items", "item")
val RABBIT_HOLES_MODULE = ModuleDescriptor("Rabbit Holes", "holes", "thread")
val TIME_CAPSULES_MODULE = ModuleDescriptor("Time Capsules", "capsules", "capsule")
val PHOTOS_MODULE = ModuleDescriptor("Photos", "albums", "album")
val FINANCE_ENTRIES_MODULE = ModuleDescriptor("Finance", "entries", "ledger entry")
val FINANCE_BILLS_MODULE = ModuleDescriptor("Finance", "bills", "bill")

val EXPORTABLE_MODULES: List<ModuleDescriptor> = listOf(
    DOCUMENTS_MODULE, LINKS_MODULE, BOOKS_MODULE, COLLECTIONS_MODULE, PLACES_MODULE,
    RECIPES_MODULE, MILESTONES_MODULE, CONTACTS_MODULE, QUARTERMASTER_MODULE,
    RABBIT_HOLES_MODULE, TIME_CAPSULES_MODULE, PHOTOS_MODULE, FINANCE_ENTRIES_MODULE,
    FINANCE_BILLS_MODULE,
)

sealed class ImportResult {
    data class Success(val label: String, val newId: Long) : ImportResult()
    data class Failure(val reason: String) : ImportResult()
}

private const val MANIFEST_APP = "lifeos-record"
private const val MANIFEST_ENTRY = "manifest.json"
private val BLOB_ID = Regex("^(blob|text)_[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

@OptIn(ExperimentalEncodingApi::class)
object RecordPackage {
    private val json = Json { ignoreUnknownKeys = true }

    // Find the one record with this id inside descriptor's list, bundle it with
    // every blob it references that actually exists on this device, and hand
    // back the finished package's bytes — or null if the record/data isn't there.
    fun export(descriptor: ModuleDescriptor, recordId: Long): ByteArray? {
        val raw = Storage.read(descriptor.storageKey) ?: return null
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val list = (root[descriptor.listPath] as? JsonArray) ?: return null
        val record = list.firstOrNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull == recordId } as? JsonObject
            ?: return null

        val blobIds = LinkedHashSet<String>()
        findBlobIds(record, blobIds)

        val zipEntries = LinkedHashMap<String, ByteArray>()
        for (id in blobIds) {
            val imageB64 = readBlobBase64(id)
            if (imageB64 != null) {
                zipEntries["blobs/$id.bin"] = Base64.decode(imageB64)
                continue
            }
            val text = readTextBlob(id)
            if (text != null) zipEntries["blobs/$id.txt"] = text.encodeToByteArray()
            // Neither found: this blob simply isn't on this device. Export what we
            // have rather than failing the whole package over one missing photo.
        }

        val manifest = JsonObject(
            mapOf(
                "app" to JsonPrimitive(MANIFEST_APP),
                "version" to JsonPrimitive(1),
                "storageKey" to JsonPrimitive(descriptor.storageKey),
                "listPath" to JsonPrimitive(descriptor.listPath),
                "label" to JsonPrimitive(descriptor.label),
                "record" to record,
            ),
        )
        zipEntries[MANIFEST_ENTRY] = manifest.toString().encodeToByteArray()
        return ZipPackage.zip(zipEntries)
    }

    // Restore a package's blobs to this device (at their original ids, so nothing
    // inside the record needs rewriting), give the record a fresh local id so it
    // can't collide with an unrelated existing record, and merge it into that
    // module's list.
    fun import(zipBytes: ByteArray): ImportResult {
        val entries = runCatching { ZipPackage.unzip(zipBytes) }.getOrNull()
            ?: return ImportResult.Failure("That file isn't a Life OS package.")
        val manifestBytes = entries[MANIFEST_ENTRY]
            ?: return ImportResult.Failure("That file isn't a Life OS package.")
        val manifest = runCatching { json.parseToJsonElement(manifestBytes.decodeToString()).jsonObject }.getOrNull()
            ?: return ImportResult.Failure("That file isn't a Life OS package.")
        if (manifest["app"]?.jsonPrimitive?.content != MANIFEST_APP) {
            return ImportResult.Failure("That file isn't a Life OS package.")
        }
        val storageKey = manifest["storageKey"]?.jsonPrimitive?.content
            ?: return ImportResult.Failure("Package is missing its module.")
        val listPath = manifest["listPath"]?.jsonPrimitive?.content
            ?: return ImportResult.Failure("Package is missing its module.")
        val label = manifest["label"]?.jsonPrimitive?.content ?: "record"
        val record = manifest["record"] as? JsonObject
            ?: return ImportResult.Failure("Package has no record in it.")
        val known = EXPORTABLE_MODULES.any { it.storageKey == storageKey && it.listPath == listPath }
        if (!known) return ImportResult.Failure("This build doesn't know how to import a $label.")

        // Blobs first: restored at their existing ids, so the record's own JSON —
        // photoBlob, attachments[].blobId, whatever shape it is — keeps working
        // completely unchanged.
        for ((name, bytes) in entries) {
            if (name == MANIFEST_ENTRY || !name.startsWith("blobs/")) continue
            val id = name.substringAfter("blobs/").substringBeforeLast('.')
            when {
                name.endsWith(".bin") -> restoreBlob(id, bytes)
                name.endsWith(".txt") -> restoreTextBlob(id, bytes.decodeToString())
            }
        }

        val raw = Storage.read(storageKey)
        val root = if (raw.isNullOrBlank()) JsonObject(emptyMap())
        else runCatching { json.parseToJsonElement(raw).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        val existing = (root[listPath] as? JsonArray) ?: JsonArray(emptyList())
        val newId = (existing.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull }.maxOrNull() ?: 0L) + 1
        val reIded = JsonObject(record.toMutableMap().apply { this["id"] = JsonPrimitive(newId) })
        val updatedList = JsonArray(existing + reIded)
        val updatedRoot = JsonObject(root.toMutableMap().apply { this[listPath] = updatedList })
        Storage.write(storageKey, updatedRoot.toString())

        return ImportResult.Success(label, newId)
    }

    private fun findBlobIds(el: JsonElement, into: MutableSet<String>) {
        when (el) {
            is JsonObject -> el.values.forEach { findBlobIds(it, into) }
            is JsonArray -> el.forEach { findBlobIds(it, into) }
            is JsonPrimitive -> if (el.isString && BLOB_ID.matches(el.content)) into.add(el.content)
            else -> {}
        }
    }
}
