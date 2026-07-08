import { useEffect, useState } from "react";
import type { Repos } from "@polyglotai/core-learning";
import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { LearnerProfile } from "@polyglotai/shared-types";
import { bootstrap } from "./app/bootstrap";
import { Onboarding } from "./screens/Onboarding";
import { Dashboard } from "./screens/Dashboard";
import { Review } from "./screens/Review";
import { Library } from "./screens/Library";
import { Tutor } from "./screens/Tutor";
import { Conversation } from "./screens/Conversation";
import { Settings } from "./screens/Settings";
import "./App.css";

type View = "dashboard" | "review" | "library" | "tutor" | "conversation" | "settings";

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

  const goHome = () => setView("dashboard");

  if (view === "review") {
    return <Review repos={ready.repos} profile={profile} onDone={goHome} />;
  }

  if (view === "library") {
    return <Library repos={ready.repos} profile={profile} onDone={goHome} />;
  }

  if (view === "tutor") {
    return (
      <Tutor
        repos={ready.repos}
        profile={profile}
        pack={ready.pack}
        onDone={goHome}
        onOpenSettings={() => setView("settings")}
      />
    );
  }

  if (view === "conversation") {
    return (
      <Conversation
        repos={ready.repos}
        profile={profile}
        pack={ready.pack}
        onDone={goHome}
        onOpenSettings={() => setView("settings")}
      />
    );
  }

  if (view === "settings") {
    return <Settings repos={ready.repos} profile={profile} onSaved={setProfile} onDone={goHome} />;
  }

  return (
    <Dashboard
      repos={ready.repos}
      profile={profile}
      onStartReview={() => setView("review")}
      onOpenLibrary={() => setView("library")}
      onOpenTutor={() => setView("tutor")}
      onOpenConversation={() => setView("conversation")}
      onOpenSettings={() => setView("settings")}
    />
  );
}

export default App;
