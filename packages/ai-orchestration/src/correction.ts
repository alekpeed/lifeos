import { AiCorrectionSchema, type AiCorrection } from "@polyglotai/shared-types";
import { buildSystemPrompt, type LearnerContext } from "./policy.js";
import { renderTemplate } from "./template.js";
import { MalformedModelOutput, type AIProvider } from "./types.js";

/**
 * Default correction task prompt, used when the active pack ships no
 * `prompt.tutor.correction` template. Mirrors the spec §6 correction contract: corrected /
 * natural / formal / casual / slang-native versions plus grammar, register, and
 * pronunciation notes, returned as strict JSON.
 */
export const DEFAULT_CORRECTION_TEMPLATE = `
You are an expert {{targetLanguage}} tutor correcting an adult learner's sentence.

Learner's text:
{{text}}

Respond with ONLY a JSON object (no prose, no code fences) with these fields:
- "corrected": the corrected sentence
- "literal": word-for-word English meaning of the corrected sentence
- "natural": how a native speaker would naturally express the idea
- "formal": a formal-register version
- "casual": a casual-register version
- "slangNative": a slangy/native-street version when one exists, otherwise omit
- "grammarExplanation": brief explanation of each fix
- "registerExplanation": when each version is appropriate
- "pronunciationNotes": pitfalls a learner is likely to hit saying the corrected sentence
- "futureReviewItemKeys": [] (reserved; return an empty array)
`.trim();

/** Strips markdown code fences if the model wrapped its JSON despite instructions. */
export function extractJson(raw: string): string {
  const trimmed = raw.trim();
  const fenced = /^```(?:json)?\s*([\s\S]*?)\s*```$/m.exec(trimmed);
  return fenced?.[1] ?? trimmed;
}

export interface CorrectionEngineOptions {
  /** Pack-provided template (spec §11 ai-prompts) overriding the built-in default. */
  template?: string;
}

/**
 * Turns a learner sentence into the structured §6 correction via the configured provider.
 * The system prompt always carries the learner context and the §13 content-policy clause;
 * output is validated with Zod so a malformed model reply surfaces as MalformedModelOutput
 * instead of leaking garbage into the UI or the review pipeline.
 */
export class CorrectionEngine {
  constructor(
    private readonly provider: AIProvider,
    private readonly options: CorrectionEngineOptions = {},
  ) {}

  async correct(text: string, ctx: LearnerContext): Promise<AiCorrection> {
    const template = this.options.template ?? DEFAULT_CORRECTION_TEMPLATE;
    const task = renderTemplate(template, {
      targetLanguage: ctx.targetLanguage,
      text,
    });

    const { text: raw } = await this.provider.complete({
      messages: [
        { role: "system", content: buildSystemPrompt("You return only strict JSON.", ctx) },
        { role: "user", content: task },
      ],
      temperature: 0.2,
    });

    let parsed: unknown;
    try {
      parsed = JSON.parse(extractJson(raw));
    } catch {
      throw new MalformedModelOutput("model reply was not valid JSON", raw);
    }

    const result = AiCorrectionSchema.safeParse(parsed);
    if (!result.success) {
      const issues = result.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ");
      throw new MalformedModelOutput(`model JSON did not match the correction schema (${issues})`, raw);
    }
    return result.data;
  }
}
