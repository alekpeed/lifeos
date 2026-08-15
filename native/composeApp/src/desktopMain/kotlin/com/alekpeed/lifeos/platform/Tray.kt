package com.alekpeed.lifeos.platform

import java.awt.Color
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap

// Desktop's stand-in for a phone's notification tray. A java.awt.SystemTray icon
// carries balloon notifications and a persistent tooltip for the "next up" pin; a
// background Timer fires scheduled reminders.
//
// Everything here only works while this JVM process is alive — there is no OS-level
// service behind it, so this is honestly "while the app is running" (including
// minimized to the tray, which is what Main.kt's close behavior is for), not "while
// closed" in the sense a phone's AlarmManager means it. A true closed-app story would
// need a systemd user service or a Windows Task Scheduler entry — a separate, larger
// piece of work, deliberately not done here. See NATIVE_FEATURES.md.
//
// Not every Linux desktop environment ships a tray at all (GNOME's default session
// famously doesn't without an extension); `available` reports that honestly and every
// caller degrades to a no-op rather than assuming it's there.
object Tray {
    private var icon: TrayIcon? = null
    private val timer = Timer("lifeos-reminders", true)
    private val pending = ConcurrentHashMap<Int, TimerTask>()
    private const val DEFAULT_TOOLTIP = "Life OS"

    // Wired by Main.kt: what the tray's Open item / left-click and Quit item do.
    var onOpen: (() -> Unit)? = null
    var onQuit: (() -> Unit)? = null

    val available: Boolean get() = runCatching { SystemTray.isSupported() }.getOrDefault(false)

    // Idempotent — safe to call from every notification path; only actually builds
    // the icon the first time it succeeds.
    fun ensure(): Boolean {
        if (icon != null) return true
        if (!available) return false
        return try {
            val menu = PopupMenu()
            val open = MenuItem("Open").apply { addActionListener { onOpen?.invoke() } }
            val quit = MenuItem("Quit").apply { addActionListener { onQuit?.invoke() } }
            menu.add(open)
            menu.addSeparator()
            menu.add(quit)
            val ti = TrayIcon(markerImage(), DEFAULT_TOOLTIP, menu)
            ti.isImageAutoSize = true
            ti.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) onOpen?.invoke()
                }
            })
            SystemTray.getSystemTray().add(ti)
            icon = ti
            true
        } catch (e: Exception) {
            false
        }
    }

    fun notify(title: String, body: String) {
        if (!ensure()) return
        runCatching { icon?.displayMessage(title, body, TrayIcon.MessageType.NONE) }
    }

    fun setTooltip(text: String?) {
        if (!ensure()) return
        runCatching { icon?.toolTip = text?.ifBlank { null } ?: DEFAULT_TOOLTIP }
    }

    fun schedule(id: Int, title: String, body: String, atEpochMillis: Long) {
        cancel(id)
        val task = object : TimerTask() {
            override fun run() { pending.remove(id); notify(title, body) }
        }
        pending[id] = task
        try {
            // A time already in the past fires as soon as the timer thread gets to
            // it, mirroring what a phone's alarm scheduler does for an overdue fire.
            timer.schedule(task, (atEpochMillis - System.currentTimeMillis()).coerceAtLeast(0))
        } catch (e: Exception) {
            pending.remove(id)
        }
    }

    fun cancel(id: Int) {
        pending.remove(id)?.cancel()
    }

    // A plain marker dot in an existing app accent (the same green Quartermaster uses
    // for "Full" stock) — not a designed icon, just the minimum TrayIcon requires.
    private fun markerImage(): Image {
        val size = 16
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x4C, 0x9E, 0x6F)
        g.fillOval(1, 1, size - 2, size - 2)
        g.dispose()
        return img
    }
}
