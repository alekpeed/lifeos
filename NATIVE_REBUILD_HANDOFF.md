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
- `ui/SummaryScreen.kt` — live roll-up of several modules (Today, Briefing).
- `ui/StatsScreen.kt` — per-module item tally with bars (The Almanac).
- `ui/SearchScreen.kt` — live search across all stored data (Ask, Search).
- Bespoke: `finance/LedgerScreen.kt` (balance), `core/CommandScreen.kt`
  (quick-capture → Tasks/Ideas), `insight/AssistantScreen.kt` (persisted
  question queue, no fabricated replies), `system/QrSyncScreen.kt`,
  `system/StationCatScreen.kt`, `settings/SettingsScreen.kt`.

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

All 42 modules are functional, so the remaining work is depth, not coverage:

- **Graphical interfaces** — Alek designs them; register per-module screens
  against the module ids via `Interfaces.register(...)` (see above). Modules with
  an inherently visual nature currently ship a functional default standing in for
  a future graphical interface: **Orrery** (celestial notes → orrery view),
  **Knowledge Graph** (node/links list → graph), **Skill Trees** (status list →
  tree), **Photos** (captions → gallery), **Theme from Photo** (hex entry →
  palette extractor), **Station Cat** (glyph → character).
- **External integrations** — **Finance** (Plaid account sync on top of the manual
  ledger), **AI Assistant** / **Ask** (wire a model to answer the queue / questions;
  today Ask honestly searches stored data). **QR Sync** needs camera + a QR
  encoder to turn its payload into a scannable code.
- **Data layer** — the current `Storage` is a per-key text file. A shared local
  database + cross-device sync replaces it; screens already isolate persistence,
  and `DATA_SOURCES` centralises the keys.
