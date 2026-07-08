import { useMemo, useState, type FormEvent } from "react";
import type { AiCorrection } from "@polyglotai/shared-types";
import type { LearnerProfile } from "@polyglotai/shared-types";
import type { Repos } from "@polyglotai/core-learning";
import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import { makeCorrectionEngine, makeLearnerContext, makeProvider } from "../ai/aiContext";

interface Props {
  repos: Repos;
  profile: LearnerProfile;
  pack: LoadedPack;
  onDone: () => void;
  onOpenSettings: () => void;
}

const FIELD_LABELS: Array<[keyof AiCorrection, string]> = [
  ["corrected", "Corrected"],
  ["literal", "Literal meaning"],
  ["natural", "Natural version"],
  ["formal", "Formal"],
  ["casual", "Casual"],
  ["slangNative", "Slang / native"],
  ["grammarExplanation", "Grammar"],
  ["registerExplanation", "Register"],
  ["pronunciationNotes", "Pronunciation"],
];

export function Tutor({ profile, pack, onDone, onOpenSettings }: Props) {
  const provider = useMemo(() => makeProvider(profile), [profile]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [correction, setCorrection] = useState<AiCorrection | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!provider) {
    return (
      <main className="container">
        <h1>AI Tutor</h1>
        <p className="subtitle">Add your OpenAI API key in Settings to enable the tutor.</p>
        <button type="button" onClick={onOpenSettings}>
          Open Settings
        </button>
        <button type="button" className="link" onClick={onDone}>
          Back to dashboard
        </button>
      </main>
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!text.trim() || !provider) return;
    setBusy(true);
    setError(null);
    setCorrection(null);
    try {
      const engine = makeCorrectionEngine(provider, pack);
      setCorrection(await engine.correct(text.trim(), makeLearnerContext(profile, pack)));
    } catch (err) {
      setError(String(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="container">
      <h1>AI Tutor</h1>
      <p className="subtitle">Write a sentence in {pack.manifest.name}; get the full correction.</p>

      <form className="tutor-form" onSubmit={handleSubmit}>
        <textarea
          value={text}
          onChange={(e) => setText(e.currentTarget.value)}
          rows={3}
          placeholder="Eu sou cansado hoje…"
        />
        <button type="submit" disabled={busy || !text.trim()}>
          {busy ? "Correcting…" : "Correct it"}
        </button>
      </form>

      {error && <p className="error">{error}</p>}

      {correction && (
        <section className="card correction">
          {FIELD_LABELS.map(([key, label]) => {
            const value = correction[key];
            if (!value || (Array.isArray(value) && value.length === 0)) return null;
            return (
              <div key={key} className="correction-row">
                <span className="correction-label">{label}</span>
                <span className="correction-value">{String(value)}</span>
              </div>
            );
          })}
        </section>
      )}

      <button type="button" className="link" onClick={onDone}>
        Back to dashboard
      </button>
    </main>
  );
}
