import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { Manifest, VocabularyItem } from "@polyglotai/shared-types";
import { beforeEach, describe, expect, it } from "vitest";
import { contentId } from "../src/db/ids.js";
import { importPack } from "../src/packs/importer.js";
import { PackRegistry } from "../src/packs/registry.js";
import type { Database } from "../src/db/database.js";
import { createMigratedDb } from "./nodeSqliteDb.js";

const FIXED_NOW = () => "2026-07-08T00:00:00.000Z";

function manifest(overrides: Partial<Manifest> = {}): Manifest {
  return {
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
    ...overrides,
  };
}

function vocab(key: string, lemma: string): VocabularyItem {
  return {
    schemaVersion: 1,
    key,
    entryType: "word",
    lemma,
    translation: lemma,
    tags: [],
    examples: [],
  };
}

function emptyPack(m: Manifest): LoadedPack {
  return { manifest: m, vocabulary: [], grammar: [], realSpeech: [], dialogues: [], pronunciation: [], lessons: [], aiPrompts: [] };
}

describe("importPack", () => {
  let db: Database;

  beforeEach(() => {
    db = createMigratedDb().database;
  });

  it("imports a pack's content into the DB", async () => {
    const pack = { ...emptyPack(manifest()), vocabulary: [vocab("vocab.agua", "água"), vocab("vocab.pao", "pão")] };
    const result = await importPack(db, pack, { now: FIXED_NOW });

    expect(result.counts.vocabulary).toBe(2);
    const packs = await db.all("SELECT id FROM language_packs");
    expect(packs).toHaveLength(1);
    const rows = await db.all<{ id: string; item_key: string }>(
      "SELECT id, item_key FROM vocabulary_items ORDER BY item_key",
    );
    expect(rows.map((r) => r.item_key)).toEqual(["vocab.agua", "vocab.pao"]);
    expect(rows[0]!.id).toBe(contentId("pt-br", "vocab.agua"));
  });

  it("is idempotent: re-importing the same pack does not duplicate rows or change ids", async () => {
    const pack = { ...emptyPack(manifest()), vocabulary: [vocab("vocab.agua", "água")] };
    await importPack(db, pack, { now: FIXED_NOW });
    const before = await db.all<{ id: string }>("SELECT id FROM vocabulary_items");

    await importPack(db, pack, { now: () => "2026-09-01T00:00:00.000Z" });
    const after = await db.all<{ id: string; created_at: string; updated_at: string }>(
      "SELECT id, created_at, updated_at FROM vocabulary_items",
    );

    expect(after).toHaveLength(1);
    expect(after[0]!.id).toBe(before[0]!.id);
    expect(after[0]!.created_at).toBe("2026-07-08T00:00:00.000Z"); // immutable
    expect(after[0]!.updated_at).toBe("2026-09-01T00:00:00.000Z"); // refreshed
  });

  it("removes items dropped from a pack while preserving user review history for kept items", async () => {
    const v1 = { ...emptyPack(manifest()), vocabulary: [vocab("vocab.agua", "água"), vocab("vocab.pao", "pão")] };
    await importPack(db, v1, { now: FIXED_NOW });

    // A user has a review scheduled against the kept item (references it by deterministic id).
    const keptId = contentId("pt-br", "vocab.agua");
    await db.run(
      "INSERT INTO learner_profiles (id, display_name, schema_version, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
      ["p1", "Alek", 1, FIXED_NOW(), FIXED_NOW()],
    );
    await db.run(
      "INSERT INTO review_items (id, profile_id, item_type, content_id, schema_version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ["r1", "p1", "vocabulary", keptId, 1, FIXED_NOW(), FIXED_NOW()],
    );

    // New pack version drops "vocab.pao".
    const v2 = { ...emptyPack(manifest({ packVersion: "0.2.0" })), vocabulary: [vocab("vocab.agua", "água")] };
    await importPack(db, v2, { now: FIXED_NOW });

    const remaining = await db.all<{ item_key: string }>("SELECT item_key FROM vocabulary_items");
    expect(remaining.map((r) => r.item_key)).toEqual(["vocab.agua"]); // dropped item gone

    const review = await db.all<{ content_id: string }>("SELECT content_id FROM review_items WHERE id = 'r1'");
    expect(review).toHaveLength(1); // history survived
    const referenced = await db.all("SELECT id FROM vocabulary_items WHERE id = ?", [review[0]!.content_id]);
    expect(referenced).toHaveLength(1); // and still resolves to a live content row
  });
});

describe("PackRegistry", () => {
  it("lists and gets installed packs", async () => {
    const db = createMigratedDb().database;
    await importPack(db, emptyPack(manifest()), { now: FIXED_NOW });
    const registry = new PackRegistry(db);

    const list = await registry.list();
    expect(list).toHaveLength(1);
    expect(list[0]!.languageCode).toBe("pt-BR");

    const one = await registry.get("pt-br");
    expect(one?.name).toBe("Brazilian Portuguese");
    expect(await registry.get("nope")).toBeNull();
  });
});
