import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { describe, expect, it } from "vitest";
import { NodeFsPackReader } from "../src/nodeReader.js";
import { loadPack, mergePacks, PackValidationError, type LoadedPack } from "../src/loader.js";

const here = dirname(fileURLToPath(import.meta.url));
const miniPackDir = join(here, "fixtures/mini-pack");

describe("loadPack", () => {
  it("returns validated in-memory content for a valid pack", async () => {
    const pack = await loadPack(new NodeFsPackReader(miniPackDir));
    expect(pack.manifest.id).toBe("mini");
    expect(pack.vocabulary.map((v) => v.key)).toContain("vocab.agua");
    expect(pack.dialogues).toHaveLength(1);
  });

  it("throws PackValidationError listing every problem", async () => {
    const reader = {
      async readText(path: string) {
        if (path === "manifest.json") {
          return JSON.stringify({
            schemaVersion: 1,
            id: "bad",
            name: "Bad",
            languageCode: "pt-BR",
            packVersion: "0.0.1",
            contents: { grammar: ["grammar/bad.json"] },
          });
        }
        return JSON.stringify([
          { schemaVersion: 1, key: "g1", title: "G", explanationMd: "x", relatedVocabulary: ["nope"] },
        ]);
      },
    };
    await expect(loadPack(reader)).rejects.toBeInstanceOf(PackValidationError);
  });
});

describe("mergePacks", () => {
  it("overrides base items by key and extends with new keys", () => {
    const empty = {
      manifest: { schemaVersion: 1 as const, id: "x", name: "x", languageCode: "pt-BR", packVersion: "0", basePack: null, authors: [], dialects: [], featureFlags: {}, contents: {} },
      grammar: [],
      realSpeech: [],
      dialogues: [],
      pronunciation: [],
      lessons: [],
      aiPrompts: [],
    };
    const base: LoadedPack = {
      ...empty,
      vocabulary: [
        { schemaVersion: 1, key: "v.a", entryType: "word", lemma: "base-a", translation: "a", tags: [], examples: [] },
        { schemaVersion: 1, key: "v.b", entryType: "word", lemma: "base-b", translation: "b", tags: [], examples: [] },
      ],
    };
    const child: LoadedPack = {
      ...empty,
      vocabulary: [
        { schemaVersion: 1, key: "v.a", entryType: "word", lemma: "child-a", translation: "a2", tags: [], examples: [] },
        { schemaVersion: 1, key: "v.c", entryType: "word", lemma: "child-c", translation: "c", tags: [], examples: [] },
      ],
    };
    const merged = mergePacks(base, child);
    const byKey = Object.fromEntries(merged.vocabulary.map((v) => [v.key, v.lemma]));
    expect(byKey["v.a"]).toBe("child-a"); // overridden
    expect(byKey["v.b"]).toBe("base-b"); // inherited
    expect(byKey["v.c"]).toBe("child-c"); // extended
  });
});
