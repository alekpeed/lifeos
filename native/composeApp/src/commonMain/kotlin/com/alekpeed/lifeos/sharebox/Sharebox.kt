package com.alekpeed.lifeos.sharebox

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Sharebox — ported from the web app's Sharebox as its real LOCAL core: links
// and notes, each with an urgency flag and a "posted by" name, sorted urgent →
// normal. Persists as one JSON blob under "Sharebox" (so it rides the existing
// device sync). The web app's friend-sharing backends (a Drive shared folder,
// or Supabase spaces + Realtime with a second person's account) are a separate
// multi-user backend that native doesn't have yet — deferred, not a stub. Files
// wait on the attachment layer.

@Serializable
data class ShareItem(
    val id: Long,
    val kind: String,                // link | note
    val url: String = "",
    val title: String = "",
    val body: String = "",
    val urgency: String = "normal",  // urgent | soon | normal
    val postedBy: String = "",
    val createdAt: String = "",
)

@Serializable
data class ShareboxData(
    val items: List<ShareItem> = emptyList(),
    val myName: String = "",
)

val URGENCIES = listOf("normal" to "Normal", "soon" to "Soon", "urgent" to "Urgent")
fun urgencyRank(u: String): Int = when (u) { "urgent" -> 0; "soon" -> 1; else -> 2 }

// Default a bare host (espn.com) to https:// so it isn't read as a relative link.
fun normalizeUrl(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty() || Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(t)) return t
    return "https://$t"
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadSharebox(): ShareboxData {
    val raw = Storage.read("Sharebox")
    if (raw.isNullOrBlank()) return ShareboxData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<ShareboxData>(raw) }.getOrElse { ShareboxData() }
    }
    // Old NoteListScreen stub ("<something to share>\t<with whom>") → notes.
    val items = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val parts = line.split("\t", limit = 2)
        ShareItem(id = i + 1L, kind = "note", body = parts[0].trim(), postedBy = parts.getOrElse(1) { "" })
    }
    return ShareboxData(items = items)
}

fun saveSharebox(data: ShareboxData) {
    Storage.write("Sharebox", json.encodeToString(data))
}
