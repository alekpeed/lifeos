import { useState, type FormEvent } from "react";
import { runOnboarding, type Repos } from "@polyglotai/core-learning";
import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { LearnerGoal, LearnerProfile, RealSpeechLevel } from "@polyglotai/shared-types";

const GOALS: { value: LearnerGoal; label: string }[] = [
  { value: "conversation", label: "Conversation" },
  { value: "travel", label: "Travel" },
  { value: "fluency", label: "Fluency" },
  { value: "professional", label: "Professional" },
  { value: "dating_social", label: "Dating / social" },
  { value: "media_comprehension", label: "Media comprehension" },
  { value: "tutoring", label: "Tutoring" },
  { value: "custom", label: "Custom" },
];

// Spec §6 step 6: how much real speech to surface (drives the profanity/severity gate).
const LEVELS: { value: RealSpeechLevel; label: string }[] = [
  { value: "standard", label: "Standard only" },
  { value: "informal", label: "Informal included" },
  { value: "slang", label: "Slang included" },
  { value: "profanity", label: "Profanity explained" },
];

interface Props {
  repos: Repos;
  pack: LoadedPack;
  onComplete: (profile: LearnerProfile) => void;
}

export function Onboarding({ repos, pack, onComplete }: Props) {
  const [name, setName] = useState("");
  const [goal, setGoal] = useState<LearnerGoal>("conversation");
  const [level, setLevel] = useState<RealSpeechLevel>("informal");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const profile = await runOnboarding(repos, {
        displayName: name.trim() || "Learner",
        goal,
        realSpeechLevel: level,
        pack,
      });
      onComplete(profile);
    } catch (err) {
      setError(String(err));
      setBusy(false);
    }
  }

  return (
    <main className="container">
      <h1>Welcome to PolyglotAI</h1>
      <p className="subtitle">
        Learning <strong>{pack.manifest.name}</strong> — real speech, not sanitized textbook language.
      </p>
      <form className="onboarding" onSubmit={handleSubmit}>
        <label>
          What should we call you?
          <input value={name} onChange={(e) => setName(e.currentTarget.value)} placeholder="Your name" />
        </label>

        <label>
          Your goal
          <select value={goal} onChange={(e) => setGoal(e.currentTarget.value as LearnerGoal)}>
            {GOALS.map((g) => (
              <option key={g.value} value={g.value}>
                {g.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          Real-speech level
          <select value={level} onChange={(e) => setLevel(e.currentTarget.value as RealSpeechLevel)}>
            {LEVELS.map((l) => (
              <option key={l.value} value={l.value}>
                {l.label}
              </option>
            ))}
          </select>
        </label>

        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={busy}>
          {busy ? "Setting up…" : "Start learning"}
        </button>
      </form>
    </main>
  );
}
