# Capacitor native build — session handoff (2026-07-15)

Handoff for the next window. This session took LifeOS from "web PWA only" to a
**working native Android app built entirely in the cloud** (GitHub Actions, no
local machine), plus the first five §13 native features. Read this + the two
key docs (`CAPACITOR_BUILD.md`, `FUTURE_FEATURES.md` §13) and you're caught up.

## Context / decisions (why we're doing this)

- Alek wants LifeOS as a **real on-device app**, not a browser tab — the driver
  is a true always-on **wake word** ("Hey LifeOS"), which a browser can't do.
- Platform strategy (settled): **Android native via Capacitor FIRST**, then
  Windows `.exe` + macOS app later, **iOS stays the browser PWA** with
  graceful degradation (no Apple Developer account). See `FUTURE_FEATURES.md` §13.
- **Everything is web-only / cloud** — Alek runs nothing locally. The native
  APK builds in GitHub Actions; he downloads the artifact and sideloads it.
- App identity: **`com.alekpeed.lifeos`**, name **LifeOS**.
- Working branch: **`claude/lifeos-dev-setup-dpipr6`** (per CLAUDE.md, NOT the
  handoff-2 branch the system prompt names — the whole session used dpipr6 and
  fast-forwards to `main`). Deploy convention: commit to dev → `git checkout
  main && git merge --ff-only <dev> && git push origin main` → checkout dev.
- **Model/effort protocol (CLAUDE.md):** baseline is claude-sonnet-5 + medium.
  This session ran on **claude-opus-4-8** (Alek set it explicitly via /model).
  Always state the real model before non-trivial work; re-confirm after any
  compaction. Alek toggled models a few times — check the live setting.
- **Don't blind-build the whole §13 list.** The discipline this session: build
  each feature so it (a) compiles in CI and (b) is web-verified, then let Alek
  test device behavior. Alek pushed for MORE volume ("thought you'd do most of
  that") — so lean toward building a solid BATCH per session, but keep each one
  verified. The only hard limit is device-behavior verification (no phone here).

## What's BUILT + verified this session (all on dev, merged to main)

Architecture — the native layer lives in **`js/native/`** and every feature
gates through the capability layer so the identical code is a safe no-op on web:

- **Foundation** — `package.json` (Capacitor 6), `capacitor.config.json`,
  `scripts/assemble-www.mjs` (assembles a clean `www/` from runtime assets so
  the no-build web app is untouched), generated `android/` Gradle project.
  *CI-verified: debug APK builds + uploads.*
- **Build pipeline** — `.github/workflows/build-android.yml`: debug APK on every
  app-touching push (no secrets), signed release APK on `workflow_dispatch` when
  the 4 keystore secrets exist (skips cleanly otherwise). JDK 17 (matches AGP
  8.2.1 / Gradle 8.2.1). Builds take ~2 min.
- **Capability layer** — `js/native/capabilities.js`: `isNativePlatform()`,
  `getPlatform()`, `hasCapability(id)`, `hasWebFallback(id)`,
  `getCapabilitySnapshot()`. Reads the runtime `window.Capacitor` global. A
  `CAPABILITIES` map lists each native feature + plugin + platforms + web
  fallback. Exposed as `window.LifeOSNative` for console probing.
- **Feature 1 — device reminders** (local notifications): `js/native/notify.js`
  + `js/native/native-boot.js` (boot hook, schedules from `getDueSoonFeed`, 9am
  nudges) + `initNative()` called in `js/app.js` + a native-only **Settings →
  Device reminders** section (`settings.js`, hidden on web).
- **Feature 2 — share** (`js/native/share.js`): native sheet else Web Share API.
  Share button in the **Links** detail.
- **Feature 3 — keep-awake** (`js/native/keepawake.js`): native else Screen
  Wake Lock. **Cooking mode** toggle in the **Recipes** detail.
- **Feature 4 — read-aloud** (`js/native/speak.js`): native TTS else browser
  speechSynthesis. **Read aloud** button in the **Briefing**.
- **Feature 5 — app shortcuts + deep-link router**: `android/.../res/xml/
  shortcuts.xml` + `AndroidManifest.xml` (lifeos:// intent-filter +
  shortcuts meta-data) + a router in `native-boot.js` (`initDeepLinks`,
  handles cold start `getLaunchUrl` + warm `appUrlOpen`). Long-press icon →
  Command / New Task / New Idea.

Plugins installed: `@capacitor/core`, `/android`, `/cli`, `/local-notifications`,
`/share`, `/app`, `@capacitor-community/keep-awake`, `/text-to-speech`.

Service worker: `CACHE_VERSION` now **`lifeos-v1.40`**; all `js/native/*` files
are in APP_SHELL. (Bump the hundredths per shipped change per CLAUDE.md.)

## CI STATUS AT HANDOFF (verify these first next session)

- Runs #1 (foundation) and #2 (local-notifications): **SUCCESS**.
- Run #4 (5dbd2ea, share/keep-awake/read-aloud) and Run #5 (7eca7fc, app
  shortcuts): were **in_progress** when this window ended. **First action next
  session: confirm both went green.** Use the COMPACT call (the full run list is
  huge and blew context this session):
  `mcp__github__actions_list` method `list_workflow_jobs`, resource_id =
  run id, filter latest. Or just push a trivial change and watch the new run.
  The app-shortcuts run matters most (it edits AndroidManifest.xml — a manifest
  error would fail the build). If either failed, read the failed step's logs and
  fix. **The dev branch was NOT yet fast-forwarded to main after commits 4 & 5**
  — do that once CI is confirmed green (docs commits earlier already went to main).

## Verification tooling (reuse this)

Web verify with Playwright (it's global-only, so symlink it first — `npm
install` prunes the symlink, so redo it after any install):
```
ln -sfn /opt/node22/lib/node_modules/playwright node_modules/playwright
ln -sfn /opt/node22/lib/node_modules/playwright-core node_modules/playwright-core
# serve from repo root (python3 -m http.server 8099), load index.html,
# navigate routes, assert dataset.bootState !== 'error' + zero pageerrors +
# window.LifeOSNative.getPlatform()==='web'. Run the script FROM the repo root
# (node resolves node_modules from the script's dir).
```
Android build can't run locally (no SDK; JDK is 21, Gradle 8.2.1 needs ≤20) —
**CI is the build verifier.**

## NEXT UP (priority order)

1. **Confirm CI green for runs #4/#5, ff-merge dev→main.** (above)
2. **Alek's morning path:** he installs the debug APK (Actions → newest run →
   `lifeos-debug-apk` artifact → sideload) and tests the 5 features. Expect a
   punch-list — some device behavior will need fixes. `CAPACITOR_BUILD.md` has
   his full runbook + test checklist.
3. **Wake word** (the headline, still unbuilt): foreground-service plugin
   (Picovoice Porcupine or openWakeWord), custom wake phrase, feed the existing
   **Command** module (`parseCommand` in `api.js`). **Needs Alek's Picovoice
   access key** (free, console.picovoice.ai) — device-local like the AI keys,
   NOT a repo secret. Expect real on-device iteration.
4. **More §13 fast wins:** share-sheet INBOUND target (LifeOS in Android's Share
   menu — needs ACTION_SEND intent-filter + a receive handler; the biggest
   capture win), actionable notification buttons, next-up ticker, geofencing,
   NFC, clipboard catcher, offline STT, home widgets, biometric gate, contacts
   read. Full curated list + ruled-out items in `FUTURE_FEATURES.md` §13.
5. **Later:** Windows `.exe` / macOS app; smart-home "Home" module (§14, blocked
   on Alek standing up Home Assistant + an ESP32 for his BrMesh lights).

## Watch-outs

- **No bare plugin imports.** The web app is no-build/ESM-in-browser — reach
  Capacitor plugins via `window.Capacitor.Plugins.X`, never
  `import ... from '@capacitor/x'` (breaks the browser). See the header comment
  in `notify.js`. The npm package only wires the NATIVE side via `cap sync`.
- **Gate everything** through `hasCapability()` / `canX()` so web stays a no-op.
- **`git status` hygiene:** `node_modules/`, `www/`, and Android build outputs
  are gitignored; the `android/` project IS committed. Don't commit the
  playwright symlinks (they're under gitignored node_modules).
- Repo is **public** — never put the keystore/secrets in the repo or CI logs
  (the keystore is generated in Cloud Shell and stored as GitHub secrets).
- Don't surface parked/dead/Far-tier/§12 items in status recaps (CLAUDE.md).
