import { useEffect, useState, type FormEvent } from "react";
import type { Repos } from "@polyglotai/core-learning";
import type { LearnerProfile, RealSpeechLevel } from "@polyglotai/shared-types";
import { readAiSettings } from "../ai/aiContext";

interface Props {
  repos: Repos;
  profile: LearnerProfile;
  onSaved: (profile: LearnerProfile) => void;
  onDone: () => void;
}

const LEVELS: RealSpeechLevel[] = ["standard", "informal", "slang", "profanity"];
const STRICTNESS = ["lenient", "balanced", "strict"] as const;

export function Settings({ repos, profile, onSaved, onDone }: Props) {
  const initial = readAiSettings(profile);
  const [apiKey, setApiKey] = useState(initial.openaiApiKey ?? "");
  const [model, setModel] = useState(initial.openaiModel ?? "gpt-4o-mini");
  const [level, setLevel] = useState<RealSpeechLevel>(profile.realSpeechLevel);
  const [strictness, setStrictness] = useState(profile.correctionStrictness);
  const [logging, setLogging] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    repos.flags.isEnabled("conversation_logging").then(setLogging);
  }, [repos]);

  async function handleSave(e: FormEvent) {
    e.preventDefault();
    // API key lives in the local SQLite profile settings — local-first personal app (spec
    // §22); nothing leaves the machine except direct calls to the provider.
    const updated = await repos.profiles.update(profile.id, {
      realSpeechLevel: level,
      correctionStrictness: strictness,
      settings: { ...profile.settings, openaiApiKey: apiKey.trim(), openaiModel: model.trim() },
    });
    await repos.flags.setEnabled("conversation_logging", logging);
    onSaved(updated);
    setSaved(true);
  }

  return (
    <main className="container">
      <h1>Settings</h1>
      <form className="onboarding" onSubmit={handleSave}>
        <label>
          OpenAI API key
          <input
            type="password"
            value={apiKey}
            onChange={(e) => setApiKey(e.currentTarget.value)}
            placeholder="sk-…"
          />
        </label>
        <label>
          Model
          <input value={model} onChange={(e) => setModel(e.currentTarget.value)} />
        </label>
        <label>
          Real-speech level
          <select value={level} onChange={(e) => setLevel(e.currentTarget.value as RealSpeechLevel)}>
            {LEVELS.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </select>
        </label>
        <label>
          Correction strictness
          <select
            value={strictness}
            onChange={(e) => setStrictness(e.currentTarget.value as (typeof STRICTNESS)[number])}
          >
            {STRICTNESS.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <label className="checkbox">
          <input type="checkbox" checked={logging} onChange={(e) => setLogging(e.currentTarget.checked)} />
          Save conversation logs locally (off by default)
        </label>
        <button type="submit">Save</button>
        {saved && <p className="subtitle">Saved.</p>}
      </form>
      <button type="button" className="link" onClick={onDone}>
        Back to dashboard
      </button>
    </main>
  );
}
