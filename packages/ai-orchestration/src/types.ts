/**
 * Provider abstraction — spec §8: no external AI provider is hard-coded. The OpenAI adapter
 * is the only MVP implementation; local LLMs and alternate providers slot in behind this
 * interface later (spec Phase 9).
 */
export interface AIMessage {
  role: "system" | "user" | "assistant";
  content: string;
}

export interface AICompletionRequest {
  messages: AIMessage[];
  /** Sampling temperature; tutors want low-ish values for consistency. */
  temperature?: number;
  /** Upper bound on generated tokens for this call (cost control, spec §7.3). */
  maxTokens?: number;
}

export interface AICompletionResult {
  text: string;
  /** Total tokens the provider reports for the call (prompt + completion), if known. */
  tokensUsed?: number;
}

export interface AIProvider {
  readonly name: string;
  complete(request: AICompletionRequest): Promise<AICompletionResult>;
}

/** Thrown when a conversation session would exceed its token budget (spec §7.3 cost controls). */
export class TokenCeilingExceeded extends Error {
  constructor(
    public readonly spent: number,
    public readonly ceiling: number,
  ) {
    super(`session token ceiling reached (${spent}/${ceiling}); start a new session or raise the ceiling`);
    this.name = "TokenCeilingExceeded";
  }
}

/** Thrown when the model's output cannot be parsed into the expected structured shape. */
export class MalformedModelOutput extends Error {
  constructor(
    message: string,
    public readonly raw: string,
  ) {
    super(message);
    this.name = "MalformedModelOutput";
  }
}
