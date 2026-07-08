import { z } from "zod";
import { LearnerShouldUseSchema, RealSpeechKindSchema, RegisterSchema, SeveritySchema } from "../enums.js";
import { ExampleSchema } from "./example.js";

/**
 * Real-speech item (slang / profanity / idiom / euphemism / taboo) — spec §11 and §13.
 * This is a first-class content type, not an afterthought: every item must explain literal
 * meaning, real meaning, who uses it, usage context, and whether the learner should use it
 * or only recognize it.
 */
export const RealSpeechItemSchema = z.object({
  schemaVersion: z.literal(1),
  key: z.string(),
  kind: RealSpeechKindSchema,
  phrase: z.string(),
  literal: z.string().optional(),
  natural: z.string().optional(),
  register: RegisterSchema,
  severity: SeveritySchema,
  region: z.string().nullable().optional(), // null = explicitly nationwide, not "unspecified"
  whoUses: z.string().optional(),
  usageContext: z.string().optional(),
  learnerShouldUse: LearnerShouldUseSchema,
  saferAlternatives: z.array(z.string()).default([]),
  warningNotes: z.string().nullable().optional(),
  examples: z.array(ExampleSchema).default([]),
});
export type RealSpeechItem = z.infer<typeof RealSpeechItemSchema>;
