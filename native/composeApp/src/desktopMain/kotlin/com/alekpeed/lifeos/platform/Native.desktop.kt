package com.alekpeed.lifeos.platform

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

// Desktop capabilities. Clipboard works via AWT; the phone-only ones (TTS,
// notifications, contacts, keep-awake) are no-ops so the shared UI can gate them
// off via the supports* flags. Outbound "share" falls back to copying to the
// clipboard, the most useful desktop equivalent.
actual object Native {
    actual val supportsTts = false
    actual val supportsNotifications = false
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
    actual val supportsPdfExport = false

    actual fun speak(text: String) {}
    actual fun stopSpeaking() {}

    actual fun shareText(text: String) {
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        } catch (e: Exception) {
            // no clipboard available; ignore
        }
    }

    actual fun shareFile(fileName: String, mimeType: String, content: String): String? = try {
        // Desktop has no share sheet, so the file itself is the deliverable: write it
        // where the person will find it and hand back the path to show them.
        val home = java.io.File(System.getProperty("user.home"))
        val dir = java.io.File(home, "Downloads").takeIf { it.isDirectory } ?: home
        val file = java.io.File(dir, fileName)
        file.writeText(content)
        file.absolutePath
    } catch (e: Exception) {
        null
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
    actual fun postReminder(title: String, body: String, subject: String) {}

    // No push transport on a desktop, by design: the Telegram digest (§7 D-5 phase 1)
    // is what reaches a laptop, and it needs no token.
    actual fun devicePushToken(onToken: (String?) -> Unit) { onToken(null) }
    actual fun setPinnedNextUp(text: String?) {}
    actual fun setWakeWordEnabled(on: Boolean) {}
    actual fun armArrivalHere(label: String) {}
    actual fun clearArrivals() {}
    actual fun scheduleReminder(id: Int, title: String, body: String, atEpochMillis: Long, subject: String) {}
    actual fun cancelReminder(id: Int) {}

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
    // The three pickers below were stubs returning null, which read as a platform
    // limit and was not one: `pickTextFile` and `pickAttachment` in this same file
    // already drive a JFileChooser. Under "desktop is the endpoint" (REDESIGN §
    // device philosophy, resolved 2026-08-29) that made two things impossible on the
    // machine best suited to them — reading a book on a big screen, and importing a
    // health export, which is about as desktop-shaped as a task gets.
    //
    // The parsing is shared with Android in `jvmShared`, so an EPUB opens the same
    // way on both and a fix lands once.

    // Streamed and filtered rather than read whole: the Apple Health export is
    // hundreds of megabytes of XML, and this is the one picker that must not slurp.
    actual fun pickFilteredTextFile(substrings: List<String>, onResult: (String?) -> Unit) {
        try {
            val f = chooseFile("Choose an export file") ?: run { onResult(null); return }
            // Off the UI thread: a big export takes seconds, and freezing the window
            // while it reads looks exactly like a hang.
            Thread {
                val text = runCatching {
                    f.inputStream().use { readFilteredText(it, substrings) }
                }.getOrNull()
                onResult(text)
            }.start()
        } catch (e: Exception) {
            onResult(null)
        }
    }

    actual fun pickEbook(onResult: (String?) -> Unit) {
        pickEbookNamed { _, text -> onResult(text) }
    }

    actual fun pickEbookNamed(onResult: (name: String?, text: String?) -> Unit) {
        try {
            val f = chooseFile("Choose an EPUB or text file") ?: run { onResult(null, null); return }
            Thread {
                val out = runCatching {
                    // Same 40MB ceiling Android uses. An EPUB is unzipped entirely in
                    // memory, so this is the size the parser can actually survive.
                    val bytes = f.readBytes()
                    if (bytes.size > 40_000_000) null else parseEbook(bytes)
                }.getOrNull()
                onResult(if (out == null) null else f.name, out)
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
    actual fun exportTextAsPdf(title: String, text: String) {}
}

// A file-open dialog, or null if the user cancelled. Swing's chooser is what the
// other desktop pickers in this file already use; sharing it keeps one answer to
// "what does cancel look like".
private fun chooseFile(title: String): java.io.File? {
    val chooser = javax.swing.JFileChooser()
    chooser.dialogTitle = title
    if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile?.takeIf { it.exists() && it.isFile }
}
