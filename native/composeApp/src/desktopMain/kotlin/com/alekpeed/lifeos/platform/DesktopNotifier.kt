package com.alekpeed.lifeos.platform

import java.awt.Color
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

// Desktop notifications, via the system tray.
//
// Honest about its limits, because the limit is the interesting part: Android hands a
// reminder to the OS alarm clock and it fires with the app closed. There is no portable
// JVM equivalent, so these fire from an in-process scheduler and therefore only while
// Life OS is running. That's still a real gain over the previous no-op — a reminder set
// for 4pm now actually arrives at 4pm on a machine you're sitting at — and the app-open
// re-arm in Shell means closing and reopening restores every still-future one.
internal object DesktopNotifier {

    val available: Boolean by lazy { runCatching { SystemTray.isSupported() }.getOrDefault(false) }

    private val scheduler by lazy {
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "lifeos-reminders").apply { isDaemon = true }
        }
    }

    // id -> the pending fire, so cancelReminder and a re-arm of the same id can replace
    // rather than stack.
    private val pending = mutableMapOf<Int, ScheduledFuture<*>>()

    private var tray: TrayIcon? = null

    // A plain 16px dot, drawn rather than shipped: the tray needs *an* image to exist at
    // all, and this file is plumbing, not artwork. Swap in a real asset when there is one.
    private fun placeholderIcon(): BufferedImage {
        val size = 16
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x9D, 0x71, 0x32)
        g.fillOval(1, 1, size - 2, size - 2)
        g.dispose()
        return img
    }

    private fun icon(): TrayIcon? {
        if (!available) return null
        tray?.let { return it }
        return try {
            val t = TrayIcon(placeholderIcon(), "Life OS")
            t.isImageAutoSize = true
            SystemTray.getSystemTray().add(t)
            tray = t
            t
        } catch (e: Exception) {
            null
        }
    }

    fun post(title: String, body: String) {
        val t = icon() ?: return
        try {
            t.displayMessage(title, body, TrayIcon.MessageType.INFO)
        } catch (e: Exception) {
            // some desktops refuse balloons; nothing else to do
        }
    }

    // The closest desktop analogue of Android's pinned "next up" notification: the tray
    // icon's hover tooltip. Always present, never steals focus.
    fun setPinned(text: String?) {
        val t = icon() ?: return
        try {
            t.toolTip = if (text.isNullOrBlank()) "Life OS" else "Life OS — $text"
        } catch (e: Exception) {
            // ignore
        }
    }

    fun schedule(id: Int, title: String, body: String, atEpochMillis: Long) {
        if (!available) return
        val delay = atEpochMillis - System.currentTimeMillis()
        if (delay <= 0) return
        cancel(id)
        try {
            synchronized(pending) {
                pending[id] = scheduler.schedule({
                    post(title, body)
                    synchronized(pending) { pending.remove(id) }
                }, delay, TimeUnit.MILLISECONDS)
            }
        } catch (e: Exception) {
            // scheduler shutting down
        }
    }

    fun cancel(id: Int) {
        synchronized(pending) {
            pending.remove(id)?.let { runCatching { it.cancel(false) } }
        }
    }
}
