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
}
