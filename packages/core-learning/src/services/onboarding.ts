import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { LearnerGoal, LearnerProfile, RealSpeechLevel } from "@polyglotai/shared-types";
import { importPack } from "../packs/importer.js";
import type { ProfileCreateInput } from "../profile/profile.js";
import type { Repos } from "../repos.js";

export interface OnboardingInput {
  displayName: string;
  goal?: LearnerGoal;
  targetDialect?: string;
  realSpeechLevel?: RealSpeechLevel;
  /** The validated pack to install (loaded by the app via loadPack + a PackFileReader). */
  pack: LoadedPack;
}

/**
 * First-run onboarding (spec §6): install the chosen pack, create the local profile, and seed
 * review items so the learner has a due queue immediately. Ordered so the language_packs row
 * exists before the profile's active_pack_id FK references it and before review generation
 * needs content rows. Returns the created profile.
 */
export async function runOnboarding(repos: Repos, input: OnboardingInput): Promise<LearnerProfile> {
  const packId = input.pack.manifest.id;

  await importPack(repos.db, input.pack);

  // Build the create input without explicit `undefined`s (exactOptionalPropertyTypes).
  const createInput: ProfileCreateInput = { displayName: input.displayName, activePackId: packId };
  if (input.goal !== undefined) createInput.goal = input.goal;
  if (input.targetDialect !== undefined) createInput.targetDialect = input.targetDialect;
  if (input.realSpeechLevel !== undefined) createInput.realSpeechLevel = input.realSpeechLevel;
  const profile = await repos.profiles.create(createInput);

  await repos.reviews.generateForPack(profile.id, packId);

  return profile;
}
