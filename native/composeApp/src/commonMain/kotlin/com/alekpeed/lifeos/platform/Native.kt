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
    val supportsDictation: Boolean

    // Whether the app can drive the mic itself (rather than handing off to a system
    // dictation UI). This is what Whisper transcription needs.
    val supportsRecording: Boolean

    // Text-to-speech: read a briefing aloud, stop it.
    fun speak(text: String)
    fun stopSpeaking()

    // Outbound share via the system share sheet (Android) / clipboard (desktop).
    fun shareText(text: String)

    // Read the current clipboard text, if any (the "clipboard catcher").
    fun readClipboard(): String?

    // Open a URL in the system browser (Android ACTION_VIEW; desktop Desktop.browse).
    // A bare host gets an https:// scheme. No-ops if it can't.
    fun openUrl(url: String)

    // Copy text to the system clipboard.
    fun copyToClipboard(text: String)

    // One-shot speech-to-text: opens the system dictation UI and hands back the
    // recognized text, or null if cancelled / nothing heard / unsupported. Gate the
    // mic button on supportsDictation.
    fun dictate(onResult: (String?) -> Unit)

    // Record from the microphone ourselves, for transcription we control. Start
    // returns false if it couldn't begin — no mic, no permission yet, already
    // recording. Stop hands back the take as a base64 16-bit mono 16 kHz WAV, or null
    // if nothing usable was captured. Cancel throws the take away.
    fun startRecording(): Boolean

    fun stopRecording(): String?

    fun cancelRecording()

    // Peak level of the last moment of audio, 0f..1f — the only thing that tells you
    // the mic is actually hearing you while a recording is running.
    fun micLevel(): Float

    // Full-screen (immersive) mode: hide the system status and navigation bars so a
    // graphical interface with its own top row owns the whole screen. Swiping from an
    // edge still reveals the system bars temporarily. No-op where unsupported.
    fun setImmersive(on: Boolean)

    // Height in px of the display cutout (notch / punch hole) at the top of the
    // screen, so full-screen art can start just below it. 0 where there is none.
    fun cutoutTopPx(): Int

    // Height in px of the status bar itself (icons/clock), independent of any
    // cutout — a device can show a status bar with zero cutout, or a cutout
    // shorter than the bar around it. An ordinary (non-immersive) screen needs
    // this, not cutoutTopPx, to keep its own header clear of the system bar.
    fun statusBarTopPx(): Int

    // Height in px of the system navigation / gesture area at the bottom, so a
    // full-screen interface can keep its own controls out of the home-swipe lane.
    fun navBottomPx(): Int

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

    // Scan ANY supported code — QR, EAN-13/UPC, Code 128, … — in one camera pass, so
    // a universal scanner doesn't have to ask what kind of code it's looking at.
    // `onResult` gets the decoded text, or null if cancelled/unsupported.
    fun scanAnyCode(onResult: (String?) -> Unit)

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

    // Can this platform grab a picture of the screen? Desktop can (AWT Robot); the
    // phone can't without a foreground-service dance nobody asked for.
    val supportsScreenshot: Boolean

    // A PNG of the whole screen as base64, or null. The single most useful thing a
    // non-technical person can send when something is wrong.
    fun captureScreen(onResult: (String?) -> Unit)

    // One line describing the machine — OS, architecture, free disk, memory. Attached to
    // a help request so the first round of "what are you running" is already answered.
    fun machineSummary(): String

    // Pick an ebook (EPUB or .txt) and hand back its readable plain text in
    // reading order, or null if cancelled / unsupported. Backs the Books reader.
    fun pickEbook(onResult: (String?) -> Unit)

    // Same, plus the file's display name — a book can hold several files, and a list
    // of them only reads properly if each one is named. Both null on cancel.
    fun pickEbookNamed(onResult: (name: String?, text: String?) -> Unit)

    // Pick ANY file (PDF, doc, image, …) and hand back its display name, best-effort
    // mime type, and raw bytes as base64 — for the shared attachment layer. All three
    // are null if the user cancelled / the file was unreadable / there's no picker
    // (desktop). Gate the "add file" UI on supportsFilePick.
    fun pickAttachment(onResult: (name: String?, mime: String?, base64: String?) -> Unit)

    // Open a previously-stored attachment: writes its base64 bytes to a temp file
    // and hands it to the OS to view/open with the right app (PDF viewer, image
    // viewer, …). Android uses a FileProvider + ACTION_VIEW; desktop uses the system
    // opener. No-ops if it can't.
    fun openAttachment(base64: String, name: String, mime: String)

    // Render plain text to a paginated PDF and hand it to the system print / share
    // sheet (which offers "Save as PDF" and any installed printer). Android only;
    // desktop no-ops. Gate UI on supportsPdfExport.
    fun exportTextAsPdf(title: String, text: String)
}
