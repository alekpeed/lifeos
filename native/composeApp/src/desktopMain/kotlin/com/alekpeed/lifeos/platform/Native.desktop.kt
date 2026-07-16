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
}
