package com.alekpeed.lifeos.people

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
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.platform.Native

private val DANGER = Color(0xFFD64545)

private fun commaList(s: String) = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactsScreen() {
    var data by remember { mutableStateOf(loadContacts()) }
    var counter by remember { mutableStateOf(data.contacts.maxOfOrNull { it.id } ?: 0L) }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: ContactsData) { data = d; saveContacts(d) }

    var input by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Long?>(null) }

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
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(data.contacts.sortedBy { it.name.lowercase() }, key = { it.id }) { c ->
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
                                if (c.birthday.isNotBlank()) add("🎂 ${c.birthday}")
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
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) {
        Label("Name"); Field(c.name, "Name") { v -> patch { it.copy(name = v.replace("\n", " ")) } }
        Label("Phones (comma separated)"); Field(c.phones.joinToString(", "), "555-1234") { v -> patch { it.copy(phones = commaList(v)) } }
        Label("Emails (comma separated)"); Field(c.emails.joinToString(", "), "a@b.com") { v -> patch { it.copy(emails = commaList(v)) } }
        Row {
            Column(Modifier.weight(1f)) { Label("Company"); Field(c.company, "") { v -> patch { it.copy(company = v.replace("\n", " ")) } } }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Label("Title"); Field(c.title, "") { v -> patch { it.copy(title = v.replace("\n", " ")) } } }
        }
        Row {
            Column(Modifier.weight(1f)) { Label("Relationship"); Field(c.relationship, "friend, family…") { v -> patch { it.copy(relationship = v.replace("\n", " ")) } } }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Label("Birthday"); Field(c.birthday, "YYYY-MM-DD") { v -> patch { it.copy(birthday = v.trim()) } } }
        }
        Label("Tags (comma separated)"); Field(c.tags.joinToString(", "), "work, gym") { v -> patch { it.copy(tags = commaList(v)) } }
        Label("Notes"); Field(c.notes, "Notes", singleLine = false) { v -> patch { it.copy(notes = v) } }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Close") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { save(data.copy(contacts = data.contacts.filterNot { it.id == c.id })); onClose() }) { Text("Delete", color = DANGER) }
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
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = singleLine, placeholder = { Text(placeholder) })
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
