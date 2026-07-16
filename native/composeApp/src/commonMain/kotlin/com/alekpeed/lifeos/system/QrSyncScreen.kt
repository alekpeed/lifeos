package com.alekpeed.lifeos.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.DATA_SOURCES
import com.alekpeed.lifeos.data.countOf
import com.alekpeed.lifeos.platform.Native

// Device sync. Builds the payload that pairs two devices — a compact snapshot
// fingerprint of what this device holds. Shown as text today; a QR interface
// renders the same payload as a scannable code (camera + QR encode) when plugged
// in. The data contract doesn't change, only how it's displayed.
@Composable
fun QrSyncScreen() {
    val active = DATA_SOURCES.map { it.label to countOf(it.key) }.filter { it.second > 0 }
    val total = active.sumOf { it.second }
    val token = buildString {
        append("lifeos:v1;items=")
        append(total)
        append(';')
        append(active.joinToString(",") { "${it.first.take(3).lowercase()}=${it.second}" })
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("QR Sync", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Pair another device by sharing this payload. A QR interface turns it into a scannable code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(18.dp),
        ) {
            Text("SYNC PAYLOAD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(token, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { Native.shareText(token) }) { Text("Share payload") }
        Spacer(Modifier.height(12.dp))
        Text(
            "$total items across ${active.size} modules on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
