import type { Database, SqlValue } from "../db/database.js";
import { newId } from "../db/ids.js";

export interface PronunciationAttempt {
  id: string;
  targetText: string;
  transcript: string | null;
  score: number | null;
  createdAt: string;
}

/**
 * Persists pronunciation attempts (spec §17 Phase 1; plan §4 pronunciation_attempts).
 * Audio bytes stay out of the DB — `audioPath` records where the app put the file, and the
 * MVP screen keeps audio in memory only (nothing written unless the learner opts in later).
 */
export class PronunciationRepo {
  constructor(
    private readonly db: Database,
    private readonly now: () => string = () => new Date().toISOString(),
  ) {}

  async record(
    profileId: string,
    input: { targetText: string; transcript?: string; score?: number; audioPath?: string },
  ): Promise<PronunciationAttempt> {
    const ts = this.now();
    const id = newId();
    await this.db.run(
      `INSERT INTO pronunciation_attempts (id, profile_id, target_text, transcript, score, audio_path, schema_version, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [id, profileId, input.targetText, input.transcript ?? null, input.score ?? null, input.audioPath ?? null, 1, ts],
    );
    return {
      id,
      targetText: input.targetText,
      transcript: input.transcript ?? null,
      score: input.score ?? null,
      createdAt: ts,
    };
  }

  async listRecent(profileId: string, limit = 20): Promise<PronunciationAttempt[]> {
    const rows = await this.db.all<{
      id: string;
      target_text: string;
      transcript: string | null;
      score: number | null;
      created_at: string;
    }>(
      "SELECT id, target_text, transcript, score, created_at FROM pronunciation_attempts WHERE profile_id = ? ORDER BY created_at DESC, id DESC LIMIT ?",
      [profileId as SqlValue, limit],
    );
    return rows.map((r) => ({
      id: r.id,
      targetText: r.target_text,
      transcript: r.transcript,
      score: r.score,
      createdAt: r.created_at,
    }));
  }
}
