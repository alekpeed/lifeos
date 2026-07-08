import type { SqlValue } from "../db/database.js";
import type { Repos } from "../repos.js";
import type { ReviewItem } from "../review/reviewRepo.js";

/** A review item rendered for display: prompt on the front, answer on the back. */
export interface ReviewCard {
  front: string;
  back: string;
  note?: string;
}

/**
 * Loads the display content for a review item from its content table (spec §16 reviewable
 * types). Real-speech cards surface register + severity so the learner reviews *judgment*, not
 * just meaning — the core adult-language goal (spec §3, §13).
 */
export async function loadReviewCard(repos: Repos, item: ReviewItem): Promise<ReviewCard> {
  const id = item.contentId as SqlValue;

  if (item.itemType === "vocabulary") {
    const rows = await repos.db.all<{ lemma: string; translation: string; pronunciation_notes: string | null }>(
      "SELECT lemma, translation, pronunciation_notes FROM vocabulary_items WHERE id = ?",
      [id],
    );
    const r = rows[0];
    if (!r) throw new Error(`vocabulary content ${item.contentId} not found`);
    return r.pronunciation_notes
      ? { front: r.lemma, back: r.translation, note: r.pronunciation_notes }
      : { front: r.lemma, back: r.translation };
  }

  if (item.itemType === "grammar") {
    const rows = await repos.db.all<{ title: string; explanation_md: string }>(
      "SELECT title, explanation_md FROM grammar_items WHERE id = ?",
      [id],
    );
    const r = rows[0];
    if (!r) throw new Error(`grammar content ${item.contentId} not found`);
    return { front: r.title, back: r.explanation_md };
  }

  // real_speech
  const rows = await repos.db.all<{
    phrase: string;
    natural: string | null;
    register: string;
    severity: number;
    cultural_warning: string | null;
  }>("SELECT phrase, natural, register, severity, cultural_warning FROM real_speech_items WHERE id = ?", [id]);
  const r = rows[0];
  if (!r) throw new Error(`real-speech content ${item.contentId} not found`);
  const warning = r.cultural_warning ? ` — ${r.cultural_warning}` : "";
  return {
    front: r.phrase,
    back: r.natural ?? r.phrase,
    note: `${r.register} · severity ${r.severity}/7${warning}`,
  };
}
