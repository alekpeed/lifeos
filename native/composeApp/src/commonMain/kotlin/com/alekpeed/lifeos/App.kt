package com.alekpeed.lifeos

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// Observable theme prefs (mirrors the Interfaces registry pattern), so a change
// in Settings or Theme-from-Photo re-themes the whole app live. Backed by Storage
// so the choice survives a restart.
object AppTheme {
    var accentHex by mutableStateOf(Storage.read("Theme from Photo")?.trim().orEmpty())
        private set
    var mode by mutableStateOf(Storage.read("ThemeMode")?.ifBlank { null } ?: "system") // system | light | dark
        private set
    var compact by mutableStateOf(Storage.read("Density") == "compact")
        private set

    fun updateAccent(hex: String) { accentHex = hex; Storage.write("Theme from Photo", hex) }
    fun updateMode(m: String) { mode = m; Storage.write("ThemeMode", m) }
    fun updateCompact(c: Boolean) { compact = c; Storage.write("Density", if (c) "compact" else "comfortable") }
}

private fun parseAccent(hex: String): Color? {
    val h = hex.trim().removePrefix("#")
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}

// The shared native UI, rendered natively on both Android and Windows.
@Composable
fun App() {
    val dark = when (AppTheme.mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val accent = parseAccent(AppTheme.accentHex)
    val scheme = if (accent != null) base.copy(primary = accent, secondary = accent) else base

    MaterialTheme(colorScheme = scheme) {
        val d = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(d.density * (if (AppTheme.compact) 0.9f else 1f), d.fontScale),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Shell()
            }
        }
    }
}
