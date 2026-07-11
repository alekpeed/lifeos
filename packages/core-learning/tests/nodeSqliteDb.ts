import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import type { Database, SqlValue } from "../src/db/database.js";

// node:sqlite is a newer built-in that Vite/Vitest don't recognize and try to bundle (they
// strip the `node:` prefix and fail to resolve "sqlite"). Loading it through a runtime
// createRequire keeps it a plain Node builtin resolution and sidesteps bundler analysis.
interface DatabaseSyncLike {
  exec(sql: string): void;
  prepare(sql: string): {
    run(...params: SqlValue[]): unknown;
    all(...params: SqlValue[]): unknown[];
  };
}
interface NodeSqliteModule {
  DatabaseSync: new (path: string) => DatabaseSyncLike;
}
const { DatabaseSync } = createRequire(import.meta.url)("node:sqlite") as NodeSqliteModule;

/**
 * Test-only Database adapter over Node's built-in node:sqlite. Lives in tests/ and is never
 * exported from the package, so the node:sqlite import never reaches the Tauri webview bundle.
 * node:sqlite is single-connection and synchronous, so it satisfies the port's atomic
 * `transaction` contract exactly (the wrapped async is just for interface conformance).
 */
export class NodeSqliteDatabase implements Database {
  constructor(private readonly db: DatabaseSyncLike) {}

  async run(sql: string, params: SqlValue[] = []): Promise<void> {
    this.db.prepare(sql).run(...params);
  }

  async all<T = Record<string, SqlValue>>(sql: string, params: SqlValue[] = []): Promise<T[]> {
    return this.db.prepare(sql).all(...params) as T[];
  }

  async transaction<T>(fn: (tx: Database) => Promise<T>): Promise<T> {
    this.db.exec("BEGIN");
    try {
      const result = await fn(this);
      this.db.exec("COMMIT");
      return result;
    } catch (err) {
      this.db.exec("ROLLBACK");
      throw err;
    }
  }
}

const here = dirname(fileURLToPath(import.meta.url));
const MIGRATIONS_DIR = join(here, "../../../apps/desktop-tauri/src-tauri/migrations");
// Single source of truth: the same SQLx migration files the real app ships (plan §8 DB tests).
const MIGRATION_FILES = [
  "0001_initial.sql",
  "0002_seed_feature_flags.sql",
  "0003_pronunciation_rules.sql",
  "0004_grammar_ladders.sql",
];

/** Creates an in-memory SQLite DB migrated to head, wrapped in the Database port. */
export function createMigratedDb(): { db: DatabaseSyncLike; database: Database } {
  const db = new DatabaseSync(":memory:");
  db.exec("PRAGMA foreign_keys = ON");
  for (const file of MIGRATION_FILES) {
    db.exec(readFileSync(join(MIGRATIONS_DIR, file), "utf-8"));
  }
  return { db, database: new NodeSqliteDatabase(db) };
}
