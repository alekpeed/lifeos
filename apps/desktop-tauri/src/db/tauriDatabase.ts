import Database from "@tauri-apps/plugin-sql";
import type { Database as CoreDatabase, SqlValue } from "@polyglotai/core-learning";

/**
 * The Tauri implementation of core-learning's Database port, over @tauri-apps/plugin-sql
 * (sqlx/SQLite). All core SQL uses `?` positional placeholders, which sqlx-SQLite binds in
 * order (verified by the Rust migration test) — the same style node:sqlite uses in unit tests.
 *
 * `transaction` is best-effort: the plugin pools connections, so BEGIN/COMMIT issued as
 * separate calls may not share a connection (plan risk 5). core-learning's writes are all
 * idempotent upserts, so correctness never depends on rollback — this just narrows the window.
 */
export class TauriDatabase implements CoreDatabase {
  private constructor(private readonly db: Database) {}

  static async connect(dbName = "polyglotai.db"): Promise<TauriDatabase> {
    // Migrations registered in Rust (lib.rs) run on load.
    const db = await Database.load(`sqlite:${dbName}`);
    await db.execute("PRAGMA foreign_keys = ON");
    return new TauriDatabase(db);
  }

  async run(sql: string, params: SqlValue[] = []): Promise<void> {
    await this.db.execute(sql, params);
  }

  async all<T = Record<string, SqlValue>>(sql: string, params: SqlValue[] = []): Promise<T[]> {
    return this.db.select<T[]>(sql, params);
  }

  async transaction<T>(fn: (tx: CoreDatabase) => Promise<T>): Promise<T> {
    await this.run("BEGIN");
    try {
      const result = await fn(this);
      await this.run("COMMIT");
      return result;
    } catch (err) {
      try {
        await this.run("ROLLBACK");
      } catch {
        // Ignore rollback failure (e.g. no active tx on a pooled connection); surface the
        // original error below.
      }
      throw err;
    }
  }
}
