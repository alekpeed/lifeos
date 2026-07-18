package com.alekpeed.lifeos.people

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.deleteBlob
import com.alekpeed.lifeos.platform.loadBlobImage
import com.alekpeed.lifeos.platform.saveBlob
import com.alekpeed.lifeos.ui.DateField
import com.alekpeed.lifeos.ui.SaveToast
import com.alekpeed.lifeos.ui.usDate

private val DANGER = Color(0xFFD64545)

private fun commaList(s: String) = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactsScreen() {
    var data by remember { mutableStateOf(loadContacts()) }
    var counter by remember { mutableStateOf(data.contacts.maxOfOrNull { it.id } ?: 0L) }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: ContactsData) { data = d; saveContacts(d); SaveToast.show() }

    var input by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var tagFilter by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Contacts", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("New contact") })
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val n = input.trim().replace("\n", " ")
                if (n.isNotEmpty() && data.contacts.none { it.name.equals(n, ignoreCase = true) }) {
                    save(data.copy(contacts = data.contacts + Contact(freshId(), n))); input = ""
                }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("🔍 Search name, phone, email, company, tag…") },
        )
        val allTags = data.contacts.flatMap { it.tags }.distinct().sorted()
        if (allTags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                allTags.forEach { t ->
                    androidx.compose.material3.FilterChip(
                        selected = tagFilter == t,
                        onClick = { tagFilter = if (tagFilter == t) null else t },
                        label = { Text("#$t") },
                    )
                }
            }
        }

        if (Native.supportsContacts) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                val existing = data.contacts.map { it.name.lowercase() }.toMutableSet()
                val added = mutableListOf<Contact>()
                Native.importContacts().forEach { c ->
                    if (existing.add(c.name.lowercase())) {
                        added.add(Contact(freshId(), c.name, phones = if (c.detail.isNotBlank()) listOf(c.detail) else emptyList()))
                    }
                }
                if (added.isNotEmpty()) save(data.copy(contacts = data.contacts + added))
                note = if (added.isEmpty()) "No new contacts (grant permission if this keeps happening)" else "Imported ${added.size} contact(s)"
            }) { Text("📇 Import from phone") }
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (data.contacts.isEmpty()) { Muted("No contacts yet."); return }
        val q = query.trim().lowercase()
        val shown = data.contacts
            .filter { tagFilter == null || tagFilter in it.tags }
            .filter { c ->
                q.isEmpty() || listOf(
                    c.name, c.company, c.title, c.relationship, c.address, c.notes,
                    c.phones.joinToString(" "), c.emails.joinToString(" "), c.tags.joinToString(" "),
                ).any { it.lowercase().contains(q) }
            }
            .sortedBy { it.name.lowercase() }
        if (shown.isEmpty()) { Muted("No matches."); return }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.id }) { c ->
                Column {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selected = if (selected == c.id) null else c.id }.padding(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name.ifBlank { "(unnamed)" }, style = MaterialTheme.typography.bodyLarge)
                            val chips = buildList {
                                if (c.relationship.isNotBlank()) add(c.relationship)
                                if (c.company.isNotBlank()) add(c.company)
                                if (c.birthday.isNotBlank()) add("🎂 ${usDate(c.birthday).ifBlank { c.birthday }}")
                                c.phones.firstOrNull()?.let { add(it) }
                                c.tags.forEach { add("#$it") }
                            }
                            if (chips.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                chips.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                    if (selected == c.id) ContactDetail(data, ::save, c) { selected = null }
                }
            }
        }
    }
}

@Composable
private fun ContactDetail(data: ContactsData, save: (ContactsData) -> Unit, c: Contact, onClose: () -> Unit) {
    fun patch(f: (Contact) -> Contact) = save(data.copy(contacts = data.contacts.map { if (it.id == c.id) f(it) else it }))
    var showSource by remember { mutableStateOf(false) }

    // Attach/replace the photo: save the new blob, drop the old one, point the
    // record at the new id.
    fun onAttach(b64: String?) {
        if (b64.isNullOrEmpty()) return
        val id = saveBlob(b64) ?: return
        deleteBlob(c.photoBlob)
        patch { it.copy(photoBlob = id) }
    }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) {
        Label("Name"); Field(c.name, "Name") { v -> patch { it.copy(name = v.replace("\n", " ")) } }
        Label("Phones")
        LabeledEntryEditor(c.phones, "mobile", "555-1234") { next -> patch { it.copy(phones = next) } }
        Label("Emails")
        LabeledEntryEditor(c.emails, "personal", "a@b.com") { next -> patch { it.copy(emails = next) } }
        Row {
            Column(Modifier.weight(1f)) { Label("Company"); Field(c.company, "") { v -> patch { it.copy(company = v.replace("\n", " ")) } } }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Label("Title"); Field(c.title, "") { v -> patch { it.copy(title = v.replace("\n", " ")) } } }
        }
        Row {
            Column(Modifier.weight(1f)) { Label("Relationship"); Field(c.relationship, "friend, family…") { v -> patch { it.copy(relationship = v.replace("\n", " ")) } } }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Label("Birthday"); DateField(c.birthday) { v -> patch { it.copy(birthday = v) } } }
        }
        Label("Address"); Field(c.address, "Street, city…") { v -> patch { it.copy(address = v.replace("\n", " ")) } }
        Label("Tags (comma separated)"); Field(c.tags.joinToString(", "), "work, gym") { v -> patch { it.copy(tags = commaList(v)) } }
        Label("Notes"); Field(c.notes, "Notes", singleLine = false) { v -> patch { it.copy(notes = v) } }

        Label("Photo")
        val photo = remember(c.photoBlob) { loadBlobImage(c.photoBlob) }
        if (c.photoBlob.isNotBlank()) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = "Attached photo",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("Photo attached (no preview available).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Native.supportsCamera) TextButton(onClick = { showSource = true }) { Text("Replace") }
                TextButton(onClick = { deleteBlob(c.photoBlob); patch { it.copy(photoBlob = "") } }) { Text("Remove photo") }
            }
        } else if (Native.supportsCamera) {
            OutlinedButton(onClick = { showSource = true }) { Text("📷 Attach photo") }
        } else {
            Text("Photo attachments need a camera.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (showSource) {
            AlertDialog(
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
            TextButton(onClick = { deleteBlob(c.photoBlob); save(data.copy(contacts = data.contacts.filterNot { it.id == c.id })); onClose() }) { Text("Delete", color = DANGER) }
        }
    }
}

// Editable rows of "label: value" entries (phones, emails). Each row has a small
// label field (mobile / work / …) and the value; a blank label stores just the
// value, so old unlabeled data round-trips untouched.
@Composable
private fun LabeledEntryEditor(entries: List<String>, labelHint: String, valueHint: String, onChange: (List<String>) -> Unit) {
    entries.forEachIndexed { i, entry ->
        val (label, value) = splitLabeled(entry)
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                label, { v -> onChange(entries.mapIndexed { j, e -> if (j == i) joinLabeled(v, splitLabeled(e).second) else e }) },
                modifier = Modifier.width(110.dp), singleLine = true, placeholder = { Text(labelHint) },
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value, { v -> onChange(entries.mapIndexed { j, e -> if (j == i) joinLabeled(splitLabeled(e).first, v) else e }) },
                modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text(valueHint) },
            )
            TextButton(onClick = { onChange(entries.filterIndexed { j, _ -> j != i }) }) { Text("✕") }
        }
    }
    TextButton(onClick = { onChange(entries + "") }) { Text("+ Add") }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Field(value: String, placeholder: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = singleLine, placeholder = { Text(placeholder) })
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
