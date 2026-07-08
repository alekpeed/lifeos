import { createRepos, type Repos } from "@polyglotai/core-learning";
import { loadPack, type LoadedPack } from "@polyglotai/language-pack-sdk";
import type { LearnerProfile } from "@polyglotai/shared-types";
import { TauriDatabase } from "../db/tauriDatabase";
import { BundledPackReader } from "../packs/bundledPackReader";

export interface AppBootstrap {
  repos: Repos;
  pack: LoadedPack;
  profile: LearnerProfile | null;
}

/**
 * App startup: open the SQLite DB (migrations run in Rust on load), validate + load the
 * bundled seed pack, wire the repositories, and fetch the existing local profile if any.
 * A null profile means first run → onboarding.
 */
export async function bootstrap(): Promise<AppBootstrap> {
  const db = await TauriDatabase.connect();
  const repos = createRepos(db);
  const pack = await loadPack(new BundledPackReader());
  const profile = await repos.profiles.getFirst();
  return { repos, pack, profile };
}
