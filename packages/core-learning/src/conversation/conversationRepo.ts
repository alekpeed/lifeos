import type { Database, SqlValue } from "../db/database.js";
import { newId } from "../db/ids.js";

export interface ConversationRecord {
  id: string;
  profileId: string;
  mode: string;
  scenario: string | null;
  title: string | null;
  createdAt: string;
}

export interface StoredMessage {
  id: string;
  role: "system" | "user" | "assistant";
  content: string;
  correctionJson: string | null;
  createdAt: string;
}

/**
 * Persists conversations + ai_messages (plan §4). Whether anything is persisted at all is the
 * CALLER's decision via the `conversation_logging` feature flag (off by default — owner
 * decision, plan risk 7); this repo just does the writing when asked.
 */
export class ConversationRepo {
  constructor(
    private readonly db: Database,
    private readonly now: () => string = () => new Date().toISOString(),
  ) {}

  async create(profileId: string, mode: string, scenario?: string, title?: string): Promise<ConversationRecord> {
    const ts = this.now();
    const id = newId();
    await this.db.run(
      `INSERT INTO conversations (id, profile_id, mode, scenario, title, schema_version, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [id, profileId, mode, scenario ?? null, title ?? null, 1, ts, ts],
    );
    return { id, profileId, mode, scenario: scenario ?? null, title: title ?? null, createdAt: ts };
  }

  async appendMessage(
    conversationId: string,
    role: StoredMessage["role"],
    content: string,
    options: { correctionJson?: string; tokens?: number } = {},
  ): Promise<void> {
    const ts = this.now();
    await this.db.run(
      `INSERT INTO ai_messages (id, conversation_id, role, content, correction_json, tokens, schema_version, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [newId(), conversationId, role, content, options.correctionJson ?? null, options.tokens ?? null, 1, ts],
    );
    await this.db.run("UPDATE conversations SET updated_at = ? WHERE id = ?", [ts, conversationId]);
  }

  async listMessages(conversationId: string): Promise<StoredMessage[]> {
    const rows = await this.db.all<{
      id: string;
      role: string;
      content: string;
      correction_json: string | null;
      created_at: string;
    }>(
      "SELECT id, role, content, correction_json, created_at FROM ai_messages WHERE conversation_id = ? ORDER BY created_at, id",
      [conversationId as SqlValue],
    );
    return rows.map((r) => ({
      id: r.id,
      role: r.role as StoredMessage["role"],
      content: r.content,
      correctionJson: r.correction_json,
      createdAt: r.created_at,
    }));
  }

  async listConversations(profileId: string): Promise<ConversationRecord[]> {
    const rows = await this.db.all<{
      id: string;
      profile_id: string;
      mode: string;
      scenario: string | null;
      title: string | null;
      created_at: string;
    }>(
      "SELECT id, profile_id, mode, scenario, title, created_at FROM conversations WHERE profile_id = ? ORDER BY created_at DESC",
      [profileId as SqlValue],
    );
    return rows.map((r) => ({
      id: r.id,
      profileId: r.profile_id,
      mode: r.mode,
      scenario: r.scenario,
      title: r.title,
      createdAt: r.created_at,
    }));
  }
}
