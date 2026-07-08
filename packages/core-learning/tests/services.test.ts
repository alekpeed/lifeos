import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { Manifest, VocabularyItem } from "@polyglotai/shared-types";
import { describe, expect, it } from "vitest";
import { createRepos } from "../src/repos.js";
import { runOnboarding } from "../src/services/onboarding.js";
import { loadDashboard } from "../src/services/dashboard.js";
import { createMigratedDb } from "./nodeSqliteDb.js";

const T0 = new Date("2026-07-08T00:00:00.000Z");

function seedPack(): LoadedPack {
  const manifest: Manifest = {
    schemaVersion: 1,
    id: "pt-br",
    name: "Brazilian Portuguese",
    languageCode: "pt-BR",
    packVersion: "0.1.0",
    basePack: null,
    authors: [],
    dialects: [],
    featureFlags: {},
    contents: {},
  };
  const vocab: VocabularyItem[] = ["agua", "cafe", "pao"].map((k) => ({
    schemaVersion: 1,
    key: `vocab.${k}`,
    entryType: "word",
    lemma: k,
    translation: k,
    tags: [],
    examples: [],
  }));
  return { manifest, vocabulary: vocab, grammar: [], realSpeech: [], dialogues: [], pronunciation: [], lessons: [] };
}

describe("runOnboarding", () => {
  it("installs the pack, creates the profile, and seeds a due queue", async () => {
    const repos = createRepos(createMigratedDb().database, () => T0);
    const profile = await runOnboarding(repos, {
      displayName: "Alek",
      goal: "conversation",
      realSpeechLevel: "slang",
      pack: seedPack(),
    });

    expect(profile.displayName).toBe("Alek");
    expect(profile.activePackId).toBe("pt-br");
    expect(profile.realSpeechLevel).toBe("slang");

    // Pack installed and review items seeded (3 vocab -> 3 due).
    expect(await repos.packs.get("pt-br")).not.toBeNull();
    expect(await repos.reviews.countDue(profile.id)).toBe(3);
  });
});

describe("loadDashboard", () => {
  it("summarizes profile, active pack, due count, and content totals", async () => {
    const repos = createRepos(createMigratedDb().database, () => T0);
    const profile = await runOnboarding(repos, { displayName: "Alek", pack: seedPack() });

    const data = await loadDashboard(repos, profile.id);
    expect(data.profile.id).toBe(profile.id);
    expect(data.activePackName).toBe("Brazilian Portuguese");
    expect(data.dueCount).toBe(3);
    expect(data.totals.vocabulary).toBe(3);
    expect(data.totals.grammar).toBe(0);
  });
});
