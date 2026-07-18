package com.alekpeed.lifeos.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import com.alekpeed.lifeos.ai.AiClient
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.deleteBlob
import com.alekpeed.lifeos.platform.loadBlobImage
import com.alekpeed.lifeos.platform.saveBlob
import com.alekpeed.lifeos.ui.DateField
import com.alekpeed.lifeos.ui.SaveToast
import com.alekpeed.lifeos.ui.usDate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val DANGER = Color(0xFFD64545)
private val OVERDUE = Color(0xFFE05C5C)

private const val SCAN_SYSTEM =
    "You read a photo of a document (a lease, insurance card, warranty, ID, bill, medical " +
        "letter, etc.). Respond with ONLY a JSON object and no other text — no prose, no " +
        "markdown, no code fence. Use exactly these keys: " +
        "title, category, issuer, policyNumber, expiryDate, transcription, summary. " +
        "\"transcription\" is a faithful, verbatim transcription of ALL readable text in the " +
        "document, preserving line breaks between lines (use \\n). Do not paraphrase or omit text. " +
        "\"summary\" is a plain-language summary of what the document is and its key facts — as long " +
        "as it needs to be to capture what matters and no longer: a sentence for something trivial, a " +
        "short paragraph for something involved. It should let you understand the document without " +
        "reading it, but it is NOT a restatement of the full text (that is the transcription). " +
        "\"category\" is a short lowercase type such as lease, insurance, warranty, id, medical, bill. " +
        "\"expiryDate\" must be an ISO date (YYYY-MM-DD) or an empty string if none is visible. " +
        "Use an empty string for any field you cannot read confidently. Never invent values."

private val scanJson = Json { ignoreUnknownKeys = true }

// Pull the model's JSON out of whatever it returned (it may wrap it in prose or a
// code fence despite instructions) and build a draft Document. Returns null if no
// object is parseable. id is filled in by the caller.
private fun parseScan(raw: String): Document? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val obj = try {
        scanJson.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
    } catch (e: Exception) {
        return null
    }
    fun field(key: String): String =
        try { obj[key]?.jsonPrimitive?.content?.takeIf { it != "null" }?.trim().orEmpty() } catch (e: Exception) { "" }

    val title = field("title").ifBlank { "Scanned document" }
    val doc = Document(
        id = 0L,
        title = title.replace("\n", " "),
        category = field("category").replace("\n", " "),
        issuer = field("issuer").replace("\n", " "),
        policyNumber = field("policyNumber").replace("\n", " "),
        expiryDate = field("expiryDate"),
        transcription = field("transcription"), // keep line breaks
        summary = field("summary"),
    )
    return doc
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentsScreen() {
    var data by remember { mutableStateOf(loadDocuments()) }
    var counter by remember { mutableStateOf(data.documents.maxOfOrNull { it.id } ?: 0L) }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: DocumentsData) { data = d; saveDocuments(d); SaveToast.show() }

    var input by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var selected by remember { mutableStateOf<Long?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var showSource by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Handle a captured/picked photo: null = cancelled (silent), "" = couldn't
    // decode the image, otherwise a base64 JPEG to send for extraction.
    fun onPhoto(b64: String?) {
        when {
            b64 == null -> {}
            b64.isEmpty() -> scanError = "Couldn't read that image — try another photo."
            else -> {
                scanError = null
                scanning = true
                scope.launch {
                    val reply = AiClient.askWithImage(SCAN_SYSTEM, "Read this document and return the JSON.", b64, 2048)
                    scanning = false
                    if (reply.isError) { scanError = reply.text; return@launch }
                    val draft = parseScan(reply.text)
                    if (draft == null) { scanError = "Couldn't read fields from that photo — add it manually."; return@launch }
                    val id = freshId()
                    val blobId = saveBlob(b64) // keep the scanned photo with the record
                    save(data.copy(documents = data.documents + draft.copy(id = id, photoBlob = blobId ?: "")))
                    selected = id
                }
            }
        }
    }

    fun startScan() {
        if (!AiClient.hasKey()) { scanError = "Add an AI key in Settings to scan documents."; return }
        scanError = null
        showSource = true
    }

    val categories = data.documents.mapNotNull { it.category.ifBlank { null } }.distinct()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Documents", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("New document") })
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim().replace("\n", " ")
                if (t.isNotEmpty()) { save(data.copy(documents = data.documents + Document(freshId(), t))); input = "" }
            }) { Text("Add") }
        }

        if (Native.supportsCamera) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { startScan() }, enabled = !scanning) {
                    if (scanning) {
                        CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Reading…")
                    } else {
                        Text("📷 Scan a document")
                    }
                }
            }
            scanError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = OVERDUE)
            }
        }

        if (showSource) {
            AlertDialog(
                onDismissRequest = { showSource = false },
                title = { Text("Scan a document") },
                text = { Text("Take a new photo, or choose one from your library.") },
                confirmButton = {
                    TextButton(onClick = { showSource = false; Native.takePhoto { onPhoto(it) } }) { Text("Take a photo") }
                },
                dismissButton = {
                    TextButton(onClick = { showSource = false; Native.capturePhoto { onPhoto(it) } }) { Text("Choose from library") }
                },
            )
        }

        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("All") })
                categories.forEach { c ->
                    FilterChip(selected = filter == c, onClick = { filter = c }, label = { Text(c) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        val filtered = data.documents
            .filter { filter == "all" || it.category == filter }
            .sortedBy { it.expiryDate.ifBlank { "9999" } }

        if (filtered.isEmpty()) {
            Text("No documents match the current filter.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { doc ->
                Column {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selected = if (selected == doc.id) null else doc.id }.padding(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(doc.title.ifBlank { "(untitled document)" }, style = MaterialTheme.typography.bodyLarge)
                            DocMeta(doc)
                        }
                    }
                    if (selected == doc.id) DocumentDetail(data, ::save, doc) { selected = null }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocMeta(doc: Document) {
    val chips = buildList {
        if (doc.category.isNotBlank()) add(doc.category to false)
        if (doc.issuer.isNotBlank()) add(doc.issuer to false)
        if (doc.linkedContact.isNotBlank()) add("👤 ${doc.linkedContact}" to false)
        if (doc.expiryDate.isNotBlank()) {
            val shown = usDate(doc.expiryDate).ifBlank { doc.expiryDate }
            val label = when (expiryState(doc)) {
                ExpiryState.EXPIRED -> "Expired $shown"
                ExpiryState.SOON -> "Expiring $shown"
                else -> shown
            }
            add(label to (expiryState(doc) == ExpiryState.EXPIRED || expiryState(doc) == ExpiryState.SOON))
        }
    }
    if (chips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { (t, warn) ->
            Text(t, style = MaterialTheme.typography.labelSmall, color = if (warn) OVERDUE else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DocumentDetail(data: DocumentsData, save: (DocumentsData) -> Unit, doc: Document, onClose: () -> Unit) {
    fun patch(f: (Document) -> Document) = save(data.copy(documents = data.documents.map { if (it.id == doc.id) f(it) else it }))
    var showSource by remember { mutableStateOf(false) }

    // Attach/replace the photo: save the new blob, drop the old one, point the
    // record at the new id.
    fun onAttach(b64: String?) {
        if (b64.isNullOrEmpty()) return
        val id = saveBlob(b64) ?: return
        deleteBlob(doc.photoBlob)
        patch { it.copy(photoBlob = id) }
    }

    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) {
        Label("Title")
        Field(doc.title, "Title") { v -> patch { it.copy(title = v.replace("\n", " ")) } }
        Label("Category")
        Field(doc.category, "lease, insurance, warranty…") { v -> patch { it.copy(category = v.replace("\n", " ")) } }
        Label("Issuer")
        Field(doc.issuer, "Who issued this?") { v -> patch { it.copy(issuer = v.replace("\n", " ")) } }
        Label("Policy / account #")
        Field(doc.policyNumber, "Policy / account number") { v -> patch { it.copy(policyNumber = v.replace("\n", " ")) } }
        Label("Expiry date")
        DateField(doc.expiryDate) { v -> patch { it.copy(expiryDate = v) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { patch { it.copy(expiryDate = today().toString()) } }, label = { Text("Today") })
        }
        Label("Linked contact")
        com.alekpeed.lifeos.people.ContactField(doc.linkedContact) { v -> patch { it.copy(linkedContact = v) } }
        Label("Summary")
        Field(doc.summary, "Short summary of the document", singleLine = false) { v -> patch { it.copy(summary = v) } }
        Label("Transcription")
        Field(doc.transcription, "Full text of the document", singleLine = false) { v -> patch { it.copy(transcription = v) } }
        Label("Notes")
        Field(doc.notes, "Your own notes", singleLine = false) { v -> patch { it.copy(notes = v) } }

        Label("Photo")
        if (doc.photoBlob.isNotBlank()) {
            val img = remember(doc.photoBlob) { loadBlobImage(doc.photoBlob) }
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = "Attached photo",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("Photo attached (no preview available).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Native.supportsCamera) TextButton(onClick = { showSource = true }) { Text("Replace") }
                TextButton(onClick = { deleteBlob(doc.photoBlob); patch { it.copy(photoBlob = "") } }) { Text("Remove photo") }
            }
        } else if (Native.supportsCamera) {
            OutlinedButton(onClick = { showSource = true }) { Text("📷 Attach photo") }
        } else {
            Text("Photo attachments need a camera.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (showSource) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSource = false },
                title = { Text("Attach a photo") },
                text = { Text("Take a new photo, or choose one from your library.") },
                confirmButton = {
                    TextButton(onClick = { showSource = false; Native.takePhoto { onAttach(it) } }) { Text("Take a photo") }
                },
                dismissButton = {
                    TextButton(onClick = { showSource = false; Native.capturePhoto { onAttach(it) } }) { Text("Choose from library") }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Done") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                deleteBlob(doc.photoBlob)
                save(data.copy(documents = data.documents.filterNot { it.id == doc.id }))
                onClose()
            }) {
                Text("Delete document", color = DANGER)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Field(value: String, placeholder: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine, placeholder = { Text(placeholder) },
    )
}
