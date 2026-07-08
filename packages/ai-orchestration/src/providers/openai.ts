import type { AICompletionRequest, AICompletionResult, AIProvider } from "../types.js";

export interface OpenAIProviderOptions {
  apiKey: string;
  /** Chat model id; the default balances tutoring quality against per-turn cost. */
  model?: string;
  baseUrl?: string;
  /** Injectable for tests and for future proxy routing; defaults to global fetch. */
  fetchFn?: typeof fetch;
}

interface ChatCompletionResponse {
  choices?: Array<{ message?: { content?: string } }>;
  usage?: { total_tokens?: number };
}

/**
 * OpenAI chat-completions adapter over plain fetch — no SDK, so it is trivially mockable,
 * webview-safe, and carries zero transitive weight. Only this file knows OpenAI's wire
 * format; everything above speaks AIProvider (spec §8 provider abstraction).
 */
export class OpenAIProvider implements AIProvider {
  readonly name = "openai";
  private readonly apiKey: string;
  private readonly model: string;
  private readonly baseUrl: string;
  private readonly fetchFn: typeof fetch;

  constructor(options: OpenAIProviderOptions) {
    if (!options.apiKey) throw new Error("OpenAIProvider requires an apiKey");
    this.apiKey = options.apiKey;
    this.model = options.model ?? "gpt-4o-mini";
    this.baseUrl = options.baseUrl ?? "https://api.openai.com/v1";
    this.fetchFn = options.fetchFn ?? fetch;
  }

  async complete(request: AICompletionRequest): Promise<AICompletionResult> {
    const response = await this.fetchFn(`${this.baseUrl}/chat/completions`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${this.apiKey}`,
      },
      body: JSON.stringify({
        model: this.model,
        messages: request.messages,
        temperature: request.temperature ?? 0.4,
        ...(request.maxTokens !== undefined ? { max_tokens: request.maxTokens } : {}),
      }),
    });

    if (!response.ok) {
      const body = (await response.text()).slice(0, 400);
      throw new Error(`OpenAI request failed (${response.status}): ${body}`);
    }

    const data = (await response.json()) as ChatCompletionResponse;
    const text = data.choices?.[0]?.message?.content;
    if (typeof text !== "string") {
      throw new Error("OpenAI response contained no message content");
    }

    const result: AICompletionResult = { text };
    if (typeof data.usage?.total_tokens === "number") result.tokensUsed = data.usage.total_tokens;
    return result;
  }
}
