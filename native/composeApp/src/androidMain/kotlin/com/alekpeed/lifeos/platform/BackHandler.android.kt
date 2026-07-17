package com.alekpeed.lifeos.platform

import androidx.compose.runtime.Composable

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
