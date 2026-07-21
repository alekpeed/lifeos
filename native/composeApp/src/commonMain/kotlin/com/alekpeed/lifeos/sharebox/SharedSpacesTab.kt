package com.alekpeed.lifeos.sharebox

import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.alekpeed.lifeos.realtime.openShareboxRealtime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.sync.SupabaseAuth
import com.alekpeed.lifeos.ui.SaveToast
import kotlinx.coroutines.launch

private val URGENT = Color(0xFFD64545)
private val SOON = Color(0xFFE0A25C)

// The real multi-user Sharebox: create or join a space (membership is the share),
// post links/notes that your friend sees, delete your own. Needs a signed-in
// account (Settings → Sync); the same account on each device shares the space.
@Composable
fun SharedSpacesTab() {
    if (!SupabaseAuth.isSignedIn()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                "Shared spaces let you and a friend post links and notes to the same box, live.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text("Sign in to your account first — it's what pairs you with your friend's space.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { Nav.open("settings") }) { Text("Open Settings → Sync") }
        }
        return
    }

    val scope = rememberCoroutineScope()
    var spaces by remember { mutableStateOf<List<SpaceRow>?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<ItemRow>>(emptyList()) }
    var members by remember { mutableStateOf<List<MemberRow>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var myName by remember { mutableStateOf(loadSharebox().myName.ifBlank { SupabaseAuth.email()?.substringBefore("@") ?: "Me" }) }

    fun reloadSpaces() {
        scope.launch {
            ShareboxV2.getMySpaces()
                .onSuccess { list -> spaces = list; if (selected == null || list.none { it.id == selected }) selected = list.firstOrNull()?.id }
                .onFailure { spaces = emptyList(); SaveToast.show(it.message ?: "Couldn't load spaces") }
        }
    }
    fun reloadItems(spaceId: String) {
        busy = true
        scope.launch {
            ShareboxV2.listItems(spaceId).onSuccess { items = it }.onFailure { SaveToast.show(it.message ?: "Couldn't load items") }
            ShareboxV2.getMembers(spaceId).onSuccess { members = it }
            busy = false
        }
    }

    LaunchedEffect(Unit) { reloadSpaces() }
    LaunchedEffect(selected) { selected?.let { reloadItems(it) } }

    // Live push: while a space is open, subscribe to its item changes over Realtime
    // and reload on any insert/update/delete — no manual refresh needed. The channel
    // is torn down when the selected space changes or the screen leaves composition.
    DisposableEffect(selected) {
        val sid = selected
        val handle = if (sid != null) openShareboxRealtime(sid) { scope.launch { reloadItems(sid) } } else null
        onDispose { handle?.close() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        when {
            spaces == null -> {
                Spacer(Modifier.height(20.dp))
                Text("Loading your spaces…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            spaces!!.isEmpty() -> CreateJoinForms(myName, { myName = it }, onDone = { reloadSpaces() }, scope)
            else -> {
                val sel = selected
                // Space picker (when in more than one).
                if (spaces!!.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        spaces!!.forEach { sp ->
                            FilterChip(selected = sp.id == sel, onClick = { selected = sp.id }, label = { Text(sp.name) })
                        }
                    }
                }
                if (sel != null) {
                    val nameFor = { uid: String? -> members.firstOrNull { it.userId == uid }?.displayName ?: "Someone" }
                    AddItemForm(sel, scope) { reloadItems(sel) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shared (${items.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("· live", style = MaterialTheme.typography.labelSmall, color = SOON)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { reloadItems(sel) }, enabled = !busy) { Text(if (busy) "…" else "Refresh") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Space id: $sel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { com.alekpeed.lifeos.platform.Native.copyToClipboard(sel); SaveToast.show("Space id copied — send it to your friend") }) { Text("Copy id") }
                    }
                    Spacer(Modifier.height(8.dp))
                    val sorted = items.sortedWith(compareBy({ urgencyRank(it.urgency) }, { it.createdAt ?: "" }))
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sorted, key = { it.id }) { item ->
                            SharedItemRow(
                                item, nameFor(item.postedBy), myUid = SupabaseAuth.userId(),
                                onOpenFile = {
                                    SaveToast.show("Opening…")
                                    scope.launch {
                                        val path = item.storagePath
                                        val b64 = if (path != null) ShareboxStorage.download(path) else null
                                        if (b64 != null) Native.openAttachment(b64, item.title ?: "file", mimeOf(item.title ?: ""))
                                        else SaveToast.show("Couldn't download file")
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        ShareboxV2.removeItem(item.id)
                                            .onSuccess { item.storagePath?.let { ShareboxStorage.delete(it) }; reloadItems(sel) }
                                            .onFailure { SaveToast.show(it.message ?: "Couldn't delete") }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateJoinForms(
    displayName: String,
    onName: (String) -> Unit,
    onDone: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var spaceName by remember { mutableStateOf("") }
    var joinId by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        Text("Your name in the space", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(displayName, onName, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("e.g. Alek") })
        Spacer(Modifier.height(16.dp))

        Text("Create a space", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(spaceName, { spaceName = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Space name") })
        Spacer(Modifier.height(6.dp))
        Button(onClick = {
            scope.launch {
                ShareboxV2.createSpace(spaceName, displayName)
                    .onSuccess { SaveToast.show("Space created"); onDone() }
                    .onFailure { SaveToast.show(it.message ?: "Couldn't create space") }
            }
        }) { Text("Create space") }

        Spacer(Modifier.height(20.dp))
        Text("Or join a space", style = MaterialTheme.typography.titleSmall)
        Text("Paste the space id a friend shared with you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(joinId, { joinId = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("space id") })
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            val id = joinId.trim()
            if (id.isEmpty()) return@OutlinedButton
            scope.launch {
                ShareboxV2.joinSpace(id, displayName)
                    .onSuccess { SaveToast.show("Joined"); onDone() }
                    .onFailure { SaveToast.show(it.message ?: "Couldn't join") }
            }
        }) { Text("Join space") }
    }
}

@Composable
private fun AddItemForm(spaceId: String, scope: kotlinx.coroutines.CoroutineScope, onAdded: () -> Unit) {
    var kind by remember { mutableStateOf("link") }
    var urgency by remember { mutableStateOf("normal") }
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == "link", onClick = { kind = "link" }, label = { Text("🔗 Link") })
            FilterChip(selected = kind == "note", onClick = { kind = "note" }, label = { Text("📝 Note") })
            if (Native.supportsFilePick) {
                FilterChip(selected = kind == "file", onClick = { kind = "file" }, label = { Text("📎 File") })
            }
        }
        Spacer(Modifier.height(6.dp))
        when (kind) {
            "link" -> {
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("https://…") })
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Title (optional)") })
            }
            "file" -> {
                Text(
                    "Pick a file (photo, PDF, doc). It uploads to your shared space so your friend can open it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> OutlinedTextField(noteBody, { noteBody = it }, Modifier.fillMaxWidth(), placeholder = { Text("Write a note…") })
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            URGENCIES.forEach { (v, lbl) ->
                FilterChip(selected = urgency == v, onClick = { urgency = v }, label = { Text(lbl) })
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            when (kind) {
                "link" -> Button(onClick = {
                    val u = url.trim(); if (u.isEmpty()) return@Button
                    scope.launch {
                        ShareboxV2.addItem(spaceId, "link", normalizeUrl(u), title.trim().ifBlank { null }, null, urgency)
                            .onSuccess { url = ""; title = ""; SaveToast.show("Shared"); onAdded() }
                            .onFailure { SaveToast.show(it.message ?: "Couldn't share") }
                    }
                }) { Text("Share") }
                "file" -> Button(enabled = !uploading, onClick = {
                    Native.pickAttachment { name, mime, b64 ->
                        if (b64.isNullOrBlank()) return@pickAttachment
                        val fileName = name?.ifBlank { null } ?: "file"
                        uploading = true
                        SaveToast.show("Uploading…")
                        scope.launch {
                            ShareboxStorage.upload(spaceId, fileName, mime.orEmpty(), b64)
                                .onSuccess { path ->
                                    ShareboxV2.addItem(spaceId, "file", null, fileName, null, urgency, storagePath = path)
                                        .onSuccess { SaveToast.show("Shared"); onAdded() }
                                        .onFailure { SaveToast.show(it.message ?: "Uploaded but couldn't post") }
                                }
                                .onFailure { SaveToast.show(it.message ?: "Couldn't upload") }
                            uploading = false
                        }
                    }
                }) { Text(if (uploading) "Uploading…" else "Attach a file") }
                else -> Button(onClick = {
                    val b = noteBody.trim(); if (b.isEmpty()) return@Button
                    scope.launch {
                        ShareboxV2.addItem(spaceId, "note", null, null, b, urgency)
                            .onSuccess { noteBody = ""; SaveToast.show("Shared"); onAdded() }
                            .onFailure { SaveToast.show(it.message ?: "Couldn't share") }
                    }
                }) { Text("Share") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SharedItemRow(
    item: ItemRow,
    poster: String,
    myUid: String?,
    onOpenFile: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(when (item.kind) { "link" -> "🔗"; "file" -> "📎"; else -> "📝" }, modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            val primary = when (item.kind) {
                "link" -> item.title?.ifBlank { null } ?: item.url ?: "(link)"
                "file" -> item.title ?: "(file)"
                else -> item.body ?: ""
            }
            Text(primary, style = MaterialTheme.typography.bodyLarge)
            Text(poster, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val uColor = when (item.urgency) { "urgent" -> URGENT; "soon" -> SOON; else -> MaterialTheme.colorScheme.onSurfaceVariant }
        Text(item.urgency, style = MaterialTheme.typography.labelSmall, color = uColor, modifier = Modifier.padding(end = 6.dp))
        if (item.kind == "file" && item.storagePath != null) {
            TextButton(onClick = onOpenFile) { Text("Open") }
        }
        if (item.postedBy != null && item.postedBy == myUid) {
            TextButton(onClick = onDelete) { Text("×") }
        }
    }
}

// Best-effort mime from a filename extension, so the OS opener picks the right app.
private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "application/pdf"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "txt" -> "text/plain"
    "csv" -> "text/csv"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "xls" -> "application/vnd.ms-excel"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "zip" -> "application/zip"
    else -> ""
}
