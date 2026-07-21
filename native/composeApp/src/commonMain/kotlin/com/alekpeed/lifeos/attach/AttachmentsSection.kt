package com.alekpeed.lifeos.attach

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.deleteBlob
import com.alekpeed.lifeos.platform.loadBlobImage
import com.alekpeed.lifeos.platform.readBlobBase64
import com.alekpeed.lifeos.platform.saveBlob
import com.alekpeed.lifeos.ui.SaveToast

// The shared attachments UI: a list of files (image thumbnail or type badge +
// name), each openable and removable, plus an "Add file" button. Bytes go through
// the device-local blob store; the caller keeps only the List<Attachment> on its
// record. Drop this into any editor that wants multi-file / PDF attachments.
@Composable
fun AttachmentsSection(
    attachments: List<Attachment>,
    onChange: (List<Attachment>) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Attachments",
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (attachments.isEmpty()) label else "$label (${attachments.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (Native.supportsFilePick) {
                OutlinedButton(onClick = {
                    Native.pickAttachment { name, mime, b64 ->
                        if (b64.isNullOrBlank()) return@pickAttachment
                        val id = saveBlob(b64) ?: run { SaveToast.show("Couldn't store file"); return@pickAttachment }
                        onChange(attachments + Attachment(id, name?.ifBlank { null } ?: "file", mime.orEmpty(), today().toString()))
                        SaveToast.show("Attached")
                    }
                }) { Text("+ Add file") }
            }
        }
        Spacer(Modifier.size(8.dp))
        attachments.forEach { a ->
            AttachmentRow(a, onOpen = {
                val b64 = readBlobBase64(a.blobId)
                if (b64 != null) Native.openAttachment(b64, a.name, a.mime) else SaveToast.show("File is missing")
            }, onRemove = {
                deleteBlob(a.blobId)
                onChange(attachments.filterNot { it.blobId == a.blobId })
            })
            Spacer(Modifier.size(6.dp))
        }
        if (attachments.isEmpty() && !Native.supportsFilePick) {
            Text("Attachments need the mobile app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AttachmentRow(a: Attachment, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val thumb = if (a.isImage) remember(a.blobId) { loadBlobImage(a.blobId) } else null
        if (thumb != null) {
            Image(thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
        } else {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(a.badge, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(a.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        TextButton(onClick = onOpen) { Text("Open") }
        TextButton(onClick = onRemove) { Text("×") }
    }
}
