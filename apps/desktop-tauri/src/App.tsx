import { useEffect, useState } from "react";
import type { Repos } from "@polyglotai/core-learning";
import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { LearnerProfile } from "@polyglotai/shared-types";
import { bootstrap } from "./app/bootstrap";
import { Onboarding } from "./screens/Onboarding";
import { Dashboard } from "./screens/Dashboard";
import { Review } from "./screens/Review";
import "./App.css";

type View = "dashboard" | "review";

function App() {
  const [ready, setReady] = useState<{ repos: Repos; pack: LoadedPack } | null>(null);
  const [profile, setProfile] = useState<LearnerProfile | null>(null);
  const [view, setView] = useState<View>("dashboard");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    bootstrap()
      .then(({ repos, pack, profile }) => {
        setReady({ repos, pack });
        setProfile(profile);
      })
      .catch((e) => setError(String(e)));
  }, []);

  if (error) {
    return (
      <main className="container">
        <h1>PolyglotAI</h1>
        <p className="error">Failed to start: {error}</p>
      </main>
    );
  }

  if (!ready) {
    return (
      <main className="container">
        <h1>PolyglotAI</h1>
        <p>Loading…</p>
      </main>
    );
  }

  if (!profile) {
    return <Onboarding repos={ready.repos} pack={ready.pack} onComplete={setProfile} />;
  }

  if (view === "review") {
    return <Review repos={ready.repos} profile={profile} onDone={() => setView("dashboard")} />;
  }

  return <Dashboard repos={ready.repos} profile={profile} onStartReview={() => setView("review")} />;
}

export default App;
