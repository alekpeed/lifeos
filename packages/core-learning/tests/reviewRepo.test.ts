import { FsrsScheduler } from "@polyglotai/spaced-repetition";
import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { Manifest, VocabularyItem } from "@polyglotai/shared-types";
import { beforeEach, describe, expect, it } from "vitest";
import { importPack } from "../src/packs/importer.js";
import { ProfileRepo } from "../src/profile/profile.js";
import { ReviewRepo } from "../src/review/reviewRepo.js";
import type { Database } from "../src/db/database.js";
import { createMigratedDb } from "./nodeSqliteDb.js";

const T0 = new Date("2026-07-08T00:00:00.000Z");
const ISO = () => T0.toISOString();

function pack(vocab: VocabularyItem[]): LoadedPack {
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
  return { manifest, vocabulary: vocab, grammar: [], realSpeech: [], dialogues: [], pronunciation: [], lessons: [] };
}

function vocab(key: string): VocabularyItem {
  return { schemaVersion: 1, key, entryType: "word", lemma: key, translation: key, tags: [], examples: [] };
}

describe("ReviewRepo", () => {
  let db: Database;
  let profileId: string;

  beforeEach(async () => {
    db = createMigratedDb().database;
    await importPack(db, pack([vocab("vocab.a"), vocab("vocab.b")]), { now: ISO });
    const profile = await new ProfileRepo(db, ISO).create({ displayName: "Alek", activePackId: "pt-br" });
    profileId = profile.id;
  });

  it("generates one 'new' review item per content row, idempotently", async () => {
    const repo = new ReviewRepo(db, new FsrsScheduler(), () => T0);
    const created = await repo.generateForPack(profileId, "pt-br");
    expect(created).toBe(2); // two vocab items, no grammar/real_speech

    const again = await repo.generateForPack(profileId, "pt-br");
    expect(again).toBe(0); // idempotent

    const due = await repo.listDue(profileId);
    expect(due).toHaveLength(2);
    expect(due.every((d) => d.state.state === "new")).toBe(true);
  });

  it("records a review: advances the due date, logs a result, and drops it from the due queue", async () => {
    const repo = new ReviewRepo(db, new FsrsScheduler(), () => T0);
    await repo.generateForPack(profileId, "pt-br");
    const [item] = await repo.listDue(profileId);

    const updated = await repo.recordReview(item!.id, { rating: 3, responseMs: 1200 });
    expect(updated.state.reps).toBe(1);
    expect(new Date(updated.state.dueAt).getTime()).toBeGreaterThan(T0.getTime());

    const results = await db.all<{ rating: number; response_ms: number }>(
      "SELECT rating, response_ms FROM review_results WHERE review_item_id = ?",
      [item!.id],
    );
    expect(results).toHaveLength(1);
    expect(results[0]!.rating).toBe(3);
    expect(results[0]!.response_ms).toBe(1200);

    // The reviewed item is no longer due at T0 (it was pushed into the future).
    const stillDue = await repo.listDue(profileId);
    expect(stillDue.map((d) => d.id)).not.toContain(item!.id);
    expect(await repo.countDue(profileId)).toBe(1);
  });

  it("only creates review items for the requested content types", async () => {
    const repo = new ReviewRepo(db, new FsrsScheduler(), () => T0);
    const created = await repo.generateForPack(profileId, "pt-br", ["grammar"]);
    expect(created).toBe(0); // the pack has no grammar content
  });
});
