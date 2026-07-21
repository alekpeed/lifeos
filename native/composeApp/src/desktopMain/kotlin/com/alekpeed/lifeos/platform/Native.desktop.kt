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
    actual val supportsFilePick = false
    actual val supportsDictation = false
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

    actual fun readClipboard(): String? = try {
        val cb = Toolkit.getDefaultToolkit().systemClipboard
        if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) cb.getData(DataFlavor.stringFlavor) as? String else null
    } catch (e: Exception) {
        null
    }

    actual fun keepScreenAwake(on: Boolean) {}
    actual fun importContacts(): List<PhoneContact> = emptyList()
    actual fun postReminder(title: String, body: String) {}
    actual fun setPinnedNextUp(text: String?) {}
    actual fun setWakeWordEnabled(on: Boolean) {}
    actual fun armArrivalHere(label: String) {}
    actual fun clearArrivals() {}
    actual fun scheduleReminder(id: Int, title: String, body: String, atEpochMillis: Long) {}
    actual fun cancelReminder(id: Int) {}

    actual fun enrollVoice(onStatus: (String) -> Unit, onResult: (Boolean) -> Unit) { onResult(false) }
    actual fun hasVoiceprint(): Boolean = false
    actual fun clearVoiceprint() {}
    actual fun setOnlyMyVoice(on: Boolean) {}
    actual fun onlyMyVoiceEnabled(): Boolean = false
    actual fun scanQr(onResult: (String?) -> Unit) { onResult(null) }
    actual fun scanBarcode(onResult: (String?) -> Unit) { onResult(null) }
    actual fun getCurrentLocation(onResult: (Double?, Double?) -> Unit) { onResult(null, null) }
    actual fun takePhoto(onResult: (String?) -> Unit) { onResult(null) }
    actual fun capturePhoto(onResult: (String?) -> Unit) { onResult(null) }
    actual fun pickTextFile(onResult: (String?) -> Unit) { onResult(null) }
    actual fun pickFilteredTextFile(substrings: List<String>, onResult: (String?) -> Unit) { onResult(null) }
    actual fun pickEbook(onResult: (String?) -> Unit) { onResult(null) }
    actual fun dictate(onResult: (String?) -> Unit) { onResult(null) }

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

    actual fun pickAttachment(onResult: (String?, String?, String?) -> Unit) { onResult(null, null, null) }

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
