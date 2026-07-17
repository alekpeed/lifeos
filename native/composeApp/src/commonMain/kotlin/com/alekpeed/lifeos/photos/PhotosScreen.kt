package com.alekpeed.lifeos.photos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.deleteBlob
import com.alekpeed.lifeos.platform.loadBlobImage
import com.alekpeed.lifeos.platform.saveBlob
import com.alekpeed.lifeos.ui.SaveToast

private val DANGER = Color(0xFFD64545)

@Composable
fun PhotosScreen() {
    var data by remember { mutableStateOf(loadPhotos()) }
    var counter by remember {
        mutableStateOf(maxOf(data.albums.maxOfOrNull { it.id } ?: 0L, data.albums.flatMap { it.captions }.maxOfOrNull { it.id } ?: 0L))
    }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: PhotosData) { data = d; savePhotos(d); SaveToast.show() }

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
            val cover = album.captions.firstOrNull { it.blob.isNotBlank() }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpen(album.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val coverBlob = cover?.blob ?: ""
                val coverImg = remember(coverBlob) { if (coverBlob.isBlank()) null else loadBlobImage(coverBlob) }
                if (coverImg != null) {
                    Image(coverImg, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(10.dp))
                } else {
                    Text("🖼", modifier = Modifier.padding(end = 10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(album.name.ifBlank { "(untitled album)" }, style = MaterialTheme.typography.bodyLarge)
                    Text("${album.captions.size} item${if (album.captions.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { album.captions.forEach { deleteBlob(it.blob) }; save(data.copy(albums = data.albums.filterNot { it.id == album.id })) }) { Text("×") }
            }
        }
    }
}

@Composable
private fun AlbumDetail(data: PhotosData, save: (PhotosData) -> Unit, freshId: () -> Long, album: Album, onBack: () -> Unit) {
    fun patch(f: (Album) -> Album) = save(data.copy(albums = data.albums.map { if (it.id == album.id) f(it) else it }))
    var showSource by remember { mutableStateOf(false) }
    var lightbox by remember { mutableStateOf<Long?>(null) }

    fun onPhoto(b64: String?) {
        if (b64.isNullOrEmpty()) return
        val id = saveBlob(b64) ?: return
        patch { it.copy(captions = it.captions + Caption(freshId(), "", "", id)) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("← Albums") }
        Spacer(Modifier.width(4.dp))
        Text(album.name.ifBlank { "(untitled album)" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            album.captions.forEach { deleteBlob(it.blob) }
            save(data.copy(albums = data.albums.filterNot { it.id == album.id })); onBack()
        }) { Text("Delete", color = DANGER) }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(album.name, { v -> patch { it.copy(name = v.replace("\n", " ")) } }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Album name") })
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(album.description, { v -> patch { it.copy(description = v) } }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") })
    Spacer(Modifier.height(10.dp))

    if (Native.supportsCamera) {
        OutlinedButton(onClick = { showSource = true }) { Text("📷 Add photo") }
        Spacer(Modifier.height(10.dp))
    }

    if (album.captions.isEmpty()) {
        Muted("No photos yet.")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.foundation.lazy.grid.items(album.captions, key = { it.id }) { cap ->
                Column {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { lightbox = cap.id },
                        contentAlignment = Alignment.Center,
                    ) {
                        val tileImg = remember(cap.blob) { if (cap.blob.isBlank()) null else loadBlobImage(cap.blob) }
                        if (tileImg != null) Image(tileImg, contentDescription = cap.text.ifBlank { null }, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Text("🖼", style = MaterialTheme.typography.headlineMedium)
                    }
                    if (cap.text.isNotBlank()) Text(cap.text, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
            }
        }
    }

    if (showSource) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSource = false },
            title = { Text("Add a photo") },
            text = { Text("Take a new photo, or choose one from your library.") },
            confirmButton = { TextButton(onClick = { showSource = false; Native.takePhoto { onPhoto(it) } }) { Text("Take a photo") } },
            dismissButton = { TextButton(onClick = { showSource = false; Native.capturePhoto { onPhoto(it) } }) { Text("Choose from library") } },
        )
    }

    val shown = album.captions.firstOrNull { it.id == lightbox }
    if (shown != null) {
        Lightbox(
            cap = shown,
            onCaption = { v -> patch { it.copy(captions = it.captions.map { c -> if (c.id == shown.id) c.copy(text = v.replace("\n", " ")) else c }) } },
            onDelete = { deleteBlob(shown.blob); patch { it.copy(captions = it.captions.filterNot { c -> c.id == shown.id }) }; lightbox = null },
            onClose = { lightbox = null },
        )
    }
}

@Composable
private fun Lightbox(cap: Caption, onCaption: (String) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
        ) {
            val img = remember(cap.blob) { if (cap.blob.isBlank()) null else loadBlobImage(cap.blob) }
            if (img != null) {
                Image(img, contentDescription = cap.text.ifBlank { null }, modifier = Modifier.fillMaxWidth().height(360.dp), contentScale = ContentScale.Fit)
            } else {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text("🖼 (no image)", style = MaterialTheme.typography.bodyMedium) }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(cap.text, onCaption, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Caption") })
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Close") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("Delete", color = DANGER) }
            }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
