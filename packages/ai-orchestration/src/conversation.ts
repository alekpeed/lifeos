import { buildSystemPrompt, type LearnerContext } from "./policy.js";
import { TokenCeilingExceeded, type AIMessage, type AIProvider } from "./types.js";

/**
 * Default conversation-partner task prompt (spec §14 conversation-partner /
 * roleplay-partner modes), used when the pack ships no `prompt.tutor.conversation` template.
 */
export const DEFAULT_CONVERSATION_TEMPLATE = `
You are a native {{targetLanguage}} speaker in a roleplay with an adult learner.
Scenario: {{scenario}}.

Stay in character and in {{targetLanguage}}. Keep replies to 1-3 short sentences matched to
the learner's level. If the learner makes an error that matters, finish your in-character
reply, then add one short correction line starting with "✎ " (in English). If the learner
writes in English, gently steer back to {{targetLanguage}}.
`.trim();

export interface ConversationOptions {
  /** Task prompt (pack template or default) already rendered with scenario/language. */
  taskPrompt: string;
  ctx: LearnerContext;
  /** How many recent turns ride along in each request (context cap, spec §7.3). */
  maxContextTurns?: number;
  /** Hard per-session token budget; hitting it throws TokenCeilingExceeded (spec §7.3). */
  tokenCeiling?: number;
  /** Fallback token estimator when the provider reports no usage (chars/4 heuristic). */
  estimateTokens?: (text: string) => number;
}

const DEFAULT_ESTIMATE = (text: string) => Math.ceil(text.length / 4);

/**
 * A multi-turn conversation with capped context and a hard token ceiling. The full transcript
 * is kept locally for display/persistence; only the last `maxContextTurns` messages are sent
 * to the provider, so a long chat can't silently grow into an expensive prompt.
 */
export class ConversationSession {
  private readonly systemPrompt: string;
  private readonly maxContextTurns: number;
  private readonly tokenCeiling: number;
  private readonly estimate: (text: string) => number;
  private readonly history: AIMessage[] = [];
  private spent = 0;

  constructor(
    private readonly provider: AIProvider,
    options: ConversationOptions,
  ) {
    this.systemPrompt = buildSystemPrompt(options.taskPrompt, options.ctx);
    this.maxContextTurns = options.maxContextTurns ?? 12;
    this.tokenCeiling = options.tokenCeiling ?? 20_000;
    this.estimate = options.estimateTokens ?? DEFAULT_ESTIMATE;
  }

  get transcript(): readonly AIMessage[] {
    return this.history;
  }

  get tokensSpent(): number {
    return this.spent;
  }

  async send(userText: string): Promise<string> {
    if (this.spent >= this.tokenCeiling) throw new TokenCeilingExceeded(this.spent, this.tokenCeiling);

    this.history.push({ role: "user", content: userText });
    const window = this.history.slice(-this.maxContextTurns);

    const result = await this.provider.complete({
      messages: [{ role: "system", content: this.systemPrompt }, ...window],
      temperature: 0.7,
    });

    const sent = [this.systemPrompt, ...window.map((m) => m.content)].join("\n");
    this.spent += result.tokensUsed ?? this.estimate(sent) + this.estimate(result.text);

    this.history.push({ role: "assistant", content: result.text });
    return result.text;
  }
}
