# PolyglotAI — MVP Technical Implementation Plan

> Scope: **MVP only** (Spec §20). Adult, personal, local-first learning app with one
> language pack (Brazilian Portuguese). This document is a build blueprint — no application
> code is written yet. Future layers (Teacher, Commerce, Plugins, Cloud Sync) are designed
> for but **not built**; they only influence where we put seams.

---

## 0. What the MVP must deliver

From Spec §20, the MVP is:

- Desktop app (local, single user)
- Local profile
- One language pack + language-pack loader
- Vocabulary system
- Grammar system
- Slang / register system
- AI tutor
- Spaced repetition
- Conversation mode
- Basic pronunciation recording
- Progress dashboard
- Settings

Everything else in the spec (Teacher, Commerce, Plugins, multi-user sync, phoneme-level
pronunciation scoring) is **explicitly out of scope** for the MVP but must not be
architecturally blocked. The guiding constraint from §23: *modular, local-first, versioned,
flaggable, provider-abstracted, upgradeable for years.*

---

## 1. Recommended Stack

Aligns with the spec's own recommendation (§18) with concrete version-level choices.

| Layer | Choice | Rationale |
|---|---|---|
| Shell / packaging | **Tauri 2** | Native desktop, small binary, Rust core, good local-FS + SQLite story, cross-platform (macOS/Win/Linux). Spec-recommended. |
| UI | **React 18 + TypeScript 5 (strict)** | Spec-recommended; large ecosystem; typed data models are critical given versioned schemas. |
| Build tool | **Vite** | Default for Tauri + React; fast HMR. |
| Styling | **Tailwind CSS + CSS variables** | Fast iteration; CSS variables give us a theme-manager seam (§6.1) cheaply. |
| State | **Zustand** (UI/session) + **TanStack Query** (async/DB reads) | Lightweight; avoids Redux boilerplate. Query gives caching/invalidation around the data layer. |
| Routing | **React Router** | Screen set is known (§19). |
| Local DB | **SQLite** via **`@tauri-apps/plugin-sql`** (SQLx under the hood) | Spec-recommended; migrations supported; runs in the Rust side, not the webview. |
| Migrations | **SQLx migrations** (Rust side) + app-level migration runner for JSON/pack data | Two migration concerns: DB schema and pack/profile JSON. Both versioned (§6.4–6.5). |
| Validation | **Zod** (TS) mirrored by **JSON Schema** files | Zod validates at runtime in the app; JSON Schema files are the canonical published contract for pack authors and future plugin SDK. |
| AI provider | **OpenAI adapter** behind an `AIProvider` interface | Spec §18/§6.6 — provider must be swappable. Only OpenAI implemented in MVP. |
| Audio (MVP) | Browser **MediaRecorder** for capture; STT via an `SpeechProvider` adapter (OpenAI Whisper API impl) | Spec §12.1 "record + STT comparison + playback + basic score." No phoneme analysis in MVP. |
| SRS | **FSRS** (via `ts-fsrs`) with an internal `Scheduler` interface; SM-2 as documented fallback | Spec §11 prefers FSRS. Interface lets us swap. |
| Testing | **Vitest** (unit/integration), **Playwright** (E2E on the Tauri webview), **`cargo test`** (Rust migration/DB) | Covers TS logic, DB layer, and full-app flows. |
| Lint/format | **ESLint + Prettier + `cargo fmt`/`clippy`** | — |
| Monorepo mgmt | **pnpm workspaces** | Clean separation of `app`, `core`, `packs`, `schemas`. |

**Deliberate MVP exclusions:** no backend server, no Postgres/Redis, no TTS provider (playback
is of the user's own recording + optional cached AI audio later), no pronunciation *scoring*
model beyond STT-transcript comparison, no cloud sync, no auth beyond a local profile.

---

## 2. Repository Structure

pnpm monorepo. The seams here are the whole point — each package maps to a spec layer (§5)
and can grow without touching the others.

```text
polyglotai/
  package.json                 # pnpm workspace root
  pnpm-workspace.yaml
  tsconfig.base.json
  README.md
  docs/
    mvp-implementation-plan.md # this file
    adr/                       # architecture decision records
  schemas/                     # @polyglotai/schemas — canonical JSON Schemas (versioned)
    language-pack/
      manifest.schema.json
      vocabulary.schema.json
      grammar.schema.json
      slang.schema.json
      idiom.schema.json
      pronunciation.schema.json
      lesson.schema.json
      assessment.schema.json
      ai-prompts.schema.json
    profile/
      learner-profile.schema.json
    package.json
  packages/
    core/                      # @polyglotai/core — pure TS, no React, no Tauri
      src/
        db/                    # DB access layer (repositories), migrations runner
        models/                # TS types + Zod schemas (mirror /schemas)
        packs/                 # loader, validator, registry, inheritance resolver
        profile/               # profile service
        vocabulary/            # vocabulary service
        grammar/               # grammar service
        slang/                 # slang/register service
        srs/                   # FSRS scheduler + Scheduler interface
        learning/              # lesson sequencing (thin in MVP)
        ai/                    # AIProvider interface, prompt templates, orchestrator
        speech/                # SpeechProvider interface (STT)
        featureflags/          # flag registry + evaluation
        migrations/            # JSON/data migration framework (pack + profile)
        events/                # lightweight event bus for cross-module signals
      tests/
      package.json
    adapters/                  # @polyglotai/adapters — provider implementations
      src/
        ai/openai/
        speech/openai-whisper/
      package.json
    app/                       # @polyglotai/app — Tauri + React
      src-tauri/               # Rust: window, SQLite plugin, FS access, migrations dir
        migrations/            # SQLx .sql migration files
        src/
      src/                     # React
        screens/               # dashboard, lesson, review, tutor, conversation,
                               #   pronunciation, vocab, grammar, slang, packs, settings
        components/
        stores/                # Zustand
        hooks/
        theme/
      package.json
  packs/
    pt-br/                     # the first language pack (data, not code)
      manifest.json
      metadata.json
      vocabulary/
      grammar/
      pronunciation/
      idioms/
      slang/
      profanity/
      culture/
      lessons/
      assessments/
      ai-prompts/
      examples/
  tools/
    pack-validator/            # CLI: validate a pack against /schemas (CI + authors)
```

**Key boundary rules**
- `core` never imports React or Tauri. It is a pure TS library — testable in Node/Vitest,
  reusable later by a server or CLI.
- `adapters` depend on `core` interfaces only. Swapping providers = swapping this package's wiring.
- `app` is the only package that knows about Tauri/React. It composes `core` + `adapters`.
- `packs` and `schemas` contain **no code** — data + contracts. This is what makes packs
  plug-and-play (§23) and seeds the future plugin SDK.

---

## 3. Core Modules

Each maps to a spec responsibility and exposes a narrow interface. MVP builds all of these;
"future hooks" note where we leave a seam but stop.

| Module | Responsibility (MVP) | Interface sketch | Future seam |
|---|---|---|---|
| **DB / Repositories** | Typed CRUD over SQLite; one repository per entity; transactions | `VocabularyRepo`, `ReviewRepo`, `ProfileRepo`, … | Same repos back a sync engine later |
| **Pack Loader** | Discover, validate, register, and index a pack from disk; resolve base→variant inheritance | `loadPack(path) → LoadedPack`; `PackRegistry` | Remote install, plugin manifests |
| **Pack Validator** | Validate pack JSON against JSON Schema + semantic checks (dangling refs, duplicate ids) | `validatePack(dir) → Result<Report>` | Reused verbatim by plugin SDK |
| **Profile** | Single local learner profile: level, dialect pref, slang comfort, correction strictness, settings | `ProfileService` | Multi-profile / accounts |
| **Vocabulary** | Query vocab items, mark known/weak, feed SRS | `VocabularyService` | — |
| **Grammar** | Serve grammar rules + examples; link to lessons | `GrammarService` | — |
| **Slang / Register** | First-class slang/profanity items with register + severity + usage guidance; gated by feature flag | `SlangService`, `RegisterLabel`, `Severity` | Regional/dating/professional packs |
| **SRS Scheduler** | FSRS state machine over `ReviewItem`s; schedule next review; record results | `Scheduler` iface; `FsrsScheduler` impl | SM-2 impl; per-item-type tuning |
| **Learning Engine (thin)** | Pick next lesson/review deck from progress; MVP = simple rules, not adaptive | `LearningService.nextActivity()` | Adaptive sequencing, CEFR estimate (§Phase 5) |
| **AI Orchestration** | Build prompts from templates + learner context; call `AIProvider`; parse structured corrections; enforce content policy | `AIOrchestrator`, `AIProvider` iface | Local LLM, multi-provider routing, cost controls |
| **Speech (STT)** | Capture audio, transcribe via `SpeechProvider`, diff transcript vs target → basic score | `SpeechProvider` iface | Phoneme/stress/intonation scoring |
| **Feature Flags** | Central registry; runtime-toggleable; defaults per build | `flags.isEnabled(key)` | Remote config |
| **Migrations** | DB schema (SQLx) + JSON data (pack/profile) migrations with backup + validation | `runMigrations()`, `migrateDoc(v→v)` | — |
| **Events** | Decouple modules (e.g. "review completed" → dashboard refresh) | tiny typed emitter | Analytics sink |

**AI content policy (MVP, from §8.4):** the orchestrator injects a system-prompt clause that
*permits* academic/contextual explanation of vulgarity, slang, and taboo language for learning,
while *refusing* targeted harassment, threats, sexual exploitation, and instructions for
wrongdoing. This is a prompt-layer guardrail plus a lightweight output check — not a heavy
moderation pipeline. Adult-first is a design rule (§23), so we do not sanitize teaching content.

---

## 4. SQLite Schema (MVP)

Design rules honored: every entity has `id`, `created_at`, `updated_at`, `schema_version`
(§16). IDs are app-generated UUIDv7 strings (sortable). Timestamps stored as ISO-8601 TEXT.
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
  base_pack_id  TEXT REFERENCES language_packs(id),  -- inheritance (§7.4)
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
  target_dialect TEXT,                     -- e.g. "pt-BR-SP"
  cefr_estimate TEXT,                      -- "A1".."C2", nullable in MVP
  slang_comfort INTEGER NOT NULL DEFAULT 3,-- 1..7 severity ceiling learner opts into
  correction_strictness TEXT NOT NULL DEFAULT 'balanced',
  settings_json TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);

-- ---------- content: vocabulary ----------
CREATE TABLE vocabulary_items (
  id            TEXT PRIMARY KEY,
  pack_id       TEXT NOT NULL REFERENCES language_packs(id),
  item_key      TEXT NOT NULL,             -- stable key within pack
  lemma         TEXT NOT NULL,
  translation   TEXT NOT NULL,
  part_of_speech TEXT,
  ipa           TEXT,
  register      TEXT,                      -- register label (§7.2)
  cefr          TEXT,
  tags_json     TEXT NOT NULL DEFAULT '[]',
  examples_json TEXT NOT NULL DEFAULT '[]',
  data_json     TEXT NOT NULL DEFAULT '{}',-- overflow for pack-specific fields
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);

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
  register      TEXT NOT NULL,             -- register label (§7.2)
  severity      INTEGER NOT NULL DEFAULT 1,-- 1..7 scale (§8.3)
  who_uses      TEXT,
  usage_context TEXT,
  learner_should_use TEXT,                 -- 'use' | 'recognize-only' | 'avoid'
  safer_alternatives_json TEXT NOT NULL DEFAULT '[]',
  cultural_warning TEXT,
  examples_json TEXT NOT NULL DEFAULT '[]',
  regional_tag  TEXT,                      -- e.g. "RJ", "SP" (§7.4)
  data_json     TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);

-- ---------- content: lessons ----------
CREATE TABLE lessons (
  id            TEXT PRIMARY KEY,
  pack_id       TEXT NOT NULL REFERENCES language_packs(id),
  item_key      TEXT NOT NULL,
  lesson_type   TEXT NOT NULL,             -- vocabulary|grammar|slang|dialogue|review|assessment...
  title         TEXT NOT NULL,
  cefr          TEXT,
  sequence      INTEGER,
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
  mode          TEXT NOT NULL,             -- tutor mode / scenario (§9.1, §9.2)
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
  correction_json TEXT,                    -- structured correction payload if any (§9.3)
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
  columns are the queryable/index-worthy subset. This keeps packs upgradeable (§6) while
  keeping hot queries fast.
- Content tables are wiped-and-reloaded on pack (re)import inside a transaction; user tables
  reference content by `content_id` and survive re-import as long as `item_key` is stable — so
  pack updates that keep keys don't orphan review history.
- Foreign keys enforced via `PRAGMA foreign_keys=ON`. WAL mode for concurrency.

---

## 5. Language Pack JSON Schema

A pack is a directory of JSON validated against `/schemas`. The **manifest** is the entry
point and declares versions, inheritance, and content file listing. Every content record
carries `schema_version`. Below are the canonical shapes (abbreviated; full JSON Schema files
live in `/schemas`).

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
    "grammar":      ["grammar/core.json"],
    "slang":        ["slang/general.json"],
    "profanity":    ["profanity/general.json"],
    "idioms":       ["idioms/general.json"],
    "pronunciation":["pronunciation/rules.json"],
    "lessons":      ["lessons/a1.json"],
    "assessments":  ["assessments/placement.json"],
    "aiPrompts":    ["ai-prompts/tutor.json"],
    "culture":      ["culture/notes.json"]
  },
  "checksums": { "algo": "sha256", "files": { "vocabulary/a1.json": "…" } }
}
```

### 5.2 Content item schemas (shapes)

```jsonc
// vocabulary item
{
  "schemaVersion": 1,
  "key": "vocab.agua",
  "lemma": "água",
  "translation": "water",
  "partOfSpeech": "noun",
  "ipa": "ˈa.gwɐ",
  "gender": "f",
  "register": "neutral",
  "cefr": "A1",
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
  "commonErrors": [ { "wrong": "Eu fala", "right": "Eu falo", "note": "1st person -o" } ]
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
  "whoUses": "young people, esp. SP",
  "usageContext": "casual address among friends",
  "learnerShouldUse": "use",
  "saferAlternatives": ["amigo", "cara"],
  "culturalWarning": null,
  "regionalTag": "SP",
  "examples": [ { "text": "E aí, mano?", "translation": "What's up, dude?" } ]
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

// lesson
{
  "schemaVersion": 1,
  "key": "lesson.a1.greetings",
  "lessonType": "vocabulary",
  "title": "Greetings",
  "cefr": "A1",
  "sequence": 1,
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
  "template": "You are a Brazilian Portuguese tutor… Correct the learner's sentence and return JSON with fields: corrected, literal, natural, grammar, register, alternatives…",
  "outputSchemaRef": "ai/correction.schema.json"
}
```

### 5.3 Register labels & severity (enumerations)

- **Register** (§7.2): `formal | neutral | informal | vulgar | obscene | offensive | affectionate | flirtatious | sarcastic | humorous | childish | dated | internet | regional | professional | academic | street | hostile | taboo | dangerous`.
- **Severity** (§8.3): integer `1..7` (harmless informal → severe taboo).
- **learnerShouldUse**: `use | recognize-only | avoid`.

These live as `enum`s in the JSON Schemas and as Zod enums in `core/models`, keeping the
published contract and runtime validation in lockstep.

### 5.4 Inheritance (§7.4)

`basePack` in the manifest names a parent pack id. The loader resolves child-over-parent by
`key`: a child item with an existing `key` overrides; new keys extend. MVP ships a single
self-contained `pt-br` pack (no base), but the resolver is built and tested so regional
sub-packs (e.g. `pt-br-rj`) can inherit later without core changes.

---

## 6. First Brazilian Portuguese Language Pack (structure)

MVP pack is intentionally **small but architecturally complete** — enough to exercise every
system end-to-end, per the spec's reasoning (§20). Data-authoring (not code) is a build task.

```text
packs/pt-br/
  manifest.json
  metadata.json               # long description, sources, coverage notes
  vocabulary/
    a1.json                   # ~150 items: greetings, numbers, food, everyday
    a2.json                   # ~150 items
  grammar/
    core.json                 # ser/estar, articles+gender, present -ar/-er/-ir,
                              #   ter/haver, ~12 A1–A2 rules
  pronunciation/
    rules.json                # nasal vowels (ão/ã), open/closed e-o, r/rr, lh/nh,
                              #   -de/-ti palatalization; minimal pairs
  idioms/
    general.json              # ~20 idioms (e.g. "pagar mico", "encher linguiça")
  slang/
    general.json              # ~30 items (mano, massa, beleza, top, sussa),
                              #   with SP/RJ regional tags
  profanity/
    general.json              # ~20 items across severity 3–7, with cultural warnings
                              #   and recognize-only guidance (adult-first, §8)
  culture/
    notes.json                # você vs tu, formality, regional identity, social risk
  lessons/
    a1.json                   # ~8 lessons wiring vocab+grammar+one slang breakdown
  assessments/
    placement.json            # short A1/A2 placement quiz
  ai-prompts/
    tutor.json                # correction, conversation, slang-explainer, roleplay templates
  examples/
    dialogues.json            # café, bar, making-friends sample dialogues (§9.2)
```

**Content emphasis (why PT-BR is the right first pack, §20):** the pack must showcase the
formal/informal contrast (`você`/`tu`/`o senhor`), register range, regional slang (SP vs RJ),
and pronunciation features with no clean English analog — proving the register/severity/regional
machinery, not just vocabulary storage.

**Volume target for MVP:** ~300 vocab, ~12 grammar rules, ~30 slang, ~20 profanity, ~20 idioms,
~8 lessons, 1 placement assessment. Small enough to author quickly, broad enough to validate.

---

## 7. Build Sequence

Follows the spec's incremental strategy (§22) but trimmed to MVP and ordered so each step is
independently testable. Each step ships **code + tests + acceptance criteria + docs + migration
if needed** (§22).

**Milestone A — Foundations (data spine)**
1. **Scaffold** monorepo (pnpm workspaces, TS strict, Tauri shell that opens a window, CI).
   *Accept:* `pnpm build` + app launches empty window on all 3 OSes in CI.
2. **JSON Schemas** for pack + profile in `/schemas`; Zod mirrors in `core/models`.
   *Accept:* schemas lint; round-trip a sample doc through Zod.
3. **SQLite schema + migration runner** (SQLx migrations, `PRAGMA` setup, `schema_meta`).
   *Accept:* fresh DB migrates to head; `cargo test` on migrations; rollback story documented.
4. **Pack validator** (`tools/pack-validator` + `core/packs/validator`): schema + semantic checks.
   *Accept:* validates a good fixture, rejects malformed fixtures with clear errors.
5. **Pack loader + registry + inheritance resolver**; import into DB in a transaction.
   *Accept:* importing `pt-br` populates content tables; re-import is idempotent; user tables untouched.

**Milestone B — Core learning loop (no AI yet)**
6. **Profile service** + settings + feature-flag registry.
   *Accept:* create/read/update local profile; toggle a flag and observe gated behavior.
7. **Vocabulary + Grammar + Slang/Register services** + their library screens.
   *Accept:* browse items; slang shows register/severity/usage guidance; profanity gated by flag+comfort.
8. **SRS scheduler** (FSRS) + review-item generation + **Review screen**.
   *Accept:* reviewing schedules next due date correctly; results persist; due query drives the deck.
9. **Learning engine (thin)** + **Dashboard** (streak, due count, progress events).
   *Accept:* dashboard reflects real progress; "next activity" picks a sensible lesson/deck.

**Milestone C — AI + speech**
10. **AIProvider interface + OpenAI adapter + orchestrator + prompt templates + content policy.**
    *Accept:* correction returns structured JSON; policy clause present; provider swappable via config.
11. **AI Tutor screen** (grammar/slang/writing-editor modes) using pack `ai-prompts`.
    *Accept:* learner submits a sentence, gets structured correction (§9.3) rendered.
12. **Conversation mode** (scenario roleplay; logs to `conversations`/`ai_messages`).
    *Accept:* multi-turn scenario persists; correction inline; adult content handled per policy.
13. **SpeechProvider interface + Whisper adapter + Pronunciation screen** (record → STT → basic score → playback).
    *Accept:* record a target phrase, see transcript + 0–1 score, replay own audio.

**Milestone D — Polish**
14. **Settings** (provider keys, dialect, slang comfort, correction strictness, flags, theme).
15. **Migration + backup** flow surfaced (backup-before-migrate, §6.5); packaging/installers.
    *Accept:* upgrade path from a prior DB version restores cleanly; signed installers build in CI.

Milestones A→B deliver a usable offline vocab/grammar/slang trainer with SRS even before any AI
key is configured — de-risking the AI dependency.

---

## 8. Testing Strategy

Test at the layer where a bug is cheapest to catch. `core` being framework-free makes most
logic unit-testable in Node.

| Level | Tool | What |
|---|---|---|
| **Unit** | Vitest | SRS math (FSRS transitions, due calc), pack inheritance resolution, register/severity enum handling, prompt template rendering, Zod validation, migration doc transforms. |
| **Schema/contract** | Vitest + AJV | Every `/schemas` file validates its fixtures; the `pt-br` pack validates green in CI (gate on merge). Zod ↔ JSON Schema kept consistent via a generated-fixtures round-trip test. |
| **DB / migrations** | `cargo test` + Vitest against a temp SQLite file | Migrate fresh→head; re-import idempotency; FK enforcement; user data survives pack re-import; backup-before-migrate. |
| **Adapter** | Vitest with **mocked HTTP** | OpenAI + Whisper adapters: request shape, structured-output parsing, error/retry, timeout. No live API calls in CI. A separate opt-in `@live` suite (run manually with a key) hits the real API. |
| **AI behavior** | Golden/snapshot on **mocked** provider | Given a canned model response, orchestrator produces the correct structured correction; content-policy clause is present in the assembled prompt. (We test *our* wiring, not the model.) |
| **E2E** | Playwright driving the Tauri webview | Critical flows: import pack → browse vocab → do a review → get a correction (mocked AI) → record pronunciation (mocked STT) → dashboard updates. |
| **Static** | tsc strict, ESLint, clippy | No `any` in `core`; exhaustive switch on register/severity enums. |

**Fixtures:** a tiny `fixtures/mini-pack/` (a handful of items) for fast tests, plus the real
`pt-br` pack for the contract gate. **CI gates:** typecheck + unit + schema + migration + pack
validation must pass on every PR; E2E on main. **AI cost control in tests:** mocked by default;
live suite is opt-in and never runs in CI.

---

## 9. Major Risks & Unclear Areas

| # | Risk / unknown | Impact | Mitigation / decision needed |
|---|---|---|---|
| 1 | **Adult/vulgar content + AI provider policy.** OpenAI's usage policies may refuse or degrade on profanity/taboo explanation even for pedagogy — directly conflicts with the app's core premise (§8, §23). | High — could gut a headline feature. | Prototype the slang/profanity explainer against the chosen provider *early* (Milestone C spike, before committing UI). Keep `AIProvider` swappable; evaluate a more permissive or local model as fallback. Lean on **pack-authored** static explanations for the worst-case items so profanity teaching works even if the AI declines. |
| 2 | **STT accuracy for "basic pronunciation score."** Whisper transcribes meaning well but is a poor proxy for *pronunciation* quality — it may accept mangled pronunciation or fail on single words. Spec §12.1 asks only for "basic correctness," so expectations must be set. | Medium | Frame MVP pronunciation as *comprehensibility check*, not accuracy scoring. Score = normalized transcript match. Explicitly defer phoneme scoring to Phase 4. Document the limitation in UI. |
| 3 | **Content authoring is the real bottleneck.** A good PT-BR pack needs a competent Brazilian speaker's judgment on register/severity/regional tags — engineering can't fabricate this credibly. | High for quality | Treat pack authoring as a distinct workstream with native review. Keep MVP volume small (§6). Build the validator early so authoring gets fast feedback. **Open question for owner:** who authors/reviews the PT-BR content? |
| 4 | **Schema churn during MVP.** Getting versioned schemas "right" before we've built consumers risks premature lock-in or costly reshuffles. | Medium | Use `data_json` overflow columns + `schemaVersion` on every doc so additive changes need no migration. Only breaking changes trigger a migration. Keep `schema_version` at 1 through MVP; write the first real migration only when forced. |
| 5 | **Tauri SQLite plugin maturity / migration ergonomics.** The plugin's migration model is simpler than a full ORM; complex data migrations (JSON reshaping) live in app code, not SQL. | Medium | Split concerns explicitly: SQL migrations for schema (SQLx), a TS migration framework for JSON docs (packs/profiles) with backup+validation (§6.5). Validate the approach in Milestone A step 3 before building on it. |
| 6 | **AI cost & context management.** Conversation + correction can grow context and cost unpredictably; spec lists "cost controls" (§5.3) but MVP has none. | Medium | MVP: cap context window (last N turns + a compact learner-context summary), set per-session token ceilings, cache identical explanations. Full cost controls deferred but the orchestrator is the single choke point where they'll land. |
| 7 | **"Basic pronunciation recording" audio storage & privacy.** Where audio files live, retention, and whether conversation/audio logging is on by default (§17 says "if enabled"). | Low–Medium | Default **off** for conversation/audio logging; store audio under the app data dir with explicit user control; make it a feature flag. Confirm default with owner. |
| 8 | **Single-profile assumption vs. Friends mode.** MVP is single local profile, but schema already has `profile_id` FKs. Under-building could force a migration; over-building wastes MVP time. | Low | Keep `profile_id` everywhere (already in schema) so multi-profile is additive; build UI for exactly one profile. No further investment now. |
| 9 | **FSRS across heterogeneous item types.** FSRS is tuned for flashcard recall; applying it to "register judgments" or "pronunciation targets" (§11) is unproven. | Low–Medium | MVP: apply FSRS to vocab/grammar/slang recognition (its comfort zone). Treat pronunciation as practice, not SRS-scheduled, in MVP. Scheduler interface allows per-type policies later. |

**Decisions I need from the owner before/early in the build:**
- **(a)** Confirmed AI provider for MVP and acceptance that we'll spike its handling of vulgar/taboo *teaching* content in week one (Risk 1).
- **(b)** Who authors and native-reviews the PT-BR pack content (Risk 3).
- **(c)** Default for conversation/audio logging — I propose **off** (Risk 7).

---

## Appendix — Feature flags shipped in MVP (defaults)

`ai_conversation` (on), `slang_mode` (on), `profanity_explanations` (on, gated by learner
`slang_comfort`), `pronunciation_recording` (on), `conversation_logging` (**off**),
`teacher_dashboard` (off), `cloud_sync` (off), `billing` (off), `experimental_packs` (off).
All read from the `feature_flags` table, overridable in Settings, honoring §6.3.
