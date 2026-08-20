package com.alekpeed.lifeos.core

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Manual, Drive-independent backup: a plain JSON object of every stored value,
// keyed by its Storage key. Attachments would ride along once the media layer
// exists; text data is everything today.
//
// This enumerates the store itself rather than walking DATA_SOURCES. The module
// list only names the 23 record modules, so a backup taken through it quietly
// dropped everything else the app persists — the Daily Paper's editorial and
// checklist, the AI Assistant transcript, the Time Machine's record-birth census,
// saved cities/currencies/watchlists, and every setting from the theme and accent
// to the AI keys. Restoring one looked like it worked and came back missing all of
// it.
private val json = Json { prettyPrint = true }

// Sync bookkeeping (the "__"-prefixed manifest and high-water mark) describes THIS
// device's relationship to the server, not your data. Restoring another device's
// copy would strand every record on the wrong timestamps, so it stays out.
private fun backupKey(key: String) = !key.startsWith("__")

fun exportBackupJson(): String {
    val map = LinkedHashMap<String, String>()
    Storage.keys().filter(::backupKey).sorted().forEach { key ->
        Storage.read(key)?.takeIf { it.isNotBlank() }?.let { map[key] = it }
    }
    return json.encodeToString(map)
}

// Restore from a backup blob; returns how many module keys were written, or -1 if
// the text isn't a valid backup.
fun importBackupJson(text: String): Int {
    val map = runCatching { json.decodeFromString<Map<String, String>>(text) }.getOrElse { return -1 }
    val restorable = map.filterKeys(::backupKey)
    restorable.forEach { (k, v) -> Storage.write(k, v) }
    return restorable.size
}
