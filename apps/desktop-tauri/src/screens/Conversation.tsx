import { useMemo, useRef, useState, type FormEvent } from "react";
import { ConversationSession } from "@polyglotai/ai-orchestration";
import type { Repos } from "@polyglotai/core-learning";
import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { LearnerProfile } from "@polyglotai/shared-types";
import { makeConversationTaskPrompt, makeLearnerContext, makeProvider } from "../ai/aiContext";

interface Props {
  repos: Repos;
  profile: LearnerProfile;
  pack: LoadedPack;
  onDone: () => void;
  onOpenSettings: () => void;
}

/** Adult, real-world scenarios (spec §14 tutor modes; §6 first-session conversation). */
const SCENARIOS = [
  "café",
  "bar — meeting people",
  "first date",
  "hotel check-in",
  "job interview",
  "apartment rental",
  "doctor visit",
  "texting a new friend",
  "workplace disagreement",
  "negotiating at a street market",
];

interface Bubble {
  role: "user" | "assistant";
  content: string;
}

export function Conversation({ repos, profile, pack, onDone, onOpenSettings }: Props) {
  const provider = useMemo(() => makeProvider(profile), [profile]);
  const [scenario, setScenario] = useState<string | null>(null);
  const [bubbles, setBubbles] = useState<Bubble[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sessionRef = useRef<ConversationSession | null>(null);
  const conversationIdRef = useRef<string | null>(null);

  if (!provider) {
    return (
      <main className="container">
        <h1>Conversation</h1>
        <p className="subtitle">Add your OpenAI API key in Settings to enable conversation practice.</p>
        <button type="button" onClick={onOpenSettings}>
          Open Settings
        </button>
        <button type="button" className="link" onClick={onDone}>
          Back to dashboard
        </button>
      </main>
    );
  }

  async function start(chosen: string) {
    setScenario(chosen);
    sessionRef.current = new ConversationSession(provider!, {
      taskPrompt: makeConversationTaskPrompt(pack, chosen),
      ctx: makeLearnerContext(profile, pack),
    });
    // Persist only when the learner opted in (conversation_logging, off by default).
    if (await repos.flags.isEnabled("conversation_logging")) {
      const record = await repos.conversations.create(profile.id, "roleplay-partner", chosen);
      conversationIdRef.current = record.id;
    }
  }

  async function handleSend(e: FormEvent) {
    e.preventDefault();
    const session = sessionRef.current;
    const text = input.trim();
    if (!session || !text || busy) return;

    setBusy(true);
    setError(null);
    setInput("");
    setBubbles((b) => [...b, { role: "user", content: text }]);
    try {
      const reply = await session.send(text);
      setBubbles((b) => [...b, { role: "assistant", content: reply }]);
      const convoId = conversationIdRef.current;
      if (convoId) {
        await repos.conversations.appendMessage(convoId, "user", text);
        await repos.conversations.appendMessage(convoId, "assistant", reply, {
          tokens: session.tokensSpent,
        });
      }
    } catch (err) {
      setError(String(err));
    } finally {
      setBusy(false);
    }
  }

  if (!scenario) {
    return (
      <main className="container">
        <h1>Conversation</h1>
        <p className="subtitle">Pick a scenario — your partner speaks {pack.manifest.name}.</p>
        <div className="scenario-grid">
          {SCENARIOS.map((s) => (
            <button key={s} type="button" onClick={() => start(s)}>
              {s}
            </button>
          ))}
        </div>
        <button type="button" className="link" onClick={onDone}>
          Back to dashboard
        </button>
      </main>
    );
  }

  return (
    <main className="container">
      <h1>{scenario}</h1>
      <section className="chat">
        {bubbles.length === 0 && <p className="subtitle">Say something to start — in Portuguese if you can!</p>}
        {bubbles.map((b, i) => (
          <div key={i} className={`bubble ${b.role}`}>
            {b.content}
          </div>
        ))}
        {busy && <div className="bubble assistant pending">…</div>}
      </section>

      {error && <p className="error">{error}</p>}

      <form className="chat-input" onSubmit={handleSend}>
        <input
          value={input}
          onChange={(e) => setInput(e.currentTarget.value)}
          placeholder="Escreva aqui…"
          disabled={busy}
        />
        <button type="submit" disabled={busy || !input.trim()}>
          Send
        </button>
      </form>

      <button type="button" className="link" onClick={onDone}>
        End conversation
      </button>
    </main>
  );
}
