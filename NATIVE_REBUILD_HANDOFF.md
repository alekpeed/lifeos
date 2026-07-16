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
- **Services** (batch 3): always-on wake word (`WakeWordService`, now on the Vosk
  offline engine — see the Voice section below; toggle in Settings) and arrival
  geofencing (`Geofences` + `GeofenceReceiver`, `play-services-location`; arm/clear
  in Settings). Geofencing is a working scaffold — flagged for on-device tuning
  (background-location grant + per-place coords).

`NativeHost` (androidMain) holds the current Activity/appContext + TTS engine;
`MainActivity` wires it and requests permissions. Manifest declares
READ_CONTACTS, POST_NOTIFICATIONS, NFC, RECORD_AUDIO, location (fine/coarse/
background), FOREGROUND_SERVICE(_MICROPHONE), plus the service + receivers.
Full monitoring dashboard: https://claude.ai/code/artifact/8b63ae9b-45c9-43dc-9489-64799d5e33f5

## Digital-assistant role (2026-07-16)

The wake-word foreground service is *not* what makes Android offer an app as the
system assistant — that requires a real `VoiceInteractionService`. Life OS now
registers one so it shows up under **Settings → Apps → Default apps → Digital
assistant app** and catches the assist gesture (long-press home / power, or the
assistant swipe). Files under `androidMain/.../assist/`:

- `LifeAssistService` (`VoiceInteractionService`) — the role anchor.
- `LifeAssistSessionService` (`VoiceInteractionSessionService`) → returns a
  `LifeAssistSession` per assist invocation.
- `LifeAssistSession` (`VoiceInteractionSession`) — `onShow()` opens
  `lifeos://module/command` (quick-capture) via `startAssistantActivity`, then
  `hide()`s. This is the seam where a graphical assistant surface can live later.
- `LifeAssistRecognitionService` — minimal valid `RecognitionService` stub (the
  role descriptor requires one; declines cleanly with `ERROR_CLIENT`).
- `res/xml/interaction_service.xml` — the `<voice-interaction-service>` descriptor
  (`supportsAssist=true`), pointed at the session + recognition services.

Manifest declares all three services (the `VoiceInteractionService` gated by
`BIND_VOICE_INTERACTION` + `android.voice_interaction` meta-data). `MainActivity`
also handles `ACTION_ASSIST` directly (→ `command`) for the plain assist path.
**On-device step:** reinstall, then pick LifeOS under Digital assistant app — the
picker only appears once a valid `VoiceInteractionService` is installed.

## Voice: wake word + speaker ID (2026-07-16, Vosk)

The wake word moved off Android's system `SpeechRecognizer` (spin-up/tear-down
loop — battery-heavy, network-dependent, deaf gaps, and it substring-matched the
single word "life") onto **Vosk** (`com.alphacephei:vosk-android`, offline,
Apache-2.0). Dependency + `packaging { jniLibs { pickFirsts … } }` live in
`composeApp/build.gradle.kts`; it's `androidMain`-only, desktop stays a no-op.

- `WakeWordService` — one continuous on-device Vosk decoder (no restart gaps, no
  network). Whole-word match against a **configurable** wake phrase (Storage key
  `WakePhrase`, default "hey life"), so common words can't trip it. Captures the
  words after the phrase into Ideas.
- `VoskModels` — the ~40 MB recognizer model + ~13 MB speaker model are **too big
  to ship in the repo/APK**, so they're downloaded once on first enable and
  unpacked into `filesDir` (zip-slip-guarded). This is why CI compiles fine with
  no model present — the model is a runtime asset, not a build input.
- **Only my voice** (`VoiceId` + `VoiceEnroller`): enrollment records a few seconds
  of the owner (Settings → Enroll), runs it through Vosk's **speaker model** to get
  an x-vector voiceprint, averages the samples, stores it (Storage `VoicePrint`).
  When the gate is on (`OnlyMyVoice`) and a print exists, the service attaches the
  speaker model so each utterance carries its own voiceprint, and a capture is
  accepted only if cosine similarity ≥ threshold (`VoiceThreshold`, default 0.55).
  Exposed via the `Native` expect/actual surface (`enrollVoice`, `hasVoiceprint`,
  `clearVoiceprint`, `setOnlyMyVoice`, `onlyMyVoiceEnabled`, `supportsSpeakerId`).

**Honest ceilings (documented, not bugs):** this is CPU-side *software* hotword
spotting, **not** the phone's dedicated low-power hotword chip — that chip is
gated behind the system-assistant role + signature-privileged permissions, so a
normal-install app can't load a custom wake word onto it (root/custom-ROM/OEM
only). Speaker ID is a *filter, not a lock*: short-phrase verification has real
error rates and a recording of the owner can spoof it. All runtime behavior
(actual spotting, actual voice rejection) is **device-only** — CI only proves it
compiles.

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

## External integrations (2026-07-16)

Shared HTTP: `net/Http.kt` — a dependency-free `httpGet` / `httpPostJson` over a
generic `httpRequest` (HttpURLConnection off the main thread, never throws;
`NetResponse(status, body)`). Integration clients live in `integrations/` and parse
with kotlinx-serialization-json (`parseToJsonElement` + `jsonObject`/`jsonArray`
navigation, so no per-response data classes). All fetch on demand — no background
polling.

**Built & CI-green (no account/decision needed):**
- **Weather** — `WeatherClient` (Open-Meteo, keyless): geocode a typed city →
  current temp + today's H/L. City-based, so no location permission; works on
  desktop too. In the Tools screen.
- **Currency** — `CurrencyClient` (open.er-api.com, keyless): USD-based live rates,
  converted locally across ~160 currencies. Tools screen.
- **Markets** — `MarketsClient` (keyless): CoinGecko crypto watchlist (editable
  ids, price + 24h change) + Stooq DJIA index (CSV). Tools screen.
- **Telegram (send-only)** — `TelegramClient`: posts to a bot made with @BotFather;
  token + chat id + a test button in Settings. No listener/webhook.
- **Multi-provider AI** — Ask + Assistant route through a provider picked in
  Settings: **Claude** (`AiClient` Messages API), **OpenAI** (`OpenAiClient`,
  chat/completions, Bearer key), **Gemini** (`GeminiClient`, generateContent, key
  param). `AiClient.ask` dispatches by the `AiProvider` pref; `hasKey()` reflects
  the active provider. Per-provider key + model fields in Settings. Note: OpenAI
  works direct from native — the web build's CORS block is browser-only.

The Tools module (`system/ToolsScreen.kt`) hosts the keyless data cards; it
replaced the old placeholder, so the 42-module count is unchanged.

**Not built — needs a decision, an account secret, or the data layer first:**
- **Google suite** (Drive sync, Calendar push, Photos picker) — all need native
  OAuth (consent screen + redirect scheme + a client id). Decision + setup.
- **Supabase** (auth + personal sync + Sharebox + profiles) — the planned sync
  backend; large, and wants the structured data layer first (Storage is still
  per-key text). MCP is connected on the tooling side.
- **Native push** — web build uses VAPID + a service worker; native equivalent is
  FCM + a Firebase project (a setup decision), so it's not a code-only port. Local
  scheduled notifications already exist (`AlarmManager`).
- **Apple Health import** — a local file parse of an exported `export.xml`, not a
  network integration; portable later with a file picker.
- **OSM map tiles** (Places map) — keyless, but needs a real slippy-map component
  in Compose, not just a client. Deferred as UI work.

**Parked / dead (left as-is per `FUTURE_FEATURES.md`):** Plaid (bank linking —
paid + backend token exchange), two-way Telegram (needs a webhook backend). Note
OpenAI itself was previously parked *because of the web CORS/proxy problem*; that
constraint doesn't exist natively, so it's now built as a provider.

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
- **External integrations** — the keyless data ones (weather/currency/markets),
  Telegram send, and multi-provider AI are built (see the section above). Still
  open: the account/OAuth ones (Google suite, Supabase sync, native push) and
  parked Plaid. **QR Sync** needs camera + a QR encoder to turn its payload into a
  scannable code.
- **Remaining module depth** — the lighter modules are still first-pass list/note
  screens (Places, Links, Recipes, Documents, Quartermaster, Museum, Education,
  etc.). Fine as-is, deepen as needed.
- **Data layer** — *done through cross-device sync* (see the two data-layer
  sections below): `Storage` is sync-ready (records + tombstones) and Supabase
  push/pull is wired with email auth + a Settings UI. Optional remaining polish:
  swap the per-key text backing store for a structured DB (SQLDelight) — internal
  only, since the record/sync API already sits above `Storage`.

## Data layer — step 1: sync-ready records (2026-07-16)

The per-key text `Storage` is unchanged from every screen's point of view
(`read`/`write` same strings), but each key is now a **syncable record**:

- `sync/SyncMeta.kt` — a JSON sidecar (under reserved key `__syncmeta`) that stamps
  each key's `updatedAt` on every `Storage.write` and a tombstone on the new
  `Storage.remove`. Reserved `__`-keys are guarded so the sidecar can't recurse
  through its own writes. `lastSyncAt` high-water mark under `__lastsync`.
- `sync/SyncEngine.kt` — deterministic **last-write-wins** merge, network-free:
  `changesToPush()` (records newer than `lastSyncAt`), `applyRemote(list)` (apply
  remote records that are newer, taking the remote timestamp so they don't look
  like fresh local changes), `markSynced(ts)`. `pendingCount()` feeds a read-only
  Settings readout.
- `sync/SyncModel.kt` — `SyncRecord(key, text?, updatedAt, deleted)` (the wire
  unit) + `RecordMeta`.
- `Storage` gained `remove()`; `write`/`remove` call into `SyncMeta` on both
  Android + desktop actuals.

## Data layer — step 2: Supabase cross-device sync (2026-07-16)

The transport, wired onto the **existing** `sync_records` table (reused, not
created) under a dedicated `store = "kv"` namespace so native records coexist with
the web app's own stores. RLS scopes every row to `auth.uid()`.

- `sync/SupabaseConfig.kt` — project URL + public anon key (public by design; RLS
  is the access control) + the `kv` store namespace.
- `sync/SupabaseAuth.kt` — **email/password** via GoTrue REST (`/auth/v1/signup`,
  `/auth/v1/token`). No OAuth redirect, so it works natively. Tokens stored under
  reserved `__sb_*` keys; refresh + sign-out.
- `sync/SupabaseSync.kt` — `syncNow()`: push `SyncEngine.changesToPush()` (upsert on
  `user_id,store,record_id`, `Prefer: resolution=merge-duplicates`) then pull the
  `kv` rows into `SyncEngine.applyRemote()`. Converts the table's `timestamptz`
  ↔ the app's epoch-millis; auto-refreshes the token on a 401.
- Settings **SYNC** section: create account / sign in / sync now / sign out + status.

**Device / account caveats (CI can't check these):**
- The native flow uses **email/password**; the web app uses **Google OAuth**. So a
  native email account is a *different* `auth.uid()` than the web Google account —
  native syncs across native installs (phone ↔ desktop, same email), not with the
  web app's data. Fine for now; unifying would mean adding Google OAuth natively.
- Supabase must have the **Email** auth provider enabled, and for frictionless
  login you may want **"Confirm email" off** (otherwise sign-up needs a one-time
  email confirmation before the first sign-in). Both are dashboard toggles.
- The actual round-trip (rows landing in `sync_records`, RLS scoping) needs a
  signed-in account on a device to confirm; the merge rules + wire format are
  deterministic and compile-verified.

**Still open (optional):** swapping the per-key text backing store for a structured
DB (SQLDelight) — the record model + sync already sit above `Storage`, so this is a
backing-store change, not an API change.
