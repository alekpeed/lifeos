package com.alekpeed.lifeos.system

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.DATA_SOURCES
import com.alekpeed.lifeos.data.countOf
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.sync.SupabaseAuth

// Storage key a scanned pairing code writes the account email into; the Settings
// sign-in field reads it as a pre-fill so the second device is one tap from
// signing into the same account (which is what actually syncs the data).
const val PAIR_EMAIL_KEY = "__pair_email"

// QR Sync — pairs a second device to your account. Cross-device sync on native is
// the account itself: sign into the same email on each device and the data merges
// (Supabase). So this screen makes that one scan away — the QR carries the account
// email (not a secret), the other device scans it and lands on a pre-filled
// sign-in. Below, a read-only fingerprint of what this device holds.
@Composable
fun QrSyncScreen() {
    val active = DATA_SOURCES.map { it.label to countOf(it.key) }.filter { it.second > 0 }
    val total = active.sumOf { it.second }
    val fingerprint = buildString {
        append("lifeos:v1;items=")
        append(total)
        append(';')
        append(active.joinToString(",") { "${it.first.take(3).lowercase()}=${it.second}" })
    }

    val signedIn = SupabaseAuth.isSignedIn()
    val email = SupabaseAuth.email()
    val pairToken = if (signedIn && !email.isNullOrBlank()) "lifeos:pair;v1;email=$email" else null
    val qr = remember(pairToken) { pairToken?.let { encodeQr(it) } }
    var status by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("QR Sync", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Sync happens through your account: sign into the same email on each device and your data merges automatically. This makes pairing a new device one scan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        if (signedIn && qr != null) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp),
                ) { QrCode(qr, Modifier.size(220.dp)) }
                Spacer(Modifier.height(10.dp))
                Text("Scan on your other device to pair it", style = MaterialTheme.typography.labelLarge)
                Text(email!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(18.dp))
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(18.dp),
            ) {
                Text("Sign in to pair devices", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text("Your account is the sync — sign in first, then scan to add another device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { Nav.open("settings") }) { Text("Open Settings → Sync") }
            }
            Spacer(Modifier.height(18.dp))
        }

        if (Native.supportsQrScan) {
            Button(onClick = {
                Native.scanQr { raw ->
                    val r = raw?.trim().orEmpty()
                    when {
                        r.isEmpty() -> status = "Scan cancelled"
                        r.startsWith("lifeos:pair") -> {
                            val em = Regex("email=([^;]+)").find(r)?.groupValues?.getOrNull(1)?.trim()
                            if (!em.isNullOrBlank()) {
                                Storage.write(PAIR_EMAIL_KEY, em)
                                status = "Pairing with $em — opening sign-in…"
                                Nav.open("settings")
                            } else status = "That code has no account to pair with."
                        }
                        else -> status = "Scanned: $r"
                    }
                }
            }) { Text("Scan a code to pair") }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(18.dp))
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(18.dp),
        ) {
            Text("ON THIS DEVICE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("$total items across ${active.size} modules", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(fingerprint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
    }
}

// Draws a QR module grid on a Canvas with a quiet-zone margin. Dark on white so it
// scans regardless of the app theme.
@Composable
fun QrCode(matrix: QrMatrix, modifier: Modifier) {
    Canvas(modifier) {
        val quiet = 4
        val cells = matrix.size + quiet * 2
        val cell = size.minDimension / cells
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix.modules[y * matrix.size + x]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset((x + quiet) * cell, (y + quiet) * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}
