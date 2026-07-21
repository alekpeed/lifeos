package com.alekpeed.lifeos.links

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.alekpeed.lifeos.net.httpGetImageBase64
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.deleteBlob
import com.alekpeed.lifeos.platform.loadBlobImage
import com.alekpeed.lifeos.platform.saveBlob
import com.alekpeed.lifeos.ui.SaveToast

private val DANGER = Color(0xFFD64545)

@Composable
fun LinksScreen() {
    var data by remember { mutableStateOf(loadLinks()) }
    var counter by remember { mutableStateOf(data.links.maxOfOrNull { it.id } ?: 0L) }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: LinksData) { data = d; saveLinks(d); SaveToast.show() }

    var tab by remember { mutableStateOf("video") }
    var showDone by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Links", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = tab == "video", onClick = { tab = "video"; selected = null }, label = { Text("YouTube") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = tab == "article", onClick = { tab = "article"; selected = null }, label = { Text("Articles") })
            Spacer(Modifier.weight(1f))
            Checkbox(checked = showDone, onCheckedChange = { showDone = it })
            Text(if (tab == "video") "Watched" else "Read", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text(if (tab == "video") "Paste a YouTube link" else "Paste an article link") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val url = input.trim()
                if (url.isNotEmpty()) {
                    val vid = if (tab == "video") parseYouTubeId(url) else ""
                    save(data.copy(links = data.links + Link(freshId(), url, tab, videoId = vid)))
                    input = ""
                }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(14.dp))

        val filtered = data.links.filter { (it.type.ifBlank { "article" }) == tab }
            .filter { showDone || it.status != "done" }
        if (filtered.isEmpty()) {
            Text(
                if (tab == "video") "No videos saved yet." else "No articles saved yet.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { link ->
                LinkCard(data, ::save, link, selected == link.id) { selected = if (selected == link.id) null else link.id }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkCard(data: LinksData, save: (LinksData) -> Unit, link: Link, expanded: Boolean, onToggle: () -> Unit) {
    fun patch(f: (Link) -> Link) = save(data.copy(links = data.links.map { if (it.id == link.id) f(it) else it }))

    Column {
        // YouTube links get a real thumbnail: fetched once from the keyless
        // img.youtube.com endpoint, cached in the blob store on the record.
        if (link.type == "video" && link.videoId.isNotBlank() && link.thumbBlob.isBlank()) {
            var tried by remember(link.id) { mutableStateOf(false) }
            LaunchedEffect(link.id) {
                if (tried) return@LaunchedEffect
                tried = true
                val b64 = httpGetImageBase64("https://img.youtube.com/vi/${link.videoId}/mqdefault.jpg")
                if (b64 != null) saveBlob(b64)?.let { id -> patch { it.copy(thumbBlob = id) } }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onToggle() }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumb = if (link.thumbBlob.isNotBlank()) remember(link.thumbBlob) { loadBlobImage(link.thumbBlob) } else null
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = "Video thumbnail",
                    modifier = Modifier.width(72.dp).height(40.dp).clip(RoundedCornerShape(6.dp)).padding(end = 0.dp),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
            } else {
                Text(if (link.type == "video") "▶" else "📄", modifier = Modifier.padding(end = 10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(link.title.ifBlank { hostnameOf(link.url) }, style = MaterialTheme.typography.bodyLarge)
                val chips = buildList {
                    add(hostnameOf(link.url))
                    if (link.status == "done") add(if (link.type == "video") "Watched" else "Read")
                    if (link.shareWith.isNotBlank()) add("Share → ${link.shareWith}")
                    link.tags.forEach { add("#$it") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        if (expanded) LinkDetail(data, save, link, ::patch, onToggle)
    }
}

@Composable
private fun LinkDetail(
    data: LinksData,
    save: (LinksData) -> Unit,
    link: Link,
    patch: ((Link) -> Link) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) {
        Label("Title")
        Field(link.title, "(untitled)") { v -> patch { it.copy(title = v.replace("\n", " ")) } }
        Label("Tags (comma separated)")
        Field(link.tags.joinToString(", "), "comma, separated, tags") { v ->
            patch { it.copy(tags = v.split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() }) }
        }
        Label("Share with")
        Field(link.shareWith, "Who is this for?") { v -> patch { it.copy(shareWith = v.replace("\n", " ")) } }

        Spacer(Modifier.height(8.dp))
        Text(link.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = link.status == "done", onCheckedChange = { c -> patch { it.copy(status = if (c) "done" else "unread") } })
            Text(if (link.type == "video") "Watched" else "Read", style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { Native.openUrl(link.url) }, enabled = link.url.isNotBlank()) { Text("↗ Open") }
            OutlinedButton(onClick = {
                Native.shareText(if (link.title.isNotBlank()) "${link.title} — ${link.url}" else link.url)
            }) { Text("Share") }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Done") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                if (link.thumbBlob.isNotBlank()) deleteBlob(link.thumbBlob)
                save(data.copy(links = data.links.filterNot { it.id == link.id })); onClose()
            }) {
                Text("Delete link", color = DANGER)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Field(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        singleLine = true, placeholder = { Text(placeholder) },
    )
}
