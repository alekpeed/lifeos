package com.alekpeed.lifeos.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp

// A "link a contact" field: free-text (still type any name) plus a Contacts
// button that opens a picker of your real Contacts. Stores the chosen name, so
// any module can link to a contact without a hard reference.
@Composable
fun ContactField(value: String, placeholder: String = "Name", onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.replace("\n", " ")) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(placeholder) },
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { picking = true }) { Text("Contacts") }
    }

    if (picking) {
        val names = remember { loadContacts().contacts.map { it.name.trim() }.filter { it.isNotBlank() }.distinct().sorted() }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Link a contact") },
            text = {
                if (names.isEmpty()) {
                    Text("No contacts yet — add some in Contacts, or type a name here.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(names) { n ->
                            Text(
                                n,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().clickable { onChange(n); picking = false }.padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}
