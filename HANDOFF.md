# Life OS — session handoff

Paste the block below into a fresh session. Everything above the line is context
for whoever is reading this file directly; the block itself is what a new session
needs. **Keep this file current as status changes.**

Last updated: 2026-08-29.

---

## Where things stand

The native app (Kotlin + Compose Multiplatform, all under `native/`) is the
product. **40 modules across 8 domains, all built and merged.** Every scheduled
item in `REDESIGN_DECISIONS.md` — §2, §4, §5, §7, §11, §12, the §10 deletion work
order and the whole schema layer — is done. What remains is listed under "Open"
below, and it is mostly decisions and credentials rather than code.

## The block to paste

> You are continuing work on **Life OS**, a local-first personal life-management
> app: Kotlin + Compose Multiplatform, one codebase for Android, Windows and
> Linux, all under `native/`. Read `CLAUDE.md` first — it carries standing rules
> that override default behaviour. Then `REDESIGN_DECISIONS.md` for the source of
> truth on scope, then `ARCHITECTURE.md` / `PROJECT_SPEC.md` / `FEATURE_LIST.md`.
>
> **Before writing any code, report where things actually stand** — read the
> registry and the docs rather than trusting a summary, including this one.
>
> Ground rules that have cost time when forgotten:
>
> - **There is no `gradlew` wrapper.** Invoke `gradle` directly, always from
>   `/home/user/lifeos/native`, always with
>   `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`.
>   Running it from a source subdirectory produces a fast, confusing
>   "BUILD FAILED in ~600ms".
> - **The gate is** `gradle assembleDebug compileKotlinDesktop desktopTest`.
>   All three must pass. `desktopTest` is the only target that runs shared logic
>   without a device — currently 342 tests.
> - **Ship it when it's green.** Commit, push the branch, fast-forward `main`,
>   push `main`, then say what landed. Do not ask permission. Alek's words:
>   "Anytime something finishes green, push and merge it automatically."
> - **Never pitch the web build.** It was deleted on 2026-08-22 and the decision
>   is not open for relitigation. To see how a module behaved in the web app,
>   read it out of git history.
> - **Don't design or generate graphics.** Alek supplies visual assets; the job
>   is wiring them up as real artwork with real hotspots, never reinterpreting a
>   mockup as fresh CSS/SVG.
> - **Don't surface parked, dead or far-tier items** in status recaps. See
>   `CLAUDE.md` for exactly which suppression rule applies to which list.

## Layout and conventions

- **Working branch this session: `command-into-ask`**, fast-forward merged into
  `main` after every green gate. A fresh session may be assigned a different
  branch name; use whatever it is assigned, keep merging to `main`.
- **Source sets:** `commonMain` (platform-agnostic — no `java.*`), `androidMain`,
  `desktopMain`, and **`jvmShared`**, which both platform sets depend on. That
  last one exists because Android and desktop are both JVM: code needing `java.*`
  but otherwise identical lives there instead of being copied twice.
- **`Storage`** is a flat key→text store, one file per key, one JSON blob per
  module. Keys beginning `__` are reserved: never synced, never in the mutation
  log, never in a backup.
- **`History`** hooks `Storage.write` and diffs every write. That is why every
  change is undoable and every deletion recoverable for 30 days.
- **kotlinx.serialization** runs with `ignoreUnknownKeys = true` and
  `encodeDefaults = true` — **every new field needs a default**, or old blobs
  fail to parse.
- **Builds** come from the `build-native.yml` GitHub Actions workflow: an Android
  APK, a Windows `.msi`, a Linux `.deb`. Always hand Alek the direct artifact
  link, never the file:
  `github.com/alekpeed/lifeos/actions/runs/<run_id>/artifacts/<artifact_id>`.

## Decisions worth knowing

- **Desktop is the endpoint** (resolved 2026-08-29). Desktop is the main surface
  where everything is ultimately controlled from; the phone is the **capture
  device**, and the split follows hardware, not product tiering — camera and
  microphone work (scanning, photos, dictation, wake word, geofences) is
  phone-only, everything else belongs on desktop in full. **A desktop feature gap
  is now a defect, not a tier.**
- **Capability flags are computed, never asserted.** `supportsNotifications` and
  `supportsPdfExport` on desktop are derived from what actually works, so a
  headless JVM reports false and the alarm paths stay dormant in tests. A button
  that posts a notification nobody receives is worse than no button.
- **The vault is one opt-in module, not app-wide encryption.** Server-side
  digests and zero-knowledge storage cannot both hold for the same records.

## Open

**Blocked on Alek:**
1. Regenerate `SUPABASE_ACCESS_TOKEN` — both deploy workflows fail on it, which
   is why `telegram-digest` and `send-fcm` have never deployed.
2. Set `TELEGRAM_BOT_TOKEN` and `TELEGRAM_WEBHOOK_SECRET` (both appear unset).
3. Firebase project, or leave FCM dormant. `fcm_tokens` / `fcm_sent` exist and
   are ready either way.

**To verify on device:**
4. Reboot alarm test — reminder at +1h, restart, don't open the app.
5. Attachment sync has never been exercised; `storage.objects` is empty.

**Decisions / cleanup:**
6. Supabase security advisor: four `SECURITY DEFINER` functions callable by the
   anon role over REST (`handle_new_user` first), `pg_net` in the public schema,
   leaked-password protection off.
7. Two stale remote branches from the deleted interfaces: `nexus-generated-home`,
   `nexus-live-status`.

**Alek's own:** the Home Assistant hub (plus ESP32 bridge and remote-access
choice), and the graphical interface he is currently designing.

**Unscheduled (§13.3):** Garmin/Fitbit ingestion; personal local API + plugin
SDK; autonomous chief-of-staff; trained ML pattern engine.

## The 40 modules

| Domain | Modules |
|---|---|
| Operations | Today · Daily Paper · Tasks · Projects · Briefing · Calendar |
| Archive | Documents · Links · Books · Photos · Collections · Time Capsules · Milestones |
| Logistics | Places · Home · Quartermaster · Travel |
| Discovery | Education · Skill Trees · Ideas · Rabbit Holes · The Almanac |
| Management | Habits · Health · Recipes · Finance |
| Intelligence | Ask · AI Assistant · Knowledge Graph · Entropy · Time Machine |
| People | Contacts · Sharebox |
| System | Search · Tools · History · Tags · QR Sync · Vault · Settings |

The home screen is header + search + these eight domains as an accordion — one
open at a time. `interfaces/Interfaces.kt` is the registry a graphical interface
attaches to; every module renders through `Interfaces.Render` so a new interface
stays attachable without touching module logic.

## Traps this session actually hit

- **A batched patch script that asserts and writes only at the end silently
  discards earlier edits when one assertion fails.** This caused a whole batch of
  doc edits to be reported as landed when none were. Verify file contents after
  patching; use a non-aborting substitution that reports misses and continues.
- **Piping `git push` into `tail` swallows its exit status**, so a retry loop
  breaks on the first attempt and a failed push looks like a success. Capture the
  status directly. Pushes to `main` returned GitHub 500s twice and succeeded on
  retry.
- **A deletion work order only deletes what it names.** The Operations module
  survived the interface purge because it had been added under `operations/`
  rather than `interfaces/`. When cutting a category of thing, search for
  siblings by shape, not by path.
- **Nesting a `verticalScroll` inside the home `LazyColumn` throws at runtime.**
- **Guard tests should be checked by sabotage.** Several invariants here were
  verified by deliberately breaking the code and confirming the tests failed.
