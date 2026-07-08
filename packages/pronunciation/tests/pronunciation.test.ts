import { describe, expect, it, vi } from "vitest";
import {
  levenshtein,
  normalizeForComparison,
  scorePronunciation,
  WhisperProvider,
} from "../src/index.js";

describe("normalizeForComparison", () => {
  it("strips case, punctuation, diacritics, and extra whitespace", () => {
    expect(normalizeForComparison("  Olá, tudo BEM?! ")).toBe("ola tudo bem");
    expect(normalizeForComparison("pão")).toBe("pao");
    expect(normalizeForComparison("coração")).toBe("coracao");
  });
});

describe("levenshtein", () => {
  it("computes edit distance", () => {
    expect(levenshtein("kitten", "sitting")).toBe(3);
    expect(levenshtein("", "abc")).toBe(3);
    expect(levenshtein("same", "same")).toBe(0);
  });
});

describe("scorePronunciation", () => {
  it("scores an exact match 1 regardless of case/punct/diacritics", () => {
    expect(scorePronunciation("Quero um copo de água.", "quero um copo de agua")).toBe(1);
  });

  it("scores near-misses high and unrelated speech low", () => {
    const near = scorePronunciation("Oi, tudo bem?", "oi tudo bem?");
    const off = scorePronunciation("Oi, tudo bem?", "bom dia professor");
    expect(near).toBeGreaterThan(0.9);
    expect(off).toBeLessThan(0.4);
    expect(off).toBeGreaterThanOrEqual(0);
  });

  it("handles empty transcripts (silence) as a zero-ish score", () => {
    expect(scorePronunciation("pão de queijo", "")).toBe(0);
  });
});

describe("WhisperProvider", () => {
  it("posts multipart form data and returns the transcript", async () => {
    const fetchFn = vi.fn(async (url: unknown, init: unknown) => {
      expect(String(url)).toBe("https://api.openai.com/v1/audio/transcriptions");
      const req = init as { headers: Record<string, string>; body: FormData };
      expect(req.headers.authorization).toBe("Bearer sk-test");
      expect(req.body).toBeInstanceOf(FormData);
      expect(req.body.get("model")).toBe("whisper-1");
      expect(req.body.get("language")).toBe("pt");
      return new Response(JSON.stringify({ text: "quero um café" }), { status: 200 });
    });

    const provider = new WhisperProvider({ apiKey: "sk-test", language: "pt", fetchFn: fetchFn as unknown as typeof fetch });
    const transcript = await provider.transcribe(new Blob(["audio-bytes"]));
    expect(transcript).toBe("quero um café");
  });

  it("surfaces HTTP errors with a body snippet", async () => {
    const fetchFn = async () => new Response("bad audio", { status: 400 });
    const provider = new WhisperProvider({ apiKey: "sk-test", fetchFn: fetchFn as unknown as typeof fetch });
    await expect(provider.transcribe(new Blob())).rejects.toThrow(/400.*bad audio/s);
  });
});
