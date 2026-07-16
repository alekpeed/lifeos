package com.alekpeed.lifeos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alekpeed.lifeos.ideas.IdeasScreen
import com.alekpeed.lifeos.tasks.TasksScreen
import com.alekpeed.lifeos.ui.SimpleListScreen

private data class Tab(val icon: String, val label: String, val content: @Composable () -> Unit)

// The app shell: bottom nav across the ported modules. Emoji stand in for icons
// until the real set is wired. Grows to a nav rail/drawer as modules pass ~5.
@Composable
fun Shell() {
    val tabs = remember {
        listOf(
            Tab("✅", "Tasks") { TasksScreen() },
            Tab("💡", "Ideas") { IdeasScreen() },
            Tab("📍", "Places") { SimpleListScreen("Places", "New place", listOf("The ramen place", "That trail")) },
            Tab("🔗", "Links") { SimpleListScreen("Links", "Paste a link", listOf("Article to read")) },
            Tab("👤", "Contacts") { SimpleListScreen("Contacts", "New contact") },
        )
    }
    var selected by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = { Text(tab.icon) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            tabs[selected].content()
        }
    }
}
