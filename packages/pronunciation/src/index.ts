/**
 * SpeechProvider (STT) — spec §17 Phase 1: record, transcribe, diff against target text for
 * a basic correctness score. Implemented via an OpenAI Whisper adapter in Milestone C step 14.
 */
export interface SpeechProvider {
  readonly name: string;
  transcribe(audio: Blob): Promise<string>;
}

/**
 * TTSProvider — spec §12. Interface only; unimplemented in MVP. §12's general voices and
 * dialect-specific voice selection are Phase 4 work, not required by the §23 MVP scope.
 */
export interface TTSProvider {
  readonly name: string;
  synthesize(text: string, voice: { languageCode: string }): Promise<Blob>;
}

export * from "./score.js";
export * from "./providers/whisper.js";
