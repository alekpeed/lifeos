import { useEffect, useMemo, useRef, useState } from "react";
import { scorePronunciation, WhisperProvider } from "@polyglotai/pronunciation";
import type { Repos } from "@polyglotai/core-learning";
import type { LoadedPack } from "@polyglotai/language-pack-sdk";
import type { LearnerProfile } from "@polyglotai/shared-types";
import { readAiSettings } from "../ai/aiContext";

interface Props {
  repos: Repos;
  profile: LearnerProfile;
  pack: LoadedPack;
  onDone: () => void;
  onOpenSettings: () => void;
}

interface Target {
  text: string;
  hint?: string | undefined;
}

/** Practice targets: pack pronunciation drills (via their minimal pairs and example graphemes)
 * plus short vocabulary/phrase items that carry audio-friendly text. */
function buildTargets(pack: LoadedPack): Target[] {
  const fromVocab: Target[] = pack.vocabulary
    .filter((v) => v.lemma.length <= 40)
    .slice(0, 12)
    .map((v) => ({ text: v.audioText ?? v.lemma, hint: v.translation }));
  const fromRules: Target[] = pack.pronunciation.flatMap((rule) =>
    rule.minimalPairs.slice(0, 1).map((p) => ({ text: `${p.a} … ${p.b}`, hint: rule.description })),
  );
  return [...fromRules, ...fromVocab];
}

type Phase = "idle" | "recording" | "processing" | "scored";

export function Pronunciation({ repos, profile, pack, onDone, onOpenSettings }: Props) {
  const targets = useMemo(() => buildTargets(pack), [pack]);
  const [index, setIndex] = useState(0);
  const [phase, setPhase] = useState<Phase>("idle");
  const [error, setError] = useState<string | null>(null);
  const [transcript, setTranscript] = useState<string | null>(null);
  const [score, setScore] = useState<number | null>(null);
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);

  const { openaiApiKey } = readAiSettings(profile);
  const target = targets[index];

  useEffect(() => () => {
    // Release the object URL and mic on unmount.
    if (audioUrl) URL.revokeObjectURL(audioUrl);
    recorderRef.current?.stream.getTracks().forEach((t) => t.stop());
  }, [audioUrl]);

  if (!openaiApiKey) {
    return (
      <main className="container">
        <h1>Pronunciation</h1>
        <p className="subtitle">Add your OpenAI API key in Settings — transcription uses Whisper.</p>
        <button type="button" onClick={onOpenSettings}>
          Open Settings
        </button>
        <button type="button" className="link" onClick={onDone}>
          Back to dashboard
        </button>
      </main>
    );
  }

  async function startRecording() {
    setError(null);
    setTranscript(null);
    setScore(null);
    if (audioUrl) URL.revokeObjectURL(audioUrl);
    setAudioUrl(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      chunksRef.current = [];
      recorder.ondataavailable = (e) => chunksRef.current.push(e.data);
      recorder.onstop = () => void handleStopped(recorder);
      recorder.start();
      recorderRef.current = recorder;
      setPhase("recording");
    } catch (err) {
      setError(`Microphone unavailable: ${String(err)}`);
    }
  }

  function stopRecording() {
    recorderRef.current?.stop();
    setPhase("processing");
  }

  async function handleStopped(recorder: MediaRecorder) {
    recorder.stream.getTracks().forEach((t) => t.stop());
    const blob = new Blob(chunksRef.current, { type: recorder.mimeType || "audio/webm" });
    setAudioUrl(URL.createObjectURL(blob));
    try {
      const language = pack.manifest.languageCode.split("-")[0]; // "pt-BR" -> "pt"
      const provider = new WhisperProvider({
        apiKey: openaiApiKey!,
        ...(language ? { language } : {}),
      });
      const heard = await provider.transcribe(blob);
      const s = scorePronunciation(target!.text, heard);
      setTranscript(heard);
      setScore(s);
      setPhase("scored");
      await repos.pronunciation.record(profile.id, {
        targetText: target!.text,
        transcript: heard,
        score: s,
      });
    } catch (err) {
      setError(String(err));
      setPhase("idle");
    }
  }

  function next() {
    setIndex((i) => (i + 1) % targets.length);
    setPhase("idle");
    setTranscript(null);
    setScore(null);
    if (audioUrl) URL.revokeObjectURL(audioUrl);
    setAudioUrl(null);
  }

  if (!target) {
    return (
      <main className="container">
        <h1>Pronunciation</h1>
        <p className="subtitle">The active pack has no practice targets yet.</p>
        <button type="button" className="link" onClick={onDone}>
          Back to dashboard
        </button>
      </main>
    );
  }

  return (
    <main className="container">
      <h1>Pronunciation</h1>
      <p className="subtitle">Say the phrase, then compare what Whisper heard. (Comprehensibility check — phoneme-level feedback comes later.)</p>

      <section className="card review-card">
        <div className="review-front">{target.text}</div>
        {target.hint && <div className="review-note">{target.hint}</div>}

        {phase === "idle" && (
          <button type="button" onClick={startRecording}>
            ● Record
          </button>
        )}
        {phase === "recording" && (
          <button type="button" onClick={stopRecording}>
            ■ Stop
          </button>
        )}
        {phase === "processing" && <p>Transcribing…</p>}

        {phase === "scored" && score !== null && (
          <>
            <div className="due-count">{Math.round(score * 100)}%</div>
            <div className="review-back">heard: “{transcript}”</div>
            {audioUrl && <audio controls src={audioUrl} />}
            <div className="grades">
              <button type="button" onClick={startRecording}>
                Try again
              </button>
              <button type="button" onClick={next}>
                Next phrase
              </button>
            </div>
          </>
        )}
      </section>

      {error && <p className="error">{error}</p>}

      <button type="button" className="link" onClick={onDone}>
        Back to dashboard
      </button>
    </main>
  );
}
