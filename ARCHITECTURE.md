# Life OS — Architecture

> **One codebase.** The **native application** — Kotlin Multiplatform + Compose
> Multiplatform — is the product and all that remains. The original vanilla-JS PWA was
> deleted on 2026-08-22 (`REDESIGN_DECISIONS.md` §10); the section that documented it
> has been removed with it. To see how something behaved in the web build, read it out
> of git history from before that commit.

---

# The native application

## Layers

```
native/composeApp/src/
  commonMain/    Everything that isn't platform-specific: all 37 module screens,
                 their record types and load/save, the shared UI kit, AI clients,
                 sync, and the `expect` platform surface.
  androidMain/   Android `actual`s + the Activity host, services (wake word,
                 geofence, reminders), and Android-only helpers.
  desktopMain/   JVM `actual`s + the desktop window entry point.
```

A module is a folder under `commonMain/.../<area>/` holding two things: a record file
(the `@Serializable` data classes plus `load…()` / `save…()`) and a screen file (the
Compose UI). Nothing else in the app reaches into another module's storage key.

## The three seams

1. **`Storage`** (`expect object`) — the only thing that touches a filesystem. One key
   per module, JSON in, JSON out. Android writes to the app's private files dir; desktop
   to `~/.lifeos`. Every write records a change for sync.
2. **`Native`** (`expect object`) — everything the app asks of the machine: camera,
   microphone, location, notifications, file pickers, screen capture. `supports*` flags
   are read *before drawing*, so a build without the hardware never draws the control.
   Functions degrade quietly and never throw into the UI. Full inventory and the
   per-platform matrix: `NATIVE_FEATURES.md`.
3. **Blobs** (`saveBlob` / `loadBlobImage` / `deleteBlob`) — bytes, kept apart from
   records on purpose. Photos, PDFs, ebooks and map tiles live here; records hold ids.
   **The blob store never syncs and never enters a backup export**, which is what keeps
   a backup small and a sync cheap.

## Registry and navigation

`Modules.kt` is the single list of what exists — id, glyph, label, domain group, and a
lambda that builds the screen. `Nav.kt` holds the current route; `Shell.kt` renders the
nav band and dispatches to the registry. Adding a module is one entry in that list.

`Interfaces.kt` is the same swappable-presentation idea as the web build: the functional
screens are one interface, a graphical home is another, and the registry switches the
whole app live without touching module data.

## Compilation

There is no local toolchain in this environment — **the native app is built by CI
only** (`.github/workflows/build-native.yml`, three jobs: Android APK, Windows, Linux).
Push triggers are limited, so a build is usually started with `workflow_dispatch`.
Artifacts are downloaded from the run page.

## One trap worth knowing

Compose's animation APIs are multiplied by the OS **animator duration scale**. With
device animations turned off, every `animateTo` / `infiniteRepeatable` jumps straight to
its end value and nothing appears to move. Anything that must animate regardless is
driven from `withFrameNanos` instead, which isn't scaled — see the mic level meter.

