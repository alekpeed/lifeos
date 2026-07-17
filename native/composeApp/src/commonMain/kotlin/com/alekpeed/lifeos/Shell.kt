package com.alekpeed.lifeos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.core.runAutomations
import com.alekpeed.lifeos.interfaces.Interfaces
import com.alekpeed.lifeos.platform.SystemBackHandler

// Home launcher <-> module detail navigation.
@Composable
fun Shell() {
    val modules = remember { lifeOsModules() }
    var current by remember { mutableStateOf<Module?>(null) }

    // Run the opt-in automation rules once on app open (no-op unless enabled).
    LaunchedEffect(Unit) { runAutomations() }

    // A deep link / app shortcut / NFC tag / shared item can request a module by id.
    LaunchedEffect(Nav.pendingModuleId) {
        val id = Nav.consume() ?: return@LaunchedEffect
        modules.firstOrNull { it.id == id }?.let { current = it }
    }

    val c = current
    if (c == null) {
        HomeScreen(modules) { current = it }
    } else {
        // Android edge-swipe / back button pops to Home instead of leaving the app.
        SystemBackHandler(enabled = true) { current = null }
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackArrow { current = null }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${c.icon}  ${c.label}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Interfaces.Render(c.id, c.content)
            }
        }
    }
}

// A persistent, obvious back control pinned at the top of every module screen.
@Composable
private fun BackArrow(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "‹",
            style = MaterialTheme.typography.headlineSmall,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
