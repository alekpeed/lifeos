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
  relatedVocabulary: z.array(z.string()).default([]),
});
export type GrammarItem = z.infer<typeof GrammarItemSchema>;
