/**
 * Basic pronunciation correctness score — spec §17 Phase 1. This is a *comprehensibility
 * check*, not phoneme-level accuracy (plan risk 2): if STT heard roughly the target phrase,
 * the attempt was intelligible. Phase 4 replaces this with real pronunciation scoring.
 *
 * Method: normalize both strings (case, punctuation, diacritics — Whisper's diacritic
 * placement is unreliable and shouldn't fail a learner), then 1 - levenshtein/maxLen.
 */

/** Lowercase, strip diacritics and punctuation, collapse whitespace. */
export function normalizeForComparison(text: string): string {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "") // combining diacritical marks
    .replace(/[^\p{L}\p{N}\s]/gu, "")
    .replace(/\s+/g, " ")
    .trim();
}

export function levenshtein(a: string, b: string): number {
  if (a === b) return 0;
  if (a.length === 0) return b.length;
  if (b.length === 0) return a.length;

  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const curr = [i];
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      curr[j] = Math.min(curr[j - 1]! + 1, prev[j]! + 1, prev[j - 1]! + cost);
    }
    prev = curr;
  }
  return prev[b.length]!;
}

/** 0..1 similarity between what the learner was asked to say and what STT heard. */
export function scorePronunciation(target: string, transcript: string): number {
  const t = normalizeForComparison(target);
  const h = normalizeForComparison(transcript);
  if (t.length === 0 && h.length === 0) return 1;
  const maxLen = Math.max(t.length, h.length);
  if (maxLen === 0) return 1;
  return Math.max(0, 1 - levenshtein(t, h) / maxLen);
}
