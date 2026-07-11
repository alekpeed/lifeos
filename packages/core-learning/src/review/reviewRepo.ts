import type { Scheduler, SchedulerState } from "@polyglotai/spaced-repetition";
import type { Database, SqlValue } from "../db/database.js";
import { newId } from "../db/ids.js";

/** Content kinds that get spaced-repetition review items in MVP (spec §16). Pronunciation is
 * practice, not SRS-scheduled (plan risk 9), so it is intentionally excluded.
 * `grammar_ladder` schedules individual substitution-ladder steps (rock-solid idea #9) — each
 * step is its own card, flattened from grammar_items.ladders at import time (packs/rows.ts). */
export type ReviewItemType = "vocabulary" | "grammar" | "real_speech" | "grammar_ladder";

const CONTENT_TABLE: Record<ReviewItemType, string> = {
  vocabulary: "vocabulary_items",
  grammar: "grammar_items",
  real_speech: "real_speech_items",
  grammar_ladder: "grammar_ladder_steps",
};

const ALL_ITEM_TYPES: ReviewItemType[] = ["vocabulary", "grammar", "real_speech", "grammar_ladder"];

export interface ReviewItem {
  id: string;
  profileId: string;
  itemType: ReviewItemType;
  contentId: string;
  state: SchedulerState;
}

interface ReviewItemRow extends Record<string, SqlValue> {
  id: string;
  profile_id: string;
  item_type: string;
  content_id: string;
  difficulty: number | null;
  stability: number | null;
  retrievability: number | null;
  state: string;
  due_at: string | null;
  last_reviewed_at: string | null;
  lapses: number;
  reps: number;
}

function rowToReviewItem(row: ReviewItemRow): ReviewItem {
  return {
    id: row.id,
    profileId: row.profile_id,
    itemType: row.item_type as ReviewItemType,
    contentId: row.content_id,
    state: {
      difficulty: row.difficulty ?? 0,
      stability: row.stability ?? 0,
      retrievability: row.retrievability ?? 0,
      state: row.state as SchedulerState["state"],
      dueAt: row.due_at ?? new Date(0).toISOString(),
      lastReviewedAt: row.last_reviewed_at,
      lapses: row.lapses,
      reps: row.reps,
    },
  };
}

export interface RecordReviewInput {
  rating: 1 | 2 | 3 | 4;
  responseMs?: number;
  confidence?: number;
}

/**
 * Owns review_items and review_results (plan §4). Bridges the pure Scheduler
 * (spaced-repetition) to the DB: generates review items from imported content, serves the due
 * queue, and records reviews by advancing scheduler state and logging the result.
 */
export class ReviewRepo {
  constructor(
    private readonly db: Database,
    private readonly scheduler: Scheduler,
    private readonly clock: () => Date = () => new Date(),
  ) {}

  /**
   * Seeds 'new' review items for every content row of the given types belonging to `packId`
   * that the profile doesn't already have one for. Idempotent: re-running adds only genuinely
   * new content (INSERT OR IGNORE against the UNIQUE(profile_id,item_type,content_id) index).
   * Returns how many were newly created.
   */
  async generateForPack(
    profileId: string,
    packId: string,
    itemTypes: ReviewItemType[] = ALL_ITEM_TYPES,
  ): Promise<number> {
    const now = this.clock();
    const seed = this.scheduler.initialState(now);
    const ts = now.toISOString();
    let created = 0;

    await this.db.transaction(async (tx) => {
      for (const itemType of itemTypes) {
        const rows = await tx.all<{ id: string }>(
          `SELECT id FROM ${CONTENT_TABLE[itemType]} WHERE pack_id = ?`,
          [packId],
        );
        for (const { id: contentId } of rows) {
          const before = await tx.all<{ n: number }>(
            "SELECT COUNT(*) AS n FROM review_items WHERE profile_id = ? AND item_type = ? AND content_id = ?",
            [profileId, itemType, contentId],
          );
          if ((before[0]?.n ?? 0) > 0) continue;

          await tx.run(
            `INSERT INTO review_items
               (id, profile_id, item_type, content_id, difficulty, stability, retrievability,
                state, due_at, last_reviewed_at, lapses, reps, schema_version, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
            [
              newId(),
              profileId,
              itemType,
              contentId,
              seed.difficulty,
              seed.stability,
              seed.retrievability,
              seed.state,
              seed.dueAt,
              null,
              seed.lapses,
              seed.reps,
              1,
              ts,
              ts,
            ],
          );
          created += 1;
        }
      }
    });

    return created;
  }

  /** Items due for review at `now` (new items are due immediately), soonest first. Pass
   * `itemTypes` to scope the queue to a subset (e.g. the ladder drill screen wants only
   * `grammar_ladder`, since its fill-in-the-blank UI doesn't fit a flip card). */
  async listDue(profileId: string, limit = 50, itemTypes?: ReviewItemType[]): Promise<ReviewItem[]> {
    const now = this.clock().toISOString();
    const typeFilter = itemTypes?.length ? `AND item_type IN (${itemTypes.map(() => "?").join(", ")})` : "";
    const rows = await this.db.all<ReviewItemRow>(
      `SELECT * FROM review_items
        WHERE profile_id = ? AND (due_at IS NULL OR due_at <= ?) ${typeFilter}
        ORDER BY due_at IS NULL DESC, due_at ASC
        LIMIT ?`,
      [profileId, now, ...(itemTypes ?? []), limit],
    );
    return rows.map(rowToReviewItem);
  }

  async countDue(profileId: string): Promise<number> {
    const now = this.clock().toISOString();
    const rows = await this.db.all<{ n: number }>(
      "SELECT COUNT(*) AS n FROM review_items WHERE profile_id = ? AND (due_at IS NULL OR due_at <= ?)",
      [profileId, now],
    );
    return rows[0]?.n ?? 0;
  }

  /** Applies the learner's grade: advances the scheduler, persists the new FSRS state, and
   * logs a review_results row. Returns the updated review item. */
  async recordReview(reviewItemId: string, input: RecordReviewInput): Promise<ReviewItem> {
    const now = this.clock();
    const ts = now.toISOString();

    const existing = await this.db.all<ReviewItemRow>("SELECT * FROM review_items WHERE id = ?", [
      reviewItemId,
    ]);
    const row = existing[0];
    if (!row) throw new Error(`review item ${reviewItemId} not found`);

    const current = rowToReviewItem(row);
    const next = this.scheduler.schedule(current.state, { rating: input.rating }, now);

    await this.db.transaction(async (tx) => {
      await tx.run(
        `UPDATE review_items
            SET difficulty = ?, stability = ?, retrievability = ?, state = ?, due_at = ?,
                last_reviewed_at = ?, lapses = ?, reps = ?, updated_at = ?
          WHERE id = ?`,
        [
          next.difficulty,
          next.stability,
          next.retrievability,
          next.state,
          next.dueAt,
          next.lastReviewedAt,
          next.lapses,
          next.reps,
          ts,
          reviewItemId,
        ],
      );
      await tx.run(
        `INSERT INTO review_results
           (id, review_item_id, rating, response_ms, confidence, reviewed_at, schema_version, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          newId(),
          reviewItemId,
          input.rating,
          input.responseMs ?? null,
          input.confidence ?? null,
          ts,
          1,
          ts,
        ],
      );
    });

    return { ...current, state: next };
  }
}
