package com.alekpeed.lifeos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alekpeed.lifeos.tasks.TasksScreen

// The shared native UI, rendered natively on both Android and Windows.
// Modules get added here one by one; Tasks is the first real one.
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            TasksScreen()
        }
    }
}
