import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { Manifest, RealSpeechItem } from "@polyglotai/shared-types";
import { describe, expect, it } from "vitest";
import { importPack } from "../src/packs/importer.js";
import { createRepos } from "../src/repos.js";
import { listRealSpeech, listVocabulary } from "../src/services/content.js";
import { createMigratedDb } from "./nodeSqliteDb.js";

const T0 = new Date("2026-07-08T00:00:00.000Z");

function packWithSlang(): LoadedPack {
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
  const realSpeech: RealSpeechItem[] = [
    { schemaVersion: 1, key: "slang.mano", kind: "slang", phrase: "mano", natural: "bro", register: "informal", severity: 1, learnerShouldUse: "use", saferAlternatives: [], examples: [] },
    { schemaVersion: 1, key: "profanity.merda", kind: "profanity", phrase: "merda", natural: "crap", register: "vulgar", severity: 4, learnerShouldUse: "recognize-only", saferAlternatives: [], examples: [] },
    { schemaVersion: 1, key: "profanity.severe", kind: "taboo", phrase: "(severe)", natural: "(severe)", register: "taboo", severity: 7, learnerShouldUse: "avoid", saferAlternatives: [], examples: [] },
  ];
  return {
    manifest,
    vocabulary: [{ schemaVersion: 1, key: "vocab.agua", entryType: "word", lemma: "água", translation: "water", tags: [], examples: [] }],
    grammar: [],
    realSpeech,
    dialogues: [],
    pronunciation: [],
    lessons: [],
    aiPrompts: [],
  };
}

describe("content library services", () => {
  it("lists vocabulary for a pack", async () => {
    const repos = createRepos(createMigratedDb().database, () => T0);
    await importPack(repos.db, packWithSlang(), { now: () => T0.toISOString() });
    const vocab = await listVocabulary(repos, "pt-br");
    expect(vocab.map((v) => v.lemma)).toEqual(["água"]);
  });

  it("orders real-speech by severity and flags items beyond the comfort ceiling", async () => {
    const repos = createRepos(createMigratedDb().database, () => T0);
    await importPack(repos.db, packWithSlang(), { now: () => T0.toISOString() });

    // Ceiling 3 (default for real-speech level 'informal'): sev 1 shown, sev 4 and 7 locked.
    const items = await listRealSpeech(repos, "pt-br", 3);
    expect(items.map((i) => i.severity)).toEqual([1, 4, 7]); // ascending severity
    expect(items.find((i) => i.key === "slang.mano")?.withinComfort).toBe(true);
    expect(items.find((i) => i.key === "profanity.merda")?.withinComfort).toBe(false);
    expect(items.find((i) => i.key === "profanity.severe")?.withinComfort).toBe(false);

    // Raising the ceiling to 7 brings everything within comfort.
    const all = await listRealSpeech(repos, "pt-br", 7);
    expect(all.every((i) => i.withinComfort)).toBe(true);
  });
});
