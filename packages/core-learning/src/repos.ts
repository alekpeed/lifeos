import { FsrsScheduler } from "@polyglotai/spaced-repetition";
import { ConversationRepo } from "./conversation/conversationRepo.js";
import { PronunciationRepo } from "./pronunciation/pronunciationRepo.js";
import type { Database } from "./db/database.js";
import { FeatureFlagRegistry } from "./featureflags/registry.js";
import { PackRegistry } from "./packs/registry.js";
import { ProfileRepo } from "./profile/profile.js";
import { ReviewRepo } from "./review/reviewRepo.js";

/** The bundle of repositories/services the app composes over one Database connection. */
export interface Repos {
  db: Database;
  profiles: ProfileRepo;
  flags: FeatureFlagRegistry;
  packs: PackRegistry;
  reviews: ReviewRepo;
  conversations: ConversationRepo;
  pronunciation: PronunciationRepo;
}

/**
 * Wires every repository over a single Database (plan §3). The app calls this once with the
 * Tauri adapter; tests call it with the node:sqlite adapter. `clock` is injected so tests get
 * deterministic timestamps.
 */
export function createRepos(db: Database, clock: () => Date = () => new Date()): Repos {
  const iso = () => clock().toISOString();
  return {
    db,
    profiles: new ProfileRepo(db, iso),
    flags: new FeatureFlagRegistry(db, iso),
    packs: new PackRegistry(db),
    reviews: new ReviewRepo(db, new FsrsScheduler(), clock),
    conversations: new ConversationRepo(db, iso),
    pronunciation: new PronunciationRepo(db, iso),
  };
}
