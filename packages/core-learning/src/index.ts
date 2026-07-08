/**
 * @polyglotai/core-learning — pure TS, no React, no Tauri (plan §2). DB repositories,
 * profile/vocabulary/grammar/slang/dialogue services, the thin learning engine, feature
 * flags, migrations, and the event bus. Built out step-by-step per the plan's build sequence.
 */
export const CORE_LEARNING_PACKAGE_NAME = "@polyglotai/core-learning";

export * from "./db/database.js";
export * from "./db/upsert.js";
export * from "./db/ids.js";
export * from "./packs/importer.js";
export * from "./packs/registry.js";
export * from "./profile/profile.js";
export * from "./featureflags/registry.js";
export * from "./review/reviewRepo.js";
