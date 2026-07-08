import { describe, expect, it } from "vitest";
import { PronunciationRepo } from "../src/pronunciation/pronunciationRepo.js";
import { ProfileRepo } from "../src/profile/profile.js";
import { createMigratedDb } from "./nodeSqliteDb.js";

describe("PronunciationRepo", () => {
  it("records attempts and lists them newest-first", async () => {
    const db = createMigratedDb().database;
    let t = 0;
    const tick = () => `2026-07-08T00:00:0${t++}.000Z`;
    const profile = await new ProfileRepo(db, tick).create({ displayName: "Alek" });
    const repo = new PronunciationRepo(db, tick);

    await repo.record(profile.id, { targetText: "pão", transcript: "pau", score: 0.66 });
    await repo.record(profile.id, { targetText: "água", transcript: "agua", score: 1 });

    const recent = await repo.listRecent(profile.id);
    expect(recent).toHaveLength(2);
    expect(recent[0]!.targetText).toBe("água"); // newest first
    expect(recent[1]!.score).toBeCloseTo(0.66);
  });
});
