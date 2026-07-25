package com.alekpeed.lifeos.attach

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.deleteBlob
import com.alekpeed.lifeos.platform.loadBlobImage
import com.alekpeed.lifeos.platform.saveBlob
import com.alekpeed.lifeos.ui.SaveToast

// A wrapping grid of photo thumbnails with a remove button on each and an add tile,
// plus tap-to-enlarge. The shared counterpart to AttachmentsSection: that one is a
// file list (right for PDFs and documents), this one is for records where the photos
// are the point and a list of filenames tells you nothing.
//
// Takes the record's whole attachment list and hands back the whole list: images are
// shown here, anything else is passed through untouched, so a record can use this and
// AttachmentsSection together without them fighting over the same field.
//
// Rows are built by hand rather than with LazyVerticalGrid because this sits inside
// already-scrolling editors, where a nested lazy grid has no bounded height.
@Composable
fun PhotoGrid(
    attachments: List<Attachment>,
    onChange: (List<Attachment>) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Photos",
    columns: Int = 3,
    // An optional single "primary" photo held directly on the record (the thumbnail
    // its list row shows). Rendered as the first tile so there's one place to see
    // every photo, even though it isn't stored as an attachment.
    primaryBlob: String = "",
    onPrimaryChange: ((String) -> Unit)? = null,
) {
    val photos = attachments.filter { it.isImage }
    val others = attachments.filterNot { it.isImage }
    var showSource by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf("") } // blob id being enlarged
    val hasPrimary = primaryBlob.isNotBlank() && onPrimaryChange != null

    fun add(b64: String?) {
        if (b64.isNullOrBlank()) return
        val id = saveBlob(b64) ?: run { SaveToast.show("Couldn't store photo"); return }
        val n = photos.size + 1
        onChange(others + photos + Attachment(id, "photo-$n.jpg", "image/jpeg", today().toString()))
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            if (photos.isEmpty() && !hasPrimary) label else "$label (${photos.size + if (hasPrimary) 1 else 0})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        // Tiles: the primary photo (if any), then the image attachments, then "+".
        val tiles = buildList {
            if (hasPrimary) add(primaryBlob to true)
            photos.forEach { add(it.blobId to false) }
        }
        val canAdd = Native.supportsCamera || Native.supportsFilePick
        val cells: List<Pair<String, Boolean>?> = tiles + if (canAdd) listOf(null) else emptyList()

        cells.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell ->
                    Box(Modifier.weight(1f)) {
                        if (cell == null) {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (Native.supportsCamera) {
                                            showSource = true
                                        } else {
                                            Native.pickAttachment { _, _, b64 -> add(b64) }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "+\nAdd",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            val (blob, isPrimary) = cell
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                val img = remember(blob) { if (blob.isBlank()) null else loadBlobImage(blob) }
                                Box(
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { viewing = blob },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (img != null) {
                                        Image(img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        Text("🖼", style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                                // Remove, top-right of the tile — the web view's × on each thumb.
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                                        .clip(CircleShape).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.66f))
                                        .clickable {
                                            deleteBlob(blob)
                                            if (isPrimary) onPrimaryChange?.invoke("")
                                            else onChange(others + photos.filterNot { it.blobId == blob })
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("×", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                // Pad the last row so two photos don't stretch to half the screen each.
                repeat(columns - row.size) { Box(Modifier.weight(1f)) {} }
            }
        }

        if (photos.isEmpty() && !hasPrimary && !canAdd) {
            Text(
                "Photos need a camera or file picker.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showSource) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSource = false },
            title = { Text("Add a photo") },
            text = { Text("Take a new photo, or choose one from your library.") },
            confirmButton = { TextButton(onClick = { showSource = false; Native.takePhoto { add(it) } }) { Text("Take a photo") } },
            dismissButton = { TextButton(onClick = { showSource = false; Native.capturePhoto { add(it) } }) { Text("Choose from library") } },
        )
    }

    if (viewing.isNotBlank()) {
        val blob = viewing
        Dialog(onDismissRequest = { viewing = "" }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(12.dp),
            ) {
                val img = remember(blob) { loadBlobImage(blob) }
                if (img != null) {
                    Image(img, contentDescription = null, modifier = Modifier.fillMaxWidth().height(360.dp), contentScale = ContentScale.Fit)
                } else {
                    Text("This photo's file is missing.", style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { viewing = "" }) { Text("Close") }
                }
            }
        }
    }
}
