package com.alekpeed.lifeos.core

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.DATA_SOURCES
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Manual, Drive-independent backup: a plain JSON object of every module's stored
// value (keyed by its Storage key). Round-trips all local data. Attachments would
// ride along once the media layer exists; text data is everything today.
private val json = Json { prettyPrint = true }

fun exportBackupJson(): String {
    val map = LinkedHashMap<String, String>()
    DATA_SOURCES.forEach { ds -> Storage.read(ds.key)?.takeIf { it.isNotBlank() }?.let { map[ds.key] = it } }
    return json.encodeToString(map)
}

// Restore from a backup blob; returns how many module keys were written, or -1 if
// the text isn't a valid backup.
fun importBackupJson(text: String): Int {
    val map = runCatching { json.decodeFromString<Map<String, String>>(text) }.getOrElse { return -1 }
    map.forEach { (k, v) -> Storage.write(k, v) }
    return map.size
}
