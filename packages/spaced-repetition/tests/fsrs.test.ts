import { describe, expect, it } from "vitest";
import { FsrsScheduler, type SchedulerState } from "../src/index.js";

const T0 = new Date("2026-07-08T00:00:00.000Z");
const addDays = (d: Date, days: number) => new Date(d.getTime() + days * 86_400_000);

describe("FsrsScheduler", () => {
  const scheduler = new FsrsScheduler();

  it("starts items in the 'new' state due now with zero history", () => {
    const s = scheduler.initialState(T0);
    expect(s.state).toBe("new");
    expect(s.reps).toBe(0);
    expect(s.lapses).toBe(0);
    expect(s.dueAt).toBe(T0.toISOString());
  });

  it("advances reps and pushes the due date out on a 'good' review", () => {
    const initial = scheduler.initialState(T0);
    const next = scheduler.schedule(initial, { rating: 3 }, T0);
    expect(next.reps).toBe(1);
    expect(new Date(next.dueAt).getTime()).toBeGreaterThan(T0.getTime());
    expect(next.lastReviewedAt).toBe(T0.toISOString());
    expect(next.stability).toBeGreaterThan(0);
  });

  it("schedules 'easy' further out than 'good', and 'good' further than 'again'", () => {
    const initial = scheduler.initialState(T0);
    const again = scheduler.schedule(initial, { rating: 1 }, T0);
    const good = scheduler.schedule(initial, { rating: 3 }, T0);
    const easy = scheduler.schedule(initial, { rating: 4 }, T0);

    const due = (s: SchedulerState) => new Date(s.dueAt).getTime();
    expect(due(again)).toBeLessThan(due(good));
    expect(due(good)).toBeLessThanOrEqual(due(easy));
  });

  it("counts a lapse when a learned item is forgotten ('again')", () => {
    // Build up a reviewed item over a few sessions, then fail it.
    let state = scheduler.initialState(T0);
    state = scheduler.schedule(state, { rating: 3 }, T0);
    state = scheduler.schedule(state, { rating: 3 }, addDays(T0, 1));
    state = scheduler.schedule(state, { rating: 3 }, new Date(state.dueAt));
    const lapsesBefore = state.lapses;

    const failed = scheduler.schedule(state, { rating: 1 }, new Date(state.dueAt));
    expect(failed.lapses).toBe(lapsesBefore + 1);
    expect(failed.state).toBe("relearning");
  });

  it("round-trips persisted state: a reconstructed state schedules the same as a live one", () => {
    const live = scheduler.schedule(scheduler.initialState(T0), { rating: 3 }, T0);
    // Simulate persisting to review_items columns and reloading (structuredClone = JSON-safe).
    const reloaded: SchedulerState = structuredClone(live);
    const reviewTime = addDays(T0, 3);
    const fromLive = scheduler.schedule(live, { rating: 3 }, reviewTime);
    const fromReloaded = scheduler.schedule(reloaded, { rating: 3 }, reviewTime);
    expect(fromReloaded.dueAt).toBe(fromLive.dueAt);
    expect(fromReloaded.stability).toBeCloseTo(fromLive.stability, 6);
  });
});
