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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.alekpeed.lifeos.data.today

private val DANGER = Color(0xFFD64545)
private val OVERDUE = Color(0xFFE05C5C)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentsScreen() {
    var data by remember { mutableStateOf(loadDocuments()) }
    var counter by remember { mutableStateOf(data.documents.maxOfOrNull { it.id } ?: 0L) }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: DocumentsData) { data = d; saveDocuments(d) }

    var input by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var selected by remember { mutableStateOf<Long?>(null) }

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
            val label = when (expiryState(doc)) {
                ExpiryState.EXPIRED -> "Expired ${doc.expiryDate}"
                ExpiryState.SOON -> "Expiring ${doc.expiryDate}"
                else -> doc.expiryDate
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
        Field(doc.expiryDate, "YYYY-MM-DD") { v -> patch { it.copy(expiryDate = v.trim()) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { patch { it.copy(expiryDate = today().toString()) } }, label = { Text("Today") })
            if (doc.expiryDate.isNotBlank()) TextButton(onClick = { patch { it.copy(expiryDate = "") } }) { Text("Clear") }
        }
        Label("Linked contact")
        Field(doc.linkedContact, "Name") { v -> patch { it.copy(linkedContact = v.replace("\n", " ")) } }
        Label("Notes")
        Field(doc.notes, "Notes", singleLine = false) { v -> patch { it.copy(notes = v) } }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Close") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { save(data.copy(documents = data.documents.filterNot { it.id == doc.id })); onClose() }) {
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
