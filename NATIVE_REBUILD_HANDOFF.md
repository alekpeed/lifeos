# Native rebuild — session handoff

> **BRANCH: all active work is on `claude/lifeos-dev-setup-dpipr6`.** If you're
> starting fresh, run `git fetch origin && git checkout claude/lifeos-dev-setup-dpipr6`
> before anything else, then continue from here.

**Read this first if you're continuing the native Life OS app.** As of
2026-07-16 the project is being rebuilt as a **real native app** (Kotlin +
Compose Multiplatform), replacing the Capacitor/WebView wrapper. Alek was
explicit: he wants a **real native Android app AND a real native Windows app**,
not a web app in a shell. The old Capacitor build (`android/`, the web PWA at
repo root, `CAPACITOR_*` docs) is **legacy** — still there, still deployed, but
superseded. Do not add features to it; build the native app.

## Where the native app lives

- **`native/`** — a Kotlin + Compose Multiplatform Gradle project (separate from
  the Capacitor `android/`). Native-rendered UI (Skia), no WebView anywhere.
- **`.github/workflows/build-native.yml`** — builds a **native Android APK**
  (ubuntu) and packages a **native Windows `.msi`** (windows-latest runner),
  both uploaded as artifacts (`lifeos-native-android`, `lifeos-native-windows`).
  Cloud-only, no local machine — same as everything else here.

Stack: Kotlin 2.0.0, Compose Multiplatform 1.6.11, AGP 8.2.1, compileSdk 34,
JDK 17, Gradle 8.7 (via `gradle/actions/setup-gradle`, no wrapper jar committed).

## Structure

- `native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/`
  - `App.kt` — root, wraps `Shell()` in MaterialTheme.
  - `Shell.kt` — home launcher ⇄ module-detail navigation (back button).
  - `HomeScreen.kt` — grouped list of all modules; tap to open.
  - `Modules.kt` — **the registry**. One `Module(icon, label, group, ready, content)`
    per module. `ready=true` = functional; `false` = reachable `Placeholder`.
  - `Storage.kt` — `expect object Storage { read/write }` (text KV store).
  - `tasks/`, `ideas/`, `habits/`, `ui/SimpleListScreen.kt`, `ui/Placeholder.kt`.
- `androidMain/…/Storage.kt` — writes to app filesDir; `MainActivity.kt` sets
  `Storage.appContext` then `setContent { App() }`.
- `desktopMain/…/Storage.kt` — writes to `~/.lifeos`; `Main.kt` is the desktop
  entry (`application { Window { App() } }`).

## Status (42 / 42 modules functional)

**All 42 modules are live and persisting** — no placeholders left. Every one has
a genuine functional native screen; the rich/graphical ones ship an honest
functional default (see below) and are wired to accept a graphical interface
later. Progress report: https://claude.ai/code/artifact/ccc38e78-08e6-412b-b892-ec689dd3658f

Screen types in play:
- `ui/SimpleListScreen.kt` — add/list/delete.
- `ui/NoteListScreen.kt` — title + secondary note.
- `ui/StatusListScreen.kt` — item with a status chip that cycles.
- `ui/StatsScreen.kt` — per-module item tally with bars (The Almanac).
- `ui/SearchScreen.kt` — live search across all stored data (Ask, Search).
- `ui/SummaryScreen.kt` — generic multi-module roll-up (still available; Today and
  Briefing have since moved to bespoke screens, see the depth pass below).
- Bespoke: `tasks/TasksScreen.kt` (due/priority/project), `habits/HabitsScreen.kt`
  (real streaks), `core/TodayScreen.kt`, `insight/BriefingScreen.kt`,
  `insight/NotificationsScreen.kt` (scheduled), `insight/RecallScreen.kt` (spaced
  repetition), `finance/LedgerScreen.kt` (categories + recurring),
  `health/HealthScreen.kt` (metric trends), `core/CommandScreen.kt` (quick-capture
  → Tasks/Ideas), `insight/AskScreen.kt` + `insight/AssistantScreen.kt` (real
  model-backed Q&A / chat — see AI layer below), `people/ContactsScreen.kt`,
  `system/QrSyncScreen.kt`, `system/StationCatScreen.kt`, `settings/SettingsScreen.kt`.

## AI layer (Ask + Assistant)

Ask and the Assistant call the **Anthropic Messages API** directly. There's no
official Anthropic SDK for Kotlin `commonMain`, so it's raw HTTP:
- `ai/Http.kt` — `expect suspend fun httpPostJson(...)`; actuals in androidMain/
  desktopMain use `java.net.HttpURLConnection` on `Dispatchers.IO` (both targets
  are JVM, so no HTTP-client dependency).
- `ai/AiClient.kt` — builds/parses `/v1/messages` via kotlinx-serialization,
  reads the user's key + model from local Storage (`ApiKey`, `AiModel`), omits
  `temperature`/`thinking` (Opus 4.8 rejects them), maps HTTP errors to friendly
  messages. Default model `claude-opus-4-8`; switchable to Sonnet 5 / Haiku 4.5
  in **Settings → AI**.
- `data/aiContext(query)` grounds answers in the user's saved data (matching
  items + module totals), kept small to bound cost.
- No key set → Ask falls back to local search; Assistant says it needs a key.

The key is stored **on-device only** (Settings). CI compiles this; the live API
call needs a real key on a device to verify. Build deps added:
kotlinx-serialization plugin + `kotlinx-serialization-json`; manifest gains
`INTERNET`.

## Interchangeable interfaces (the interface layer)

`interfaces/Interfaces.kt` is what keeps interfaces swappable. Every module now
has a stable `id` (`tasks`, `habits`, `orrery`, …) and every page renders through
`Interfaces.Render(id, default)`. To plug in a graphical interface Alek designs:

```kotlin
Interfaces.register("spatial-1", "tasks") { MySpatialTasks() }
Interfaces.register("spatial-1", "orrery") { MyOrrery() }
```

It then appears in **Settings → Interface** and switches the whole app live.
Interfaces can be partial — any module without a custom screen for the active
interface falls back to its functional default. Graphical screens read/write the
same Storage keys, so no data migration. This is the seam for the
"every page can accept a graphical interface" requirement (2026-07-16).

`data/Data.kt` holds `DATA_SOURCES` (label ↔ storage key) plus `linesOf` /
`countOf` / `searchAll` helpers that the aggregate + search screens share.

## Native OS capabilities (2026-07-16)

Ported from the old Capacitor build to real Kotlin. All compile + wire on CI;
runtime paths that touch mic/notifications/NFC/location/contacts need a device to
confirm. The cross-platform surface is `platform/Native.kt` (`expect object`),
real on Android (`platform/Native.android.kt`), no-op/JVM on desktop
(`platform/Native.desktop.kt`) — screens gate optional UI on the `supports*` flags.

- **UI-triggered** (`Native.*`): TTS read-aloud (Today/Briefing), actionable +
  pinned notifications (NotificationsScreen; `NotificationActionReceiver`),
  outbound share (QR Sync), clipboard paste (Command), contacts import
  (ContactsScreen), keep-awake/cooking mode (Settings).
- **Passive** (`MainActivity` + manifest, routed via `Nav`): inbound share sheet
  (ACTION_SEND → Ideas), deep links (`lifeos://module/<id>`), dynamic home-screen
  shortcuts, NFC NDEF read (foreground dispatch → Ideas), charging ritual (runtime
  `ACTION_POWER_CONNECTED` receiver).
- **Services** (batch 3): always-on wake word (`WakeWordService`, foreground
  SpeechRecognizer loop, "life …" trigger → Ideas; toggle in Settings) and arrival
  geofencing (`Geofences` + `GeofenceReceiver`, `play-services-location`; arm/clear
  in Settings). Both are working scaffolds — flagged for on-device tuning
  (ASR duty cycle; background-location grant + per-place coords).

`NativeHost` (androidMain) holds the current Activity/appContext + TTS engine;
`MainActivity` wires it and requests permissions. Manifest declares
READ_CONTACTS, POST_NOTIFICATIONS, NFC, RECORD_AUDIO, location (fine/coarse/
background), FOREGROUND_SERVICE(_MICROPHONE), plus the service + receivers.
Full monitoring dashboard: https://claude.ai/code/artifact/8b63ae9b-45c9-43dc-9489-64799d5e33f5

## Module depth pass (2026-07-16)

Eight modules deepened past their first-pass screens, all on a shared date
foundation (`data/Dates.kt`, backed by `kotlinx-datetime`). Model records live
next to their screens (`tasks/Task.kt`, `habits/Habit.kt`, `insight/Recall.kt`)
with centralized parse/serialize so cross-module screens (Today, Briefing) reuse
them.

- **Tasks** — due date (quick-pick Today/Tomorrow/Next week, color-flagged
  today/overdue), cycling priority, project tag; tap a row to expand inline edit.
- **Habits** — streak derived from real per-day check-in history (can't inflate
  by double-tapping; a missed day resets it), 7-day dot strip.
- **Today** (`core/TodayScreen.kt`) — overdue + due-today tasks (checkable inline)
  and not-yet-checked-in habits (check-in inline). Live from Tasks/Habits storage.
- **Briefing** (`insight/BriefingScreen.kt`) — prioritized worklist: overdue →
  due-today → at-risk streaks → light rollup. Real computation, not a list dump.
- **Notifications** — quick-pick scheduling backed by real `AlarmManager`
  (`ReminderFireReceiver` posts even when app is closed; `setAndAllowWhileIdle`,
  no exact-alarm permission). New `Native.scheduleReminder/cancelReminder`.
- **Recall** (`insight/Recall.kt`) — real spaced repetition on an interval ladder
  (1/3/7/14/30/90d); Know It advances, Forgot resets; due vs upcoming split.
- **Finance** — per-entry category + recurring flag, live per-category breakdown;
  recurring schedules a ~30-day reminder (real nudge, not fabricated data).
- **Health** (`health/HealthScreen.kt`) — structured metric/value/unit readings
  grouped by metric with latest value, Δ-arrow vs previous, and a bar trend.

Note: Habits and Recall changed storage format for real per-day/scheduling data;
old integer-streak / status saves don't migrate exactly (documented honest
tradeoff). Some modules that reason about dates now depend on the device clock.

## How to port a module (the pattern)

1. Write a `@Composable` screen in `commonMain` (see `tasks/TasksScreen.kt` for a
   bespoke one, `ui/SimpleListScreen.kt` for a simple list). Persist via
   `Storage.read(key)` / `Storage.write(key, text)` — serialize to lines.
2. In `Modules.kt`, flip that module's entry to `ready = true` and point
   `content` at the new screen.
3. Push → `build-native.yml` runs. **The Android job is the compile proof**
   (commonMain compiles there); the Windows job also packages the `.msi` (slower,
   ~5 min). Both must be green.
4. Update the tracker artifact (republish same URL) — mark the module done, bump
   the count.

## Watch-outs

- **CI is the only verifier** — no local Android/Compose build here. Push and
  read the run. The Android job compiles the shared code; trust it for
  correctness, the Windows job just packages.
- Compose scope extensions: `Modifier.weight`/`Modifier.align` are `RowScope`/
  `ColumnScope`/`BoxScope` members — **don't `import` them**; `Modifier.height`/
  `width`/`padding` DO need `androidx.compose.foundation.layout.*` imports.
- Keep the module count honest — mark `ready=true` only when a module genuinely
  works (persists, does its thing), not for a placeholder.
- Model/effort protocol still applies (see `CLAUDE.md`): re-check model on a
  fresh window; this native work has been running on claude-opus-4-8 by Alek's
  choice.

## What's left

All 42 modules are functional and the core ones have real depth (see the depth
pass above). Remaining work:

- **Graphical interfaces** — Alek designs them; register per-module screens
  against the module ids via `Interfaces.register(...)` (see above). Modules with
  an inherently visual nature currently ship a functional default standing in for
  a future graphical interface: **Orrery** (celestial notes → orrery view),
  **Knowledge Graph** (node/links list → graph), **Skill Trees** (status list →
  tree), **Photos** (captions → gallery), **Theme from Photo** (hex entry →
  palette extractor), **Station Cat** (glyph → character).
- **External integrations** — **Finance** (Plaid account sync on top of the
  category ledger), **AI Assistant** / **Ask** (wire a model to answer the queue /
  questions; today Ask honestly searches stored data). **QR Sync** needs camera + a
  QR encoder to turn its payload into a scannable code.
- **Remaining module depth** — the lighter modules are still first-pass list/note
  screens (Places, Links, Recipes, Documents, Quartermaster, Museum, Education,
  etc.). Fine as-is, deepen as needed.
- **Data layer** — the current `Storage` is a per-key text file. A shared local
  database + cross-device sync replaces it; screens already isolate persistence,
  and `DATA_SOURCES` centralises the keys. (This also gives dated modules a real
  migration path instead of the format-change resets used in the depth pass.)
