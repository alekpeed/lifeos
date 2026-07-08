import type { LearnerProfile } from "@polyglotai/shared-types";
import type { SqlValue } from "../db/database.js";
import type { Repos } from "../repos.js";

export interface DashboardData {
  profile: LearnerProfile;
  activePackName: string | null;
  dueCount: number;
  totals: {
    vocabulary: number;
    grammar: number;
    realSpeech: number;
    dialogues: number;
  };
}

async function count(repos: Repos, table: string, packId: string): Promise<number> {
  const rows = await repos.db.all<{ n: number }>(
    `SELECT COUNT(*) AS n FROM ${table} WHERE pack_id = ?`,
    [packId as SqlValue],
  );
  return rows[0]?.n ?? 0;
}

/** Aggregates the home dashboard (spec §5.1): profile, active pack, due count, content totals. */
export async function loadDashboard(repos: Repos, profileId: string): Promise<DashboardData> {
  const profile = await repos.profiles.get(profileId);
  if (!profile) throw new Error(`profile ${profileId} not found`);

  const packId = profile.activePackId ?? null;
  const activePack = packId ? await repos.packs.get(packId) : null;
  const dueCount = await repos.reviews.countDue(profileId);

  const totals = packId
    ? {
        vocabulary: await count(repos, "vocabulary_items", packId),
        grammar: await count(repos, "grammar_items", packId),
        realSpeech: await count(repos, "real_speech_items", packId),
        dialogues: await count(repos, "dialogues", packId),
      }
    : { vocabulary: 0, grammar: 0, realSpeech: 0, dialogues: 0 };

  return { profile, activePackName: activePack?.name ?? null, dueCount, totals };
}
