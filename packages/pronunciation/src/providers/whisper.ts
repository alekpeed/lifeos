import type { SpeechProvider } from "../index.js";

export interface WhisperProviderOptions {
  apiKey: string;
  model?: string;
  baseUrl?: string;
  /** BCP-47-ish language hint passed to Whisper (e.g. "pt"). */
  language?: string;
  fetchFn?: typeof fetch;
}

/**
 * OpenAI Whisper transcription adapter (spec §17 Phase 1 STT). Plain fetch + FormData —
 * webview-safe, mockable, and only this file knows the wire format (spec §8 adapters).
 */
export class WhisperProvider implements SpeechProvider {
  readonly name = "openai-whisper";
  private readonly apiKey: string;
  private readonly model: string;
  private readonly baseUrl: string;
  private readonly language: string | undefined;
  private readonly fetchFn: typeof fetch;

  constructor(options: WhisperProviderOptions) {
    if (!options.apiKey) throw new Error("WhisperProvider requires an apiKey");
    this.apiKey = options.apiKey;
    this.model = options.model ?? "whisper-1";
    this.baseUrl = options.baseUrl ?? "https://api.openai.com/v1";
    this.language = options.language;
    this.fetchFn = options.fetchFn ?? fetch;
  }

  async transcribe(audio: Blob): Promise<string> {
    const form = new FormData();
    form.append("file", audio, "recording.webm");
    form.append("model", this.model);
    if (this.language) form.append("language", this.language);
    form.append("response_format", "json");

    const response = await this.fetchFn(`${this.baseUrl}/audio/transcriptions`, {
      method: "POST",
      headers: { authorization: `Bearer ${this.apiKey}` },
      body: form,
    });

    if (!response.ok) {
      const body = (await response.text()).slice(0, 400);
      throw new Error(`Whisper request failed (${response.status}): ${body}`);
    }

    const data = (await response.json()) as { text?: string };
    if (typeof data.text !== "string") throw new Error("Whisper response contained no text");
    return data.text;
  }
}
