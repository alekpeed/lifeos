import { useEffect, useState } from "react";
import { loadDashboard, type DashboardData, type Repos } from "@polyglotai/core-learning";
import type { LearnerProfile } from "@polyglotai/shared-types";

interface Props {
  repos: Repos;
  profile: LearnerProfile;
  onStartReview: () => void;
  onStartDrill: () => void;
  onOpenLibrary: () => void;
  onOpenTutor: () => void;
  onOpenConversation: () => void;
  onOpenInterpreter: () => void;
  onOpenPronunciation: () => void;
  onOpenSettings: () => void;
}

export function Dashboard({
  repos,
  profile,
  onStartReview,
  onStartDrill,
  onOpenLibrary,
  onOpenTutor,
  onOpenConversation,
  onOpenInterpreter,
  onOpenPronunciation,
  onOpenSettings,
}: Props) {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    loadDashboard(repos, profile.id)
      .then((d) => active && setData(d))
      .catch((e) => active && setError(String(e)));
    return () => {
      active = false;
    };
  }, [repos, profile.id]);

  if (error) return <p className="error">{error}</p>;
  if (!data) return <p>Loading…</p>;

  return (
    <main className="container">
      <h1>Olá, {data.profile.displayName}!</h1>
      <p className="subtitle">
        {data.activePackName ?? "No pack"} · goal: {data.profile.goal ?? "—"}
      </p>

      <section className="card due-card">
        <div className="due-count">{data.dueCount}</div>
        <div>due for review</div>
        <button type="button" onClick={onStartReview} disabled={data.dueCount === 0}>
          {data.dueCount === 0 ? "All caught up" : "Start review"}
        </button>
      </section>

      <section className="totals">
        <div className="stat">
          <span className="stat-n">{data.totals.vocabulary}</span> vocabulary
        </div>
        <div className="stat">
          <span className="stat-n">{data.totals.grammar}</span> grammar
        </div>
        <div className="stat">
          <span className="stat-n">{data.totals.realSpeech}</span> slang / register
        </div>
        <div className="stat">
          <span className="stat-n">{data.totals.dialogues}</span> dialogues
        </div>
      </section>

      <nav className="dash-nav">
        <button type="button" onClick={onOpenTutor}>
          AI Tutor
        </button>
        <button type="button" onClick={onOpenConversation}>
          Conversation
        </button>
        <button type="button" onClick={onOpenInterpreter}>
          Live Interpreter
        </button>
        <button type="button" onClick={onStartDrill}>
          Substitution Drills
        </button>
        <button type="button" onClick={onOpenPronunciation}>
          Pronunciation
        </button>
        <button type="button" onClick={onOpenLibrary}>
          Library
        </button>
        <button type="button" onClick={onOpenSettings}>
          Settings
        </button>
      </nav>
    </main>
  );
}
