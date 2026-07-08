/**
 * Scheduler interface — spec §16. FSRS (via ts-fsrs) is the default implementation; SM-2 is
 * the documented fallback. Kept as an interface so per-item-type scheduling policies can be
 * swapped later (spec §9, plan risk 9). SchedulerState maps 1:1 onto the review_items FSRS
 * columns (plan §4), so persisting a review is just writing these fields.
 */
export interface ReviewGrade {
  /** 1=again 2=hard 3=good 4=easy (FSRS grade). */
  rating: 1 | 2 | 3 | 4;
}

export type ReviewState = "new" | "learning" | "review" | "relearning";

export interface SchedulerState {
  difficulty: number;
  stability: number;
  /** Predicted retrievability at the moment of the most recent review (0..1). */
  retrievability: number;
  state: ReviewState;
  dueAt: string; // ISO-8601
  lastReviewedAt: string | null; // ISO-8601
  lapses: number;
  reps: number;
}

export interface Scheduler {
  /** A fresh, never-reviewed item due immediately at `now`. */
  initialState(now: Date): SchedulerState;
  /** Advance an item's state given the learner's grade at review time `now`. */
  schedule(current: SchedulerState, grade: ReviewGrade, now: Date): SchedulerState;
}
