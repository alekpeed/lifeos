package com.alekpeed.lifeos.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.ai.DEFAULT_AI_MODEL
import com.alekpeed.lifeos.ai.DEFAULT_GEMINI_MODEL
import com.alekpeed.lifeos.ai.DEFAULT_OPENAI_MODEL
import com.alekpeed.lifeos.data.DATA_SOURCES
import com.alekpeed.lifeos.data.countOf
import com.alekpeed.lifeos.integrations.TelegramClient
import com.alekpeed.lifeos.interfaces.Interfaces
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.sync.SupabaseAuth
import com.alekpeed.lifeos.sync.SupabaseSync
import com.alekpeed.lifeos.sync.SyncEngine
import com.alekpeed.lifeos.sync.SyncMeta
import kotlinx.coroutines.launch

private fun pretty(id: String): String =
    id.split('-', '_').joinToString(" ") { part ->
        if (part.isEmpty()) part else part.replaceFirstChar { it.uppercase() }
    }

// Real settings. The centrepiece is the interface switcher: it lists every
// registered interface (the built-in functional "default" plus any graphical
// interface Alek plugs in) and switches the whole app live. Because every page
// renders through Interfaces.Render, choosing one here re-skins the app without
// touching module data.
@Composable
fun SettingsScreen() {
    val interfaces = Interfaces.available
    val active = Interfaces.active
    val totalItems = DATA_SOURCES.sumOf { countOf(it.key) }
    var keepAwake by remember { mutableStateOf(false) }
    var wakeWord by remember { mutableStateOf(false) }
    var deviceMsg by remember { mutableStateOf("") }
    var wakePhrase by remember { mutableStateOf(Storage.read("WakePhrase")?.ifBlank { null } ?: "hey life") }
    var onlyMyVoice by remember { mutableStateOf(Native.onlyMyVoiceEnabled()) }
    var hasVoiceprint by remember { mutableStateOf(Native.hasVoiceprint()) }
    var enrolling by remember { mutableStateOf(false) }
    var enrollStatus by remember { mutableStateOf("") }
    val showDevice = Native.supportsKeepAwake || Native.supportsWakeWord || Native.supportsGeofence
    var aiProvider by remember { mutableStateOf(Storage.read("AiProvider")?.ifBlank { null } ?: "claude") }
    var apiKey by remember { mutableStateOf(Storage.read("ApiKey") ?: "") }
    var aiModel by remember { mutableStateOf(Storage.read("AiModel")?.ifBlank { null } ?: DEFAULT_AI_MODEL) }
    var openaiKey by remember { mutableStateOf(Storage.read("OpenAiKey") ?: "") }
    var openaiModel by remember { mutableStateOf(Storage.read("OpenAiModel")?.ifBlank { null } ?: DEFAULT_OPENAI_MODEL) }
    var geminiKey by remember { mutableStateOf(Storage.read("GeminiKey") ?: "") }
    var geminiModel by remember { mutableStateOf(Storage.read("GeminiModel")?.ifBlank { null } ?: DEFAULT_GEMINI_MODEL) }
    var tgToken by remember { mutableStateOf(Storage.read("TgToken") ?: "") }
    var tgChat by remember { mutableStateOf(Storage.read("TgChatId") ?: "") }
    var tgMsg by remember { mutableStateOf("") }
    var sbEmail by remember { mutableStateOf(SupabaseAuth.email() ?: "") }
    var sbPassword by remember { mutableStateOf("") }
    var sbSignedIn by remember { mutableStateOf(SupabaseAuth.isSignedIn()) }
    var sbBusy by remember { mutableStateOf(false) }
    var sbMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val aiModels = listOf(
        "claude-opus-4-8" to "Opus 4.8",
        "claude-sonnet-5" to "Sonnet 5",
        "claude-haiku-4-5" to "Haiku 4.5",
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        Text("INTERFACE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "Swap the whole app's look. Graphical interfaces appear here once registered.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        interfaces.forEach { id ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { Interfaces.setActive(id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = id == active, onClick = { Interfaces.setActive(id) })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(pretty(id), style = MaterialTheme.typography.bodyLarge)
                    if (id == Interfaces.DEFAULT) {
                        Text(
                            "Built-in functional screens",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (interfaces.size == 1) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Only the default is installed. Register a graphical interface to see it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showDevice) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Spacer(Modifier.height(24.dp))

            Text("DEVICE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (Native.supportsKeepAwake) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cooking mode", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Keep the screen on while you're following a recipe",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = keepAwake, onCheckedChange = { keepAwake = it; Native.keepScreenAwake(it) })
                }
            }

            if (Native.supportsWakeWord) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Wake word", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Listen for “hey life …” and capture what follows — offline, on-device (uses the mic; downloads a voice model on first use)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = wakeWord, onCheckedChange = { wakeWord = it; Native.setWakeWordEnabled(it) })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = wakePhrase,
                    onValueChange = { wakePhrase = it; Storage.write("WakePhrase", it.trim()) },
                    label = { Text("Wake phrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "A two-word phrase you rarely say by accident works best (e.g. “hey life”, “ok life”).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (Native.supportsSpeakerId) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Only my voice", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "The wake word fires only for your enrolled voice. A filter, not a lock — a recording of you could still pass.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = onlyMyVoice,
                        onCheckedChange = { onlyMyVoice = it; Native.setOnlyMyVoice(it) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = !enrolling,
                        onClick = {
                            enrolling = true
                            enrollStatus = "Starting…"
                            Native.enrollVoice(
                                onStatus = { enrollStatus = it },
                                onResult = { enrolling = false; hasVoiceprint = Native.hasVoiceprint() },
                            )
                        },
                    ) {
                        Text(if (hasVoiceprint) "Re-enroll my voice" else "Enroll my voice")
                    }
                    if (hasVoiceprint && !enrolling) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            Native.clearVoiceprint(); hasVoiceprint = false; enrollStatus = "Voiceprint cleared"
                        }) { Text("Clear") }
                    }
                }
                val statusLine = when {
                    enrollStatus.isNotBlank() -> enrollStatus
                    hasVoiceprint -> "Voice enrolled ✓"
                    else -> "Not enrolled yet"
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (Native.supportsGeofence) {
                Spacer(Modifier.height(14.dp))
                Text("Arrival alert", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Get a nudge when you next return to where you are now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { Native.armArrivalHere("here"); deviceMsg = "Arrival alert armed for this location" }) { Text("Arm here") }
                    OutlinedButton(onClick = { Native.clearArrivals(); deviceMsg = "Arrival alerts cleared" }) { Text("Clear") }
                }
                if (deviceMsg.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(deviceMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.height(24.dp))

        Text("AI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick a provider and paste that provider's API key — stored only on this device. Powers Ask and the Assistant.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text("Provider", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("claude" to "Claude", "openai" to "OpenAI", "gemini" to "Gemini").forEach { (id, label) ->
                FilterChip(
                    selected = aiProvider == id,
                    onClick = { aiProvider = id; Storage.write("AiProvider", id) },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when (aiProvider) {
            "openai" -> {
                OutlinedTextField(
                    value = openaiKey,
                    onValueChange = { openaiKey = it; Storage.write("OpenAiKey", it.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("OpenAI API key") },
                    placeholder = { Text("sk-…") },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = openaiModel,
                    onValueChange = { openaiModel = it; Storage.write("OpenAiModel", it.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Model") },
                    placeholder = { Text(DEFAULT_OPENAI_MODEL) },
                )
                Spacer(Modifier.height(4.dp))
                Text("e.g. gpt-4o-mini, gpt-4o, gpt-4.1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "gemini" -> {
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it; Storage.write("GeminiKey", it.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Google Gemini API key") },
                    placeholder = { Text("AIza…") },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = geminiModel,
                    onValueChange = { geminiModel = it; Storage.write("GeminiModel", it.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Model") },
                    placeholder = { Text(DEFAULT_GEMINI_MODEL) },
                )
                Spacer(Modifier.height(4.dp))
                Text("e.g. gemini-1.5-flash, gemini-1.5-pro, gemini-2.0-flash", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; Storage.write("ApiKey", it.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Anthropic API key") },
                    placeholder = { Text("sk-ant-…") },
                )
                Spacer(Modifier.height(12.dp))
                Text("Model", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    aiModels.forEach { (id, label) ->
                        FilterChip(
                            selected = aiModel == id,
                            onClick = { aiModel = id; Storage.write("AiModel", id) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.height(24.dp))

        Text("TELEGRAM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "Send yourself messages through a bot you create with @BotFather. Send-only — paste the bot token and your chat id.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = tgToken,
            onValueChange = { tgToken = it; Storage.write("TgToken", it.trim()); tgMsg = "" },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("Bot token") },
            placeholder = { Text("123456:ABC-…") },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = tgChat,
            onValueChange = { tgChat = it; Storage.write("TgChatId", it.trim()); tgMsg = "" },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Chat id") },
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            enabled = tgToken.isNotBlank() && tgChat.isNotBlank(),
            onClick = {
                tgMsg = "Sending…"
                scope.launch {
                    val r = TelegramClient.send("Life OS test ✓")
                    tgMsg = if (r.isSuccess) "Sent ✓ — check Telegram" else (r.exceptionOrNull()?.message ?: "Send failed")
                }
            },
        ) { Text("Send test message") }
        if (tgMsg.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(tgMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.height(24.dp))

        Text("SYNC", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "Cross-device sync via your Supabase account. Sign in with the same email on each device to share your data.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        if (!sbSignedIn) {
            OutlinedTextField(
                value = sbEmail,
                onValueChange = { sbEmail = it; sbMsg = "" },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Email") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = sbPassword,
                onValueChange = { sbPassword = it; sbMsg = "" },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("Password") },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = !sbBusy && sbEmail.isNotBlank() && sbPassword.length >= 6,
                    onClick = {
                        sbBusy = true; sbMsg = "Signing in…"
                        scope.launch {
                            SupabaseAuth.signIn(sbEmail.trim(), sbPassword)
                                .onSuccess { sbSignedIn = true; sbPassword = ""; sbMsg = "Signed in" }
                                .onFailure { sbMsg = it.message ?: "Sign-in failed" }
                            sbBusy = false
                        }
                    },
                ) { Text("Sign in") }
                OutlinedButton(
                    enabled = !sbBusy && sbEmail.isNotBlank() && sbPassword.length >= 6,
                    onClick = {
                        sbBusy = true; sbMsg = "Creating account…"
                        scope.launch {
                            SupabaseAuth.signUp(sbEmail.trim(), sbPassword)
                                .onSuccess { session ->
                                    sbSignedIn = session
                                    sbMsg = if (session) "Account created — signed in" else "Account created — confirm your email, then sign in"
                                    if (session) sbPassword = ""
                                }
                                .onFailure { sbMsg = it.message ?: "Sign-up failed" }
                            sbBusy = false
                        }
                    },
                ) { Text("Create account") }
            }
        } else {
            Text("Signed in as ${SupabaseAuth.email() ?: "?"}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = !sbBusy,
                    onClick = {
                        sbBusy = true; sbMsg = "Syncing…"
                        scope.launch {
                            SupabaseSync.syncNow()
                                .onSuccess { sbMsg = "Synced — pushed ${it.pushed}, pulled ${it.applied}" }
                                .onFailure { sbMsg = it.message ?: "Sync failed" }
                            sbBusy = false
                        }
                    },
                ) { Text("Sync now") }
                OutlinedButton(
                    enabled = !sbBusy,
                    onClick = { SupabaseAuth.signOut(); sbSignedIn = false; sbMsg = "Signed out" },
                ) { Text("Sign out") }
            }
        }
        if (sbMsg.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(sbMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.height(24.dp))

        Text("STORAGE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("$totalItems items saved locally", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        val trackedRecords = remember { SyncMeta.all().size }
        val pendingChanges = remember { SyncEngine.pendingCount() }
        Text(
            "$trackedRecords records tracked for sync · $pendingChanges changed since last sync",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Every change is now stamped for last-write-wins sync. Cross-device sync turns on once a backend is connected.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
