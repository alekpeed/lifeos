# LifeOS — Native Android build runbook (Capacitor)

Everything here is **web-only / no local machine** unless a step explicitly
says "on your phone." The app is built in the cloud by GitHub Actions, exactly
like the Supabase and Telegram deploys. See `FUTURE_FEATURES.md` §13 for the
platform strategy this implements (Android native first; Windows/macOS later;
iOS stays the browser PWA).

App identity: **`com.alekpeed.lifeos`**, display name **LifeOS**.

---

## How it works (the 30-second mental model)

The LifeOS web app stays exactly as it is — no build step, served from the repo
root by GitHub Pages. Capacitor wraps *those same files* in a native Android
shell so the app can reach phone hardware a browser can't. The build pipeline:

1. `npm run assemble` copies the runtime web assets into a clean `www/`
   (`scripts/assemble-www.mjs`).
2. `npx cap sync android` copies `www/` into the Android project and wires in
   the native code for each Capacitor plugin.
3. Gradle builds the APK.

All three run **in GitHub Actions** (`.github/workflows/build-android.yml`), so
you never install Android Studio, Java, or the SDK.

---

## Part A — Get the app on your phone (no secrets, ~5 min)

A **debug APK** builds automatically on every push and installs fine for your
own use. No keystore needed.

1. GitHub → **Actions** tab → **Build Android APK** → open the newest run.
2. Scroll to **Artifacts** at the bottom → download **`lifeos-debug-apk`**
   (it's a zip containing `app-debug.apk`).
3. Get the APK onto your phone — easiest is to open the GitHub run **on the
   phone's browser** and download it there, or transfer the file however you like.
4. Tap the APK. Android will say the source isn't allowed yet → **Settings →
   "Allow from this source"** → back → **Install**. (You only do this once.)
5. Open **LifeOS**. It's the full app, now running natively.

That's the whole critical path. Everything below is optional or for testing.

> **Debug vs. release:** a debug APK is perfect for testing every feature. You
> only need a *signed release* build (Part B) for a clean, updatable install you
> could keep long-term or share. Both are real, installable apps.

---

## Part B — Signed release build (optional, ~5 min, no local machine)

You only need this when you want a proper, updatable release APK. The one secret
the build needs is a **signing keystore**. Generate it once in a browser
terminal — nothing sensitive ever touches the repo.

### 1. Make the keystore (Google Cloud Shell — free, in-browser)

Open **https://shell.cloud.google.com** (sign in with any Google account; it's
a free Linux terminal with Java preinstalled). Pick a password and run:

```bash
KSPASS='choose-a-strong-password-here'

keytool -genkeypair -v \
  -keystore lifeos-release.keystore \
  -alias lifeos \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$KSPASS" -keypass "$KSPASS" \
  -dname "CN=LifeOS, O=LifeOS, C=US"

# Print the base64 blob to paste into the GitHub secret:
base64 -w0 lifeos-release.keystore ; echo
```

Then **download the keystore file to keep it safe** (Cloud Shell → ⋮ menu →
Download → `lifeos-release.keystore`). ⚠️ **Back this file + password up
somewhere permanent.** Android requires the *same* key to sign every future
update; lose it and you can't update an installed app (you'd have to uninstall
and reinstall fresh).

### 2. Add the 4 GitHub secrets

GitHub → **Settings → Secrets and variables → Actions → New repository secret**
(same place as the VAPID/Telegram secrets). Add:

| Secret name | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the long base64 blob the command printed |
| `ANDROID_KEYSTORE_PASSWORD` | the password you chose (`$KSPASS`) |
| `ANDROID_KEY_ALIAS` | `lifeos` |
| `ANDROID_KEY_PASSWORD` | the same password |

### 3. Build it

GitHub → **Actions → Build Android APK → Run workflow** → tick **"Also build a
signed release APK"** → **Run**. When it finishes, download the
**`lifeos-release-apk`** artifact and install as in Part A.

(If the secrets aren't present, the release job skips cleanly instead of
failing — the debug build always still runs.)

---

## What's built vs. what's staged

### ✅ Built and verified (compiles in CI; behavior is yours to test on-device)

- **Foundation** — Capacitor Android project wrapping the web app; cloud build
  pipeline producing an installable APK. *Verified: debug APK builds in CI.*
- **Capability layer** (`js/native/capabilities.js`) — every native feature
  gates through this, so the same code runs as a plain browser PWA (iOS/web),
  hiding native-only features instead of breaking. *Verified across
  web/Android/iOS simulations.*
- **Device reminders** (local notifications) — on-device reminders for
  upcoming bills/tasks/documents, scheduled from your real due-soon data, fire
  offline with the app closed. *Web build verified clean; enable + fire is the
  device test below.*

### 🔜 Staged next (scaffolded / planned, not yet wired)

In rough priority order — each is its own future commit with a CI build check:

- **System share sheet (inbound)** — LifeOS in Android's Share menu so links/
  text/images from any app land in LifeOS. Needs an `ACTION_SEND` intent-filter
  in the Android manifest + a receive handler; genuinely needs on-device
  iteration. (`@capacitor/share` for *outbound* sharing is already installed.)
- **App shortcuts, keep-awake (cooking mode), NFC, geofencing, background
  geolocation** — each a discrete plugin + capability entry.
- **Wake word** ("Hey LifeOS") — the headline. Plan: a foreground-service
  plugin (Picovoice Porcupine or openWakeWord) holding the mic, custom wake
  phrase, feeding the existing Command module. **Needs your Picovoice access
  key** (free tier, sign up at console.picovoice.ai) — it'll be a device-local
  key like the AI keys, not a repo secret. Realistically needs a round or two
  of on-device tuning; this is the one to expect back-and-forth on.

The full native wishlist and what's ruled out lives in `FUTURE_FEATURES.md` §13.

---

## Morning test checklist

Install the APK (Part A), then:

- [ ] **App launches** and shows your real data (same account/sync as the web
      app — IndexedDB + Supabase carry over).
- [ ] Poke around a few modules; nothing crashes on open.
- [ ] **Device reminders:** Settings → scroll to **Device reminders** (this
      section only appears in the native app) → **Turn on device reminders** →
      accept the Android permission prompt. It should report how many upcoming
      reminders it scheduled. To see one actually fire, make a bill/task due
      tomorrow, reopen Settings (reschedules), and it'll notify at 9am on the
      due date — or use your phone's clock to test.

If anything's off, note which feature + what happened; that's the punch-list
for the next pass.

---

## Troubleshooting

- **A build failed** → Actions → open the run → the failed step's logs. The
  debug job needs no secrets, so a debug failure is a real build issue to fix.
- **Release job "skipped"** → that's expected until the 4 keystore secrets
  exist (Part B).
- **Service worker in the native app** — the app is already fully offline as an
  APK (all assets are bundled), so even if the SW behaves differently inside the
  native WebView, offline still works. Flag it if you see anything odd; it's a
  known thing to verify on-device.
