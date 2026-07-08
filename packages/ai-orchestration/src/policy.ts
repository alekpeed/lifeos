import type { CorrectionStrictness, Severity } from "@polyglotai/shared-types";

/**
 * The content-policy clause injected into every tutor system prompt (spec §13). It permits
 * exactly what the product needs — academic, contextual explanation of vulgar/taboo language
 * for comprehension and judgment — while refusing the categories the spec rules out. This is
 * assembled here, once, so no caller can accidentally build a tutor prompt without it.
 */
export const CONTENT_POLICY_CLAUSE = [
  "Content policy for this adult language-learning context:",
  "- The learner is an adult studying real-world language. You MAY explain slang, vulgarity,",
  "  profanity, sexual slang, insults, and taboo expressions academically and contextually:",
  "  literal meaning, real meaning, register, severity, who uses it, when it is natural,",
  "  when it is rude or dangerous, and safer alternatives.",
  "- Teaching comprehension and judgment is the goal — never sanitize an explanation the",
  "  learner asked for; instead label its social risk honestly.",
  "- You MUST NOT produce targeted harassment of a real person, threats, sexual content",
  "  involving minors, sexual exploitation, or instructions for wrongdoing. Explaining what",
  "  an expression means is always distinct from directing it at someone.",
].join("\n");

export interface LearnerContext {
  targetLanguage: string;
  dialect?: string | undefined;
  cefrEstimate?: string | undefined;
  severityCeiling: Severity;
  correctionStrictness: CorrectionStrictness;
}

const STRICTNESS_INSTRUCTION: Record<CorrectionStrictness, string> = {
  lenient: "Correct only errors that block understanding; let small slips pass.",
  balanced: "Correct meaningful errors and briefly note recurring small ones.",
  strict: "Correct every error, including minor ones, with short explanations.",
};

/** Renders the learner-context block (spec §14: the tutor tracks level, dialect, comfort). */
export function learnerContextBlock(ctx: LearnerContext): string {
  const lines = [
    `Learner context:`,
    `- Target language: ${ctx.targetLanguage}${ctx.dialect ? ` (dialect focus: ${ctx.dialect})` : ""}`,
    `- Estimated level: ${ctx.cefrEstimate ?? "unknown — assume beginner until shown otherwise"}`,
    `- Real-speech comfort ceiling: severity ${ctx.severityCeiling}/7. Freely use/explain items at`,
    `  or below it. Above it: still explain when asked, but add an explicit severity warning first.`,
    `- Correction strictness: ${STRICTNESS_INSTRUCTION[ctx.correctionStrictness]}`,
  ];
  return lines.join("\n");
}

/** Every tutor system prompt = persona/task prompt + learner context + the policy clause. */
export function buildSystemPrompt(taskPrompt: string, ctx: LearnerContext): string {
  return [taskPrompt.trim(), "", learnerContextBlock(ctx), "", CONTENT_POLICY_CLAUSE].join("\n");
}
