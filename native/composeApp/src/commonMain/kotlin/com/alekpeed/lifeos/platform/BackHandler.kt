package com.alekpeed.lifeos.platform

import androidx.compose.runtime.Composable

// Intercept the platform "back" gesture/button. On Android this catches the edge
// swipe / back button so it can pop one level inside the app instead of leaving
// it. Desktop has no system back and no-ops.
@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
