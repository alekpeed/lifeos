package com.alekpeed.lifeos.photos

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Photos — ported from the web app's Photos view as an album index. The web
// version's core is image storage (albums of photo attachments, a lightbox,
// Google Photos import) which native can't hold until the media/attachment
// layer exists. Until then this is a real, persisted album organizer: named
// albums, each with a description and captioned entries (text stand-ins for the
// photos that will attach later). One JSON blob under "Photos"; old note stubs
// migrate into an Imported album.

@Serializable
data class Caption(val id: Long, val text: String, val note: String = "")

@Serializable
data class Album(
    val id: Long,
    val name: String,
    val description: String = "",
    val captions: List<Caption> = emptyList(),
)

@Serializable
data class PhotosData(val albums: List<Album> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadPhotos(): PhotosData {
    val raw = Storage.read("Photos")
    if (raw.isNullOrBlank()) return PhotosData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<PhotosData>(raw) }.getOrElse { PhotosData() }
    }
    // Old NoteListScreen stub ("<caption>\t<where stored>") → one Imported album.
    val caps = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val parts = line.split("\t", limit = 2)
        Caption(id = i + 2L, text = parts[0].trim(), note = parts.getOrElse(1) { "" })
    }
    if (caps.isEmpty()) return PhotosData()
    return PhotosData(albums = listOf(Album(id = 1L, name = "Imported", captions = caps)))
}

fun savePhotos(data: PhotosData) {
    Storage.write("Photos", json.encodeToString(data))
}
