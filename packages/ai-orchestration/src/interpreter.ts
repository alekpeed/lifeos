import { z } from "zod";
import { extractJson } from "./correction.js";
import { buildSystemPrompt, type LearnerContext } from "./policy.js";
import { MalformedModelOutput, TokenCeilingExceeded, type AIProvider } from "./types.js";

/**
 * Live interpreter mode (unhinged idea #1): a short two-speaker dialogue, generated once and
 * alternating strictly between the target language and English, that the learner interprets
 * turn by turn under time pressure. Speaker A always speaks the target language, speaker B
 * always speaks English — fixed client-side rather than trusted to the model, so the UI always
 * knows which direction each turn needs interpreting.
 */
export const DEFAULT_INTERPRETER_DIALOGUE_TEMPLATE = `
Generate a short, natural back-and-forth dialogue between two speakers, A and B, about: {{topic}}.

Speaker A always speaks in {{targetLanguage}}{{dialectSuffix}}. Speaker B always speaks in English.
Produce exactly {{turnCount}} turns total, strictly alternating A, B, A, B, and so on, starting with A.
Keep each line short (one sentence) and natural for spoken conversation, matched to an adult learner
around the level described below.

Respond with ONLY a JSON object (no prose, no code fences):
{"turns": [{"speaker": "A", "text": "..."}, {"speaker": "B", "text": "..."}]}
`.trim();

export const DEFAULT_INTERPRETER_GRADING_TEMPLATE = `
A speaker said the following line in {{sourceLanguage}}:
"{{originalText}}"

An adult learner is practicing live interpretation and rendered it in {{interpretLanguage}} as:
"{{userInterpretation}}"

Score the interpretation's accuracy and naturalness from 1 (way off) to 5 (excellent, native-level).
Respond with ONLY a JSON object (no prose, no code fences):
{"score": <integer 1-5>, "feedback": "<one short sentence of feedback>", "modelAnswer": "<a strong interpretation>"}
`.trim();

const DialogueResponseSchema = z.object({
  turns: z.array(z.object({ speaker: z.enum(["A", "B"]), text: z.string().min(1) })).min(2),
});

const GradeResponseSchema = z.object({
  score: z.number().int().min(1).max(5),
  feedback: z.string(),
  modelAnswer: z.string(),
});

export interface InterpreterTurn {
  index: number;
  speaker: "A" | "B";
  /** "target" = the language being learned (speaker A); "native" = English (speaker B, MVP). */
  language: "target" | "native";
  text: string;
}

export interface InterpretationGrade {
  score: 1 | 2 | 3 | 4 | 5;
  feedback: string;
  modelAnswer: string;
}

export interface InterpreterOptions {
  topic: string;
  ctx: LearnerContext;
  /** Total dialogue turns to generate. Default 6 keeps the one generation call cheap. */
  turnCount?: number;
  /** Hard budget across dialogue generation + every per-turn grading call (cost control,
   * mirrors ConversationSession's tokenCeiling). */
  tokenCeiling?: number;
  estimateTokens?: (text: string) => number;
}

const DEFAULT_ESTIMATE = (text: string) => Math.ceil(text.length / 4);

/**
 * One generation call produces the full dialogue up front (no streaming support on
 * AIProvider); grading is a separate, bounded call per turn, issued only when the learner
 * submits an interpretation for that turn — so cost scales with turns actually attempted.
 */
export class InterpreterSession {
  private readonly systemPrompt: string;
  private readonly turnCount: number;
  private readonly tokenCeiling: number;
  private readonly estimate: (text: string) => number;
  private spent = 0;
  private turns: InterpreterTurn[] = [];

  constructor(
    private readonly provider: AIProvider,
    private readonly options: InterpreterOptions,
  ) {
    this.systemPrompt = buildSystemPrompt(
      "You generate natural short dialogues for a language-interpretation exercise.",
      options.ctx,
    );
    this.turnCount = options.turnCount ?? 6;
    this.tokenCeiling = options.tokenCeiling ?? 20_000;
    this.estimate = options.estimateTokens ?? DEFAULT_ESTIMATE;
  }

  get tokensSpent(): number {
    return this.spent;
  }

  private guardCeiling(): void {
    if (this.spent >= this.tokenCeiling) throw new TokenCeilingExceeded(this.spent, this.tokenCeiling);
  }

  /** Generates and caches the dialogue; calling again returns the same cached turns. */
  async generateDialogue(): Promise<InterpreterTurn[]> {
    if (this.turns.length > 0) return this.turns;
    this.guardCeiling();

    const dialect = this.options.ctx.dialect ? ` (${this.options.ctx.dialect} dialect)` : "";
    const task = DEFAULT_INTERPRETER_DIALOGUE_TEMPLATE.replace("{{topic}}", this.options.topic)
      .replace(/\{\{targetLanguage\}\}/g, this.options.ctx.targetLanguage)
      .replace("{{dialectSuffix}}", dialect)
      .replace("{{turnCount}}", String(this.turnCount));

    const { text: raw, tokensUsed } = await this.provider.complete({
      messages: [
        { role: "system", content: this.systemPrompt },
        { role: "user", content: task },
      ],
      temperature: 0.8,
    });
    this.spent += tokensUsed ?? this.estimate(this.systemPrompt) + this.estimate(task) + this.estimate(raw);

    let parsed: unknown;
    try {
      parsed = JSON.parse(extractJson(raw));
    } catch {
      throw new MalformedModelOutput("model reply was not valid JSON", raw);
    }
    const result = DialogueResponseSchema.safeParse(parsed);
    if (!result.success) {
      const issues = result.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ");
      throw new MalformedModelOutput(`model JSON did not match the dialogue schema (${issues})`, raw);
    }

    this.turns = result.data.turns.slice(0, this.turnCount).map((t, index) => ({
      index,
      speaker: t.speaker,
      language: t.speaker === "A" ? "target" : "native",
      text: t.text,
    }));
    return this.turns;
  }

  /** Grades one turn's interpretation via a single bounded AI call. */
  async gradeTurn(turn: InterpreterTurn, userInterpretation: string): Promise<InterpretationGrade> {
    this.guardCeiling();

    const sourceLanguage = turn.language === "target" ? this.options.ctx.targetLanguage : "English";
    const interpretLanguage = turn.language === "target" ? "English" : this.options.ctx.targetLanguage;
    const task = DEFAULT_INTERPRETER_GRADING_TEMPLATE.replace("{{sourceLanguage}}", sourceLanguage)
      .replace("{{originalText}}", turn.text)
      .replace("{{interpretLanguage}}", interpretLanguage)
      .replace("{{userInterpretation}}", userInterpretation);
    const gradingSystemPrompt = buildSystemPrompt(
      "You grade interpretation practice for an adult learner. You return only strict JSON.",
      this.options.ctx,
    );

    const { text: raw, tokensUsed } = await this.provider.complete({
      messages: [
        { role: "system", content: gradingSystemPrompt },
        { role: "user", content: task },
      ],
      temperature: 0.2,
    });
    this.spent += tokensUsed ?? this.estimate(gradingSystemPrompt) + this.estimate(task) + this.estimate(raw);

    let parsed: unknown;
    try {
      parsed = JSON.parse(extractJson(raw));
    } catch {
      throw new MalformedModelOutput("model reply was not valid JSON", raw);
    }
    const result = GradeResponseSchema.safeParse(parsed);
    if (!result.success) {
      const issues = result.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ");
      throw new MalformedModelOutput(`model JSON did not match the grading schema (${issues})`, raw);
    }
    return result.data as InterpretationGrade;
  }
}
