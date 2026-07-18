package com.alekpeed.lifeos.platform

// A phone contact pulled from the device address book.
data class PhoneContact(val name: String, val detail: String)

// The cross-platform native-capability surface. Android provides real
// implementations (TTS, notifications, share, clipboard, contacts, keep-awake);
// desktop provides sensible no-ops or JVM equivalents so the Windows build stays
// green. Screens gate optional UI on the `supports*` flags so a capability that
// isn't real on a platform simply isn't offered there.
expect object Native {
    val supportsTts: Boolean
    val supportsNotifications: Boolean
    val supportsContacts: Boolean
    val supportsKeepAwake: Boolean
    val supportsWakeWord: Boolean
    val supportsGeofence: Boolean
    val supportsSpeakerId: Boolean
    val supportsQrScan: Boolean
    val supportsLocation: Boolean
    val supportsCamera: Boolean
    val supportsFilePick: Boolean
    val supportsPdfExport: Boolean

    // Text-to-speech: read a briefing aloud, stop it.
    fun speak(text: String)
    fun stopSpeaking()

    // Outbound share via the system share sheet (Android) / clipboard (desktop).
    fun shareText(text: String)

    // Read the current clipboard text, if any (the "clipboard catcher").
    fun readClipboard(): String?

    // Cooking mode: keep the screen on while true.
    fun keepScreenAwake(on: Boolean)

    // One-tap import of phone contacts (empty if unsupported / not permitted).
    fun importContacts(): List<PhoneContact>

    // An actionable reminder notification (Done / Snooze on Android).
    fun postReminder(title: String, body: String)

    // A pinned, ongoing "next up" notification; pass null to clear it.
    fun setPinnedNextUp(text: String?)

    // Always-on wake word: start/stop a foreground listening service that captures
    // what you say after the trigger word. Requires the microphone permission.
    fun setWakeWordEnabled(on: Boolean)

    // Low-power arrival geofence: arm an alert at the device's current location,
    // labelled; fires a notification when you next arrive there. Clear removes all.
    fun armArrivalHere(label: String)
    fun clearArrivals()

    // A reminder that fires as a real notification at a future time, even if the
    // app is closed (Android: AlarmManager). `id` identifies it for cancellation.
    fun scheduleReminder(id: Int, title: String, body: String, atEpochMillis: Long)
    fun cancelReminder(id: Int)

    // "Only my voice" — speaker verification for the wake word. Enrollment records a
    // few seconds of the owner's speech and stores a voiceprint; when the gate is on,
    // the wake word only fires for a matching voice. A filter, not a lock (recordings
    // can spoof it). onStatus reports progress; onResult(true) on a successful enroll.
    fun enrollVoice(onStatus: (String) -> Unit, onResult: (Boolean) -> Unit)
    fun hasVoiceprint(): Boolean
    fun clearVoiceprint()
    fun setOnlyMyVoice(on: Boolean)
    fun onlyMyVoiceEnabled(): Boolean

    // Scan a QR code with the camera; `onResult` gets the decoded text, or null if
    // cancelled/unsupported. Android launches a scanner; desktop is a no-op.
    fun scanQr(onResult: (String?) -> Unit)

    // Scan a 1D product barcode (EAN-13/UPC — e.g. a book's ISBN) with the camera;
    // `onResult` gets the decoded digits, or null if cancelled/unsupported. Gated
    // by supportsQrScan (same camera scanner). Desktop is a no-op.
    fun scanBarcode(onResult: (String?) -> Unit)

    // One-shot current location (fused provider). Requests the permission if it's
    // missing and returns null,null for that call — the caller re-taps once granted.
    // Desktop has no location source and always returns null,null.
    fun getCurrentLocation(onResult: (lat: Double?, lng: Double?) -> Unit)

    // A photo for AI vision, as a base64 JPEG (no data: prefix), already downscaled
    // for a vision API. `onResult` receives:
    //   • the base64 string on success,
    //   • null if the user cancelled (or the platform has no source),
    //   • "" (empty) if a photo was taken/picked but couldn't be decoded — the
    //     caller shows a "couldn't read that image" message rather than failing
    //     silently.
    // takePhoto opens the camera (Android; requests the camera permission first);
    // capturePhoto opens the system image picker (gallery). Desktop no-ops both.
    fun takePhoto(onResult: (String?) -> Unit)
    fun capturePhoto(onResult: (String?) -> Unit)

    // Pick a text/CSV file and hand back its UTF-8 contents (size-capped), or null
    // if the user cancelled / the platform has no file picker. Android opens the
    // system document picker; desktop has none and returns null.
    fun pickTextFile(onResult: (String?) -> Unit)

    // Pick a potentially huge text file and stream it, keeping only lines that
    // contain one of `substrings` (all lines when empty). Zips are opened to their
    // first .xml/.csv entry. Built for the Apple Health export, whose export.xml
    // runs to hundreds of MB — the whole file never sits in memory at once.
    fun pickFilteredTextFile(substrings: List<String>, onResult: (String?) -> Unit)

    // Pick an ebook (EPUB or .txt) and hand back its readable plain text in
    // reading order, or null if cancelled / unsupported. Backs the Books reader.
    fun pickEbook(onResult: (String?) -> Unit)

    // Render plain text to a paginated PDF and hand it to the system print / share
    // sheet (which offers "Save as PDF" and any installed printer). Android only;
    // desktop no-ops. Gate UI on supportsPdfExport.
    fun exportTextAsPdf(title: String, text: String)
}
