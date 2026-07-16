package com.alekpeed.lifeos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// The shared UI, rendered natively on both Android and Windows. This is the
// foundation stub — real modules get ported in here one by one.
@Composable
fun App() {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Life OS", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Native app — Android + Windows, one codebase, no web.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
