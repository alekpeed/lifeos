package com.alekpeed.lifeos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.alekpeed.lifeos.platform.Tray

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
    application {
        // Where a system tray exists, closing the window hides it rather than
        // quitting — a scheduled reminder can only ever fire while this process is
        // alive, and "closed" the way most people mean it (clicked the X, didn't
        // touch the tray) shouldn't kill that. The tray's Quit item is the real exit;
        // on a desktop with no tray (some Linux sessions don't have one) this falls
        // straight back to today's behavior of closing meaning closing.
        var visible by remember { mutableStateOf(true) }
        val hasTray = remember { Tray.ensure() }
        if (hasTray) {
            Tray.onOpen = { visible = true }
            Tray.onQuit = { exitApplication() }
        }
        Window(
            onCloseRequest = { if (hasTray) visible = false else exitApplication() },
            title = "Life OS",
            visible = visible,
        ) {
            App()
        }
    }
}
