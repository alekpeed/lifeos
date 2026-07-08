# PolyglotAI — MVP Technical Implementation Plan

> Scope: **MVP only**, per the final spec (*PolyglotAI — Adult Real-World Language Learning
> App Specification*, §23). Adult, personal, local-first learning app with one language pack
> (Brazilian Portuguese). This document is a build blueprint — no application code beyond the
> scaffold is written until each build-sequence step is reached. Future layers (Teacher,
> Commerce, Plugins, Cloud Sync, Android) are designed for but **not built**; they only
> influence where we put seams.

---

## 0. What the MVP must deliver

From the final spec §23, the MVP is:

- Ubuntu desktop app (local, single user) — see §0.1 on platform strategy
- Local profile
- Brazilian Portuguese seed language pack + language-pack loader
- Vocabulary system
- Grammar system
- Slang / register system
- AI tutor
- Spaced repetition
- Conversation mode
- Basic pronunciation recording
- Progress dashboard
- Settings

**MVP content minimum (§23, = §10.1 "MVP Seed Pack Target," the official Tier 1):**

| Category | Count |
|---|---|
| Vocabulary items | 300 |
| Core phrases | 75 |
| Grammar points | 25 |
| Dialogues | 25 |
| Slang/register items | 20 |
| Pronunciation drills | 20 |
| Listening exercises | 10 |
| Writing prompts | 10 |
| Assessments/checkpoints | 10 |
| Roleplay scenarios | 5 |

§10.2–§10.6 (A1 through long-term, up to 8,000–12,000 vocab) are **post-MVP roadmap targets**
the pack grows into over time — not additional MVP work.

Everything else in the spec (Teacher, Commerce, Plugins, multi-user sync, phoneme-level
pronunciation scoring, TTS, Android/web apps) is **explicitly out of scope** for the MVP but
must not be architecturally blocked. The guiding constraint from §26: *modular, local-first,
versioned, flaggable, provider-abstracted, upgradeable for years.*

### 0.1 Platform strategy: Windows first

The spec (§2, §26) names Ubuntu desktop as its long-term target, with Android (Phase 6)
reusing the same core packages later. The developer's actual machine is Windows, so **Windows
is the primary MVP platform** — that's where the app is built, run, and validated day to day.
This doesn't fight the spec's architecture: **Tauri produces Windows, Ubuntu, and macOS builds
from one codebase**, there is no Ubuntu-specific code required, so nothing about the spec's
long-term Ubuntu/Android direction is blocked by starting on Windows.

- **Primary target: Windows (`.msi`).** This is what gets built, run, and manually tested
  throughout the MVP.
- **CI build matrix also builds Ubuntu (`.deb`/`AppImage`) and macOS** from the same source
  from day one, so the spec's Ubuntu requirement stays continuously green even though it isn't
  the hands-on-testing platform right now. When an Ubuntu machine is available later, it's the
  same repo, same commands — no rework.
- **Day-to-day local dev/testing happens on the developer's Windows machine** via
  `pnpm tauri dev` — code is written/typechecked/unit-tested here in a Linux sandbox, but
  visual/manual verification (does the window open, does recording work) happens locally on
  Windows.
- **No OS-conditional application logic** — Tauri's path/FS/dialog APIs abstract OS
  differences; the one thing to watch is audio capture (MediaRecorder/WebView2 vs WebKitGTK),
  which gets manually verified on Windows now and Ubuntu later.

---

## 1. Recommended Stack

Aligns with the spec's own recommendation (§2) with concrete version-level choices.

| Layer | Choice | Rationale |
|---|---|---|
| Shell / packaging | **Tauri 2** | Native desktop, small binary, Rust core, good local-FS + SQLite story, cross-platform (macOS/Win/Linux). Spec-recommended. |
| UI | **React 18 + TypeScript 5 (strict)** | Spec-recommended; large ecosystem; typed data models are critical given versioned schemas. |
| Build tool | **Vite** | Default for Tauri + React; fast HMR. |
| Styling | **Tailwind CSS + CSS variables** | Fast iteration; CSS variables give us the theme-system seam required by §8 cheaply. |
| State | **Zustand** (UI/session) + **TanStack Query** (async/DB reads) | Lightweight; avoids Redux boilerplate. Query gives caching/invalidation around the data layer. |
| Routing | **React Router** | Screen set follows the personal-mode feature list (§5.1) and onboarding flow (§6). |
| Local DB | **SQLite** via **`@tauri-apps/plugin-sql`** (SQLx under the hood) | Spec-recommended; migrations supported; runs in the Rust side, not the webview. |
| Migrations | **SQLx migrations** (Rust side) + app-level migration runner for JSON/pack data | Two migration concerns: DB schema and pack/profile JSON. Both versioned per §8's migration-system requirement. |
| Validation | **Zod** (TS) mirrored by **JSON Schema** files | Zod validates at runtime in the app; JSON Schema files are the canonical published contract for pack authors and future plugin SDK. |
| AI provider | **OpenAI adapter** behind an `AIProvider` interface | Spec §2/§8 — provider must be swappable. Only OpenAI implemented in MVP. |
| Audio (MVP) | Browser **MediaRecorder** for capture; STT via an `SpeechProvider` adapter (OpenAI Whisper API impl) | Spec §17.1 "record + STT comparison + playback + basic score." No phoneme analysis in MVP. |
| SRS | **FSRS** (via `ts-fsrs`) with an internal `Scheduler` interface; SM-2 as documented fallback | Spec §16 prefers FSRS. Interface lets us swap. |
| Testing | **Vitest** (unit/integration), **Playwright** (E2E on the Tauri webview), **`cargo test`** (Rust migration/DB) | Covers TS logic, DB layer, and full-app flows. |
| Lint/format | **ESLint + Prettier + `cargo fmt`/`clippy`** | — |
| Monorepo mgmt | **pnpm workspaces** | Clean separation of `app`, `core`, `packs`, `schemas`. |

**Deliberate MVP exclusions:** no backend server, no Postgres/Redis, no TTS provider (playback
is of the user's own recording + optional cached AI audio later), no pronunciation *scoring*
model beyond STT-transcript comparison, no cloud sync, no auth beyond a local profile.

---

## 2. Repository Structure

pnpm monorepo, following the spec's own recommended layout (§2): `apps/` for platform shells,
`packages/` for shared, platform-free logic. The `packages/` boundary is what lets Android
(Phase 6) reuse `core-learning`, `spaced-repetition`, `ai-orchestration`, and
`language-pack-sdk` verbatim later without a rewrite.

```text
polyglotai/
  package.json                 # pnpm workspace root
  pnpm-workspace.yaml
  tsconfig.base.json
  README.md
  docs/
    mvp-implementation-plan.md # this file
    adr/                       # architecture decision records
  apps/
    desktop-tauri/             # Tauri + React — the only MVP app; PRIMARY build target = Windows
      src-tauri/                # Rust: window, SQLite plugin, FS access, migrations dir
        migrations/              # SQLx .sql migration files
        src/
      src/                       # React
        screens/                 # onboarding, dashboard, lesson, review, tutor,
                                 #   conversation, pronunciation, vocab, grammar,
                                 #   slang, packs, settings
        components/
        stores/                  # Zustand
        hooks/
        theme/
      package.json
    android/                   # placeholder only — Phase 6, not built in MVP
      README.md                 # notes intended reuse of packages/*
    web/                       # placeholder only — future/optional, not built in MVP
      README.md
  packages/
    shared-types/              # @polyglotai/shared-types — cross-cutting TS types + Zod
                               #   schemas mirroring /schemas; register/severity enums; no logic
    language-pack-sdk/         # @polyglotai/language-pack-sdk — loader, validator, registry,
                               #   inheritance resolver, CLI entry point
      schemas/                  # canonical JSON Schemas (versioned) — the published contract
        manifest.schema.json
        vocabulary.schema.json
        phrase.schema.json
        grammar.schema.json
        slang-register.schema.json
        dialogue.schema.json
        pronunciation.schema.json
        lesson.schema.json
        assessment.schema.json
        roleplay.schema.json
        ai-prompts.schema.json
      src/
      bin/                       # `pack-validate` CLI
      tests/
    core-learning/              # @polyglotai/core-learning — pure TS, no React, no Tauri
      src/
        db/                      # DB access layer (repositories), migrations runner
        profile/                 # profile service (incl. onboarding/goal/dialect/real-speech level)
        vocabulary/              # vocabulary + phrase service
        grammar/                 # grammar service
        slang/                   # slang/register/profanity service
        dialogue/                # dialogue service
        learning/                # lesson sequencing (thin in MVP), progress tracking
        featureflags/            # flag registry + evaluation
        migrations/              # JSON/data migration framework (pack + profile)
        events/                  # lightweight event bus for cross-module signals
      tests/
    spaced-repetition/          # @polyglotai/spaced-repetition — FSRS scheduler,
                               #   Scheduler interface, SM-2 fallback
      src/
      tests/
    ai-orchestration/            # @polyglotai/ai-orchestration — AIProvider interface,
                               #   OpenAI adapter, prompt templates, orchestrator,
                               #   content policy, cost/context controls
      src/
        providers/openai/
      tests/
    pronunciation/               # @polyglotai/pronunciation — SpeechProvider (STT) interface,
                               #   OpenAI Whisper adapter, recording pipeline, basic scoring;
                               #   TTS adapter interface defined but unimplemented (post-MVP)
      src/
        speech/openai-whisper/
      tests/
  packs/
    pt-br/                     # the first language pack (data, not code)
      manifest.json
      metadata.json
      vocabulary/
      phrases/
      grammar/
      pronunciation/
      idioms/
      slang/
      profanity/
      dialogues/
      culture/
      lessons/
      assessments/
      ai-prompts/
      examples/
```

**Key boundary rules**
- `core-learning`, `spaced-repetition`, `ai-orchestration`, `language-pack-sdk`, and
  `shared-types` never import React or Tauri. They are pure TS libraries — testable in
  Node/Vitest, reusable later by Android or a server.
- `pronunciation` isolates the one MVP module with a genuine platform dependency (audio
  capture); its `SpeechProvider`/`TTSProvider` interfaces stay platform-agnostic even though
  the MVP capture implementation lives in the Tauri app.
- `apps/desktop-tauri` is the only package that knows about Tauri/React. It composes the
  `packages/*` above.
- `packs` and `language-pack-sdk/schemas` contain **no application code** — data + contracts.
  This is what makes packs plug-and-play (§9) and seeds the future plugin SDK.

---

## 3. Core Modules

Each maps to a spec responsibility and lives in the package named in §2. MVP builds all of
these; "future hooks" note where we leave a seam but stop.

| Module | Package | Responsibility (MVP) | Interface sketch | Future seam |
|---|---|---|---|---|
| **DB / Repositories** | `core-learning` | Typed CRUD over SQLite; one repository per entity; transactions | `VocabularyRepo`, `PhraseRepo`, `DialogueRepo`, `ReviewRepo`, `ProfileRepo`, … | Same repos back a sync engine later |
| **Pack Loader** | `language-pack-sdk` | Discover, validate, register, and index a pack from disk; resolve base→variant inheritance | `loadPack(path) → LoadedPack`; `PackRegistry` | Remote install, plugin manifests |
| **Pack Validator** | `language-pack-sdk` | Validate pack JSON against JSON Schema + semantic checks (dangling refs, duplicate ids, content-volume report) | `validatePack(dir) → Result<Report>` | Reused verbatim by plugin SDK |
| **Profile** | `core-learning` | Single local learner profile: level, goal, dialect focus, real-speech level, correction strictness, settings; onboarding flow (§6) | `ProfileService` | Multi-profile / accounts, teacher-managed students |
| **Vocabulary / Phrases** | `core-learning` | Query vocab + core-phrase items, mark known/weak, feed SRS | `VocabularyService` | — |
| **Grammar** | `core-learning` | Serve grammar rules + examples + drills; link to related vocab and review items | `GrammarService` | — |
| **Slang / Register** | `core-learning` | First-class slang/profanity items with register + severity + usage guidance; gated by feature flag + learner's real-speech level | `SlangService`, `RegisterLabel`, `Severity` | Regional/dating/professional packs |
| **Dialogue** | `core-learning` | Serve dialogues (transcript, translation, key vocab, grammar/slang notes) for dialogue-analysis lessons and roleplay | `DialogueService` | Audio playback via future TTS |
| **SRS Scheduler** | `spaced-repetition` | FSRS state machine over `ReviewItem`s; schedule next review; record results | `Scheduler` iface; `FsrsScheduler` impl | SM-2 impl; per-item-type tuning |
| **Learning Engine (thin)** | `core-learning` | Pick next lesson/review deck from progress; run the diagnostic + first study plan; MVP = simple rules, not adaptive | `LearningService.nextActivity()`, `runDiagnostic()` | Adaptive sequencing, CEFR estimate (Phase 5) |
| **AI Orchestration** | `ai-orchestration` | Build prompts from templates + learner context; call `AIProvider`; parse structured corrections; enforce content policy | `AIOrchestrator`, `AIProvider` iface | Local LLM, multi-provider routing, cost controls |
| **Speech (STT)** | `pronunciation` | Capture audio, transcribe via `SpeechProvider`, diff transcript vs target → basic score | `SpeechProvider` iface | Phoneme/stress/intonation scoring (Phase 4) |
| **Speech (TTS)** | `pronunciation` | Interface defined only — **not implemented in MVP** (§12 general voices are Phase 4 work; MVP has no required TTS feature) | `TTSProvider` iface (stub) | OpenAI/ElevenLabs TTS adapter, dialect voice selection |
| **Feature Flags** | `core-learning` | Central registry; runtime-toggleable; defaults per build | `flags.isEnabled(key)` | Remote config |
| **Migrations** | `core-learning` | DB schema (SQLx) + JSON data (pack/profile) migrations with backup + validation | `runMigrations()`, `migrateDoc(v→v)` | — |
| **Events** | `core-learning` | Decouple modules (e.g. "review completed" → dashboard refresh) | tiny typed emitter | Analytics sink |

**AI content policy (MVP, from §13):** the orchestrator injects a system-prompt clause that
*permits* academic/contextual explanation of vulgarity, slang, and taboo language for learning,
while *refusing* targeted harassment, threats, sexual exploitation, and instructions for
wrongdoing. This is a prompt-layer guardrail plus a lightweight output check — not a heavy
moderation pipeline. Adult-first is a design rule (§26), so we do not sanitize teaching content.
This exact behavior is spiked early (Milestone C, step 10) since it's a confirmed risk (§9,
risk 1) — the owner has accepted OpenAI as the MVP provider and wants this validated in week one.

---

## 4. SQLite Schema (MVP)

Design rules honored: every entity has `id`, `created_at`, `updated_at`, `schema_version`
(§21). IDs are app-generated UUIDv7 strings (sortable). Timestamps stored as ISO-8601 TEXT.
JSON-heavy, variable-shape content (usage guidance, examples, prompt templates) is stored as
`TEXT` JSON columns rather than over-normalized — packs are the source of truth, the DB is an
index + progress store.

**Two data classes:**
1. **Pack-derived content** (vocabulary, grammar, slang, lessons…) — loaded from a pack,
   re-loadable/rebuildable. Keyed by `(pack_id, item_key)`.
2. **User-owned state** (profile, review scheduling, conversation logs, progress) — the
   precious data; never lost on pack re-import.

```sql
-- ---------- meta ----------
CREATE TABLE schema_meta (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL          -- e.g. db_version, app_version
);

-- ---------- packs ----------
CREATE TABLE language_packs (
  id            TEXT PRIMARY KEY,          -- e.g. "pt-br"
  base_pack_id  TEXT REFERENCES language_packs(id),  -- inheritance (§9 regional variants)
  name          TEXT NOT NULL,
  language_code TEXT NOT NULL,             -- BCP-47, e.g. "pt-BR"
  version       TEXT NOT NULL,             -- pack semver
  schema_version INTEGER NOT NULL,
  manifest_json TEXT NOT NULL,             -- full manifest cached
  installed_at  TEXT NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);

-- ---------- learner ----------
CREATE TABLE learner_profiles (
  id            TEXT PRIMARY KEY,
  display_name  TEXT NOT NULL,
  active_pack_id TEXT REFERENCES language_packs(id),
  goal          TEXT,                      -- travel|conversation|fluency|tutoring|professional|
                                           --   dating_social|media_comprehension|custom (§6)
  target_dialect TEXT,                     -- e.g. "pt-BR-SP"
  real_speech_level TEXT NOT NULL DEFAULT 'informal', -- standard|informal|slang|profanity (§6 step 6)
                                           --   drives the default severity ceiling in app code;
                                           --   settings can override the numeric ceiling directly
  slang_severity_override INTEGER,         -- nullable 1..7; overrides real_speech_level's default mapping
  cefr_estimate TEXT,                      -- "A1".."C2", set by diagnostic (§6 step 8)
  correction_strictness TEXT NOT NULL DEFAULT 'balanced',
  settings_json TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);

-- ---------- content: vocabulary + core phrases (unified per §11 "word/phrase" field) ----------
CREATE TABLE vocabulary_items (
  id            TEXT PRIMARY KEY,
  pack_id       TEXT NOT NULL REFERENCES language_packs(id),
  item_key      TEXT NOT NULL,             -- stable key within pack
  entry_type    TEXT NOT NULL DEFAULT 'word', -- 'word' | 'phrase' | 'chunk' — distinguishes
                                           --   the "300 vocabulary items" vs "75 core phrases"
                                           --   content-volume categories (§10.1) within one table
  lemma         TEXT NOT NULL,             -- the word or phrase itself
  translation   TEXT NOT NULL,
  literal_meaning TEXT,                    -- §11: literal meaning
  natural_meaning TEXT,                    -- §11: natural meaning
  part_of_speech TEXT,
  frequency_rank INTEGER,                  -- §11: frequency rank if available
  ipa           TEXT,
  audio_text    TEXT,                      -- §11: text passed to future TTS for this item
  pronunciation_notes TEXT,
  register      TEXT,                      -- register label (§9)
  cefr          TEXT,
  tags_json     TEXT NOT NULL DEFAULT '[]',
  examples_json TEXT NOT NULL DEFAULT '[]',
  data_json     TEXT NOT NULL DEFAULT '{}',-- overflow for pack-specific fields
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);
-- Note: "review status" (§11) is intentionally NOT a stored column here — it's derived by
-- joining to review_items (below) so it can never go stale relative to actual SRS state.

-- ---------- content: grammar ----------
CREATE TABLE grammar_items (
  id            TEXT PRIMARY KEY,
  pack_id       TEXT NOT NULL REFERENCES language_packs(id),
  item_key      TEXT NOT NULL,
  title         TEXT NOT NULL,
  cefr          TEXT,
  explanation_md TEXT NOT NULL,
  examples_json TEXT NOT NULL DEFAULT '[]',
  common_errors_json TEXT NOT NULL DEFAULT '[]',
  drills_json   TEXT NOT NULL DEFAULT '[]', -- §11: drills
  related_vocabulary_json TEXT NOT NULL DEFAULT '[]', -- §11: related vocabulary (item_keys)
  data_json     TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);
-- Note: "related review items" (§11) is likewise derived via review_items.content_id joins,
-- not duplicated here.

-- ---------- content: dialogues (first-class per §11) ----------
CREATE TABLE dialogues (
  id            TEXT PRIMARY KEY,
  pack_id       TEXT NOT NULL REFERENCES language_packs(id),
  item_key      TEXT NOT NULL,
  scenario      TEXT NOT NULL,             -- e.g. "café", "job interview" — grounded in the
                                           --   roleplay/travel/nightlife/workplace tutor
                                           --   modes (§14)
  speakers_json TEXT NOT NULL DEFAULT '[]',-- [{ id, name, role }]
  target_level  TEXT,                      -- CEFR
  region_dialect TEXT,                     -- e.g. "pt-BR-RJ"
  formality     TEXT,                      -- 'formal' | 'informal'
  transcript_json TEXT NOT NULL,           -- [{ speakerId, text }]
  translation_json TEXT NOT NULL,          -- parallel translated lines
  key_vocabulary_json TEXT NOT NULL DEFAULT '[]', -- referenced vocabulary_items.item_key
  grammar_notes TEXT,
  slang_register_notes TEXT,
  audio_generation_instructions TEXT,      -- for future TTS (§12); unused in MVP
  data_json     TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);

-- ---------- content: slang / profanity / idiom (unified real-speech table) ----------
CREATE TABLE real_speech_items (
  id            TEXT PRIMARY KEY,
  pack_id       TEXT NOT NULL REFERENCES language_packs(id),
  item_key      TEXT NOT NULL,
  kind          TEXT NOT NULL,             -- 'slang' | 'profanity' | 'idiom' | 'euphemism' | 'taboo'
  phrase        TEXT NOT NULL,
  literal       TEXT,                      -- literal translation
  natural       TEXT,                      -- natural translation
  register      TEXT NOT NULL,             -- register label (§9)
  severity      INTEGER NOT NULL DEFAULT 1,-- 1..7 scale (§13)
  who_uses      TEXT,
  usage_context TEXT,
  learner_should_use TEXT,                 -- 'use' | 'recognize-only' | 'avoid'
  safer_alternatives_json TEXT NOT NULL DEFAULT '[]',
  cultural_warning TEXT,
  examples_json TEXT NOT NULL DEFAULT '[]',
  regional_tag  TEXT,                      -- e.g. "RJ", "SP" (§9 regional variants)
  data_json     TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);

-- ---------- content: lessons (covers phrases-drill, listening, writing, roleplay, assessment) ----------
CREATE TABLE lessons (
  id            TEXT PRIMARY KEY,
  pack_id       TEXT NOT NULL REFERENCES language_packs(id),
  item_key      TEXT NOT NULL,
  lesson_type   TEXT NOT NULL,             -- vocabulary|grammar|pronunciation_drill|
                                           --   listening_exercise|dictation|dialogue_analysis|
                                           --   roleplay|reading_passage|writing_prompt|
                                           --   translation_drill|slang_breakdown|idiom_lesson|
                                           --   real_world_scenario|review_session|assessment
                                           --   — covers §10.1's pronunciation-drill/listening/
                                           --   writing/assessment/roleplay categories
  title         TEXT NOT NULL,
  cefr          TEXT,
  sequence      INTEGER,
  dialogue_id   TEXT REFERENCES dialogues(id), -- set for dialogue_analysis/roleplay lessons
  body_json     TEXT NOT NULL,             -- exercises/steps
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);

-- ---------- SRS ----------
CREATE TABLE review_items (
  id            TEXT PRIMARY KEY,
  profile_id    TEXT NOT NULL REFERENCES learner_profiles(id),
  item_type     TEXT NOT NULL,             -- 'vocabulary'|'grammar'|'real_speech'|'pronunciation'
  content_id    TEXT NOT NULL,             -- FK-by-convention to the content table row
  -- FSRS state
  difficulty    REAL,
  stability     REAL,
  retrievability REAL,
  state         TEXT NOT NULL DEFAULT 'new', -- new|learning|review|relearning
  due_at        TEXT,
  last_reviewed_at TEXT,
  lapses        INTEGER NOT NULL DEFAULT 0,
  reps          INTEGER NOT NULL DEFAULT 0,
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(profile_id, item_type, content_id)
);
CREATE INDEX idx_review_due ON review_items(profile_id, due_at);

CREATE TABLE review_results (
  id            TEXT PRIMARY KEY,
  review_item_id TEXT NOT NULL REFERENCES review_items(id),
  rating        INTEGER NOT NULL,          -- FSRS grade 1..4 (again/hard/good/easy)
  response_ms   INTEGER,
  confidence    INTEGER,                   -- optional self-rating
  reviewed_at   TEXT NOT NULL,
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL
);

-- ---------- AI / conversation ----------
CREATE TABLE conversations (
  id            TEXT PRIMARY KEY,
  profile_id    TEXT NOT NULL REFERENCES learner_profiles(id),
  mode          TEXT NOT NULL,             -- tutor mode / scenario (§14 supported modes)
  scenario      TEXT,
  title         TEXT,
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);

CREATE TABLE ai_messages (
  id            TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  role          TEXT NOT NULL,             -- system|user|assistant
  content       TEXT NOT NULL,
  correction_json TEXT,                    -- structured correction payload if any (§6)
  tokens        INTEGER,
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL
);

-- ---------- pronunciation ----------
CREATE TABLE pronunciation_attempts (
  id            TEXT PRIMARY KEY,
  profile_id    TEXT NOT NULL REFERENCES learner_profiles(id),
  target_text   TEXT NOT NULL,
  transcript    TEXT,                      -- STT output
  score         REAL,                      -- 0..1 basic correctness (MVP)
  audio_path    TEXT,                      -- local file ref
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL
);

-- ---------- progress / events ----------
CREATE TABLE progress_events (
  id            TEXT PRIMARY KEY,
  profile_id    TEXT NOT NULL REFERENCES learner_profiles(id),
  kind          TEXT NOT NULL,             -- lesson_completed|review_completed|streak|...
  payload_json  TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL
);

-- ---------- feature flags ----------
CREATE TABLE feature_flags (
  key           TEXT PRIMARY KEY,
  enabled       INTEGER NOT NULL DEFAULT 0,
  updated_at    TEXT NOT NULL
);
```

**Notes**
- `data_json` overflow columns let pack authors add fields without a DB migration; the typed
  columns are the queryable/index-worthy subset. This keeps packs upgradeable (§8) while
  keeping hot queries fast.
- Content tables are wiped-and-reloaded on pack (re)import inside a transaction; user tables
  reference content by `content_id` and survive re-import as long as `item_key` is stable — so
  pack updates that keep keys don't orphan review history.
- Foreign keys enforced via `PRAGMA foreign_keys=ON`. WAL mode for concurrency.
- Onboarding's diagnostic result (§6 steps 7–9) is recorded as a `progress_events` row
  (`kind='diagnostic_completed'`) rather than a dedicated table — it's a one-time MVP event,
  not an ongoing tracked entity.

---

## 5. Language Pack JSON Schema

A pack is a directory of JSON validated against `language-pack-sdk/schemas`. The **manifest**
is the entry point and declares versions, inheritance, and content file listing. Every content
record carries `schema_version`. Content-item field requirements follow spec §11 exactly.
Below are the canonical shapes (abbreviated; full JSON Schema files live in
`packages/language-pack-sdk/schemas`).

### 5.1 `manifest.json`

```json
{
  "schemaVersion": 1,
  "id": "pt-br",
  "name": "Brazilian Portuguese",
  "languageCode": "pt-BR",
  "packVersion": "0.1.0",
  "basePack": null,
  "authors": ["PolyglotAI"],
  "license": "proprietary",
  "dialects": [
    { "id": "pt-BR-SP", "name": "São Paulo" },
    { "id": "pt-BR-RJ", "name": "Rio de Janeiro (Carioca)" }
  ],
  "defaultDialect": "pt-BR-SP",
  "cefrRange": ["A1", "B2"],
  "featureFlags": { "slang": true, "profanity": true },
  "contents": {
    "vocabulary":   ["vocabulary/a1.json", "vocabulary/a2.json"],
    "phrases":      ["phrases/core.json"],
    "grammar":      ["grammar/core.json"],
    "slang":        ["slang/general.json"],
    "profanity":    ["profanity/general.json"],
    "idioms":       ["idioms/general.json"],
    "pronunciation":["pronunciation/rules.json"],
    "dialogues":    ["dialogues/general.json"],
    "lessons":      ["lessons/a1.json"],
    "assessments":  ["assessments/placement.json"],
    "aiPrompts":    ["ai-prompts/tutor.json"],
    "culture":      ["culture/notes.json"]
  },
  "checksums": { "algo": "sha256", "files": { "vocabulary/a1.json": "…" } }
}
```

### 5.2 Content item schemas (shapes)

Field sets follow spec §11's per-entity requirements exactly.

```jsonc
// vocabulary item (entryType: 'word' | 'phrase' | 'chunk' — same schema covers both
// the 300-vocabulary and 75-core-phrase content categories from §10.1)
{
  "schemaVersion": 1,
  "key": "vocab.agua",
  "entryType": "word",
  "lemma": "água",
  "translation": "water",
  "literalMeaning": "water",
  "naturalMeaning": "water",
  "partOfSpeech": "noun",
  "cefr": "A1",
  "frequencyRank": 214,
  "ipa": "ˈa.gwɐ",
  "audioText": "água",
  "pronunciationNotes": "stressed first syllable; open /a/",
  "gender": "f",
  "register": "neutral",
  "tags": ["food-drink"],
  "examples": [
    { "text": "Quero um copo de água.", "translation": "I want a glass of water.", "register": "neutral" }
  ]
}

// grammar item
{
  "schemaVersion": 1,
  "key": "grammar.present-regular-ar",
  "title": "Present tense — regular -ar verbs",
  "cefr": "A1",
  "explanationMd": "…",
  "examples": [ { "text": "Eu falo português.", "translation": "I speak Portuguese." } ],
  "commonErrors": [ { "wrong": "Eu fala", "right": "Eu falo", "note": "1st person -o" } ],
  "drills": [ { "prompt": "Conjugate 'falar' for 'nós'", "answer": "falamos" } ],
  "relatedVocabulary": ["vocab.falar"]
}

// real-speech item (slang / profanity / idiom / euphemism / taboo)
{
  "schemaVersion": 1,
  "key": "slang.mano",
  "kind": "slang",
  "phrase": "mano",
  "literal": "brother",
  "natural": "dude / bro",
  "register": "informal",
  "severity": 1,
  "region": "SP",
  "whoUses": "young people, esp. SP",
  "usageContext": "casual address among friends",
  "learnerShouldUse": "use",
  "saferAlternatives": ["amigo", "cara"],
  "warningNotes": null,
  "examples": [ { "text": "E aí, mano?", "translation": "What's up, dude?" } ]
}

// dialogue (first-class per §11)
{
  "schemaVersion": 1,
  "key": "dialogue.cafe-order",
  "scenario": "café",
  "speakers": [ { "id": "customer", "name": "Ana" }, { "id": "barista", "name": "Léo" } ],
  "targetLevel": "A1",
  "regionDialect": "pt-BR-SP",
  "formality": "informal",
  "transcript": [
    { "speakerId": "customer", "text": "Oi, um café com leite, por favor." },
    { "speakerId": "barista", "text": "Claro! Pra levar ou pra ficar?" }
  ],
  "translation": [
    { "speakerId": "customer", "text": "Hi, a coffee with milk, please." },
    { "speakerId": "barista", "text": "Sure! To go or to stay?" }
  ],
  "keyVocabulary": ["vocab.cafe", "vocab.leite", "phrase.por-favor"],
  "grammarNotes": "polite requests with 'por favor'",
  "slangRegisterNotes": "'pra' = informal contraction of 'para'",
  "audioGenerationInstructions": null
}

// pronunciation rule
{
  "schemaVersion": 1,
  "key": "pron.nasal-ao",
  "grapheme": "ão",
  "ipa": "ɐ̃w̃",
  "description": "Nasal diphthong; no clean English equivalent.",
  "minimalPairs": [ { "a": "pão", "b": "pau", "note": "nasal vs oral" } ]
}

// lesson (lessonType covers vocabulary/grammar/pronunciation_drill/listening_exercise/
// writing_prompt/roleplay/assessment/dialogue_analysis/slang_breakdown/...)
{
  "schemaVersion": 1,
  "key": "lesson.a1.greetings",
  "lessonType": "vocabulary",
  "title": "Greetings",
  "cefr": "A1",
  "sequence": 1,
  "dialogueRef": null,
  "body": {
    "steps": [
      { "type": "teach", "vocabRefs": ["vocab.oi", "vocab.tudo-bem"] },
      { "type": "quiz",  "prompt": "Translate: 'Hi, how are you?'", "answer": "Oi, tudo bem?" }
    ]
  }
}

// ai prompt template
{
  "schemaVersion": 1,
  "key": "prompt.tutor.correction",
  "mode": "writing-editor",
  "template": "You are a Brazilian Portuguese tutor… Correct the learner's sentence and return JSON with fields: corrected, literal, natural, formal, casual, slangNative, grammar, register, pronunciationNotes…",
  "outputSchemaRef": "ai/correction.schema.json"
}
```

### 5.3 Register labels & severity (enumerations)

- **Register** (§9): `formal | neutral | informal | vulgar | obscene | offensive | affectionate | flirtatious | sarcastic | humorous | childish | dated | internet | regional | professional | academic | street | hostile | taboo | dangerous`.
- **Severity** (§13): integer `1..7` (harmless informal → severe taboo).
- **learnerShouldUse**: `use | recognize-only | avoid`.

These live as `enum`s in the JSON Schemas (`packages/language-pack-sdk/schemas`) and as Zod
enums in `packages/shared-types`, keeping the published contract and runtime validation in
lockstep.

### 5.4 Inheritance (§9)

`basePack` in the manifest names a parent pack id. The loader resolves child-over-parent by
`key`: a child item with an existing `key` overrides; new keys extend. MVP ships a single
self-contained `pt-br` pack (no base), but the resolver is built and tested so regional
sub-packs (e.g. `pt-br-rj`) can inherit later without core changes.

---

## 6. First Brazilian Portuguese Language Pack (structure)

MVP pack targets the spec's **official Tier 1 / MVP Seed Pack Target** (§10.1, restated
verbatim in §23) — no more, no less. Content authoring is done by Claude, native-review by
the owner (confirmed decision, §9 risk 3).

```text
packs/pt-br/
  manifest.json
  metadata.json               # long description, sources, coverage notes
  vocabulary/
    a1.json                   # ~150 word-entries
    a2.json                   # ~150 word-entries
                              #   → 300 vocabulary items total (§10.1)
  phrases/
    core.json                 # 75 core phrases (entryType: 'phrase') — everyday chunks,
                              #   greetings, requests, small talk
  grammar/
    core.json                 # 25 grammar points: ser/estar, articles+gender, present
                              #   -ar/-er/-ir, ter/haver, object pronouns, você/tu, etc.
  pronunciation/
    rules.json                # 20 pronunciation drills: nasal vowels (ão/ã), open/closed
                              #   e-o, r/rr, lh/nh, -de/-ti palatalization; minimal pairs
  idioms/
    general.json              # idioms folded into the 20 slang/register items below
                              #   (kind: 'idiom') — not a separate MVP count in §10.1
  slang/
    general.json              # slang/register items (kind: 'slang'), SP/RJ regional tags
  profanity/
    general.json              # slang/register items (kind: 'profanity'/'taboo'), severity
                              #   3–7, cultural warnings, recognize-only guidance
                              #   → slang + profanity + idiom combined = 20 items (§10.1)
  dialogues/
    general.json              # 25 dialogues: café, bar, hotel, date, job interview,
                              #   apartment rental, doctor visit, customer service,
                              #   flirting, texting, workplace disagreement, etc. (§10.1, §14)
  lessons/
    a1.json                   # lesson wrappers around vocab/grammar/slang, plus:
                              #   10 listening exercises (lessonType: listening_exercise)
                              #   10 writing prompts (lessonType: writing_prompt)
                              #   5 roleplay scenarios (lessonType: roleplay, dialogueRef set)
  assessments/
    placement.json            # 10 assessments/checkpoints (lessonType: assessment)
  ai-prompts/
    tutor.json                # correction, conversation, slang-explainer, roleplay templates
  culture/
    notes.json                # você vs tu, formality, regional identity, social risk
```

**Content emphasis (why PT-BR is the right first pack, §23):** the pack must showcase the
formal/informal contrast (`você`/`tu`/`o senhor`), register range, regional slang (SP vs RJ),
and pronunciation features with no clean English analog — proving the register/severity/regional
machinery, not just vocabulary storage.

**Volume target — official §10.1 MVP Seed Pack Target, no scaling:**

| Category | Count | Where it lives |
|---|---|---|
| Vocabulary items | 300 | `vocabulary/*.json` |
| Core phrases | 75 | `phrases/core.json` |
| Grammar points | 25 | `grammar/core.json` |
| Dialogues | 25 | `dialogues/general.json` |
| Slang/register items | 20 | `slang/`, `profanity/`, `idioms/` combined |
| Pronunciation drills | 20 | `pronunciation/rules.json` |
| Listening exercises | 10 | `lessons/a1.json` |
| Writing prompts | 10 | `lessons/a1.json` |
| Assessments/checkpoints | 10 | `assessments/placement.json` |
| Roleplay scenarios | 5 | `lessons/a1.json` (linked to `dialogues/`) |

A1–B2 and long-term tiers (§10.2–§10.6) are documented future growth, authored in later
passes once the MVP architecture is validated end-to-end — not built now.

---

## 7. Build Sequence

Follows the spec's incremental strategy (§25) but trimmed to MVP and ordered so each step is
independently testable. Each step ships **code + tests + acceptance criteria + docs + migration
if needed** (§25). Primary manual-test platform is **Windows**; CI validates Ubuntu + macOS
builds alongside it from step 1 (§0.1).

**Milestone A — Foundations (data spine)**
1. **Scaffold** monorepo (pnpm workspaces per §2's `apps/`+`packages/` layout, TS strict,
   Tauri shell that opens a window, CI matrix for Windows/Ubuntu/macOS).
   *Accept:* `pnpm build` succeeds; app launches an empty window locally on Windows; CI builds
   green on all three OS targets.
2. **JSON Schemas** for pack + profile in `packages/language-pack-sdk/schemas`; Zod mirrors in
   `packages/shared-types`.
   *Accept:* schemas lint; round-trip a sample doc (incl. a dialogue) through Zod.
3. **SQLite schema + migration runner** (SQLx migrations, `PRAGMA` setup, `schema_meta`).
   *Accept:* fresh DB migrates to head; `cargo test` on migrations; rollback story documented.
4. **Pack validator** (`language-pack-sdk/bin` CLI + validator module): schema + semantic
   checks + a **content-volume report** against the §10.1 targets.
   *Accept:* validates a good fixture, rejects malformed fixtures with clear errors, reports
   under/over target counts per category.
5. **Pack loader + registry + inheritance resolver**; import into DB in a transaction.
   *Accept:* importing `pt-br` populates content tables (incl. `dialogues`); re-import is
   idempotent; user tables untouched.

**Milestone B — Core learning loop + onboarding (no AI yet)**
6. **Profile service** + settings + feature-flag registry.
   *Accept:* create/read/update local profile; toggle a flag and observe gated behavior.
7. **First-time onboarding flow** (§6): goal selection, dialect focus, real-speech level,
   short diagnostic, generated first study plan.
   *Accept:* a new profile completes onboarding end-to-end and lands with a populated study
   plan and a `diagnostic_completed` progress event.
8. **Vocabulary/Phrase + Grammar + Slang/Register + Dialogue services** + their library screens.
   *Accept:* browse items; slang shows register/severity/usage guidance; profanity gated by
   flag + the learner's real-speech level; dialogues render transcript + translation + notes.
9. **SRS scheduler** (FSRS) + review-item generation + **Review screen**.
   *Accept:* reviewing schedules next due date correctly; results persist; due query drives the deck.
10. **Learning engine (thin)** + **Dashboard** (streak, due count, progress events).
    *Accept:* dashboard reflects real progress; "next activity" picks a sensible lesson/deck.

**Milestone C — AI + speech**
11. **AIProvider interface + OpenAI adapter + orchestrator + prompt templates + content policy.**
    *Accept:* correction returns structured JSON (corrected/natural/formal/casual/slang-native/
    grammar/register/pronunciation notes per §6); policy clause present; provider swappable.
    **Spike the vulgar/taboo-explanation behavior here first**, before building the UI on top of it.
12. **AI Tutor screen** (grammar/slang/writing-editor modes) using pack `ai-prompts`.
    *Accept:* learner submits a sentence, gets the full structured correction rendered.
13. **Conversation mode** (scenario roleplay grounded in the roleplay/travel/nightlife/
    workplace tutor modes from §14; logs to `conversations`/`ai_messages`, gated off by
    default per the `conversation_logging` flag).
    *Accept:* multi-turn scenario persists when the flag is on; correction inline; adult
    content handled per policy.
14. **SpeechProvider interface + Whisper adapter + Pronunciation screen** (record → STT → basic
    score → playback). TTS interface stubbed only, not implemented (§12 is post-MVP).
    *Accept:* record a target phrase, see transcript + 0–1 score, replay own audio.

**Milestone D — Polish**
15. **Settings** (provider keys, dialect, real-speech level, correction strictness, flags, theme).
16. **Migration + backup** flow surfaced (backup-before-migrate); packaging/installers.
    *Accept:* upgrade path from a prior DB version restores cleanly; Windows `.msi` builds and
    installs locally; Ubuntu `.deb`/`AppImage` and macOS bundle build green in CI.

Milestones A→B deliver a usable offline vocab/grammar/slang/dialogue trainer with SRS even
before any AI key is configured — de-risking the AI dependency.

---

## 8. Testing Strategy

Test at the layer where a bug is cheapest to catch. `core-learning`, `spaced-repetition`,
`ai-orchestration`, and `language-pack-sdk` being framework-free makes most logic
unit-testable in Node.

| Level | Tool | What |
|---|---|---|
| **Unit** | Vitest | SRS math (FSRS transitions, due calc), pack inheritance resolution, register/severity enum handling, prompt template rendering, Zod validation, migration doc transforms, real-speech-level → severity-ceiling mapping. |
| **Schema/contract** | Vitest + AJV | Every `language-pack-sdk/schemas` file validates its fixtures; the `pt-br` pack validates green in CI, including a content-volume check against §10.1. Zod ↔ JSON Schema kept consistent via a generated-fixtures round-trip test. |
| **DB / migrations** | `cargo test` + Vitest against a temp SQLite file | Migrate fresh→head; re-import idempotency; FK enforcement; user data survives pack re-import; backup-before-migrate. |
| **Adapter** | Vitest with **mocked HTTP** | OpenAI + Whisper adapters: request shape, structured-output parsing, error/retry, timeout. No live API calls in CI. A separate opt-in `@live` suite (run manually with a key) hits the real API — this is also where the vulgar/taboo-explanation spike (Milestone C, step 11) gets its go/no-go answer. |
| **AI behavior** | Golden/snapshot on **mocked** provider | Given a canned model response, orchestrator produces the correct structured correction; content-policy clause is present in the assembled prompt. (We test *our* wiring, not the model.) |
| **E2E** | Playwright driving the Tauri webview on Windows (CI also runs it on Ubuntu) | Critical flows: onboarding → import pack → browse vocab/dialogues → do a review → get a correction (mocked AI) → record pronunciation (mocked STT) → dashboard updates. |
| **Static** | tsc strict, ESLint, clippy | No `any` in `packages/*`; exhaustive switch on register/severity/lessonType enums. |

**Fixtures:** a tiny `fixtures/mini-pack/` (a handful of items) for fast tests, plus the real
`pt-br` pack for the contract gate. **CI gates:** typecheck + unit + schema + migration + pack
validation must pass on every PR, on Windows and Ubuntu runners; E2E on main. **AI cost control
in tests:** mocked by default; live suite is opt-in and never runs in CI.

---

## 9. Major Risks & Unclear Areas

Three risks below carried an open decision for the owner; all three are now **resolved** and
folded into the plan (marked ✅). Remaining risks are technical unknowns to manage during the build.

| # | Risk / unknown | Impact | Mitigation |
|---|---|---|---|
| 1 ✅ | **Adult/vulgar content + AI provider policy.** OpenAI's usage policies may refuse or degrade on profanity/taboo explanation even for pedagogy — directly conflicts with the app's core premise (§13, §26). **Resolved:** owner accepts OpenAI for MVP and wants this spiked immediately — "yes, absolutely, it's just for me." | High — could gut a headline feature. | Spike the slang/profanity explainer against OpenAI *first*, in Milestone C step 11, before building the Tutor UI on top of it. Keep `AIProvider` swappable as a fallback path. Lean on **pack-authored** static explanations for the worst-case items so profanity teaching works even if the AI declines on a given prompt. |
| 2 | **STT accuracy for "basic pronunciation score."** Whisper transcribes meaning well but is a poor proxy for *pronunciation* quality — it may accept mangled pronunciation or fail on single words. Spec §17 (Phase 1) asks only for "basic correctness." | Medium | Frame MVP pronunciation as *comprehensibility check*, not accuracy scoring. Score = normalized transcript match. Explicitly defer phoneme scoring to Phase 4 (§24). Document the limitation in UI. |
| 3 ✅ | **Content authoring bottleneck.** A good PT-BR pack needs judgment on register/severity/regional tags. **Resolved:** Claude authors the `pt-br` pack content; owner does native review before it's treated as final. | High for quality | Author to the exact §10.1 volumes (no more, no less) so review stays tractable. Build the validator (Milestone A step 4) early, including its content-volume report, so authoring gets fast feedback and review has a clear checklist. |
| 4 | **Schema churn during MVP.** Getting versioned schemas "right" before we've built consumers risks premature lock-in or costly reshuffles. | Medium | Use `data_json` overflow columns + `schemaVersion` on every doc so additive changes need no migration. Only breaking changes trigger a migration. Keep `schema_version` at 1 through MVP; write the first real migration only when forced. |
| 5 | **Tauri SQLite plugin maturity / migration ergonomics**, and now **cross-platform build friction** (Windows dev machine, Ubuntu/macOS validated only in CI until an Ubuntu machine is available). | Medium | Split concerns explicitly: SQL migrations for schema (SQLx), a TS migration framework for JSON docs (packs/profiles) with backup+validation. Validate the migration approach in Milestone A step 3. Keep the CI matrix (Windows + Ubuntu + macOS) green continuously so a platform gap never goes undetected, even though manual testing is Windows-only for now. |
| 6 | **AI cost & context management.** Conversation + correction can grow context and cost unpredictably; spec lists "cost controls" (§7.3) but MVP has none. | Medium | MVP: cap context window (last N turns + a compact learner-context summary), set per-session token ceilings, cache identical explanations. Full cost controls deferred but the orchestrator is the single choke point where they'll land. |
| 7 ✅ | **Conversation/audio logging default.** **Resolved:** default is **off**, per the owner. | Low–Medium | `conversation_logging` feature flag ships **off** by default (Appendix); store audio under the app data dir with explicit user control when the flag is enabled later. |
| 8 | **Single-profile assumption vs. Friends mode.** MVP is single local profile, but schema already has `profile_id` FKs. | Low | Keep `profile_id` everywhere (already in schema) so multi-profile is additive; build UI for exactly one profile. No further investment now. |
| 9 | **FSRS across heterogeneous item types.** FSRS is tuned for flashcard recall; applying it to "register judgments" or "pronunciation targets" (§16) is unproven. | Low–Medium | MVP: apply FSRS to vocab/phrase/grammar/slang recognition (its comfort zone). Treat pronunciation as practice, not SRS-scheduled, in MVP. Scheduler interface allows per-item-type policies later. |
| 10 | **Content-volume authoring time.** 300 vocab + 75 phrases + 25 grammar + 25 dialogues + 20 slang/register + 20 pronunciation drills + 10+10+10 exercises + 5 roleplay is real writing work, even at the (deliberately non-inflated) official Tier 1 size. | Medium | Author pack content in parallel with Milestones A–B's engineering (the validator needs real content to test against anyway); sequence dialogues and roleplay last since they depend on vocabulary/grammar/slang items already existing to reference. |

---

## Appendix — Feature flags shipped in MVP (defaults)

`ai_conversation` (on), `slang_mode` (on), `profanity_explanations` (on, gated by the
learner's `real_speech_level`/severity ceiling), `pronunciation_recording` (on),
`conversation_logging` (**off**), `teacher_dashboard` (off), `cloud_sync` (off), `billing`
(off), `experimental_packs` (off). All read from the `feature_flags` table, overridable in
Settings, honoring §8.
