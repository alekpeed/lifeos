# Session handoff — Life OS native rebuild (→ next window)

Read this first. `NATIVE_REBUILD_HANDOFF.md` has technical detail, but **trust this
file's honest accounting over any older "42/42 / 92% done" claims in other docs —
those were wrong (see §1).**

## 0. Protocol — before any non-trivial work

- **Model/effort:** baseline is **claude-sonnet-5 + medium**. This session ran on
  **claude-opus-4-8 by Alek's explicit approval.** On a different model/effort,
  flag it and get confirmation first. A compaction/auto-resume does not override
  this. Re-verify each window.
- **Branch:** `claude/lifeos-dev-setup-dpipr6`. Commit + push there. Signed commits;
  end messages with the `Co-Authored-By: Claude Opus 4.8` + `Claude-Session:`
  trailers. No model id in repo artifacts. No PR unless asked.
- **CI is the only compile check** (`build-native.yml`: Android APK job = compile
  proof; Windows job packages). Push, confirm both green. **CI green ≠ done.**

## 1. READ THIS — the honest state (why the last stretch went sideways)

Alek installed the APK and found **most modules are empty "add an item" stubs.**
The rebuild recreated 42 module *screens that save text* and reported that as
"42/42 functional / 92% done." That was misleading: **"functional" only meant the
screen persisted a line of text, not that it had its real features.** About half
the app is stubs missing the behavior the existing app has.

**Honest split: ~20 / 42 modules have real features; ~22 are still stubs.**
Honest status artifact (the corrected one):
https://claude.ai/code/artifact/8b63ae9b-45c9-43dc-9489-64799d5e33f5

**Two hard rules, non-negotiable (also in CLAUDE.md top):**
1. **Never pitch / offer / "note the trade of" the web app.** Native is the
   product. `js/` is a reference to port FROM, nothing else. Do not frame native
   as a lift "versus" web.
2. **Never call a module done until its real behavior is there and Alek has seen
   it work on device.** "Compiles green" is not "done." Say "compiled; verify on
   device," never "done/works."

## 2. THE JOB: port each stub's real features from `js/` into Compose

This is the actual remaining work (task #35). For each stub module:

1. Read its web implementation: `js/interfaces/default/views/<name>.js` (real
   feature source), and `js/data/schema.js` for its data shape.
2. Build a real native Compose screen with the same behavior; persist via
   `Storage`. For anything structured, serialize with kotlinx-serialization JSON
   (see `tasks/Task.kt` for the pattern) and **migrate old data** so Alek's
   existing entries survive.
3. Point the module's entry in `Modules.kt` at the new screen (replace the
   `SimpleListScreen` / `NoteListScreen` / `StatusListScreen` stub).
4. If it changes a shared model, fix every reader (e.g. the Tasks model change
   touched `TodayScreen` + `BriefingScreen` — grep before you push).
5. Push, confirm CI green, tell Alek to verify on device. Don't over-claim.

**Stubs still to port (22):** Education, Places, Links, Recipes, Documents,
Quartermaster, Packing Lists, Sharebox, Books, Photos, Milestones, Museum, Time
Capsules, Collections, Ghost Days, Rabbit Holes, Daily Paper, Orrery, Skill Trees,
Knowledge Graph, Time Machine, Theme from Photo.
Web view sizes for scope: education 495, places 492, recipes 367, milestones 299,
knowledge 275, quartermaster 214, museum 194, documents 186, packing 183, orrery
173, timemachine 168, photos 165, themefromphoto 154, links 145, collections 131,
rabbitholes 123, ghostdays 103, timecapsules 85, skilltree 71, entropy 77 (done).
Sharebox (584) + paper (448) are backend-dependent — scope carefully.

**Also honest:** some "real" modules are lighter than web — **Finance** (native =
category ledger; web = bills / statement import / crypto, 549 lines) and **Health**
are the amber ones. Fill them out too.

## 3. Done just now (compiled green on CI; Alek verifies on device)

- **Settings scroll bug fixed** — root Column had no `verticalScroll`, lower
  sections (AI/Telegram/Sync/Storage) were unreachable.
- **Entropy rebuilt** — real neglect dashboard (days-since-touched per area, from
  the sync layer's per-key timestamps), sorted most-neglected first. Not a box.
  (`insight/EntropyScreen.kt`.)
- **Tasks rebuilt** — full model in `tasks/Task.kt` (JSON-serialized, migrates the
  old tab format): status (not_started/in_progress/waiting/done), priority, due,
  project, tags, notes, waiting-on, subtask checklist, recurrence (marking a
  repeating task done spawns the next), snooze. Screen `tasks/TasksScreen.kt`:
  row meta chips + tap-to-expand editor. Updated `TodayScreen`/`BriefingScreen`
  to `dueDate()`/status. **Last pushed commit; run 29549557551 Android job was
  green.**

## 4. What IS real (platform — the reason native exists)

Native shell + CI; OS capabilities (assistant role, notifications, TTS, share,
clipboard, NFC, geofence, contacts, shortcuts, alarms); **AI** (Ask + Assistant via
Claude/OpenAI/Gemini); **Vosk voice** (wake word + only-my-voice); **Supabase
cross-device sync** (email auth, push/pull vs reused `sync_records`, `store='kv'`);
**QR** (generate + camera scan). Detail + device/account caveats (assistant picker,
Supabase email-provider toggle, model downloads) are in `NATIVE_REBUILD_HANDOFF.md`.

## 5. Prototypes & side deliverables (NOT in the app)

- **Station Cat** procedural walking/petting cat:
  https://claude.ai/code/artifact/77deafb0-f5ff-4982-87a6-99dd8bf44ef8 (source
  `cat.html` in scratchpad). Not wired in.
- **Music app spin-out:** `CHORDS_APP_HANDOFF.md` (engine) + `MUSIC_APP_DESIGN_SPEC.md`
  (design, incl. Riff Pads).

## 6. Standing rules (CLAUDE.md)

- **NEVER suggest the web app** (see §1). Native is the product.
- Alek brings visual assets; wire as-is (cat/tree were explicit "show me" asks).
- No brand names per interface (generic ids: `default`, `spatial-1`, `mobile-1`).
- Don't surface parked/dead/far-tier/rearchitecture items unless Alek names them.
- Backups manual only (`scripts/make-backup.sh`). GitHub links → a directory/path.
- Latest APK/MSI download: the newest green `build-native.yml` run's Artifacts.

## 7. Architecture quick-map

`native/composeApp/src/commonMain/.../` — `Modules.kt` (42-module registry),
`interfaces/Interfaces.kt` (interface-swap seam), `Storage.kt` (per-key persistence;
`sync/` adds timestamps+tombstones), `platform/Native.kt` (expect/actual),
`net/ ai/ integrations/ sync/ system/ tasks/ insight/ core/ ...` subsystems.
CI: `.github/workflows/build-native.yml`.
