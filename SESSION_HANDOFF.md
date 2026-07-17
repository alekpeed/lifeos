# Session handoff — Life OS native rebuild (2026-07-16 → next window)

Read this first, then `NATIVE_REBUILD_HANDOFF.md` for the technical detail. This is
the "where we are / what's next" note.

## 0. Protocol — do this before any non-trivial work

- **Model/effort:** baseline is **claude-sonnet-5 + medium**. This session ran on
  **claude-opus-4-8 by Alek's explicit approval.** If you resume on a different
  model/effort, **flag it and get confirmation before executing** — a
  compaction/auto-resume does NOT override this. Re-verify each window.
- **Branch:** all work on **`claude/lifeos-dev-setup-dpipr6`**. Commit + push there.
  Commits are signed; end messages with the `Co-Authored-By: Claude Opus 4.8` +
  `Claude-Session:` trailers. Never put the model id in repo artifacts. Don't open
  a PR unless asked.
- **CI is the only verifier.** `build-native.yml`: the **Android APK job = the
  compile proof** (commonMain), the **Windows .msi job packages** (also compiles
  everything). Push, then confirm both green. Many features are **device-only** at
  runtime (see caveats) — CI proves compile, not behavior.
- MCP `actions_list` output often exceeds the token cap → it's saved to a file;
  `jq` the file for `.workflow_runs[]` status instead of re-reading.

## 1. What shipped this session (all CI-green on the branch)

- **Digital-assistant role** — real `VoiceInteractionService` (`androidMain/.../assist/`)
  so Life OS is selectable as the phone assistant; assist gesture → quick-capture.
- **Voice engine on Vosk** — offline wake word (`WakeWordService`, editable phrase
  default "hey life"), **only-my-voice** speaker ID (`VoiceId`/`VoiceEnroller`),
  models download on first enable (`VoskModels`). Settings has phrase + enroll UI.
- **`net/Http.kt`** — shared dependency-free GET/POST used by all integrations.
- **External integrations** (see `NATIVE_REBUILD_HANDOFF.md` §External integrations):
  - Keyless, in a real **Tools** screen: **Weather** (Open-Meteo), **Currency**
    (open.er-api.com), **Markets** (CoinGecko crypto + Stooq DJIA).
  - **Telegram** send-only (bot token + chat id in Settings).
  - **Multi-provider AI** — Ask + Assistant route through **Claude / OpenAI /
    Gemini**, picked in Settings, per-provider key+model (`AiClient` dispatches).
- **Data layer + cross-device sync** (two steps, see the data-layer sections):
  - `sync/SyncMeta` + `SyncEngine` — every `Storage` write becomes a syncable
    record (timestamp + tombstone); deterministic last-write-wins. `Storage.remove`
    added. No screen changes.
  - `sync/Supabase*` — email/password auth (GoTrue) + push/pull against the
    **reused** `sync_records` table under `store='kv'`. Settings **SYNC** section.
- **QR Sync** — generates a scannable QR of the payload (`encodeQr` zxing + Compose
  Canvas) and **scans** with the camera (`Native.scanQr`, zxing-android-embedded).

## 2. Device / account caveats to remember (can't be CI-verified)

- **Assistant:** reinstall the APK, then pick Life OS under Settings → Default apps
  → Digital assistant (picker only appears once the service is installed).
- **Vosk:** it's CPU software hotword spotting, **not** the reserved low-power chip
  (system-assistant only). Speaker-ID is a **filter, not a lock** (a recording can
  spoof it). ~40 MB + ~13 MB models download on first enable.
- **Supabase sync:** in the Supabase dashboard, **enable the Email auth provider**
  and (for frictionless login) **turn "Confirm email" off**. The native email
  account is a **different `auth.uid()`** than the web app's Google account, so
  native syncs across native installs (phone ↔ desktop, same email), **not** with
  the web app's data. The live round-trip needs a signed-in device to confirm.
  Project `ukqdbxxhxxafbcnkmskg`; only `sync_records` was read/reused — no schema
  was changed.
- **QR camera scan:** device-only.

## 3. What's OPEN

1. **Graphical interfaces — Alek's art (the one real open item).** Six modules ship
   a functional default awaiting art: **Orrery, Photos, Skill Trees, Knowledge
   Graph, Theme from Photo, Station Cat**. The seam is built:
   `Interfaces.register(moduleId, content)` and every page renders through
   `Interfaces.Render(id, default)`. Alek brings the asset; wire it in as a real
   background + hotspots (mobile-1 technique) or procedurally — **don't redesign
   his art.** See the per-module "what it represents" breakdown in the chat / can
   be re-derived from module intent.
2. **Optional polish (no decision needed, low priority):** swap the per-key text
   `Storage` backing for a structured DB (SQLDelight) — internal only, the
   record/sync API already sits above `Storage`; deepen the ~18 lighter list/note
   modules as desired.

## 4. Prototypes & side deliverables (NOT in the app yet)

- **Station Cat interactive prototype** — procedural, animated, responsive cat that
  **walks around**, comes when tapped, purrs when petted, naps when ignored,
  synth purr/chirp. Artifact: https://claude.ai/code/artifact/77deafb0-f5ff-4982-87a6-99dd8bf44ef8
  (source in the session scratchpad `cat.html`). **Not wired into the app.** Open
  choice: (a) wire it in as the Station Cat interface, reacting to real data;
  (b) push the procedural version further (poses, breeds, cursor-chase, wake-word
  reaction); (c) scope a **Rive** rig if Alek wants authored/near-photoreal.
  Honest ceiling established: photoreal needs authored assets (Rive/3D/video);
  procedural is the "lively from code" tier.
- **Music app spin-out** — the old Chords section, documented for a standalone app:
  `CHORDS_APP_HANDOFF.md` (the portable theory engine + synth source) and
  `MUSIC_APP_DESIGN_SPEC.md` (product/UX spec, incl. the **Riff Pads** feature:
  keyboard-as-instrument, one-key voicings/riffs with articulation, "packs").
  Open decisions there: name, platform (PWA vs Kotlin native), instrument focus.

## 5. Progress tracker (keep updated, same URL)

https://claude.ai/code/artifact/8b63ae9b-45c9-43dc-9489-64799d5e33f5 — complete
report, **92% / 10 of 11 workstreams**; only graphical interfaces open. Republish
the same scratchpad file path to keep the URL.

## 6. Standing rules (from CLAUDE.md — don't relearn the hard way)

- **Graphics:** Alek brings visual assets; wire them as-is (don't reinterpret). The
  cat/tree/cathedral were explicit "show me what you can do" exceptions he asked
  for — fine when he directly requests a mockup.
- **No brand names per interface** — generic numbered ids (`mobile-1`, `default`,
  `spatial-1`). Applies to every interface.
- **Don't surface parked / dead / far-tier / conditional-rearchitecture items** in
  status recaps unless Alek names them. (Parked incl. Plaid, two-way Telegram,
  Spotify, Garmin/Fitbit, OpenAI-was-parked-now-built-natively.)
- Backups are **manual only** (`scripts/make-backup.sh`) — run only when asked.
- Deploy (web PWA) is `main` via Pages; bump `service-worker.js` CACHE_VERSION per
  shipped web change (decimal scheme). **N/A to the native rebuild** — that's
  CI-built, no service worker.
- GitHub links: link to a directory/path, not the bare repo root.

## 7. Architecture quick-map

`native/composeApp/src/` — KMP (androidTarget + jvm desktop), Compose MP.
- `commonMain/.../Modules.kt` — the 42-module registry (`Module(id, …, content)`).
- `.../interfaces/Interfaces.kt` — the interface-swap seam.
- `.../Storage.kt` — per-key persistence (now sync-ready via `sync/`).
- `.../platform/Native.kt` — `expect object Native` (Android real, desktop no-op).
- `.../net/`, `.../ai/`, `.../integrations/`, `.../sync/`, `.../system/` — the new
  subsystems from this session.
- CI: `.github/workflows/build-native.yml`.
