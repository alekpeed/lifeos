import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { GrammarItem, Manifest } from "@polyglotai/shared-types";
import { describe, expect, it } from "vitest";
import { contentId } from "../src/db/ids.js";
import { importPack } from "../src/packs/importer.js";
import { createRepos } from "../src/repos.js";
import { loadReviewCard } from "../src/services/reviewCard.js";
import type { Database } from "../src/db/database.js";
import { createMigratedDb } from "./nodeSqliteDb.js";

const T0 = new Date("2026-07-08T00:00:00.000Z");
const ISO = () => T0.toISOString();

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

function grammarWithLadder(): GrammarItem {
  return {
    schemaVersion: 1,
    key: "grammar.estar",
    title: "estar",
    explanationMd: "to be (temporary)",
    examples: [],
    commonErrors: [],
    drills: [],
    relatedVocabulary: [],
    ladders: [
      {
        key: "ladder.estar-pronouns",
        title: "estar across pronouns",
        pattern: "{pronoun} ___ cansado.",
        steps: [
          { prompt: "Eu ___ cansado.", answer: "estou" },
          { prompt: "Você ___ cansado?", answer: "está", note: "third-person form" },
          { prompt: "Nós ___ cansados.", answer: "estamos" },
        ],
      },
    ],
  };
}

function pack(): LoadedPack {
  return {
    manifest: manifest(),
    vocabulary: [],
    grammar: [grammarWithLadder()],
    realSpeech: [],
    dialogues: [],
    pronunciation: [],
    lessons: [],
    aiPrompts: [],
  };
}

describe("grammar substitution ladders", () => {
  it("flattens ladder steps into one row each on import, keyed deterministically", async () => {
    const db = createMigratedDb().database;
    const result = await importPack(db, pack(), { now: ISO });
    expect(result.counts.grammarLadderSteps).toBe(3);

    const rows = await db.all<{ item_key: string; prompt: string; answer: string; step_index: number }>(
      "SELECT item_key, prompt, answer, step_index FROM grammar_ladder_steps ORDER BY step_index",
    );
    expect(rows).toHaveLength(3);
    expect(rows[0]!.item_key).toBe("grammar.estar::ladder.estar-pronouns::0");
    expect(rows[0]!.answer).toBe("estou");
    expect(rows[2]!.answer).toBe("estamos");

    const expectedId = contentId("pt-br", "grammar.estar::ladder.estar-pronouns::1");
    const byId = await db.all<{ id: string }>("SELECT id FROM grammar_ladder_steps WHERE id = ?", [expectedId]);
    expect(byId).toHaveLength(1);
  });

  it("is idempotent and drops stale steps when a pack removes a ladder step", async () => {
    const db = createMigratedDb().database;
    await importPack(db, pack(), { now: ISO });

    const trimmed = pack();
    trimmed.grammar[0]!.ladders[0]!.steps = trimmed.grammar[0]!.ladders[0]!.steps.slice(0, 1);
    await importPack(db, trimmed, { now: () => "2026-08-01T00:00:00.000Z" });

    const rows = await db.all<{ item_key: string }>("SELECT item_key FROM grammar_ladder_steps");
    expect(rows).toHaveLength(1);
    expect(rows[0]!.item_key).toBe("grammar.estar::ladder.estar-pronouns::0");
  });

  it("schedules each ladder step as its own SRS review item, filterable via listDue", async () => {
    const db: Database = createMigratedDb().database;
    await importPack(db, pack(), { now: ISO });
    const repos = createRepos(db, () => T0);
    const profile = await repos.profiles.create({ displayName: "Alek", activePackId: "pt-br" });

    const created = await repos.reviews.generateForPack(profile.id, "pt-br", ["grammar_ladder"]);
    expect(created).toBe(3);

    const due = await repos.reviews.listDue(profile.id, 50, ["grammar_ladder"]);
    expect(due).toHaveLength(3);
    expect(due.every((d) => d.itemType === "grammar_ladder")).toBe(true);
  });

  it("renders a fill-in-the-blank card via loadReviewCard", async () => {
    const db: Database = createMigratedDb().database;
    await importPack(db, pack(), { now: ISO });
    const repos = createRepos(db, () => T0);
    const profile = await repos.profiles.create({ displayName: "Alek", activePackId: "pt-br" });

    await repos.reviews.generateForPack(profile.id, "pt-br", ["grammar_ladder"]);
    const [first] = await repos.reviews.listDue(profile.id, 1, ["grammar_ladder"]);

    const card = await loadReviewCard(repos, first!);
    expect(card.front).toBe("Eu ___ cansado.");
    expect(card.back).toBe("estou");
    expect(card.note).toContain("estar across pronouns");
  });
});
