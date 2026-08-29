package com.alekpeed.lifeos.platform

import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.io.File

// Desktop notifications, and the honest limits of them.
//
// Under "desktop is the endpoint" a machine that never tells you a bill is due is
// not the endpoint of anything, so this exists. What it is NOT is an equivalent of
// Android's AlarmManager, and the difference matters enough to state at the top:
//
//   **Nothing fires while the app is closed.** A desktop JVM has no OS-level
//   scheduler it can hand a future alarm to and walk away from — cron and Task
//   Scheduler are install-time system configuration, not something an app should
//   quietly write to. So desktop reminders fire while Life OS is running, and that
//   is all. Anything whose moment passed while the app was shut stays silent; the
//   app-open sweep re-arms only what is still in the future. Briefing is what
//   catches the rest, which is what it is for.
//
// Delivery goes through two mechanisms because one is not enough:
//
//   · **`notify-send` on Linux.** The desktop notification daemon is the thing that
//     actually owns notifications on Linux, honours Do Not Disturb, and puts an entry
//     where the user expects one. AWT's tray balloon on Cinnamon or GNOME is at best
//     inconsistent and at worst invisible.
//   · **The AWT system tray elsewhere**, which is what Windows supports well.
//
// Availability is computed from what actually works, never assumed. That is what
// keeps `supportsNotifications` from becoming a lie — it gates real UI across the
// app, and a button that posts a notification nobody receives is worse than a button
// that is not there. It is also why a headless JVM (CI, `desktopTest`) reports false
// and the whole path stays dormant in tests.
object DesktopNotifications {

    private val isLinux: Boolean by lazy {
        System.getProperty("os.name").orEmpty().lowercase().contains("linux")
    }

    // Looked up on disk rather than by running `which`: the answer is the same and it
    // costs no process spawn during startup.
    private val notifySend: String? by lazy {
        if (!isLinux) null
        else listOf("/usr/bin/notify-send", "/bin/notify-send", "/usr/local/bin/notify-send")
            .firstOrNull { File(it).canExecute() }
    }

    private val headless: Boolean by lazy {
        runCatching { GraphicsEnvironment.isHeadless() }.getOrDefault(true)
    }

    private val trayIcon: TrayIcon? by lazy {
        if (headless) return@lazy null
        runCatching {
            if (!SystemTray.isSupported()) return@runCatching null
            // Drawn rather than loaded: a tray icon needs an actual image, and an
            // empty or missing one shows as a gap in some trays and nothing at all in
            // others. Sixteen pixels is the size every tray accepts.
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).also { img ->
                val g = img.createGraphics()
                g.color = Color(0x2F, 0x5D, 0x50)
                g.fillOval(1, 1, 14, 14)
                g.dispose()
            }
            val icon = TrayIcon(image, "Life OS")
            icon.isImageAutoSize = true
            SystemTray.getSystemTray().add(icon)
            icon
        }.getOrNull()
    }

    // True only when a notification posted right now would actually be seen.
    val available: Boolean by lazy {
        if (headless) false else notifySend != null || trayIcon != null
    }

    fun notify(title: String, body: String) {
        if (!available) return
        val cmd = notifySend
        if (cmd != null) {
            // -a names the app so the daemon groups and labels it. Failure falls
            // through to the tray rather than being swallowed: a daemon can be absent
            // even when the binary is present.
            val sent = runCatching {
                ProcessBuilder(cmd, "-a", "Life OS", title, body)
                    .redirectErrorStream(true)
                    .start()
                true
            }.getOrDefault(false)
            if (sent) return
        }
        runCatching { trayIcon?.displayMessage(title, body, TrayIcon.MessageType.INFO) }
    }

    // Android's pinned "next up" is an ongoing entry in the notification shade.
    // Desktop has no such surface, so this is the tray icon's tooltip — genuinely
    // less prominent, and deliberately not dressed up as more. Null clears it.
    fun setTooltip(text: String?) {
        runCatching { trayIcon?.toolTip = if (text.isNullOrBlank()) "Life OS" else "Life OS — $text" }
    }
}
