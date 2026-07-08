import { describe, expect, it } from "vitest";
import { ConversationRepo } from "../src/conversation/conversationRepo.js";
import { ProfileRepo } from "../src/profile/profile.js";
import { createMigratedDb } from "./nodeSqliteDb.js";

describe("ConversationRepo", () => {
  it("creates conversations, appends ordered messages, and lists newest-first", async () => {
    const db = createMigratedDb().database;
    let t = 0;
    const tick = () => `2026-07-08T00:00:0${t++}.000Z`;
    const profiles = new ProfileRepo(db, tick);
    const repo = new ConversationRepo(db, tick);

    const profile = await profiles.create({ displayName: "Alek" });
    const convo = await repo.create(profile.id, "roleplay-partner", "café", "Café roleplay");

    await repo.appendMessage(convo.id, "user", "Oi, um café por favor");
    await repo.appendMessage(convo.id, "assistant", "Claro! Pra viagem?", { tokens: 12 });

    const messages = await repo.listMessages(convo.id);
    expect(messages.map((m) => m.role)).toEqual(["user", "assistant"]);
    expect(messages[1]!.content).toContain("Pra viagem");

    const second = await repo.create(profile.id, "conversation-partner");
    const list = await repo.listConversations(profile.id);
    expect(list[0]!.id).toBe(second.id); // newest first
    expect(list).toHaveLength(2);
  });
});
