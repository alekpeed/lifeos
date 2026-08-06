package com.alekpeed.lifeos.photos

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Photos — named albums of real images. Each entry (Caption) can carry a blob-
// store id (`blob`) pointing at the actual image on disk, plus an optional
// caption. Entries without a blob are legacy text stand-ins (from before the
// media layer) and still render as captions. One JSON blob under "Photos"; old
// note stubs migrate into an Imported album.

@Serializable
data class Caption(val id: Long, val text: String, val note: String = "", val blob: String = "")

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
