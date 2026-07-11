import { useCallback, useEffect, useState } from "react";
import { loadReviewCard, type ReviewCard, type ReviewItem, type Repos } from "@polyglotai/core-learning";
import type { LearnerProfile } from "@polyglotai/shared-types";

interface Props {
  repos: Repos;
  profile: LearnerProfile;
  onDone: () => void;
}

/** Loose match: trims, lowercases, and strips accents — a learner who nails the conjugation
 * but skips a diacritic (easy to do on an unfamiliar keyboard) still passes the drill. */
function normalize(s: string): string {
  return s
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "");
}

/** Fill-the-slot substitution-ladder drills (rock-solid idea #9): each step is its own SRS
 * card, but the interaction is typing the answer rather than flipping a card, since that's
 * what actually drills production of a pattern. */
export function Drill({ repos, profile, onDone }: Props) {
  const [queue, setQueue] = useState<ReviewItem[]>([]);
  const [card, setCard] = useState<ReviewCard | null>(null);
  const [input, setInput] = useState("");
  const [checked, setChecked] = useState<{ correct: boolean } | null>(null);
  const [loading, setLoading] = useState(true);
  const [reviewed, setReviewed] = useState(0);

  const showNext = useCallback(
    async (items: ReviewItem[]) => {
      const next = items[0];
      if (!next) {
        setCard(null);
        return;
      }
      setCard(await loadReviewCard(repos, next));
      setInput("");
      setChecked(null);
    },
    [repos],
  );

  useEffect(() => {
    let active = true;
    const packId = profile.activePackId;
    (async () => {
      if (packId) await repos.reviews.generateForPack(profile.id, packId, ["grammar_ladder"]);
      const items = await repos.reviews.listDue(profile.id, 50, ["grammar_ladder"]);
      if (!active) return;
      setQueue(items);
      await showNext(items);
      setLoading(false);
    })();
    return () => {
      active = false;
    };
  }, [repos, profile.id, profile.activePackId, showNext]);

  function check() {
    if (!card || !input.trim()) return;
    setChecked({ correct: normalize(input) === normalize(card.back) });
  }

  async function next(rating: 1 | 2 | 3) {
    const current = queue[0];
    if (!current) return;
    await repos.reviews.recordReview(current.id, { rating });
    const rest = queue.slice(1);
    setQueue(rest);
    setReviewed((n) => n + 1);
    await showNext(rest);
  }

  if (loading) return <p className="container">Loading ladder drills…</p>;

  if (!card) {
    return (
      <main className="container">
        <h1>Ladder drill complete</h1>
        <p className="subtitle">
          {reviewed > 0 ? `You drilled ${reviewed} step(s).` : "No substitution ladders are due right now."}
        </p>
        <button type="button" onClick={onDone}>
          Back to dashboard
        </button>
      </main>
    );
  }

  return (
    <main className="container">
      <p className="progress">{queue.length} left</p>
      <section className="card review-card">
        <div className="review-front">{card.front}</div>
        {card.note && <div className="review-note">{card.note}</div>}

        {!checked ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              check();
            }}
          >
            <input
              value={input}
              onChange={(e) => setInput(e.currentTarget.value)}
              placeholder="Fill in the blank…"
              autoFocus
            />
            <button type="submit" disabled={!input.trim()}>
              Check
            </button>
          </form>
        ) : (
          <>
            <hr />
            <div className={checked.correct ? "review-back correct" : "review-back incorrect"}>
              {checked.correct ? "Correct! " : "Not quite. "}
              Answer: {card.back}
            </div>
            <div className="grades">
              {checked.correct ? (
                <button type="button" onClick={() => next(3)}>
                  Next
                </button>
              ) : (
                <>
                  <button type="button" onClick={() => next(1)}>
                    Next (missed it)
                  </button>
                  <button type="button" onClick={() => next(2)}>
                    Next (knew it, typo)
                  </button>
                </>
              )}
            </div>
          </>
        )}
      </section>

      <button type="button" className="link" onClick={onDone}>
        Stop for now
      </button>
    </main>
  );
}
