import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { Manifest, RealSpeechItem, VocabularyItem } from "@polyglotai/shared-types";
import { describe, expect, it } from "vitest";
import { createRepos } from "../src/repos.js";
import { runOnboarding } from "../src/services/onboarding.js";
import { loadReviewCard } from "../src/services/reviewCard.js";
import { createMigratedDb } from "./nodeSqliteDb.js";

const T0 = new Date("2026-07-08T00:00:00.000Z");

function pack(): LoadedPack {
  const manifest: Manifest = {
    schemaVersion: 1,
    id: "pt-br",
    name: "BR",
    languageCode: "pt-BR",
    packVersion: "0.1.0",
    basePack: null,
    authors: [],
    dialects: [],
    featureFlags: {},
    contents: {},
  };
  const vocab: VocabularyItem[] = [
    { schemaVersion: 1, key: "vocab.agua", entryType: "word", lemma: "água", translation: "water", tags: [], examples: [] },
  ];
  const realSpeech: RealSpeechItem[] = [
    {
      schemaVersion: 1,
      key: "profanity.merda",
      kind: "profanity",
      phrase: "merda",
      natural: "shit / crap",
      register: "vulgar",
      severity: 4,
      learnerShouldUse: "recognize-only",
      saferAlternatives: [],
      warningNotes: "Vulgar but common; avoid with strangers.",
      examples: [],
    },
  ];
  return { manifest, vocabulary: vocab, grammar: [], realSpeech, dialogues: [], pronunciation: [], lessons: [], aiPrompts: [] };
}

describe("loadReviewCard", () => {
  it("renders vocabulary and real-speech cards (with register + severity)", async () => {
    const repos = createRepos(createMigratedDb().database, () => T0);
    const profile = await runOnboarding(repos, { displayName: "Alek", pack: pack() });
    const due = await repos.reviews.listDue(profile.id);

    const cards = await Promise.all(due.map((item) => loadReviewCard(repos, item)));
    const vocabCard = cards.find((c) => c.front === "água");
    const slangCard = cards.find((c) => c.front === "merda");

    expect(vocabCard?.back).toBe("water");
    expect(slangCard?.back).toBe("shit / crap");
    expect(slangCard?.note).toContain("vulgar");
    expect(slangCard?.note).toContain("severity 4/7");
    expect(slangCard?.note).toContain("avoid with strangers");
  });
});
