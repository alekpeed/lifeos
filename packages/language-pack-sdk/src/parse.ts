import {
  AiPromptTemplateSchema,
  DialogueSchema,
  GrammarItemSchema,
  LessonSchema,
  ManifestSchema,
  PronunciationRuleSchema,
  RealSpeechItemSchema,
  VocabularyItemSchema,
  type AiPromptTemplate,
  type Dialogue,
  type GrammarItem,
  type Lesson,
  type Manifest,
  type PronunciationRule,
  type RealSpeechItem,
  type VocabularyItem,
} from "@polyglotai/shared-types";
import type { PackFileReader } from "./reader.js";

/**
 * The parsed, schema-valid contents of a single pack directory, grouped by category.
 * Vocabulary includes core phrases (distinguished by `entryType`); lessons include
 * assessments/roleplay/listening/writing (distinguished by `lessonType`) — matching how the
 * §10.1 content categories map onto shared tables (plan §4, §6).
 */
export interface ParsedPackData {
  manifest: Manifest;
  vocabulary: VocabularyItem[];
  grammar: GrammarItem[];
  realSpeech: RealSpeechItem[];
  dialogues: Dialogue[];
  pronunciation: PronunciationRule[];
  lessons: Lesson[];
  /** Pack-authored AI prompt templates (spec §11). Kept in memory, not imported into the DB —
   * the orchestrator reads them straight off the loaded pack. */
  aiPrompts: AiPromptTemplate[];
}

export interface ParseResult {
  /** Present even when there are errors, so callers can still report volume/semantics. */
  data: ParsedPackData;
  /** Schema-level errors (bad manifest, malformed item, non-array file). */
  errors: string[];
}

async function readJsonArray(reader: PackFileReader, path: string): Promise<unknown[]> {
  const text = await reader.readText(path);
  const parsed = JSON.parse(text);
  if (!Array.isArray(parsed)) {
    throw new Error(`${path}: expected a JSON array of items`);
  }
  return parsed;
}

/**
 * Reads and schema-validates every content file a pack manifest declares. Never throws for
 * content problems — it accumulates them in `errors` so validation and loading can share one
 * parse pass. Only a structurally unreadable/invalid manifest short-circuits.
 */
export async function parsePack(reader: PackFileReader): Promise<ParseResult> {
  const errors: string[] = [];

  let manifest: Manifest;
  try {
    manifest = ManifestSchema.parse(JSON.parse(await reader.readText("manifest.json")));
  } catch (err) {
    throw new Error(`manifest.json: ${(err as Error).message}`);
  }

  const data: ParsedPackData = {
    manifest,
    vocabulary: [],
    grammar: [],
    realSpeech: [],
    dialogues: [],
    pronunciation: [],
    lessons: [],
    aiPrompts: [],
  };

  const loadCategory = async <T>(
    files: string[] | undefined,
    schema: { parse: (v: unknown) => T },
    sink: T[],
  ) => {
    for (const file of files ?? []) {
      let rows: unknown[];
      try {
        rows = await readJsonArray(reader, file);
      } catch (err) {
        errors.push(`${file}: ${(err as Error).message}`);
        continue;
      }
      for (const [i, row] of rows.entries()) {
        try {
          sink.push(schema.parse(row));
        } catch (err) {
          errors.push(`${file}[${i}]: ${(err as Error).message}`);
        }
      }
    }
  };

  const { contents } = manifest;
  await loadCategory(contents.vocabulary, VocabularyItemSchema, data.vocabulary);
  await loadCategory(contents.phrases, VocabularyItemSchema, data.vocabulary);
  await loadCategory(contents.grammar, GrammarItemSchema, data.grammar);
  await loadCategory(contents.slang, RealSpeechItemSchema, data.realSpeech);
  await loadCategory(contents.profanity, RealSpeechItemSchema, data.realSpeech);
  await loadCategory(contents.idioms, RealSpeechItemSchema, data.realSpeech);
  await loadCategory(contents.dialogues, DialogueSchema, data.dialogues);
  await loadCategory(contents.pronunciation, PronunciationRuleSchema, data.pronunciation);
  await loadCategory(contents.lessons, LessonSchema, data.lessons);
  await loadCategory(contents.assessments, LessonSchema, data.lessons);
  await loadCategory(contents.aiPrompts, AiPromptTemplateSchema, data.aiPrompts);

  return { data, errors };
}

/** Duplicate-key and dangling-cross-reference checks over already-parsed data. */
export function semanticErrors(data: ParsedPackData): string[] {
  const errors: string[] = [];

  const checkDuplicates = (items: Array<{ key: string }>, label: string) => {
    const seen = new Set<string>();
    for (const item of items) {
      if (seen.has(item.key)) errors.push(`duplicate ${label} key: ${item.key}`);
      seen.add(item.key);
    }
  };
  checkDuplicates(data.vocabulary, "vocabulary");
  checkDuplicates(data.grammar, "grammar");
  checkDuplicates(data.realSpeech, "slang/profanity/idiom");
  checkDuplicates(data.dialogues, "dialogue");
  checkDuplicates(data.pronunciation, "pronunciation");
  checkDuplicates(data.lessons, "lesson");
  checkDuplicates(data.aiPrompts, "ai-prompt");

  const vocabKeys = new Set(data.vocabulary.map((v) => v.key));
  const dialogueKeys = new Set(data.dialogues.map((d) => d.key));
  for (const g of data.grammar) {
    for (const ref of g.relatedVocabulary) {
      if (!vocabKeys.has(ref)) errors.push(`grammar ${g.key}: dangling relatedVocabulary "${ref}"`);
    }
  }
  for (const d of data.dialogues) {
    for (const ref of d.keyVocabulary) {
      if (!vocabKeys.has(ref)) errors.push(`dialogue ${d.key}: dangling keyVocabulary "${ref}"`);
    }
  }
  for (const l of data.lessons) {
    if (l.dialogueRef && !dialogueKeys.has(l.dialogueRef)) {
      errors.push(`lesson ${l.key}: dangling dialogueRef "${l.dialogueRef}"`);
    }
  }

  return errors;
}
