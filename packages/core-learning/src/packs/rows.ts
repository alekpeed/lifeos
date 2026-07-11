import type {
  Dialogue,
  GrammarItem,
  Lesson,
  Manifest,
  PronunciationRule,
  RealSpeechItem,
  VocabularyItem,
} from "@polyglotai/shared-types";
import { contentId } from "../db/ids.js";
import type { Row } from "../db/upsert.js";

/**
 * Pure item→DB-row mappers. Each returns the column map for one content row, translating
 * camelCase schema fields to snake_case columns and JSON-stringifying array/object fields
 * (plan §4 stores variable-shape content as TEXT JSON). `now` is injected for deterministic
 * timestamps in tests.
 */

export function languagePackRow(manifest: Manifest, now: string): Row {
  return {
    id: manifest.id,
    base_pack_id: manifest.basePack ?? null,
    name: manifest.name,
    language_code: manifest.languageCode,
    version: manifest.packVersion,
    schema_version: manifest.schemaVersion,
    manifest_json: JSON.stringify(manifest),
    installed_at: now,
    created_at: now,
    updated_at: now,
  };
}

export function vocabularyRow(packId: string, item: VocabularyItem, now: string): Row {
  return {
    id: contentId(packId, item.key),
    pack_id: packId,
    item_key: item.key,
    entry_type: item.entryType,
    lemma: item.lemma,
    translation: item.translation,
    literal_meaning: item.literalMeaning ?? null,
    natural_meaning: item.naturalMeaning ?? null,
    part_of_speech: item.partOfSpeech ?? null,
    frequency_rank: item.frequencyRank ?? null,
    ipa: item.ipa ?? null,
    audio_text: item.audioText ?? null,
    pronunciation_notes: item.pronunciationNotes ?? null,
    register: item.register ?? null,
    cefr: item.cefr ?? null,
    tags_json: JSON.stringify(item.tags),
    examples_json: JSON.stringify(item.examples),
    data_json: "{}",
    schema_version: item.schemaVersion,
    created_at: now,
    updated_at: now,
  };
}

export function grammarRow(packId: string, item: GrammarItem, now: string): Row {
  return {
    id: contentId(packId, item.key),
    pack_id: packId,
    item_key: item.key,
    title: item.title,
    cefr: item.cefr ?? null,
    explanation_md: item.explanationMd,
    examples_json: JSON.stringify(item.examples),
    common_errors_json: JSON.stringify(item.commonErrors),
    drills_json: JSON.stringify(item.drills),
    ladders_json: JSON.stringify(item.ladders),
    related_vocabulary_json: JSON.stringify(item.relatedVocabulary),
    data_json: "{}",
    schema_version: item.schemaVersion,
    created_at: now,
    updated_at: now,
  };
}

/** Item key for one flattened ladder step — unique within the pack, stable across re-import
 * as long as the ladder key and step order don't change. */
function ladderStepKey(grammarKey: string, ladderKey: string, stepIndex: number): string {
  return `${grammarKey}::${ladderKey}::${stepIndex}`;
}

/** Flattens every grammar item's ladders into one row per step, so each step can carry its own
 * FSRS review schedule (a ladder is a sequence of individually-reviewable prompts, not one
 * atomic card). */
export function grammarLadderStepRows(packId: string, items: GrammarItem[], now: string): Row[] {
  const rows: Row[] = [];
  for (const item of items) {
    for (const ladder of item.ladders) {
      ladder.steps.forEach((step, stepIndex) => {
        const itemKey = ladderStepKey(item.key, ladder.key, stepIndex);
        rows.push({
          id: contentId(packId, itemKey),
          pack_id: packId,
          item_key: itemKey,
          grammar_item_id: contentId(packId, item.key),
          ladder_key: ladder.key,
          ladder_title: ladder.title,
          pattern: ladder.pattern,
          step_index: stepIndex,
          prompt: step.prompt,
          answer: step.answer,
          note: step.note ?? null,
          data_json: "{}",
          schema_version: item.schemaVersion,
          created_at: now,
          updated_at: now,
        });
      });
    }
  }
  return rows;
}

export function realSpeechRow(packId: string, item: RealSpeechItem, now: string): Row {
  return {
    id: contentId(packId, item.key),
    pack_id: packId,
    item_key: item.key,
    kind: item.kind,
    phrase: item.phrase,
    literal: item.literal ?? null,
    natural: item.natural ?? null,
    register: item.register,
    severity: item.severity,
    who_uses: item.whoUses ?? null,
    usage_context: item.usageContext ?? null,
    learner_should_use: item.learnerShouldUse,
    safer_alternatives_json: JSON.stringify(item.saferAlternatives),
    cultural_warning: item.warningNotes ?? null,
    examples_json: JSON.stringify(item.examples),
    regional_tag: item.region ?? null,
    data_json: "{}",
    schema_version: item.schemaVersion,
    created_at: now,
    updated_at: now,
  };
}

export function dialogueRow(packId: string, item: Dialogue, now: string): Row {
  return {
    id: contentId(packId, item.key),
    pack_id: packId,
    item_key: item.key,
    scenario: item.scenario,
    speakers_json: JSON.stringify(item.speakers),
    target_level: item.targetLevel ?? null,
    region_dialect: item.regionDialect ?? null,
    formality: item.formality ?? null,
    transcript_json: JSON.stringify(item.transcript),
    translation_json: JSON.stringify(item.translation),
    key_vocabulary_json: JSON.stringify(item.keyVocabulary),
    grammar_notes: item.grammarNotes ?? null,
    slang_register_notes: item.slangRegisterNotes ?? null,
    audio_generation_instructions: item.audioGenerationInstructions ?? null,
    data_json: "{}",
    schema_version: item.schemaVersion,
    created_at: now,
    updated_at: now,
  };
}

export function pronunciationRuleRow(packId: string, item: PronunciationRule, now: string): Row {
  return {
    id: contentId(packId, item.key),
    pack_id: packId,
    item_key: item.key,
    grapheme: item.grapheme,
    ipa: item.ipa,
    description: item.description,
    minimal_pairs_json: JSON.stringify(item.minimalPairs),
    data_json: "{}",
    schema_version: item.schemaVersion,
    created_at: now,
    updated_at: now,
  };
}

export function lessonRow(packId: string, item: Lesson, now: string): Row {
  return {
    id: contentId(packId, item.key),
    pack_id: packId,
    item_key: item.key,
    lesson_type: item.lessonType,
    title: item.title,
    cefr: item.cefr ?? null,
    sequence: item.sequence ?? null,
    dialogue_id: item.dialogueRef ? contentId(packId, item.dialogueRef) : null,
    body_json: JSON.stringify(item.body),
    schema_version: item.schemaVersion,
    created_at: now,
    updated_at: now,
  };
}
