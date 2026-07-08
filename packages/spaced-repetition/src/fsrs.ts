import { createEmptyCard, fsrs, State, type Card, type FSRS } from "ts-fsrs";
import type { ReviewGrade, ReviewState, Scheduler, SchedulerState } from "./types.js";

const STATE_TO_FSRS: Record<ReviewState, State> = {
  new: State.New,
  learning: State.Learning,
  review: State.Review,
  relearning: State.Relearning,
};

const FSRS_TO_STATE: Record<State, ReviewState> = {
  [State.New]: "new",
  [State.Learning]: "learning",
  [State.Review]: "review",
  [State.Relearning]: "relearning",
};

function toCard(state: SchedulerState): Card {
  const card: Card = {
    due: new Date(state.dueAt),
    stability: state.stability,
    difficulty: state.difficulty,
    elapsed_days: 0, // recomputed by FSRS from last_review + now; stored value is an output
    scheduled_days: 0,
    reps: state.reps,
    lapses: state.lapses,
    state: STATE_TO_FSRS[state.state],
  };
  // Only set last_review when the item has been reviewed (exactOptionalPropertyTypes: no
  // explicit `undefined` on an optional field).
  if (state.lastReviewedAt) card.last_review = new Date(state.lastReviewedAt);
  return card;
}

function fromCard(card: Card, retrievability: number): SchedulerState {
  return {
    difficulty: card.difficulty,
    stability: card.stability,
    retrievability,
    state: FSRS_TO_STATE[card.state],
    dueAt: new Date(card.due).toISOString(),
    lastReviewedAt: card.last_review ? new Date(card.last_review).toISOString() : null,
    lapses: card.lapses,
    reps: card.reps,
  };
}

/**
 * FSRS scheduler (spec §16 preferred algorithm). Wraps ts-fsrs, translating between its Card
 * shape and our persisted SchedulerState. Reviewable item types share this scheduler in MVP
 * (vocab/phrase/grammar/slang recognition); pronunciation is practice, not SRS-scheduled
 * (plan risk 9). Optional `params` allow later per-item-type tuning without a new interface.
 */
export class FsrsScheduler implements Scheduler {
  private readonly f: FSRS;

  constructor(params?: Partial<Parameters<typeof fsrs>[0]>) {
    this.f = fsrs(params);
  }

  initialState(now: Date): SchedulerState {
    return fromCard(createEmptyCard(now), 0);
  }

  schedule(current: SchedulerState, grade: ReviewGrade, now: Date): SchedulerState {
    const card = toCard(current);
    // How recallable the item was when the learner reviewed it (0..1). New cards read ~0.
    const retrievability = this.f.get_retrievability(card, now, false) as number;
    const next = this.f.repeat(card, now)[grade.rating].card;
    return fromCard(next, retrievability);
  }
}
