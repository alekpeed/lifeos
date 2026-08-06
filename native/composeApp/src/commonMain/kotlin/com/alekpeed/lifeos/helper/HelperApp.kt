package com.alekpeed.lifeos.helper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.realtime.openShareboxRealtime
import com.alekpeed.lifeos.sharebox.ItemRow
import com.alekpeed.lifeos.sharebox.ShareboxStorage
import com.alekpeed.lifeos.sharebox.ShareboxV2
import com.alekpeed.lifeos.sync.SupabaseAuth
import kotlinx.coroutines.launch

// The helper build — the whole app for someone who is not going to administer anything.
// One window: a button that asks for help, and the shared feed. No modules, no settings,
// no interface switcher, nothing that can be configured into a state that needs talking
// someone out of over the phone.
//
// It rides entirely on Sharebox, so there is no second system to maintain: a help request
// is an item with an urgency, and the reply is another item in the same space. Whoever is
// on the other end already sees it in their normal Sharebox.

// Plain-language urgency. Same three values the schema already has.
private val URGENCY = listOf(
    "normal" to "Whenever you can",
    "soon" to "It's annoying",
    "urgent" to "I can't work",
)

@Composable
fun HelperApp(ownerName: String = "Alek") {
    var signedIn by remember { mutableStateOf(SupabaseAuth.isSignedIn()) }
    if (!signedIn) {
        SignIn { signedIn = true }
        return
    }

    val scope = rememberCoroutineScope()
    var spaceId by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<ItemRow>>(emptyList()) }
    var status by remember { mutableStateOf("Connecting…") }
    var asking by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sentMsg by remember { mutableStateOf("") }

    suspend fun reload(id: String) {
        ShareboxV2.listItems(id).fold(
            onSuccess = { items = it; status = "Connected" },
            onFailure = { status = "Can't reach the server — it will retry" },
        )
    }

    LaunchedEffect(Unit) {
        ShareboxV2.getMySpaces().fold(
            onSuccess = { list ->
                val first = list.firstOrNull()
                if (first == null) {
                    status = "Not in a shared space yet — ask $ownerName to add you."
                } else {
                    spaceId = first.id
                    reload(first.id)
                }
            },
            onFailure = { status = "Can't reach the server — it will retry" },
        )
    }

    // New items arrive on their own; nothing to refresh, nothing to press.
    val sid = spaceId
    DisposableEffect(sid) {
        val handle = if (sid != null) openShareboxRealtime(sid) { scope.launch { reload(sid) } } else null
        onDispose { handle?.close() }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Life OS", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                if (status == "Connected") "Connected ✓" else status,
                style = MaterialTheme.typography.labelMedium,
                color = if (status == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(18.dp))

        // The one thing this app is for.
        Button(
            onClick = { asking = true; sentMsg = "" },
            enabled = spaceId != null,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text("🔧   I need help with something", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (sentMsg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(sentMsg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(22.dp))
        Text("Shared with you", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            Text(
                "Nothing here yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item -> FeedRow(item, ownerName, scope) }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                val id = spaceId ?: return@OutlinedButton
                Native.pickAttachment { name, mime, b64 ->
                    if (b64.isNullOrBlank()) return@pickAttachment
                    sending = true
                    scope.launch {
                        val up = ShareboxStorage.upload(id, name ?: "file", mime ?: "", b64)
                        up.fold(
                            onSuccess = { path ->
                                ShareboxV2.addItem(id, "file", null, name ?: "file", null, "normal", path)
                                reload(id)
                                sentMsg = "Sent."
                            },
                            onFailure = { sentMsg = "Couldn't send that file." },
                        )
                        sending = false
                    }
                }
            },
            enabled = spaceId != null && !sending,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (sending) "Sending…" else "+  Send $ownerName a file") }
    }

    if (asking) {
        AskForHelp(
            ownerName = ownerName,
            onDismiss = { asking = false },
            onSend = { text, urgency, withShot ->
                val id = spaceId ?: return@AskForHelp
                asking = false
                sending = true
                sentMsg = "Sending…"
                // The screenshot is grabbed before anything else so the dialog isn't in it.
                fun post(shotPath: String?) {
                    scope.launch {
                        val body = buildString {
                            append(text.trim())
                            val machine = Native.machineSummary()
                            if (machine.isNotBlank()) append("\n\n— $machine")
                        }
                        val r = ShareboxV2.addItem(
                            id, if (shotPath != null) "file" else "note", null,
                            "🔧 Help: " + text.trim().take(60), body, urgency, shotPath,
                        )
                        sending = false
                        sentMsg = if (r.isSuccess) "Sent — $ownerName's phone just buzzed." else "Couldn't send. Try again in a moment."
                        reload(id)
                    }
                }
                if (withShot && Native.supportsScreenshot) {
                    Native.captureScreen { b64 ->
                        if (b64.isNullOrBlank()) {
                            post(null)
                        } else {
                            scope.launch {
                                val up = ShareboxStorage.upload(id, "screen.png", "image/png", b64)
                                post(up.getOrNull())
                            }
                        }
                    }
                } else {
                    post(null)
                }
            },
        )
    }
}

@Composable
private fun FeedRow(item: ItemRow, ownerName: String, scope: kotlinx.coroutines.CoroutineScope) {
    val mine = item.postedBy == SupabaseAuth.userId()
    val who = if (mine) "you" else ownerName
    val icon = when {
        item.title?.startsWith("🔧") == true -> "🔧"
        item.kind == "file" -> "📄"
        item.kind == "link" -> "🔗"
        else -> "💬"
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$icon  ", style = MaterialTheme.typography.bodyLarge)
            Text(
                item.title?.ifBlank { null } ?: item.body?.take(60) ?: "(untitled)",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            if (item.kind == "link" && !item.url.isNullOrBlank()) {
                TextButton(onClick = { Native.openUrl(item.url!!) }) { Text("Open") }
            }
            if (item.kind == "file" && !item.storagePath.isNullOrBlank()) {
                TextButton(onClick = {
                    scope.launch {
                        val b64 = ShareboxStorage.download(item.storagePath!!)
                        if (b64 != null) {
                            Native.openAttachment(b64, item.title?.ifBlank { null } ?: "file", "")
                        }
                    }
                }) { Text("Open") }
            }
        }
        val body = item.body
        if (!body.isNullOrBlank() && body != item.title) {
            Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            "from $who" + (item.createdAt?.take(10)?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// The ask-for-help dialog: what's wrong, how stuck, and a picture of the screen.
@Composable
private fun AskForHelp(
    ownerName: String,
    onDismiss: () -> Unit,
    onSend: (text: String, urgency: String, withShot: Boolean) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var urgency by remember { mutableStateOf("normal") }
    var withShot by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's happening?") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("Describe it however you like.") },
                )
                Spacer(Modifier.height(12.dp))
                Text("How stuck are you?", style = MaterialTheme.typography.labelMedium)
                URGENCY.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { urgency = value }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = urgency == value, onClick = { urgency = value })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (Native.supportsScreenshot) {
                    Row(
                        Modifier.fillMaxWidth().clickable { withShot = !withShot }.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = withShot, onCheckedChange = { withShot = it })
                        Text("Include a picture of my screen", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSend(text, urgency, withShot) }, enabled = text.isNotBlank()) {
                Text("Send to $ownerName")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Never mind") } },
    )
}

// Shown once, the first time — after that the session is remembered like anywhere else.
@Composable
private fun SignIn(onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Life OS", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Sign in once with the details you were given.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                email, { email = it; msg = "" },
                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Email") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                password, { password = it; msg = "" },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(), label = { Text("Password") },
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    busy = true; msg = "Signing in…"
                    scope.launch {
                        val r = SupabaseAuth.signIn(email.trim(), password)
                        busy = false
                        if (r.isSuccess) onDone() else msg = "That didn't work — check the email and password."
                    }
                },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
            ) { Text("Sign in") }
            if (busy) {
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(18.dp).height(18.dp))
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
