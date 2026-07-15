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
  Command / New Task / New Idea. **NOTE:** shipped this session with two CI
  bugs that had to be fixed after the fact — a `--` inside an XML comment in
  `AndroidManifest.xml` (manifest merger can't parse it), and inline string
  literals on `shortcutShortLabel`/`shortcutLongLabel` (AAPT requires
  `@string/` refs; labels now live in `res/values/strings.xml`). Both green now.
- **Feature 6 — actionable notification buttons** (`notify.js` +
  `native-boot.js`): due-item reminders carry action buttons — Bill → Mark
  paid / Snooze, Task → Mark done / Snooze, Assignment → Mark done, Document →
  Create renew task. `registerNotificationActions()` + a
  `localNotificationActionPerformed` listener apply the same data-layer
  mutations the Briefing uses, then resync. Snooze re-arms the reminder 24h out.
- **Feature 7 — pinned "next up" ticker** (`notify.js` `showNextUp`/`clearNextUp`
  + `native-boot.js` `refreshNextUp`): opt-in persistent notification showing
  the top `getBriefing()` item. Toggle in Settings → Device reminders
  (`nextUpTickerEnabled`, off by default).
- **Feature 8 — inbound system share sheet** (`AndroidManifest.xml` ACTION_SEND
  intent-filter + `MainActivity.java` + `native-boot.js` `initShareReceiver`):
  LifeOS in Android's Share menu. `MainActivity` captures the ACTION_SEND
  intent (cold + warm) and hands `{text, subject}` to JS via a `lifeosshared`
  window event (JSONObject-encoded, injection-safe); the web side files a URL
  into Links, plain text into Ideas. **No new npm dep** (kept `npm ci`/lockfile
  untouched — native + JS only). **On-device receive behavior, esp. the
  cold-start timing, is the thing to iterate next** (web side re-checks the
  global a few times after boot as a backstop).
- **Feature 9 — clipboard catcher** (`native-boot.js` `initClipboardCatcher`):
  on app foreground (App `resume`/`appStateChange`), reads the clipboard and
  offers to file a URL → Links / text → Ideas (reuses the share routing). A
  self-contained confirm banner (`showCaptureBanner`); never re-offers the same
  contents. No new plugin. Android only lets a foregrounded, focused app read
  the clipboard — that timing is the device-iteration point.
- **Feature 10 — one-tap phone-contacts import** (`js/native/contacts.js` +
  Settings → Phone contacts): `@capacitor-community/contacts@^6`, reached via
  `window.Capacitor.Plugins.Contacts`. Maps to LifeOS's Contact shape, tags
  `imported`, dedups by name. First commit to add a plugin this session — CI's
  `npm ci` + `cap sync` compiled it green, so the plugin path is proven.
- **Feature 11 — charging-cable evening ritual** (`native-boot.js`
  `maybeRunChargingRitual` + Settings → Evening ritual): `@capacitor/device@^6`
  (`window.Capacitor.Plugins.Device.getBatteryInfo`) → when charging in the
  evening with the app open, offers to read the Briefing aloud. Opt-in
  (`chargingRitualEnabled`), once/day. New `battery` capability.

Plugins installed: `@capacitor/core`, `/android`, `/cli`, `/local-notifications`,
`/share`, `/app`, `/device`, `@capacitor-community/keep-awake`,
`/text-to-speech`, `/contacts`. **Adding a plugin works from the cloud session:**
`npm install <pkg>@^6` (pin the Capacitor-6 major) updates `package.json` +
`package-lock.json`, and CI's `npm ci` + `cap sync` compiles it. Re-create the
Playwright symlinks after any `npm install` (it prunes them). Reach every plugin
via `window.Capacitor.Plugins.X` — never a bare import (buildless web app).

Service worker: `CACHE_VERSION` now **`lifeos-v1.48`**; all `js/native/*` files
are in APP_SHELL (add new ones — `contacts.js` was added this session). (Bump
the hundredths per shipped change per CLAUDE.md.)

## CI STATUS (all green as of the 2026-07-15 continuation session)

- Runs #1 (foundation), #2 (local-notifications), #4 (share/keep-awake/
  read-aloud): **SUCCESS**.
- Run #5 (app shortcuts, 7eca7fc): **FAILED** at handoff and was fixed the next
  session — two bugs (XML-comment `--` in the manifest, then inline shortcut
  labels needing `@string/` refs). Fix commits `d33cb48` + `a502c5c` are green.
- Features 6–8 (actionable notifications, next-up ticker, inbound share sheet):
  commits `059be31`, `807a75b`, `bd13979` — all **SUCCESS**.
- Features 9–11 (clipboard catcher, contacts import, charging ritual): commits
  `e63a26c`, `b137477`, `d778beb` — all **SUCCESS**. (F11's first run hit a
  transient `android-actions/setup-android` SDK-download flake — "Error on
  ZipFile unknown archive" in the Set up Android SDK step, before any code ran;
  a `rerun_failed_jobs` went green. If a build fails in SDK setup, just re-run.)
- **`dev` was fast-forwarded to `main`** after the fixes and after each feature
  batch. main == dev.
- Compact CI check (the full run list is huge and blows context): use
  `mcp__github__actions_list` `list_workflow_runs` and `jq` the saved file for
  `id`/`head_sha`/`status`/`conclusion`, then `list_workflow_jobs` +
  `get_job_logs` (failed_only) on a failure. Don't dump the raw run list.

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

1. **Alek's device pass:** install the debug APK (Actions → newest green run →
   `lifeos-debug-apk` artifact → sideload) and test the now-11 features. Expect a
   punch-list — some device behavior will need fixes. `CAPACITOR_BUILD.md` has
   his full runbook + test checklist. **Highest-value device tests this round:**
   (a) **inbound share sheet** — share a link from Chrome/YouTube → lands in
   Links, plain text → Ideas (cold-start timing is the known risk); (b)
   **clipboard catcher** — copy a link elsewhere, return to LifeOS, expect the
   "File it" banner (Android only reads the clipboard for a focused foreground
   app); (c) **contacts import** (Settings → Phone contacts) — permission +
   dedup; (d) **charging ritual** (Settings → Evening ritual, opt-in) — plug in
   in the evening with the app open. Actionable notification buttons + next-up
   ticker (Settings → Device reminders) are the earlier ones still to poke.
2. **Wake word** (the headline, still unbuilt): foreground-service plugin
   (Picovoice Porcupine or openWakeWord), custom wake phrase, feed the existing
   **Command** module (`parseCommand` in `api.js`). **Needs Alek's Picovoice
   access key** (free, console.picovoice.ai) — device-local like the AI keys,
   NOT a repo secret. Expect real on-device iteration.
3. **More §13 fast wins** (DONE so far: share-sheet inbound, actionable
   notifications, next-up ticker, clipboard catcher, contacts import, charging
   ritual): geofencing, NFC, offline STT, home widgets, biometric gate,
   screenshot reflex, auto-sorting file inbox, full-screen alarm alerts. Full
   curated list + ruled-out items in `FUTURE_FEATURES.md` §13.
4. **Later:** Windows `.exe` / macOS app; smart-home "Home" module (§14, blocked
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
