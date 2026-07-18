package com.alekpeed.lifeos.links

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Links — ported from the web app's Links view: saved YouTube videos and
// articles, each with a title, tags, share-with, and read/watched status.
// Persists as one JSON blob under the "Links" key; old plain-line stubs migrate
// to articles (or videos, if the line is a YouTube URL) so existing entries survive.

@Serializable
data class Link(
    val id: Long,
    val url: String,
    val type: String = "article",       // video | article
    val title: String = "",
    val tags: List<String> = emptyList(),
    val status: String = "unread",       // unread | done
    val shareWith: String = "",
    val videoId: String = "",
    val thumbBlob: String = "",          // blob-store id of the cached YouTube thumbnail
)

@Serializable
data class LinksData(val links: List<Link> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// YouTube id from watch?v=, youtu.be/, /embed/, /shorts/. "" if not a YT url.
fun parseYouTubeId(url: String): String {
    val u = url.trim()
    Regex("""youtu\.be/([^/?&#]+)""").find(u)?.let { return it.groupValues[1] }
    Regex("""[?&]v=([^&#]+)""").find(u)?.let { if (u.contains("youtube.com")) return it.groupValues[1] }
    Regex("""youtube\.com/(?:embed|shorts)/([^/?&#]+)""").find(u)?.let { return it.groupValues[1] }
    return ""
}

// Bare host for a URL string: "https://www.nytimes.com/x" -> "nytimes.com".
fun hostnameOf(url: String): String {
    var s = url.trim()
    val scheme = s.indexOf("://")
    if (scheme >= 0) s = s.substring(scheme + 3)
    s = s.substringBefore('/').substringBefore('?')
    return s.removePrefix("www.").ifBlank { url }
}

fun loadLinks(): LinksData {
    val raw = Storage.read("Links")
    if (raw.isNullOrBlank()) return LinksData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<LinksData>(raw) }.getOrElse { LinksData() }
    }
    val links = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val url = line.trim()
        val vid = parseYouTubeId(url)
        Link(id = i + 1L, url = url, type = if (vid.isNotEmpty()) "video" else "article", videoId = vid)
    }
    return LinksData(links)
}

fun saveLinks(data: LinksData) {
    Storage.write("Links", json.encodeToString(data))
}
