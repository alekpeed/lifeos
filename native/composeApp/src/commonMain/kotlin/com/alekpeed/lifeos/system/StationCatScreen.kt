package com.alekpeed.lifeos.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage

private fun mood(pets: Int): String = when {
    pets == 0 -> "The station cat blinks at you."
    pets < 5 -> "The station cat purrs."
    pets < 15 -> "The station cat headbutts your hand, content."
    else -> "The station cat has decided this is its station now."
}

// The station's companion. Persists how often you've stopped to say hello. The cat
// itself is a placeholder glyph today; a graphical Station Cat interface draws the
// real character over the same interaction count.
@Composable
fun StationCatScreen() {
    var pets by remember { mutableStateOf(Storage.read("Station Cat")?.trim()?.toIntOrNull() ?: 0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🐱", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            mood(pets),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "$pets hello${if (pets == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            pets += 1
            Storage.write("Station Cat", pets.toString())
        }) { Text("Say hello") }
    }
}
