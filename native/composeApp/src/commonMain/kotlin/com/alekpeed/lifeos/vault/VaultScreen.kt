package com.alekpeed.lifeos.vault

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.ui.SaveToast

private val DANGER = Color(0xFFD64545)

// The vault, front of house.
//
// Three states, and the screen is only ever in one: no vault yet, locked, open. It locks
// itself when you navigate away — leaving it open behind you would undo the point of
// having it, and a lock you have to remember to press is a lock that stays unpressed.
@Composable
fun VaultScreen() {
    var hasVault by remember { mutableStateOf(Vault.exists()) }
    var open by remember { mutableStateOf(Vault.unlocked) }
    var data by remember { mutableStateOf(if (Vault.unlocked) Vault.load() else null) }
    var message by remember { mutableStateOf("") }

    // Leaving the screen locks it. Not a nicety: an unlocked vault behind a back button
    // is an unlocked vault.
    DisposableEffect(Unit) {
        onDispose {
            Vault.lock()
        }
    }

    fun refresh() {
        data = Vault.load()
        open = Vault.unlocked
        hasVault = Vault.exists()
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Vault", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (open) TextButton(onClick = { Vault.lock(); refresh() }) { Text("Lock") }
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        when {
            !hasVault -> CreatePane(
                onCreate = { pass ->
                    if (Vault.create(pass)) { message = ""; refresh() }
                    else message = "That passphrase is too short — $MIN_PASSPHRASE characters at least."
                },
            )
            !open -> UnlockPane(
                onUnlock = { pass ->
                    if (Vault.unlock(pass)) { message = ""; refresh() }
                    else message = "That didn't open it."
                },
                onDestroy = { Vault.destroy(); message = ""; refresh() },
            )
            else -> EntriesPane(
                data = data ?: VaultData(),
                onChange = { next ->
                    if (Vault.save(next)) { data = next; SaveToast.show() } else message = "The vault locked itself."
                },
            )
        }
    }
}

@Composable
private fun CreatePane(onCreate: (String) -> Unit) {
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }

    Spacer(Modifier.height(10.dp))
    Text(
        "Anything in here is encrypted on this device before it is written down or synced. " +
            "The passphrase never leaves the device and is never stored, so what reaches Supabase " +
            "is a blob nobody — including whoever holds the database — can open.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "There is no recovery. No reset, no hint, no way back in. If you forget the passphrase " +
            "the contents are gone permanently — that is what makes it worth having, and it is not " +
            "something anyone can undo for you afterwards.",
        style = MaterialTheme.typography.bodyMedium,
        color = DANGER,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "Two things it does not do. It does not hide anything from someone holding your phone " +
            "while the vault is open, and it does not encrypt the rest of the app — the daily " +
            "digest has to be able to read your bills to tell you they are due.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        pass, { pass = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        again, { again = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("Again") }, visualTransformation = PasswordVisualTransformation(),
    )
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
        Text(
            "I understand that forgetting this passphrase destroys what's inside.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = { onCreate(pass) },
        enabled = acknowledged && pass.length >= MIN_PASSPHRASE && pass == again,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Create the vault") }
    if (pass.isNotEmpty() && again.isNotEmpty() && pass != again) {
        Text("Those don't match.", style = MaterialTheme.typography.labelSmall, color = DANGER)
    }
}

@Composable
private fun UnlockPane(onUnlock: (String) -> Unit, onDestroy: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    var confirmDestroy by remember { mutableStateOf(false) }

    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        pass, { pass = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation(),
    )
    Spacer(Modifier.height(10.dp))
    Button(onClick = { onUnlock(pass) }, enabled = pass.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        Text("Unlock")
    }
    Spacer(Modifier.height(20.dp))
    TextButton(onClick = { confirmDestroy = true }) {
        Text("Forgotten it?", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirmDestroy) {
        AlertDialog(
            onDismissRequest = { confirmDestroy = false },
            title = { Text("There is no recovery") },
            text = {
                Text(
                    "A forgotten passphrase cannot be recovered — not by you, not by anyone with the " +
                        "database, not by me. The only thing left to do is empty the vault and start " +
                        "again, which deletes everything in it permanently.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDestroy = false; onDestroy() }) {
                    Text("Delete everything in the vault", color = DANGER)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDestroy = false }) { Text("Keep trying") } },
        )
    }
}

@Composable
private fun EntriesPane(data: VaultData, onChange: (VaultData) -> Unit) {
    var title by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Long?>(null) }

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            title, { title = it }, modifier = Modifier.weight(1f), singleLine = true,
            placeholder = { Text("What is it?") },
        )
        Spacer(Modifier.width(10.dp))
        Button(onClick = {
            val t = title.trim().replace("\n", " ")
            if (t.isNotEmpty()) {
                onChange(Vault.put(data, VaultEntry(Vault.nextId(data), t)))
                title = ""
            }
        }) { Text("Add") }
    }
    Spacer(Modifier.height(12.dp))

    if (data.entries.isEmpty()) {
        Text(
            "Empty. Passwords, recovery codes, the safe combination — anything that should be " +
                "ciphertext everywhere except right here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(data.entries, key = { it.id }) { e ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.weight(1f).clickable { editing = if (editing == e.id) null else e.id },
                    ) {
                        Text(e.title, style = MaterialTheme.typography.bodyLarge)
                        // Hidden by default even with the vault open: a shoulder is a
                        // threat the encryption does nothing about.
                        Text(
                            if (revealed == e.id) e.secret.ifBlank { "(nothing saved)" }
                            else if (e.secret.isBlank()) "(nothing saved)" else "••••••••",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (e.secret.isNotBlank()) {
                        TextButton(onClick = { revealed = if (revealed == e.id) null else e.id }) {
                            Text(if (revealed == e.id) "Hide" else "Show")
                        }
                        TextButton(onClick = { Native.copyToClipboard(e.secret); SaveToast.show("Copied") }) {
                            Text("Copy")
                        }
                    }
                }
                if (editing == e.id) {
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            e.title, { v -> onChange(Vault.put(data, e.copy(title = v.replace("\n", " ")))) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Title") },
                        )
                        OutlinedTextField(
                            e.secret, { v -> onChange(Vault.put(data, e.copy(secret = v))) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Secret") },
                        )
                        OutlinedTextField(
                            e.notes, { v -> onChange(Vault.put(data, e.copy(notes = v))) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Notes") },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (e.updated.isNotBlank()) {
                                Text(
                                    "changed ${e.updated}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            TextButton(onClick = {
                                onChange(Vault.remove(data, e.id))
                                editing = null
                            }) { Text("Delete", color = DANGER) }
                        }
                    }
                }
            }
        }
    }
}

// Offered in Settings rather than here: changing a passphrase is a thing you do once,
// and it does not belong beside the everyday list.
@Composable
fun VaultPassphraseChanger(onDone: (String) -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            old, { old = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Current passphrase") }, visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            new, { new = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("New passphrase") }, visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                onDone(
                    if (Vault.changePassphrase(old, new)) "Passphrase changed, and the contents re-encrypted."
                    else "That didn't work — check the current passphrase, and that the new one is at least $MIN_PASSPHRASE characters.",
                )
                old = ""; new = ""
            },
            enabled = old.isNotEmpty() && new.length >= MIN_PASSPHRASE,
        ) { Text("Change passphrase") }
    }
}
