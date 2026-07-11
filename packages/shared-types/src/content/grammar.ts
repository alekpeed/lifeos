import { z } from "zod";
import { CefrLevelSchema } from "../enums.js";
import { ExampleSchema } from "./example.js";

const CommonErrorSchema = z.object({
  wrong: z.string(),
  right: z.string(),
  note: z.string().optional(),
});

const DrillSchema = z.object({
  prompt: z.string(),
  answer: z.string(),
});

const LadderStepSchema = z.object({
  /** The full sentence with a blank for the changing slot, e.g. "Eu ___ brasileiro.". */
  prompt: z.string(),
  answer: z.string(),
  note: z.string().optional(),
});

/** A substitution ladder: one sentence pattern drilled across a sequence of slot values (e.g.
 * a verb conjugated across pronouns) so the learner produces the same structure repeatedly
 * with one thing changing at a time — closer to how a pattern is actually internalized than a
 * flat list of unrelated drills. */
const SubstitutionLadderSchema = z.object({
  key: z.string(),
  title: z.string(),
  /** The shared template, slot marked with {slot} — for display/authoring context. */
  pattern: z.string(),
  steps: z.array(LadderStepSchema).min(2),
});
export type SubstitutionLadder = z.infer<typeof SubstitutionLadderSchema>;

/** Grammar item — spec §11: includes drills and related vocabulary alongside the core
 * explanation. "related review items" is intentionally NOT part of this schema — it's
 * derived at query time by joining review_items on this item's key (see plan §4 notes). */
export const GrammarItemSchema = z.object({
  schemaVersion: z.literal(1),
  key: z.string(),
  title: z.string(),
  cefr: CefrLevelSchema.optional(),
  explanationMd: z.string(),
  examples: z.array(ExampleSchema).default([]),
  commonErrors: z.array(CommonErrorSchema).default([]),
  drills: z.array(DrillSchema).default([]),
  ladders: z.array(SubstitutionLadderSchema).default([]),
  relatedVocabulary: z.array(z.string()).default([]),
});
export type GrammarItem = z.infer<typeof GrammarItemSchema>;
