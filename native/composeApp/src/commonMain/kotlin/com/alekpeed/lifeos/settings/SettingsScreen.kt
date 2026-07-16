package com.alekpeed.lifeos.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.DATA_SOURCES
import com.alekpeed.lifeos.data.countOf
import com.alekpeed.lifeos.interfaces.Interfaces

private fun pretty(id: String): String =
    id.split('-', '_').joinToString(" ") { part ->
        if (part.isEmpty()) part else part.replaceFirstChar { it.uppercase() }
    }

// Real settings. The centrepiece is the interface switcher: it lists every
// registered interface (the built-in functional "default" plus any graphical
// interface Alek plugs in) and switches the whole app live. Because every page
// renders through Interfaces.Render, choosing one here re-skins the app without
// touching module data.
@Composable
fun SettingsScreen() {
    val interfaces = Interfaces.available
    val active = Interfaces.active
    val totalItems = DATA_SOURCES.sumOf { countOf(it.key) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        Text("INTERFACE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "Swap the whole app's look. Graphical interfaces appear here once registered.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        interfaces.forEach { id ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { Interfaces.setActive(id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = id == active, onClick = { Interfaces.setActive(id) })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(pretty(id), style = MaterialTheme.typography.bodyLarge)
                    if (id == Interfaces.DEFAULT) {
                        Text(
                            "Built-in functional screens",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (interfaces.size == 1) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Only the default is installed. Register a graphical interface to see it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.height(24.dp))

        Text("STORAGE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("$totalItems items saved locally", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Local device storage. A shared database and cross-device sync land with the data layer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
