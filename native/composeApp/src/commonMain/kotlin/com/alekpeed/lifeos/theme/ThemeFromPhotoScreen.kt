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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.alekpeed.lifeos.Storage

// Theme from Photo — the web version samples a photo's pixels for a palette and
// applies a swatch as the app accent. Native has neither an image-decode/pick
// layer nor a runtime accent system yet, so this ports the choosing half: a
// preset palette + a custom hex, persisted as your chosen accent. Pulling colors
// from a photo and applying the accent live arrive with the media/theming layers.

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
    var chosen by remember { mutableStateOf(Storage.read("Theme from Photo")?.trim().orEmpty()) }
    var custom by remember { mutableStateOf("") }
    fun choose(hex: String) { chosen = hex; Storage.write("Theme from Photo", hex) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Theme from Photo", style = MaterialTheme.typography.headlineMedium)
        Text("Pick an accent color to keep. Pulling a palette from one of your photos and applying it live come with the media and theming layers.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

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
