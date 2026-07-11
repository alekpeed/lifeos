-- Adds substitution ladders (fill-the-slot conjugation/pattern drills, spec-adjacent to §11
-- grammar drills) as SRS-reviewable content in their own right, instead of living only inside
-- a grammar item's opaque drills_json. Additive migration (plan risk 4 upgrade path).
--
-- ladders_json on grammar_items carries the authored, nested pack data as-is (for
-- library/display use); grammar_ladder_steps is the flattened, individually-schedulable form
-- one row per ladder step, so each step gets its own FSRS review_items row (item_type
-- 'grammar_ladder') the same way a vocabulary item or grammar item does.

ALTER TABLE grammar_items ADD COLUMN ladders_json TEXT NOT NULL DEFAULT '[]';

CREATE TABLE grammar_ladder_steps (
  id              TEXT PRIMARY KEY,
  pack_id         TEXT NOT NULL REFERENCES language_packs(id),
  item_key        TEXT NOT NULL,
  grammar_item_id TEXT NOT NULL REFERENCES grammar_items(id),
  ladder_key      TEXT NOT NULL,
  ladder_title    TEXT NOT NULL,
  pattern         TEXT NOT NULL,
  step_index      INTEGER NOT NULL,
  prompt          TEXT NOT NULL,
  answer          TEXT NOT NULL,
  note            TEXT,
  data_json       TEXT NOT NULL DEFAULT '{}',
  schema_version  INTEGER NOT NULL,
  created_at      TEXT NOT NULL,
  updated_at      TEXT NOT NULL,
  UNIQUE(pack_id, item_key)
);
CREATE INDEX idx_grammar_ladder_steps_pack ON grammar_ladder_steps(pack_id);
CREATE INDEX idx_grammar_ladder_steps_grammar_item ON grammar_ladder_steps(grammar_item_id);

UPDATE schema_meta SET value = '3' WHERE key = 'db_version';
