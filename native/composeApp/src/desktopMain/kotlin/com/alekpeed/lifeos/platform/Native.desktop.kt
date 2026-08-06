package com.alekpeed.lifeos.platform

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

// Desktop capabilities. Clipboard works via AWT; TTS shells out to whatever the OS
// has (SpeechEngine.kt) and notifications ride the system tray (Tray.kt) — both are
// checked live rather than fixed, since whether they're actually available depends
// on what's installed and what desktop environment this is. The remaining phone-only
// ones (contacts, keep-awake, wake word, geofencing) are plain no-ops so the shared
// UI can gate them off via the supports* flags. Outbound "share" falls back to
// copying to the clipboard, the most useful desktop equivalent.
actual object Native {
    // Checked live, like supportsRecording: whichever speech command is on this
    // machine might not be there (or might newly be there) between one read and the
    // next, so this isn't a fixed constant.
    actual val supportsTts: Boolean get() = SpeechEngine.available
    // Backed by the system tray (Tray.kt), so this is true only where a tray exists
    // to carry the notification — not every Linux desktop environment has one.
    actual val supportsNotifications: Boolean get() = Tray.available
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

    actual fun speak(text: String) = SpeechEngine.speak(text)
    actual fun stopSpeaking() = SpeechEngine.stop()

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


    actual fun keepScreenAwake(on: Boolean) {}
    actual fun importContacts(): List<PhoneContact> = emptyList()
    actual fun postReminder(title: String, body: String) = Tray.notify(title, body)
    actual fun setPinnedNextUp(text: String?) = Tray.setTooltip(text)
    actual fun setWakeWordEnabled(on: Boolean) {}
    actual fun armArrivalHere(label: String) {}
    actual fun clearArrivals() {}
    actual fun scheduleReminder(id: Int, title: String, body: String, atEpochMillis: Long) =
        Tray.schedule(id, title, body, atEpochMillis)
    actual fun cancelReminder(id: Int) = Tray.cancel(id)

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
    // Same file streamed and kept-lines-filtered as Android — the shared
    // FilteredTextReader (jvmShared) is what makes the Apple Health export importable
    // here at all. The chooser blocks the caller like every other desktop picker; the
    // read itself runs on a background thread since export.xml can run to hundreds of
    // MB and the window shouldn't freeze while it streams.
    actual fun pickFilteredTextFile(substrings: List<String>, onResult: (String?) -> Unit) {
        try {
            val chooser = javax.swing.JFileChooser()
            chooser.dialogTitle = "Choose the Apple Health export"
            if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) { onResult(null); return }
            val f = chooser.selectedFile
            if (f == null || !f.exists()) { onResult(null); return }
            Thread {
                val text = try {
                    f.inputStream().use { raw -> FilteredTextReader.read(raw, substrings) }
                } catch (e: Exception) {
                    null
                }
                onResult(text)
            }.start()
        } catch (e: Exception) {
            onResult(null)
        }
    }

    actual fun pickEbook(onResult: (String?) -> Unit) = pickEbookInternal { _, text -> onResult(text) }

    actual fun pickEbookNamed(onResult: (name: String?, text: String?) -> Unit) = pickEbookInternal(onResult)

    // EPUB or .txt, via the shared EbookParser (jvmShared) — the same parsing Android
    // uses, so a book reads identically on both. The unzip-and-regex work runs off the
    // calling thread; an EPUB can be tens of MB and the window shouldn't freeze while
    // it's picked apart.
    private fun pickEbookInternal(onResult: (name: String?, text: String?) -> Unit) {
        try {
            val chooser = javax.swing.JFileChooser()
            chooser.dialogTitle = "Choose an ebook"
            chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("EPUB or text (.epub, .txt)", "epub", "txt")
            if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) { onResult(null, null); return }
            val f = chooser.selectedFile
            if (f == null || !f.exists()) { onResult(null, null); return }
            if (f.length() > 40_000_000) { onResult(f.name, null); return }
            Thread {
                val text = try { EbookParser.parse(f.readBytes()) } catch (e: Exception) { null }
                onResult(f.name, text)
            }.start()
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
    // A real save dialog, so the file lands wherever the user actually wants it —
    // a synced folder, a USB drive — rather than a temp folder they'd have to go
    // find.
    actual fun exportPackageFile(suggestedName: String, base64: String, onResult: (Boolean) -> Unit) {
        try {
            val chooser = javax.swing.JFileChooser()
            chooser.dialogTitle = "Save package"
            chooser.selectedFile = java.io.File(suggestedName)
            if (chooser.showSaveDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) { onResult(false); return }
            val f = chooser.selectedFile
            if (f == null) { onResult(false); return }
            f.writeBytes(java.util.Base64.getDecoder().decode(base64))
            onResult(true)
        } catch (e: Exception) {
            onResult(false)
        }
    }

    // Renders via PdfWriter (a small hand-rolled PDF, since there's no bundled JVM
    // engine) and hands the file to the system opener — the desktop equivalent of
    // Android's print/share sheet, which doesn't exist here.
    actual fun exportTextAsPdf(title: String, text: String) {
        try {
            val bytes = PdfWriter.write(title, text)
            val safe = title.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(40).ifBlank { "lifeos" }
            val file = java.io.File(System.getProperty("java.io.tmpdir"), "$safe.pdf")
            file.writeBytes(bytes)
            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(file)
        } catch (e: Exception) {
            // best-effort export
        }
    }
}
