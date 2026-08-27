package com.alekpeed.lifeos.diag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.platform.Native

// "The app closed" is not a bug report, and it was the only one the app made possible.
//
// This is the other half of the black box: the handler writes the trace on the way
// down, and this shows it on the way back up, with a Copy that puts it somewhere it can
// be pasted. One screen's failure now names itself instead of costing a round trip.
//
// Deliberately a banner on the home screen and not a dialog. A dialog on launch after a
// crash is a second thing going wrong; a line you can read, open, and dismiss is a
// report. It stays until dismissed, because a crash you scrolled past is one you will
// not remember to mention.
@Composable
fun CrashBanner() {
    var report by remember { mutableStateOf(Crash.last()) }
    var expanded by remember { mutableStateOf(false) }
    val text = report ?: return

    // The first line of the report body is the exception; the header lines above it are
    // the metadata. Collapsed, the screen name is what identifies the crash.
    val screen = remember(text) {
        text.lineSequence().firstOrNull { it.startsWith("screen:") }?.removePrefix("screen:")?.trim().orEmpty()
    }

    Column(
        Modifier.fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                Text(
                    "Last run ended in a crash",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (screen.isBlank()) "Tap for the trace" else "on $screen — tap for the trace",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
            TextButton(onClick = { Native.copyToClipboard(text) }) { Text("Copy") }
            TextButton(onClick = { Crash.clear(); report = null }) { Text("Dismiss") }
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            // Horizontal only, and that is not a preference. This banner lives inside
            // the home screen's LazyColumn, and a vertical scroller nested in another
            // vertical scroller throws — a crash viewer that crashes the app would be a
            // poor joke. The page scrolls vertically; a stack frame is one long
            // unbreakable line, so it gets the axis the page cannot give it.
            Text(
                text,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                softWrap = false,
            )
        }
    }
}
