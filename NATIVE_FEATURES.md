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
| `supportsTts` | ✅ | ❌ |
| `supportsNotifications` | ✅ | ❌ |
| `supportsContacts` | ✅ | ❌ |
| `supportsKeepAwake` | ✅ | ❌ |
| `supportsWakeWord` | ✅ | ❌ |
| `supportsGeofence` | ✅ | ❌ |
| `supportsSpeakerId` | ✅ | ❌ |
| `supportsQrScan` | ✅ | ❌ |
| `supportsLocation` | ✅ | ❌ |
| `supportsCamera` | ✅ | ❌ |
| `supportsFilePick` | ✅ | ✅ |
| `supportsPdfExport` | ✅ | ❌ |
| `supportsDictation` | ✅ | ❌ *(no system dictation dialog — see Whisper below)* |
| `supportsRecording` | ✅ | ✅ *(checked live: an input line must exist)* |
| `supportsScreenshot` | ❌ | ✅ |

Desktop's `supportsRecording` is the only flag evaluated on each read rather than
fixed at startup, so plugging a microphone in after launch is noticed.

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
Daily Paper. Android only.

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

**Actionable reminder** — `postReminder`. Carries Done / Snooze actions on Android.

**Pinned "next up"** — `setPinnedNextUp`. An ongoing notification showing what's next;
`null` clears it.

**Scheduled reminder** — `scheduleReminder` / `cancelReminder`. Fires as a real
notification at a future time with the app closed (AlarmManager, via
`setAndAllowWhileIdle` — no exact-alarm permission needed, so Android may shift it by
a few minutes). Used by Finance for bills and by task reminders.

All three are Android only. On desktop, Notifications is a list of what needs
attention while the app is open.

---

## Files

| | Android | Desktop |
|---|---|---|
| `pickTextFile` — text/CSV contents (bank CSV import) | ✅ document picker | ✅ `JFileChooser` |
| `pickAttachment` — any file: name, mime, bytes | ✅ | ✅ |
| `openAttachment` — hand a stored file to the OS to view | ✅ FileProvider + ACTION_VIEW | ✅ system opener |
| `pickFilteredTextFile` — stream a huge file, keep matching lines | ✅ | ❌ **gap** |
| `pickEbook` / `pickEbookNamed` — EPUB/TXT → readable text | ✅ | ❌ **gap** |
| `exportTextAsPdf` — paginated PDF to the print/share sheet | ✅ | ❌ |

`pickFilteredTextFile` exists for the Apple Health export, whose `export.xml` runs to
hundreds of megabytes — it streams and keeps only matching lines so the file never sits
in memory at once.

---

## Screen and system

**Immersive mode** — `setImmersive`, plus `cutoutTopPx()` and `navBottomPx()`. Hides
the system bars so a graphical interface with its own top row owns the whole screen,
and reports the notch and gesture-bar heights so full-screen art can stay clear of
them. Android only.

**Keep awake** — `keepScreenAwake`. Cooking mode in Recipes. Android only.

**Screen capture** — `captureScreen` (AWT Robot) and `machineSummary()` (OS,
architecture, free disk, memory). **Desktop only** — the phone can't without a
foreground-service dance nobody asked for. Both exist for the helper window: a
screenshot plus one line about the machine is the most useful thing a non-technical
person can send when something is wrong.

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

---

## Known gaps

Honest list of what a build asks for and doesn't get:

1. **Desktop can't import an ebook.** `pickEbook` / `pickEbookNamed` are no-ops on
   desktop even though it has a working file picker and the EPUB/TXT parsing is
   shared. Books on desktop can hold records but not read them.
2. **Desktop can't import the Apple Health export.** `pickFilteredTextFile` is a
   no-op there for the same reason.
3. **Desktop has no notifications.** `postReminder`, `setPinnedNextUp` and
   `scheduleReminder` all no-op, so nothing can reach you with the app closed.
   `java.awt.SystemTray` could cover the in-session case.
4. **Desktop has no read-aloud.** No TTS engine ships with the JVM.
5. **Desktop can't export a PDF.** Android renders one and hands it to the print
   sheet; desktop no-ops.
6. **Android can't screenshot itself**, which is the one direction the helper flow
   would want if the roles were ever reversed.

Items 1, 2 and 3 are the ones with a clear path — the platform can do them and the
shared code already exists.
