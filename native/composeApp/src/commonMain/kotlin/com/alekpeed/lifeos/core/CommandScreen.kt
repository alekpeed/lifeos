package com.alekpeed.lifeos.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage

private fun append(key: String, line: String) {
    val existing = Storage.read(key).orEmpty()
    val next = if (existing.isBlank()) line else "$existing\n$line"
    Storage.write(key, next)
}

// The quick-capture command bar: type once, fire it into the right module without
// navigating there. Tasks are stored in the Tasks format ("0\t<title>"), ideas as
// a plain line — the same data those modules read, so captures show up in them.
@Composable
fun CommandScreen() {
    var input by remember { mutableStateOf("") }
    var lastAction by remember { mutableStateOf("") }

    fun capture(target: String) {
        val t = input.trim().replace("\t", " ").replace("\n", " ")
        if (t.isEmpty()) return
        when (target) {
            "Task" -> append("Tasks", "0\t$t")
            "Idea" -> append("Ideas", t)
        }
        lastAction = "Added to $target${if (target == "Task") "s" else "s"}: “$t”"
        input = ""
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Command", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Capture once, send it where it belongs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Type anything…") },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { capture("Task") }) { Text("→ Task") }
            Button(onClick = { capture("Idea") }) { Text("→ Idea") }
        }

        if (lastAction.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(lastAction, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
