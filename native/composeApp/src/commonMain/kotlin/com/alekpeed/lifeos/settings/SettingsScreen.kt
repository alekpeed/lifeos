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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.ai.DEFAULT_AI_MODEL
import com.alekpeed.lifeos.data.DATA_SOURCES
import com.alekpeed.lifeos.data.countOf
import com.alekpeed.lifeos.interfaces.Interfaces
import com.alekpeed.lifeos.platform.Native

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
    var apiKey by remember { mutableStateOf(Storage.read("ApiKey") ?: "") }
    var aiModel by remember { mutableStateOf(Storage.read("AiModel")?.ifBlank { null } ?: DEFAULT_AI_MODEL) }
    val aiModels = listOf(
        "claude-opus-4-8" to "Opus 4.8",
        "claude-sonnet-5" to "Sonnet 5",
        "claude-haiku-4-5" to "Haiku 4.5",
    )

    Column(Modifier.fillMaxSize().padding(20.dp)) {
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
            "Your Anthropic API key, stored only on this device. Powers Ask and the Assistant.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; Storage.write("ApiKey", it.trim()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
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

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.height(24.dp))

        Text("STORAGE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("$totalItems items saved locally", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Local device storage. A shared database and cross-device sync land with the data layer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
