package com.alekpeed.lifeos.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DANGER = Color(0xFFD64545)

@Composable
fun PhotosScreen() {
    var data by remember { mutableStateOf(loadPhotos()) }
    var counter by remember {
        mutableStateOf(maxOf(data.albums.maxOfOrNull { it.id } ?: 0L, data.albums.flatMap { it.captions }.maxOfOrNull { it.id } ?: 0L))
    }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: PhotosData) { data = d; savePhotos(d) }

    var openId by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        val open = data.albums.firstOrNull { it.id == openId }
        if (open != null) AlbumDetail(data, ::save, ::freshId, open) { openId = null }
        else AlbumsList(data, ::save, ::freshId) { openId = it }
    }
}

@Composable
private fun AlbumsList(data: PhotosData, save: (PhotosData) -> Unit, freshId: () -> Long, onOpen: (Long) -> Unit) {
    var input by remember { mutableStateOf("") }

    Text("Photos", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Album index. Image capture, storage, and import land with the media layer; for now organize albums and caption what belongs in them.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("New album") })
        Spacer(Modifier.width(10.dp))
        Button(onClick = {
            val n = input.trim().replace("\n", " ")
            if (n.isNotEmpty()) { save(data.copy(albums = data.albums + Album(freshId(), n))); input = "" }
        }) { Text("Add") }
    }
    Spacer(Modifier.height(12.dp))

    if (data.albums.isEmpty()) { Muted("No albums yet."); return }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(data.albums, key = { it.id }) { album ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpen(album.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🖼", modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(album.name.ifBlank { "(untitled album)" }, style = MaterialTheme.typography.bodyLarge)
                    Text("${album.captions.size} item${if (album.captions.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { save(data.copy(albums = data.albums.filterNot { it.id == album.id })) }) { Text("×") }
            }
        }
    }
}

@Composable
private fun AlbumDetail(data: PhotosData, save: (PhotosData) -> Unit, freshId: () -> Long, album: Album, onBack: () -> Unit) {
    fun patch(f: (Album) -> Album) = save(data.copy(albums = data.albums.map { if (it.id == album.id) f(it) else it }))
    var capText by remember { mutableStateOf("") }
    var capNote by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("← Albums") }
        Spacer(Modifier.width(4.dp))
        Text(album.name.ifBlank { "(untitled album)" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { save(data.copy(albums = data.albums.filterNot { it.id == album.id })); onBack() }) { Text("Delete", color = DANGER) }
    }
    Spacer(Modifier.height(8.dp))
    Text("Album name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(album.name, { v -> patch { it.copy(name = v.replace("\n", " ")) } }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Name") })
    Spacer(Modifier.height(6.dp))
    Text("Description", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(album.description, { v -> patch { it.copy(description = v) } }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("What's this album about?") })
    Spacer(Modifier.height(12.dp))

    Text("Photos (captions)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(album.captions, key = { it.id }) { cap ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🖼", modifier = Modifier.padding(end = 8.dp))
                Column(Modifier.weight(1f)) {
                    Text(cap.text, style = MaterialTheme.typography.bodyLarge)
                    if (cap.note.isNotBlank()) Text(cap.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { patch { it.copy(captions = it.captions.filterNot { c -> c.id == cap.id }) } }) { Text("×") }
            }
        }
        item {
            Column(Modifier.padding(top = 8.dp)) {
                OutlinedTextField(capText, { capText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Caption") })
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(capNote, { capNote = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Where it's stored (optional)") })
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val t = capText.trim().replace("\n", " ")
                        if (t.isNotEmpty()) { patch { it.copy(captions = it.captions + Caption(freshId(), t, capNote.trim())) }; capText = ""; capNote = "" }
                    }) { Text("Add") }
                }
            }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
