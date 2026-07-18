package com.alekpeed.lifeos.rabbitholes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.deleteBlob
import com.alekpeed.lifeos.platform.loadBlobImage
import com.alekpeed.lifeos.platform.saveBlob
import com.alekpeed.lifeos.ui.SaveToast

private val DANGER = Color(0xFFD64545)

@Composable
fun RabbitHolesScreen() {
    var data by remember { mutableStateOf(loadHoles()) }
    var counter by remember {
        mutableStateOf(maxOf(data.holes.maxOfOrNull { it.id } ?: 0L, data.holes.flatMap { it.links }.maxOfOrNull { it.id } ?: 0L))
    }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: RabbitHolesData) { data = d; saveHoles(d); SaveToast.show() }
    var openId by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        val open = data.holes.firstOrNull { it.id == openId }
        if (open != null) Detail(data, ::save, ::freshId, open) { openId = null }
        else Overview(data, ::save, ::freshId) { openId = it }
    }
}

@Composable
private fun Overview(data: RabbitHolesData, save: (RabbitHolesData) -> Unit, freshId: () -> Long, onOpen: (Long) -> Unit) {
    var topic by remember { mutableStateOf("") }
    var showResolved by remember { mutableStateOf(false) }
    val active = data.holes.filter { it.status != "resolved" }
    val resolved = data.holes.filter { it.status == "resolved" }

    Text("Rabbit Hole Journal", style = MaterialTheme.typography.headlineMedium)
    Text("Track what you went down a hole researching.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(topic, { topic = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("What are you researching?") })
        Spacer(Modifier.width(10.dp))
        Button(onClick = {
            val t = topic.trim().replace("\n", " ")
            if (t.isNotEmpty()) {
                val id = freshId()
                save(data.copy(holes = data.holes + RabbitHole(id, t, startedDate = today().toString())))
                topic = ""; onOpen(id)
            }
        }) { Text("Start") }
    }
    Spacer(Modifier.height(14.dp))

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { SectionLabel("Active (${active.size})") }
        if (active.isEmpty()) item { Muted("Nothing open right now.") }
        else items(active, key = { it.id }) { HoleRow(it) { onOpen(it.id) } }

        if (resolved.isNotEmpty()) {
            item {
                OutlinedButton(onClick = { showResolved = !showResolved }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (showResolved) "Hide resolved" else "Show resolved (${resolved.size})")
                }
            }
            if (showResolved) items(resolved, key = { it.id }) { HoleRow(it) { onOpen(it.id) } }
        }
    }
}

@Composable
private fun HoleRow(hole: RabbitHole, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(hole.topic.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("${hole.links.size} link${if (hole.links.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Detail(data: RabbitHolesData, save: (RabbitHolesData) -> Unit, freshId: () -> Long, hole: RabbitHole, onBack: () -> Unit) {
    fun patch(f: (RabbitHole) -> RabbitHole) = save(data.copy(holes = data.holes.map { if (it.id == hole.id) f(it) else it }))
    var showSource by remember { mutableStateOf(false) }
    fun onAttach(b64: String?) {
        if (b64.isNullOrEmpty()) return
        val id = saveBlob(b64) ?: return
        deleteBlob(hole.photoBlob)
        patch { it.copy(photoBlob = id) }
    }
    var url by remember { mutableStateOf("") }
    var linkTitle by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("← Journal") }
        Spacer(Modifier.width(4.dp))
        Text(hole.topic.ifBlank { "(untitled)" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
    if (hole.startedDate.isNotBlank()) Text("Started ${hole.startedDate}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))

    Text("Notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(hole.notes, { v -> patch { it.copy(notes = v) } }, modifier = Modifier.fillMaxWidth().height(140.dp), placeholder = { Text("Notes as you go…") })
    Spacer(Modifier.height(10.dp))

    Text("Add a link", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(url, { url = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("https://…") })
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(linkTitle, { linkTitle = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Title (optional)") })
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            val u = url.trim()
            if (u.isNotEmpty()) { patch { it.copy(links = it.links + HoleLink(freshId(), u, linkTitle.trim())) }; url = ""; linkTitle = "" }
        }) { Text("Add") }
    }
    Spacer(Modifier.height(12.dp))

    Text("Links (${hole.links.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Column(Modifier.fillMaxWidth()) {
        if (hole.links.isEmpty()) Muted("No links yet.")
        hole.links.forEach { link ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(link.title.ifBlank { link.url }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    if (link.title.isNotBlank()) Text(link.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { Native.shareText(link.url) }) { Text("↗") }
                TextButton(onClick = { patch { it.copy(links = it.links.filterNot { l -> l.id == link.id }) } }) { Text("×") }
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    Text("Photo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val img = remember(hole.photoBlob) { loadBlobImage(hole.photoBlob) }
    if (img != null) {
        Image(
            bitmap = img,
            contentDescription = "Attached photo",
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hole.photoBlob.isBlank()) {
            if (Native.supportsCamera) OutlinedButton(onClick = { showSource = true }) { Text("📷 Attach photo") }
        } else {
            if (Native.supportsCamera) TextButton(onClick = { showSource = true }) { Text("Replace") }
            TextButton(onClick = { deleteBlob(hole.photoBlob); patch { it.copy(photoBlob = "") } }) { Text("Remove photo") }
        }
    }
    if (showSource) {
        AlertDialog(
            onDismissRequest = { showSource = false },
            title = { Text("Attach a photo") },
            text = { Text("Take a new photo, or choose one from your library.") },
            confirmButton = {
                TextButton(onClick = { showSource = false; Native.takePhoto { onAttach(it) } }) { Text("Take a photo") }
            },
            dismissButton = {
                TextButton(onClick = { showSource = false; Native.capturePhoto { onAttach(it) } }) { Text("Choose from library") }
            },
        )
    }
    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hole.status == "resolved") {
            OutlinedButton(onClick = { patch { it.copy(status = "active") } }) { Text("Reopen") }
        } else {
            OutlinedButton(onClick = { patch { it.copy(status = "resolved") } }) { Text("Mark resolved") }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { deleteBlob(hole.photoBlob); save(data.copy(holes = data.holes.filterNot { it.id == hole.id })); onBack() }) { Text("Delete", color = DANGER) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
