package com.alekpeed.lifeos

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.alekpeed.lifeos.interfaces.Interfaces
import com.alekpeed.lifeos.interfaces.machiya.MACHIYA
import com.alekpeed.lifeos.interfaces.machiya.registerMachiyaHome

// Desktop entry point. Plain `lifeos` opens the full app; `lifeos --helper` opens the
// helper window — same binary, so one build serves both and a fix reaches both.
// `--owner=Name` sets whose name the helper's copy addresses.
fun main(args: Array<String>) {
    // Three ways in, because the person running the helper build shouldn't have to do
    // any of them: a launch flag, an environment variable, or a marker file dropped in
    // the settings folder when the machine is set up. Whoever installs it picks one.
    val marker = java.io.File(System.getProperty("user.home"), ".lifeos/helper")
    AppMode.helper = args.any { it == "--helper" } ||
        System.getenv("LIFEOS_HELPER") == "1" ||
        runCatching { marker.exists() }.getOrDefault(false)

    val ownerFromMarker = runCatching {
        if (marker.exists()) marker.readText().trim().ifBlank { null } else null
    }.getOrNull()
    ownerFromMarker?.let { AppMode.ownerName = it }
    args.firstOrNull { it.startsWith("--owner=") }?.let {
        val n = it.substringAfter('=').trim()
        if (n.isNotEmpty()) AppMode.ownerName = n
    }

    val isLinux = System.getProperty("os.name").contains("linux", ignoreCase = true)
    if (!AppMode.helper && isLinux) {
        registerMachiyaHome()
        Interfaces.setActive(MACHIYA)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Life OS",
            state = rememberWindowState(
                placement = if (!AppMode.helper && isLinux) WindowPlacement.Maximized else WindowPlacement.Floating,
            ),
        ) {
            App()
        }
    }
}
