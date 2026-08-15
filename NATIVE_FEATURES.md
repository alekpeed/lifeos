# Life OS — Native Features

Every capability the app asks of the machine it's running on, what each one powers,
and which build actually has it. Written from `platform/Native.kt` and both actuals
(`Native.android.kt`, `Native.desktop.kt`) — not from memory, and not carried over
from an older document.

Companion to `LIFE_OS.md` (what the app is) and `ANDROID_APP.md` / `DESKTOP_PAGE_MAP.md`
(the two builds). This file is the one that answers "does this actually work on my
machine".

> **Source-level, not device-verified.** Everything below is read off the current
> Kotlin source. Per the project's own rule, nothing is "done" until Alek has seen it
> work on device — this is a map of what's wired, not a sign-off.

---

## How capability gating works

`Native` is a Kotlin Multiplatform `expect object` with one `actual` per target. Two
kinds of member:

- **`supports*` flags** — asked *before* drawing anything. A build without the
  hardware doesn't get a disabled button or an apology; the control isn't drawn at
  all. `if (Native.supportsCamera) { … }`.
- **functions** — each degrades quietly rather than throwing. A missing permission, a
  dead service, an unsupported platform: the call returns `null` / `false` / does
  nothing. Nothing in this layer throws into the UI.

Where a feature has a real fallback, the *feature* is gated rather than the hardware —
`MicButton` prefers Whisper, falls back to the system recognizer, and renders nothing
if neither exists, so its callers never test a flag themselves.

---

## Capability matrix

| Flag | Android | Desktop (Linux / Windows) |
|---|---|---|
| `supportsTts` | ✅ | ✅ *(checked live: a speech command must exist)* |
| `supportsNotifications` | ✅ | ✅ *(checked live: a system tray must exist)* |
| `supportsContacts` | ✅ | ❌ |
| `supportsKeepAwake` | ✅ | ❌ |
| `supportsWakeWord` | ✅ | ❌ |
| `supportsGeofence` | ✅ | ❌ |
| `supportsSpeakerId` | ✅ | ❌ |
| `supportsQrScan` | ✅ | ❌ |
| `supportsLocation` | ✅ | ❌ |
| `supportsCamera` | ✅ | ❌ |
| `supportsFilePick` | ✅ | ✅ |
| `supportsPdfExport` | ✅ | ✅ |
| `supportsDictation` | ✅ | ❌ *(no system dictation dialog — see Whisper below)* |
| `supportsRecording` | ✅ | ✅ *(checked live: an input line must exist)* |
| `supportsScreenshot` | ✅ *(API 26+, this app's own window)* | ✅ *(the whole screen)* |

`supportsRecording`, `supportsTts` and `supportsNotifications` are evaluated on each
read rather than fixed at startup, so plugging in a microphone, installing a speech
command, or a desktop environment gaining a tray is noticed without a restart.

---

## Voice and audio

**Whisper transcription** — `startRecording` / `stopRecording` / `cancelRecording` /
`micLevel`, plus `ai/Whisper.kt` and `ui/MicButton.kt`. The app drives the microphone
itself: raw 16-bit mono 16 kHz PCM (AudioRecord on Android, `javax.sound.sampled` on
desktop), wrapped as a WAV by `wavFromPcm16`, uploaded as multipart to OpenAI's
transcription endpoint. It holds the mic open until you tap Done — a pause
mid-sentence is a pause, not the end of the take — and shows a live level meter and a
timer while it listens. Capped at four minutes per take. **This is the only dictation
the desktop build has.** Settings → Dictation switches Android back to the system
recognizer. Needs a network connection and an OpenAI key; audio leaves the device.
*In: Command, Ideas, Ask, AI Assistant.*

**System dictation** — `dictate`. Android's `RecognizerIntent` dialog. Offline and
free, but it stops at the first pause and won't punctuate. The fallback, not the
default. Desktop has no equivalent and returns null.

**Text to speech** — `speak` / `stopSpeaking`. Read-aloud on Today, Briefing and the
Daily Paper. Android uses the framework `TextToSpeech` engine. Desktop has none
bundled with the JVM, so `SpeechEngine.kt` shells out to whatever the OS already has:
`spd-say` (speech-dispatcher, what most Linux desktops already carry for
accessibility) or `espeak`/`espeak-ng` as a fallback, probed once at first use; on
Windows, PowerShell + `System.Speech`, which ships with every install so it's assumed
rather than probed. `supportsTts` reports whichever was actually found — on a Linux
machine with none of the three installed, this is honestly false rather than a silent
no-op.

**Wake word** — `setWakeWordEnabled`. A foreground service listening for a trigger
phrase (configurable in Settings, default "hey life"), capturing what you say after
it. Offline, on-device (Vosk). Android only.

**Speaker identification** — `enrollVoice` / `hasVoiceprint` / `clearVoiceprint` /
`setOnlyMyVoice` / `onlyMyVoiceEnabled`. "Only my voice": enrollment records a few
seconds of speech and stores a voiceprint; with the gate on, the wake word only fires
for a matching voice. **A filter, not a lock** — a recording can spoof it. Android only.

---

## Camera and codes

**Photo for AI vision** — `takePhoto` (camera) / `capturePhoto` (gallery). Returns a
downscaled base64 JPEG, or `null` on cancel, or `""` when something was captured but
couldn't be decoded — so the caller can say "couldn't read that image" instead of
failing silently. Backs the camera scan in Documents, Photos, Places, Quartermaster,
Finance, Recipes and Milestones. Android only; desktop takes a file instead.

**Code scanning** — `scanQr` (QR), `scanBarcode` (EAN-13/UPC, e.g. a book's ISBN),
`scanAnyCode` (any supported symbology in one pass, so a universal scanner doesn't
have to ask what it's looking at). Android only.

---

## Location

**Current position** — `getCurrentLocation`, fused provider, one shot. Requests the
permission if missing and returns `null,null` for that call; the caller re-taps once
granted. Powers "Use my location" on a place and "Check nearby places". Android only.

**Arrival geofence** — `armArrivalHere` / `clearArrivals`. Low-power: arms an alert at
the current location and fires a notification when you next arrive there. Android only.

---

## Notifications and alarms

**Actionable reminder** — `postReminder`. Carries Done / Snooze actions on Android; on
desktop, a system tray balloon (`platform/Tray.kt`).

**Pinned "next up"** — `setPinnedNextUp`. An ongoing notification showing what's next
on Android; the tray icon's tooltip on desktop. `null` clears it back to the default.

**Scheduled reminder** — `scheduleReminder` / `cancelReminder`. On Android, a real
notification at a future time with the app fully closed (AlarmManager, via
`setAndAllowWhileIdle` — no exact-alarm permission needed, so it may shift by a few
minutes). Used by Finance for bills and by task reminders.

**Desktop's version is honest but weaker: while running, not while closed.** A
background `java.util.Timer` fires the reminder as a tray balloon — but only for as
long as this JVM process is alive. There's no OS-level service behind it. To make
"while running" cover what people actually mean by "I closed the window," closing the
window **minimizes to the tray instead of quitting** (`Main.kt`) wherever a tray
exists; the tray's own Quit item is the real exit. A genuine "fires even after you've
quit" story needs a systemd user service or a Windows Task Scheduler entry — a
separate, larger piece of work, not attempted here.

**Not every desktop has a tray to carry any of this** — GNOME's default session
famously doesn't, without an extension. `supportsNotifications` reports that
honestly; where it's false, closing the window still just closes it, same as before.

---

## Files

| | Android | Desktop |
|---|---|---|
| `pickTextFile` — text/CSV contents (bank CSV import) | ✅ document picker | ✅ `JFileChooser` |
| `pickAttachment` — any file: name, mime, bytes | ✅ | ✅ |
| `openAttachment` — hand a stored file to the OS to view | ✅ FileProvider + ACTION_VIEW | ✅ system opener |
| `pickFilteredTextFile` — stream a huge file, keep matching lines | ✅ | ✅ |
| `pickEbook` / `pickEbookNamed` — EPUB/TXT → readable text | ✅ | ✅ |
| `exportTextAsPdf` — paginated PDF to the print/share sheet | ✅ framework `PdfDocument` | ✅ hand-rolled writer |

`pickFilteredTextFile` exists for the Apple Health export, whose `export.xml` runs to
hundreds of megabytes — it streams and keeps only matching lines so the file never sits
in memory at once. Both this and the EPUB/TXT parsing behind `pickEbook` are plain
`java.io` / `java.util.zip` / regex with no Android API in sight, so they live in a
`jvmShared` source set (`EbookParser.kt`, `FilteredTextReader.kt`) that Android and
desktop both build against — one implementation, not two copies that could drift.

`exportTextAsPdf` on desktop is a small hand-rolled PDF writer (`PdfWriter.kt`) rather
than a bundled library: unembedded standard fonts (Helvetica / Helvetica-Bold, which
every PDF viewer already has) and plain text operators are little enough format to
write directly. Word-wrap uses Helvetica's real AFM character widths rather than
measuring a locally-installed font, so wrapping is correct even on a CI runner with no
fonts of its own. Verified by round-tripping generated output through a real PDF
parser (structure, xref offsets, and extracted text all checked, including the
hard-wrap path for a single word wider than the page) before this shipped.

---

## Screen and system

**Immersive mode** — `setImmersive`, plus `cutoutTopPx()` and `navBottomPx()`. Hides
the system bars so a graphical interface with its own top row owns the whole screen,
and reports the notch and gesture-bar heights so full-screen art can stay clear of
them. Android only.

**Keep awake** — `keepScreenAwake`. Cooking mode in Recipes. Android only.

**Screen capture** — `captureScreen`, plus `machineSummary()` (OS, architecture, free
disk, memory) on every platform. Desktop grabs the whole physical screen via AWT
`Robot` — exactly what's on it under X11; a Wayland session's compositor may hand back
a black frame, in which case the request just goes out with no picture. Android
captures its own window via `PixelCopy` (API 26+ only — below that, absent, same as
before) — the app's content, not a photo of the whole phone screen, which is the other
direction (a foreground-service `MediaProjection` dance) nobody asked for. Both exist
for the helper window: a screenshot plus one line about the machine is the most useful
thing a non-technical person can send when something is wrong.

**Clipboard, share, browser** — `readClipboard`, `copyToClipboard`, `shareText`
(system share sheet on Android, clipboard on desktop), `openUrl`. All platforms.

**Contacts import** — `importContacts`. One-tap import of phone contacts. Android only.

---

## App-wide features (not platform-gated)

Cross-cutting behaviour that every module gets, implemented once in common code:

- **Multi-select on lists** — `ui/Bulk.kt`. Long-press a row (press-and-hold with a
  mouse) or hit Select, tick as many as you like, then act on all of them: Delete
  behind a confirmation, plus per-screen bulk actions (Archive in Ideas, Watched/Read
  in Links, Mark done for assignments, Packed for packing items, → Visited in Places,
  Done on the bucket list). Selections resolve against the ids currently shown, so an
  action can never hit a row that has since disappeared, and bulk delete frees the
  records' blobs exactly as the single-row delete does. Live in Quartermaster, Ideas,
  Links, Documents, Books, Recipes, Places, Packing, Collections, Contacts,
  Milestones, Rabbit Holes, Time Capsules, the Ledger, Education, Photos, Sharebox and
  the workout log, plus the nested item lists. Tasks has its own equivalent.
- **Attachments** — `attach/`. Any number of files per record, images as a wrapping
  photo grid with tap-to-enlarge, non-images as a named list. Bytes live in the blob
  store, which never syncs and never lands in a backup export; records hold ids.
- **Local persistence** — `Storage`, one key per module, JSON. Android writes to the
  app's private files dir, desktop to `~/.lifeos`.
- **Sync** — Supabase (PostgREST + RLS) with per-key change tracking and tombstones;
  Sharebox additionally uses Realtime.
- **App lock** — `settings/AppLock.kt`. A PIN gate in front of every module. A screen
  lock, **not encryption** — the records on disk are unchanged.
- **Map tiles** — `places/MapTiles.kt`. OpenStreetMap raster tiles, cached
  memory → blob store → network, LRU-capped at 600 tiles, so places you've looked at
  work with no connection. Settings shows the count and can clear it.
- **Date entry** — `ui/DateField.kt`, typed or picked, on every date in the app.
- **Save feedback** — `ui/SaveToast.kt`, one confirmation for every write.
- **A build stamp, at the bottom of Settings.** `versionName` (Android) and
  `packageVersion` (desktop) are hand-set constants that never change per build, so
  two builds a month apart look identical by version number alone. A Gradle task
  (`generateBuildInfo`) stamps the real commit SHA and build time into the app on
  every CI build; Settings shows both, which is the actual answer to "did I get the
  new one."

---

## Closed since the first pass of this document

All six platform gaps this document originally listed are now built:

1. **Desktop ebook import** — via the shared `EbookParser` (`jvmShared`) and a
   `JFileChooser`. Books on desktop can now hold *and read* EPUB/TXT files, not just
   store the record.
2. **Desktop Apple Health import** — via the shared `FilteredTextReader`
   (`jvmShared`) and the same picker.
3. **Desktop notifications** — real, but honestly scoped: a system tray balloon and a
   background timer, which only fire while the process is alive. Closing the window
   now minimizes to the tray (where one exists) instead of quitting, specifically so
   this has a chance to matter. See "Notifications and alarms" above for what this
   does and doesn't cover — it is deliberately *not* the same guarantee Android's
   AlarmManager gives.
4. **Desktop read-aloud** — shells out to `spd-say` / `espeak(-ng)` on Linux, or
   PowerShell's `System.Speech` on Windows. Best-effort: `supportsTts` is false on a
   machine with none of those installed.
5. **Desktop PDF export** — a small hand-rolled writer, since there's no bundled JVM
   engine and a full PDF library was too heavy for "print some text."
6. **Android self-screenshot** — via `PixelCopy` (API 26+), for the same helper-flow
   reason desktop's screen capture exists.

## Remaining honest limits

Not gaps in the sense above — things that were built to the platform's real ceiling,
not left undone:

- **Desktop notifications require a system tray**, which not every Linux desktop
  environment provides (GNOME's default session doesn't, without an extension).
  Where there's no tray, `supportsNotifications` is false and the window closing
  means closing, same as before this work.
- **Desktop notifications only fire while the process is running** — including
  minimized to the tray, not including after a real quit. A guarantee like
  Android's ("fires even if the app was never reopened") needs an OS-level
  background service — a systemd user unit, a Windows Task Scheduler entry — which
  is a distinct, larger piece of work, not attempted here.
- **Desktop read-aloud depends on what's installed.** No engine ships with the JVM;
  `SpeechEngine` probes for one and reports honestly if it finds none.
- **The desktop PDF writer is plain text only** — no images, no custom fonts, no
  layout beyond a title and wrapped paragraphs. It matches what `exportTextAsPdf`'s
  callers (the Daily Paper's digest) actually send today.
