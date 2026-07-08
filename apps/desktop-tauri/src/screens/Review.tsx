import { useCallback, useEffect, useState } from "react";
import {
  loadReviewCard,
  type ReviewCard,
  type ReviewItem,
  type Repos,
} from "@polyglotai/core-learning";
import type { LearnerProfile } from "@polyglotai/shared-types";

interface Props {
  repos: Repos;
  profile: LearnerProfile;
  onDone: () => void;
}

const GRADES: { rating: 1 | 2 | 3 | 4; label: string }[] = [
  { rating: 1, label: "Again" },
  { rating: 2, label: "Hard" },
  { rating: 3, label: "Good" },
  { rating: 4, label: "Easy" },
];

export function Review({ repos, profile, onDone }: Props) {
  const [queue, setQueue] = useState<ReviewItem[]>([]);
  const [card, setCard] = useState<ReviewCard | null>(null);
  const [revealed, setRevealed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [reviewed, setReviewed] = useState(0);

  const showFront = useCallback(
    async (items: ReviewItem[]) => {
      const next = items[0];
      if (!next) {
        setCard(null);
        return;
      }
      setCard(await loadReviewCard(repos, next));
      setRevealed(false);
    },
    [repos],
  );

  useEffect(() => {
    let active = true;
    repos.reviews.listDue(profile.id).then(async (items) => {
      if (!active) return;
      setQueue(items);
      await showFront(items);
      setLoading(false);
    });
    return () => {
      active = false;
    };
  }, [repos, profile.id, showFront]);

  async function grade(rating: 1 | 2 | 3 | 4) {
    const current = queue[0];
    if (!current) return;
    await repos.reviews.recordReview(current.id, { rating });
    const rest = queue.slice(1);
    setQueue(rest);
    setReviewed((n) => n + 1);
    await showFront(rest);
  }

  if (loading) return <p className="container">Loading review…</p>;

  if (!card) {
    return (
      <main className="container">
        <h1>Review complete</h1>
        <p className="subtitle">You reviewed {reviewed} item(s).</p>
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
        {revealed ? (
          <>
            <hr />
            <div className="review-back">{card.back}</div>
            {card.note && <div className="review-note">{card.note}</div>}
          </>
        ) : (
          <button type="button" onClick={() => setRevealed(true)}>
            Show answer
          </button>
        )}
      </section>

      {revealed && (
        <div className="grades">
          {GRADES.map((g) => (
            <button key={g.rating} type="button" onClick={() => grade(g.rating)}>
              {g.label}
            </button>
          ))}
        </div>
      )}

      <button type="button" className="link" onClick={onDone}>
        Stop for now
      </button>
    </main>
  );
}
