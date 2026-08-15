package com.alekpeed.lifeos.attach

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.ui.SaveToast
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// A record — and its photos/files — as one file you can move to another device
// by hand and import there. See RecordPackage.kt for the mechanism and why it
// exists. This is the UI half: one button pair, dropped into any module that
// has one. Export sits wherever you're looking at a specific record; Import
// sits at the top of that module's list, since it's adding a new one.

private val DANGER = Color(0xFFD64545)

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun ExportRecordButton(descriptor: ModuleDescriptor, recordId: Long, suggestedName: String) {
    var msg by remember { mutableStateOf<String?>(null) }
    TextButton(onClick = {
        val bytes = RecordPackage.export(descriptor, recordId)
        if (bytes == null) {
            msg = "Nothing to export."
        } else {
            val safe = suggestedName.map { if (it.isLetterOrDigit()) it else '_' }
                .joinToString("").take(40).ifBlank { "export" }
            Native.exportPackageFile("$safe.zip", Base64.encode(bytes)) { ok ->
                msg = if (ok) null else "Save was cancelled."
                if (ok) SaveToast.show("Exported")
            }
        }
    }) { Text("Export") }
    msg?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun ImportRecordButton(descriptor: ModuleDescriptor, onImported: () -> Unit) {
    var msg by remember { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = {
        Native.pickAttachment { _, _, base64 ->
            if (base64 == null) return@pickAttachment
            when (val result = RecordPackage.import(Base64.decode(base64))) {
                is ImportResult.Success -> {
                    msg = null
                    SaveToast.show("Imported 1 ${result.label}")
                    onImported()
                }
                is ImportResult.Failure -> msg = result.reason
            }
        }
    }) { Text("📥 Import") }
    msg?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = DANGER) }
}
