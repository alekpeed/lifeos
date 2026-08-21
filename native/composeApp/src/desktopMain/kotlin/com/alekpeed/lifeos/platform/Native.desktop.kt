package com.alekpeed.lifeos.platform

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

// Desktop capabilities. Clipboard works via AWT; the genuinely phone-only ones
// (contacts, keep-awake, camera, geofencing) stay no-ops so the shared UI can gate them
// off via the supports* flags. Outbound "share" falls back to copying to the clipboard,
// the most useful desktop equivalent.
//
// TTS, notifications, ebook import and PDF export used to sit in that no-op list too,
// which made desktop quietly less than "the full app". Each is now real, and each is
// probed rather than assumed: supportsTts is false on a box with no speech engine
// installed, supportsNotifications false where there's no system tray.
actual object Native {
    actual val supportsTts: Boolean get() = DesktopTts.available
    actual val supportsNotifications: Boolean get() = DesktopNotifier.available
    actual val supportsContacts = false
    actual val supportsKeepAwake = false
    actual val supportsWakeWord = false
    actual val supportsGeofence = false
    actual val supportsSpeakerId = false
    actual val supportsQrScan = false
    actual val supportsLocation = false
    actual val supportsCamera = false
    actual val supportsFilePick = true
    actual val supportsDictation = false
    // No system dictation dialog on desktop, but there is a microphone — which is
    // what makes Whisper the only dictation this build has.
    actual val supportsRecording: Boolean get() = MicRecorder.available
    actual val supportsPdfExport = true

    actual fun speak(text: String) = DesktopTts.speak(text)
    actual fun stopSpeaking() = DesktopTts.stop()

    actual fun shareText(text: String) {
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        } catch (e: Exception) {
            // no clipboard available; ignore
        }
    }

    actual fun readClipboard(): String? = try {
        val cb = Toolkit.getDefaultToolkit().systemClipboard
        if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) cb.getData(DataFlavor.stringFlavor) as? String else null
    } catch (e: Exception) {
        null
    }

    actual fun setImmersive(on: Boolean) {}
    actual fun cutoutTopPx(): Int = 0
    actual fun navBottomPx(): Int = 0
    actual fun statusBarTopPx(): Int = 0


    actual fun keepScreenAwake(on: Boolean) {}
    actual fun importContacts(): List<PhoneContact> = emptyList()
    actual fun postReminder(title: String, body: String) = DesktopNotifier.post(title, body)
    actual fun setPinnedNextUp(text: String?) = DesktopNotifier.setPinned(text)
    actual fun setWakeWordEnabled(on: Boolean) {}
    actual fun armArrivalHere(label: String) {}
    actual fun clearArrivals() {}
    // In-process, so these fire only while Life OS is running — see DesktopNotifier.
    actual fun scheduleReminder(id: Int, title: String, body: String, atEpochMillis: Long) =
        DesktopNotifier.schedule(id, title, body, atEpochMillis)

    actual fun cancelReminder(id: Int) = DesktopNotifier.cancel(id)

    actual fun enrollVoice(onStatus: (String) -> Unit, onResult: (Boolean) -> Unit) { onResult(false) }
    actual fun hasVoiceprint(): Boolean = false
    actual fun clearVoiceprint() {}
    actual fun setOnlyMyVoice(on: Boolean) {}
    actual fun onlyMyVoiceEnabled(): Boolean = false
    actual fun scanQr(onResult: (String?) -> Unit) { onResult(null) }
    actual fun scanAnyCode(onResult: (String?) -> Unit) = onResult(null)

    actual fun scanBarcode(onResult: (String?) -> Unit) { onResult(null) }
    actual fun getCurrentLocation(onResult: (Double?, Double?) -> Unit) { onResult(null, null) }
    actual fun takePhoto(onResult: (String?) -> Unit) { onResult(null) }
    actual fun capturePhoto(onResult: (String?) -> Unit) { onResult(null) }
    actual fun pickTextFile(onResult: (String?) -> Unit) {
        try {
            val chooser = javax.swing.JFileChooser()
            chooser.dialogTitle = "Choose a file"
            if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) { onResult(null); return }
            val f = chooser.selectedFile
            onResult(if (f != null && f.exists() && f.length() <= 4_000_000) f.readText() else null)
        } catch (e: Exception) {
            onResult(null)
        }
    }
    actual fun pickFilteredTextFile(substrings: List<String>, onResult: (String?) -> Unit) { onResult(null) }
    actual fun pickEbook(onResult: (String?) -> Unit) = pickEbookNamed { _, text -> onResult(text) }

    // Same 40 MB ceiling as Android, and the same shared parser behind it.
    actual fun pickEbookNamed(onResult: (name: String?, text: String?) -> Unit) {
        try {
            val chooser = javax.swing.JFileChooser()
            chooser.dialogTitle = "Choose an EPUB or text file"
            chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "Ebooks (*.epub, *.txt)", "epub", "txt",
            )
            if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) {
                onResult(null, null); return
            }
            val f = chooser.selectedFile
            if (f == null || !f.exists()) { onResult(null, null); return }
            if (f.length() > 40_000_000) { onResult(f.name, null); return }
            onResult(f.name, parseEbook(f.readBytes()))
        } catch (e: Exception) {
            onResult(null, null)
        }
    }

    actual val supportsScreenshot = true

    // The whole screen as a PNG. On X11 (Mint's default session) this is exactly what
    // you see; under a Wayland session the compositor may hand back a black frame, in
    // which case the caller just sends the request without a picture.
    actual fun captureScreen(onResult: (String?) -> Unit) {
        try {
            val size = Toolkit.getDefaultToolkit().screenSize
            val shot = java.awt.Robot().createScreenCapture(java.awt.Rectangle(0, 0, size.width, size.height))
            val bytes = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(shot, "png", bytes)
            val b = bytes.toByteArray()
            onResult(if (b.isEmpty()) null else java.util.Base64.getEncoder().encodeToString(b))
        } catch (e: Exception) {
            onResult(null)
        }
    }

    // What machine this is, in one line — so a help request doesn't start with three
    // rounds of "what version are you running".
    actual fun machineSummary(): String = try {
        val os = runCatching {
            java.io.File("/etc/os-release").takeIf { it.exists() }?.readLines()
                ?.firstOrNull { it.startsWith("PRETTY_NAME=") }
                ?.substringAfter('=')?.trim('"')
        }.getOrNull() ?: "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
        val home = java.io.File(System.getProperty("user.home"))
        val freeGb = (home.usableSpace / 1_000_000_000.0)
        val ramGb = (Runtime.getRuntime().maxMemory() / 1_000_000_000.0)
        val disk = ((freeGb * 10).toLong() / 10.0)
        val ram = ((ramGb * 10).toLong() / 10.0)
        "$os · ${System.getProperty("os.arch")} · ${disk}GB free · app RAM ${ram}GB"
    } catch (e: Exception) {
        ""
    }
    actual fun dictate(onResult: (String?) -> Unit) { onResult(null) }

    actual fun startRecording(): Boolean = MicRecorder.start()

    actual fun stopRecording(): String? = MicRecorder.stop()

    actual fun cancelRecording() = MicRecorder.cancel()

    actual fun micLevel(): Float = MicRecorder.peak()

    actual fun openUrl(url: String) {
        try {
            val u = if (url.contains("://")) url else "https://$url"
            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(java.net.URI(u))
        } catch (e: Exception) {
            // no browser
        }
    }

    actual fun copyToClipboard(text: String) {
        try {
            val sel = java.awt.datatransfer.StringSelection(text)
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
        } catch (e: Exception) {
            // headless / no clipboard
        }
    }

    // A real file chooser, so attachments, Sharebox files and book files work here too.
    // Runs on the caller's thread: Swing's modal chooser blocks until it's dismissed,
    // which is the behavior every caller already expects from the Android side.
    actual fun pickAttachment(onResult: (String?, String?, String?) -> Unit) {
        try {
            val chooser = javax.swing.JFileChooser()
            chooser.dialogTitle = "Choose a file"
            if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) {
                onResult(null, null, null); return
            }
            val f = chooser.selectedFile
            if (f == null || !f.exists()) { onResult(null, null, null); return }
            // Same cap as Android: a huge file would blow up as base64 in memory.
            if (f.length() > 25_000_000) { onResult(f.name, null, null); return }
            val mime = runCatching { java.nio.file.Files.probeContentType(f.toPath()) }.getOrNull()
            onResult(f.name, mime, java.util.Base64.getEncoder().encodeToString(f.readBytes()))
        } catch (e: Exception) {
            onResult(null, null, null)
        }
    }

    // Best-effort: write the bytes to a temp file and hand it to the system opener.
    actual fun openAttachment(base64: String, name: String, mime: String) {
        try {
            val bytes = java.util.Base64.getDecoder().decode(base64)
            val safe = name.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }
                .joinToString("").take(60).ifBlank { "attachment" }
            val file = java.io.File(System.getProperty("java.io.tmpdir"), safe)
            file.writeBytes(bytes)
            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(file)
        } catch (e: Exception) {
            // best-effort open
        }
    }
    // Android renders a PDF and hands it to the print/share sheet. Desktop's equivalent
    // of that sheet is a save dialog, so ask where it goes, then open it in whatever the
    // system uses for PDFs.
    actual fun exportTextAsPdf(title: String, text: String) {
        try {
            val safe = title.map { if (it.isLetterOrDigit()) it else '_' }
                .joinToString("").take(40).ifBlank { "lifeos" }
            val chooser = javax.swing.JFileChooser()
            chooser.dialogTitle = "Save PDF"
            chooser.selectedFile = java.io.File("$safe.pdf")
            if (chooser.showSaveDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return
            var target = chooser.selectedFile ?: return
            if (!target.name.endsWith(".pdf", ignoreCase = true)) {
                target = java.io.File(target.parentFile, target.name + ".pdf")
            }
            target.writeBytes(DesktopPdf.build(title, text))
            if (java.awt.Desktop.isDesktopSupported()) {
                runCatching { java.awt.Desktop.getDesktop().open(target) }
            }
        } catch (e: Exception) {
            // best-effort export
        }
    }
}
