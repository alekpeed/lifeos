import {
  CorrectionEngine,
  DEFAULT_CONVERSATION_TEMPLATE,
  OpenAIProvider,
  renderTemplate,
  type AIProvider,
  type LearnerContext,
} from "@polyglotai/ai-orchestration";
import { effectiveSeverityCeiling } from "@polyglotai/core-learning";
import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { LearnerProfile } from "@polyglotai/shared-types";

/** Profile settings keys the AI layer reads (Settings screen writes these). */
export interface AiSettings {
  openaiApiKey?: string;
  openaiModel?: string;
}

export function readAiSettings(profile: LearnerProfile): AiSettings {
  const s = profile.settings as Record<string, unknown>;
  return {
    ...(typeof s.openaiApiKey === "string" && s.openaiApiKey ? { openaiApiKey: s.openaiApiKey } : {}),
    ...(typeof s.openaiModel === "string" && s.openaiModel ? { openaiModel: s.openaiModel } : {}),
  };
}

/** Builds the provider from profile settings, or null when no key is configured yet. */
export function makeProvider(profile: LearnerProfile): AIProvider | null {
  const { openaiApiKey, openaiModel } = readAiSettings(profile);
  if (!openaiApiKey) return null;
  return new OpenAIProvider({ apiKey: openaiApiKey, ...(openaiModel ? { model: openaiModel } : {}) });
}

/** Learner context for prompts (spec §14) straight from the profile + pack. */
export function makeLearnerContext(profile: LearnerProfile, pack: LoadedPack): LearnerContext {
  return {
    targetLanguage: pack.manifest.name,
    dialect: profile.targetDialect ?? pack.manifest.defaultDialect,
    cefrEstimate: profile.cefrEstimate ?? undefined,
    severityCeiling: effectiveSeverityCeiling(profile),
    correctionStrictness: profile.correctionStrictness,
  };
}

function packTemplate(pack: LoadedPack, key: string): string | undefined {
  return pack.aiPrompts.find((p) => p.key === key)?.template;
}

/** Correction engine wired to the pack's correction template when it ships one (spec §11). */
export function makeCorrectionEngine(provider: AIProvider, pack: LoadedPack): CorrectionEngine {
  const template = packTemplate(pack, "prompt.tutor.correction");
  return new CorrectionEngine(provider, template ? { template } : {});
}

/** Conversation task prompt: pack template if present, built-in default otherwise. */
export function makeConversationTaskPrompt(pack: LoadedPack, scenario: string): string {
  const template = packTemplate(pack, "prompt.tutor.conversation") ?? DEFAULT_CONVERSATION_TEMPLATE;
  return renderTemplate(template, { targetLanguage: pack.manifest.name, scenario });
}
