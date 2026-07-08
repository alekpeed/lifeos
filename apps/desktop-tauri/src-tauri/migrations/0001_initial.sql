-- Initial schema — plan §4. Every entity carries id/created_at/updated_at/schema_version
-- (spec §21). Pack-derived content tables are keyed by (pack_id, item_key) so pack
-- (re)import can upsert deterministically; user-owned tables never lose data on re-import.

PRAGMA foreign_keys = ON;

-- ---------- meta ----------
CREATE TABLE schema_meta (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

-- ---------- packs ----------
CREATE TABLE language_packs (
  id            TEXT PRIMARY KEY,
  base_pack_id  TEXT REFERENCES language_packs(id),
  name          TEXT NOT NULL,
  language_code TEXT NOT NULL,
  version       TEXT NOT NULL,
  schema_version INTEGER NOT NULL,
  manifest_json TEXT NOT NULL,
  installed_at  TEXT NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);

-- ---------- learner ----------
CREATE TABLE learner_profiles (
  id            TEXT PRIMARY KEY,
  display_name  TEXT NOT NULL,
  active_pack_id TEXT REFERENCES language_packs(id),
  goal          TEXT,
  target_dialect TEXT,
  real_speech_level TEXT NOT NULL DEFAULT 'informal',
  slang_severity_override INTEGER,
  cefr_estimate TEXT,
  correction_strictness TEXT NOT NULL DEFAULT 'balanced',
  settings_json TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);

-- ---------- content: vocabulary + core phrases ----------
CREATE TABLE vocabulary_items (
  id            TEXT PRIMARY KEY,
  pack_id       TEXT NOT NULL REFERENCES language_packs(id),
  item_key      TEXT NOT NULL,
  entry_type    TEXT NOT NULL DEFAULT 'word',
  lemma         TEXT NOT NULL,
  translation   TEXT NOT NULL,
  literal_meaning TEXT,
  natural_meaning TEXT,
  part_of_speech TEXT,
  frequency_rank INTEGER,
  ipa           TEXT,
  audio_text    TEXT,
  pronunciation_notes TEXT,
  register      TEXT,
  cefr          TEXT,
  tags_json     TEXT NOT NULL DEFAULT '[]',
  examples_json TEXT NOT NULL DEFAULT '[]',
  data_json     TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);
CREATE INDEX idx_vocabulary_pack ON vocabulary_items(pack_id);

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
  drills_json   TEXT NOT NULL DEFAULT '[]',
  related_vocabulary_json TEXT NOT NULL DEFAULT '[]',
  data_json     TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);
CREATE INDEX idx_grammar_pack ON grammar_items(pack_id);

-- ---------- content: slang / profanity / idiom (unified real-speech table) ----------
CREATE TABLE real_speech_items (
  id            TEXT PRIMARY KEY,
  pack_id       TEXT NOT NULL REFERENCES language_packs(id),
  item_key      TEXT NOT NULL,
  kind          TEXT NOT NULL,
  phrase        TEXT NOT NULL,
  literal       TEXT,
  natural       TEXT,
  register      TEXT NOT NULL,
  severity      INTEGER NOT NULL DEFAULT 1,
  who_uses      TEXT,
  usage_context TEXT,
  learner_should_use TEXT,
  safer_alternatives_json TEXT NOT NULL DEFAULT '[]',
  cultural_warning TEXT,
  examples_json TEXT NOT NULL DEFAULT '[]',
  regional_tag  TEXT,
  data_json     TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);
CREATE INDEX idx_real_speech_pack ON real_speech_items(pack_id);

-- ---------- content: dialogues ----------
CREATE TABLE dialogues (
  id            TEXT PRIMARY KEY,
  pack_id       TEXT NOT NULL REFERENCES language_packs(id),
  item_key      TEXT NOT NULL,
  scenario      TEXT NOT NULL,
  speakers_json TEXT NOT NULL DEFAULT '[]',
  target_level  TEXT,
  region_dialect TEXT,
  formality     TEXT,
  transcript_json TEXT NOT NULL,
  translation_json TEXT NOT NULL,
  key_vocabulary_json TEXT NOT NULL DEFAULT '[]',
  grammar_notes TEXT,
  slang_register_notes TEXT,
  audio_generation_instructions TEXT,
  data_json     TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);
CREATE INDEX idx_dialogues_pack ON dialogues(pack_id);

-- ---------- content: lessons (covers pronunciation-drill/listening/writing/roleplay/assessment) ----------
CREATE TABLE lessons (
  id            TEXT PRIMARY KEY,
  pack_id       TEXT NOT NULL REFERENCES language_packs(id),
  item_key      TEXT NOT NULL,
  lesson_type   TEXT NOT NULL,
  title         TEXT NOT NULL,
  cefr          TEXT,
  sequence      INTEGER,
  dialogue_id   TEXT REFERENCES dialogues(id),
  body_json     TEXT NOT NULL,
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);
CREATE INDEX idx_lessons_pack ON lessons(pack_id);

-- ---------- SRS ----------
CREATE TABLE review_items (
  id            TEXT PRIMARY KEY,
  profile_id    TEXT NOT NULL REFERENCES learner_profiles(id),
  item_type     TEXT NOT NULL,
  content_id    TEXT NOT NULL,
  difficulty    REAL,
  stability     REAL,
  retrievability REAL,
  state         TEXT NOT NULL DEFAULT 'new',
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
  rating        INTEGER NOT NULL,
  response_ms   INTEGER,
  confidence    INTEGER,
  reviewed_at   TEXT NOT NULL,
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL
);
CREATE INDEX idx_review_results_item ON review_results(review_item_id);

-- ---------- AI / conversation ----------
CREATE TABLE conversations (
  id            TEXT PRIMARY KEY,
  profile_id    TEXT NOT NULL REFERENCES learner_profiles(id),
  mode          TEXT NOT NULL,
  scenario      TEXT,
  title         TEXT,
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);

CREATE TABLE ai_messages (
  id            TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  role          TEXT NOT NULL,
  content       TEXT NOT NULL,
  correction_json TEXT,
  tokens        INTEGER,
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL
);
CREATE INDEX idx_ai_messages_conversation ON ai_messages(conversation_id);

-- ---------- pronunciation ----------
CREATE TABLE pronunciation_attempts (
  id            TEXT PRIMARY KEY,
  profile_id    TEXT NOT NULL REFERENCES learner_profiles(id),
  target_text   TEXT NOT NULL,
  transcript    TEXT,
  score         REAL,
  audio_path    TEXT,
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL
);

-- ---------- progress / events ----------
CREATE TABLE progress_events (
  id            TEXT PRIMARY KEY,
  profile_id    TEXT NOT NULL REFERENCES learner_profiles(id),
  kind          TEXT NOT NULL,
  payload_json  TEXT NOT NULL DEFAULT '{}',
  schema_version INTEGER NOT NULL,
  created_at    TEXT NOT NULL
);
CREATE INDEX idx_progress_events_profile ON progress_events(profile_id, created_at);

-- ---------- feature flags ----------
CREATE TABLE feature_flags (
  key           TEXT PRIMARY KEY,
  enabled       INTEGER NOT NULL DEFAULT 0,
  updated_at    TEXT NOT NULL
);

INSERT INTO schema_meta (key, value) VALUES ('db_version', '1');
