package com.alekpeed.lifeos.platform

import androidx.compose.runtime.Composable

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop has no system back gesture.
}
