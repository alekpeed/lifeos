package com.alekpeed.lifeos.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.paletteFromBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Theme from Photo — pull a palette out of one of your photos, or pick from a
// preset palette / custom hex. Tapping any swatch applies it as the app accent
// immediately. On Android the photo's dominant colors are sampled on-device; the
// preset + hex path works everywhere.

private val PRESETS = listOf(
    "#B5854B", "#3E8E7E", "#9E4A4A", "#5B6EE1", "#C97BB0",
    "#4C9AA6", "#C0A24C", "#7D5BA6", "#D98C5F", "#57A773",
)

private fun parseHex(hex: String): Color? {
    val h = hex.trim().removePrefix("#")
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}

@Composable
fun ThemeFromPhotoScreen() {
    var chosen by remember { mutableStateOf(com.alekpeed.lifeos.AppTheme.accentHex) }
    var custom by remember { mutableStateOf("") }
    var extracted by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var showSource by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun choose(hex: String) { chosen = hex; com.alekpeed.lifeos.AppTheme.updateAccent(hex) }

    fun onPhoto(b64: String?) {
        when {
            b64 == null -> {}
            b64.isEmpty() -> photoError = "Couldn't read that image — try another photo."
            else -> {
                photoError = null
                busy = true
                scope.launch {
                    val palette = withContext(Dispatchers.Default) { paletteFromBase64(b64) }
                    busy = false
                    if (palette.isEmpty()) photoError = "Couldn't pull colors from that photo."
                    else extracted = palette
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Theme from Photo", style = MaterialTheme.typography.headlineMedium)
        Text("Pick an accent color — it applies across the app immediately.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        if (Native.supportsCamera) {
            OutlinedButton(onClick = { photoError = null; showSource = true }, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Reading…")
                } else {
                    Text("📷 Pull colors from a photo")
                }
            }
            photoError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            if (extracted.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("From your photo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Swatches(extracted, chosen) { choose(it) }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (showSource) {
            AlertDialog(
                onDismissRequest = { showSource = false },
                title = { Text("Pull colors from a photo") },
                text = { Text("Take a new photo, or choose one from your library.") },
                confirmButton = {
                    TextButton(onClick = { showSource = false; Native.takePhoto { onPhoto(it) } }) { Text("Take a photo") }
                },
                dismissButton = {
                    TextButton(onClick = { showSource = false; Native.capturePhoto { onPhoto(it) } }) { Text("Choose from library") }
                },
            )
        }

        Text("Palette", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Swatches(PRESETS, chosen) { choose(it) }
        Spacer(Modifier.height(16.dp))

        Text("Custom hex", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(custom, { custom = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("#RRGGBB") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val h = custom.trim()
                if (parseHex(h) != null) { choose(if (h.startsWith("#")) h else "#$h"); custom = "" }
            }) { Text("Set") }
        }
        Spacer(Modifier.height(16.dp))

        if (chosen.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(parseHex(chosen) ?: MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(10.dp))
                Text("Chosen accent: $chosen", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(hexes: List<String>, chosen: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        hexes.forEach { hex ->
            val selected = hex.equals(chosen, ignoreCase = true)
            Box(
                Modifier.size(if (selected) 46.dp else 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(parseHex(hex) ?: MaterialTheme.colorScheme.primary)
                    .clickable { onPick(hex) },
            )
        }
    }
}
