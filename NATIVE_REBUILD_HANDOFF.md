# Native rebuild — session handoff

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

## Status (9 / 42 modules functional, all reachable)

Functional + persisting: **Tasks, Ideas, Places, Links, Contacts, Packing,
Collections, Rabbit Holes, Habits** (Habits has streaks + check-in). Everything
else is a reachable `Placeholder` (`ready=false` in `Modules.kt`).
Live tracker: https://claude.ai/code/artifact/a5a2e22f-a086-489d-899a-2988e2ca0cea

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

## What's left (the heavy modules)

The ~33 remaining `ready=false` entries in `Modules.kt` — the rich ones (Finance,
Education, Daily Paper, Orrery, the dashboards, Almanac, Knowledge Graph, Ask,
Briefing, Time Machine, AI features, Photos, etc.). Each is real work, not a list
port. A shared local database + cross-device sync also still to come (the current
`Storage` is a simple per-key text file).
