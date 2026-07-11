import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { InterpreterSession, type InterpretationGrade, type InterpreterTurn } from "@polyglotai/ai-orchestration";
import type { Repos } from "@polyglotai/core-learning";
import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { LearnerProfile } from "@polyglotai/shared-types";
import { makeLearnerContext, makeProvider } from "../ai/aiContext";

interface Props {
  repos: Repos;
  profile: LearnerProfile;
  pack: LoadedPack;
  onDone: () => void;
  onOpenSettings: () => void;
}

/** Everyday adult scenarios (mirrors Conversation.tsx's scenario list) — a live interpreter
 * needs a subject for the two AI speakers to talk about. */
const TOPICS = [
  "ordering coffee",
  "asking for directions",
  "small talk at a party",
  "negotiating a price at a market",
  "checking into a hotel",
  "a job interview",
  "catching up with an old friend",
  "a disagreement at work",
];

const TURN_SECONDS = 20;

/** Unhinged idea #1: an AI-generated two-speaker dialogue, one line in the target language,
 * the next in English, that the learner interprets live, under a per-turn clock. */
export function Interpreter({ profile, pack, onDone, onOpenSettings }: Props) {
  const provider = useMemo(() => makeProvider(profile), [profile]);
  const sessionRef = useRef<InterpreterSession | null>(null);
  const inputRef = useRef("");

  const [topic, setTopic] = useState<string | null>(null);
  const [turns, setTurns] = useState<InterpreterTurn[]>([]);
  const [turnIndex, setTurnIndex] = useState(0);
  const [input, setInput] = useState("");
  const [secondsLeft, setSecondsLeft] = useState(TURN_SECONDS);
  const [grade, setGrade] = useState<InterpretationGrade | null>(null);
  const [results, setResults] = useState<InterpretationGrade[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const currentTurn = turns[turnIndex] ?? null;

  useEffect(() => {
    inputRef.current = input;
  }, [input]);

  const submitInterpretation = useCallback(async () => {
    const session = sessionRef.current;
    const turn = currentTurn;
    if (!session || !turn || grade || busy) return;
    setBusy(true);
    setError(null);
    try {
      const result = await session.gradeTurn(turn, inputRef.current.trim() || "(no answer — time ran out)");
      setGrade(result);
      setResults((r) => [...r, result]);
    } catch (err) {
      setError(String(err));
    } finally {
      setBusy(false);
    }
  }, [currentTurn, grade, busy]);

  // Per-turn countdown; hitting zero auto-submits whatever's typed so far (the "live" pressure
  // that's the point of the mode). Resets whenever a new, ungraded turn is shown.
  useEffect(() => {
    if (!currentTurn || grade) return;
    setSecondsLeft(TURN_SECONDS);
    const id = setInterval(() => {
      setSecondsLeft((s) => {
        if (s <= 1) {
          clearInterval(id);
          void submitInterpretation();
          return 0;
        }
        return s - 1;
      });
    }, 1000);
    return () => clearInterval(id);
  }, [turnIndex, currentTurn, grade, submitInterpretation]);

  if (!provider) {
    return (
      <main className="container">
        <h1>Live Interpreter</h1>
        <p className="subtitle">Add your OpenAI API key in Settings to enable live interpreting.</p>
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
    setTopic(chosen);
    setBusy(true);
    setError(null);
    try {
      sessionRef.current = new InterpreterSession(provider!, {
        topic: chosen,
        ctx: makeLearnerContext(profile, pack),
      });
      const generated = await sessionRef.current.generateDialogue();
      setTurns(generated);
      setTurnIndex(0);
    } catch (err) {
      setError(String(err));
    } finally {
      setBusy(false);
    }
  }

  function nextTurn() {
    setInput("");
    setGrade(null);
    setTurnIndex((i) => i + 1);
  }

  if (!topic) {
    return (
      <main className="container">
        <h1>Live Interpreter</h1>
        <p className="subtitle">
          Two speakers talk — one in {pack.manifest.name}, one in English. Interpret each line before the clock
          runs out.
        </p>
        <div className="scenario-grid">
          {TOPICS.map((t) => (
            <button key={t} type="button" onClick={() => start(t)}>
              {t}
            </button>
          ))}
        </div>
        {error && <p className="error">{error}</p>}
        <button type="button" className="link" onClick={onDone}>
          Back to dashboard
        </button>
      </main>
    );
  }

  if (busy && turns.length === 0) {
    return <p className="container">Generating dialogue…</p>;
  }

  if (!currentTurn) {
    const avg = results.length > 0 ? (results.reduce((s, r) => s + r.score, 0) / results.length).toFixed(1) : "—";
    return (
      <main className="container">
        <h1>Interpreting complete</h1>
        <p className="subtitle">
          {results.length} turn(s) interpreted · average score {avg}/5
        </p>
        <button type="button" onClick={onDone}>
          Back to dashboard
        </button>
      </main>
    );
  }

  const interpretInto = currentTurn.language === "target" ? "English" : pack.manifest.name;

  return (
    <main className="container">
      <p className="progress">
        Turn {turnIndex + 1} of {turns.length} · {secondsLeft}s
      </p>

      <section className="card review-card">
        <div className="entry-tag">
          Speaker {currentTurn.speaker} · {currentTurn.language === "target" ? pack.manifest.name : "English"}
        </div>
        <div className="review-front">{currentTurn.text}</div>

        {!grade ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void submitInterpretation();
            }}
          >
            <input
              value={input}
              onChange={(e) => setInput(e.currentTarget.value)}
              placeholder={`Interpret into ${interpretInto}…`}
              disabled={busy}
              autoFocus
            />
            <button type="submit" disabled={busy || !input.trim()}>
              Submit
            </button>
          </form>
        ) : (
          <>
            <hr />
            <div className="review-back">
              Score: {grade.score}/5 — {grade.feedback}
            </div>
            <div className="review-note">Model answer: {grade.modelAnswer}</div>
            <div className="grades">
              <button type="button" onClick={nextTurn}>
                Next turn
              </button>
            </div>
          </>
        )}
      </section>

      {error && <p className="error">{error}</p>}

      <button type="button" className="link" onClick={onDone}>
        Stop for now
      </button>
    </main>
  );
}
