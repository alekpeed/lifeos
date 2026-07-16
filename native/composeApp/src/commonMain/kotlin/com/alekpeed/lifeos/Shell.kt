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

// The app shell: a bottom nav switching between the ported modules. Grows a tab
// (or a proper nav rail on desktop) as more modules land. Emoji stand in for
// icons until the real icon set is wired, to avoid an extra dependency now.
@Composable
fun Shell() {
    var screen by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == 0,
                    onClick = { screen = 0 },
                    icon = { Text("✅") },
                    label = { Text("Tasks") },
                )
                NavigationBarItem(
                    selected = screen == 1,
                    onClick = { screen = 1 },
                    icon = { Text("💡") },
                    label = { Text("Ideas") },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (screen) {
                0 -> TasksScreen()
                1 -> IdeasScreen()
            }
        }
    }
}
