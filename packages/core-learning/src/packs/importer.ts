import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { Database } from "../db/database.js";
import { buildDeleteStale, buildUpsert, type Row } from "../db/upsert.js";
import {
  dialogueRow,
  grammarLadderStepRows,
  grammarRow,
  languagePackRow,
  lessonRow,
  pronunciationRuleRow,
  realSpeechRow,
  vocabularyRow,
} from "./rows.js";

export interface ImportPackOptions {
  /** Injected clock for deterministic timestamps; defaults to wall-clock ISO-8601. */
  now?: () => string;
}

export interface ImportPackResult {
  packId: string;
  counts: Record<string, number>;
}

// Columns never overwritten on re-import: the content id and its natural key are stable, and
// created_at records first import. Everything else (including updated_at) refreshes.
const CONTENT_IMMUTABLE = ["id", "created_at"];
const CONTENT_CONFLICT = ["pack_id", "item_key"];

/**
 * Imports a validated, in-memory pack (from language-pack-sdk's loadPack) into the SQLite
 * content tables. Idempotent: every write is an upsert keyed by (pack_id, item_key), and stale
 * rows a pack dropped are deleted afterward — so re-importing the same pack is a no-op and
 * re-importing an edited pack converges to the new content without orphaning user review
 * history (plan §4, Milestone A step 5 acceptance).
 */
export async function importPack(
  db: Database,
  pack: LoadedPack,
  options: ImportPackOptions = {},
): Promise<ImportPackResult> {
  const now = options.now ?? (() => new Date().toISOString());
  const packId = pack.manifest.id;

  const upsertAll = async (tx: Database, table: string, rows: Row[]) => {
    for (const row of rows) {
      const { sql, params } = buildUpsert(table, row, CONTENT_CONFLICT, CONTENT_IMMUTABLE);
      await tx.run(sql, params);
    }
  };

  const deleteStale = async (tx: Database, table: string, keys: string[]) => {
    const { sql, params } = buildDeleteStale(table, packId, keys);
    await tx.run(sql, params);
  };

  await db.transaction(async (tx) => {
    // The language_packs row must exist before content rows (FK target).
    const ts = now();
    const packRow = buildUpsert(
      "language_packs",
      languagePackRow(pack.manifest, ts),
      ["id"],
      ["id", "created_at"],
    );
    await tx.run(packRow.sql, packRow.params);

    await upsertAll(tx, "vocabulary_items", pack.vocabulary.map((i) => vocabularyRow(packId, i, ts)));
    await upsertAll(tx, "grammar_items", pack.grammar.map((i) => grammarRow(packId, i, ts)));
    await upsertAll(tx, "real_speech_items", pack.realSpeech.map((i) => realSpeechRow(packId, i, ts)));
    await upsertAll(tx, "dialogues", pack.dialogues.map((i) => dialogueRow(packId, i, ts)));
    await upsertAll(
      tx,
      "pronunciation_rules",
      pack.pronunciation.map((i) => pronunciationRuleRow(packId, i, ts)),
    );
    // Lessons reference dialogues via dialogue_id, so import dialogues first (done above).
    await upsertAll(tx, "lessons", pack.lessons.map((i) => lessonRow(packId, i, ts)));

    // Flattened one row per ladder step, derived from grammar_items.ladders_json (imported
    // just above so the grammar_item_id FK target already exists).
    const ladderStepRows = grammarLadderStepRows(packId, pack.grammar, ts);
    await upsertAll(tx, "grammar_ladder_steps", ladderStepRows);

    // Remove content dropped from this pack since a previous import. Delete lessons before
    // dialogues so a lesson's FK to a now-removed dialogue never blocks the dialogue delete.
    await deleteStale(tx, "lessons", pack.lessons.map((i) => i.key));
    await deleteStale(tx, "vocabulary_items", pack.vocabulary.map((i) => i.key));
    await deleteStale(tx, "grammar_ladder_steps", ladderStepRows.map((r) => r.item_key as string));
    await deleteStale(tx, "grammar_items", pack.grammar.map((i) => i.key));
    await deleteStale(tx, "real_speech_items", pack.realSpeech.map((i) => i.key));
    await deleteStale(tx, "dialogues", pack.dialogues.map((i) => i.key));
    await deleteStale(tx, "pronunciation_rules", pack.pronunciation.map((i) => i.key));
  });

  return {
    packId,
    counts: {
      vocabulary: pack.vocabulary.length,
      grammar: pack.grammar.length,
      realSpeech: pack.realSpeech.length,
      dialogues: pack.dialogues.length,
      pronunciation: pack.pronunciation.length,
      lessons: pack.lessons.length,
      grammarLadderSteps: pack.grammar.reduce((n, i) => n + i.ladders.reduce((m, l) => m + l.steps.length, 0), 0),
    },
  };
}
