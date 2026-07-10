// Dream Journal — entries plus recurring-pattern detection: a word-frequency
// scan across every entry's body/tags (minus common stopwords) surfacing
// what keeps showing up. Pure client-side text analysis, nothing external.

import { el, fmtDate, todayStr, parseTags } from '../dom.js';

const STOPWORDS = new Set([
  'the', 'a', 'an', 'and', 'or', 'but', 'of', 'to', 'in', 'on', 'at', 'is', 'was', 'were',
  'i', 'me', 'my', 'it', 'that', 'this', 'with', 'for', 'as', 'be', 'had', 'has', 'have',
  'then', 'there', 'we', 'they', 'he', 'she', 'you', 'his', 'her', 'their', 'so', 'up',
  'out', 'like', 'into', 'about', 'from', 'not', 'no', 'all', 'one', 'just', 'been', 'im',
]);

function extractWords(text) {
  return (text || '')
    .toLowerCase()
    .replace(/[^a-z\s]/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length > 3 && !STOPWORDS.has(w));
}

// Word/tag frequency across every entry, min occurrence 2 to count as "recurring".
function findRecurringPatterns(entries) {
  const wordCounts = new Map();
  const tagCounts = new Map();
  for (const entry of entries) {
    for (const w of new Set(extractWords(entry.body))) {
      wordCounts.set(w, (wordCounts.get(w) || 0) + 1);
    }
    for (const t of entry.tags || []) {
      tagCounts.set(t, (tagCounts.get(t) || 0) + 1);
    }
  }
  const topWords = [...wordCounts.entries()].filter(([, n]) => n >= 2).sort((a, b) => b[1] - a[1]).slice(0, 10);
  const topTags = [...tagCounts.entries()].filter(([, n]) => n >= 2).sort((a, b) => b[1] - a[1]).slice(0, 10);
  return { topWords, topTags };
}

function entryCard(entry, ctx, rerender) {
  return el('div', { class: 'mer-person-card' }, [
    el('div', { class: 'mer-person-info' }, [
      el('div', { class: 'mer-person-name', text: entry.title || fmtDate(entry.date) }),
      el('div', { class: 'mer-person-meta' }, [
        el('span', { text: fmtDate(entry.date) }),
        ...(entry.tags || []).map((t) => el('span', { class: 'mer-chip mer-chip-tag', text: `#${t}` })),
      ]),
      el('p', { class: 'mer-muted', text: entry.body || '' }),
    ]),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×', title: 'Delete',
      onclick: async () => { if (confirm('Delete this entry?')) { await ctx.data.DreamEntries.remove(entry.id); rerender(); } },
    }),
  ]);
}

function newEntryForm(ctx, rerender) {
  const dateIn = el('input', { type: 'date', value: todayStr() });
  const titleIn = el('input', { type: 'text', placeholder: 'Title (optional)' });
  const bodyIn = el('textarea', { rows: '4', placeholder: 'What happened in the dream?' });
  const tagsIn = el('input', { type: 'text', placeholder: 'Tags (comma separated, e.g. flying, water, exam)' });
  return el('div', { class: 'mer-person-form' }, [
    dateIn, titleIn, bodyIn, tagsIn,
    el('button', {
      type: 'button', text: 'Save entry',
      onclick: async () => {
        if (!bodyIn.value.trim()) return;
        await ctx.data.DreamEntries.create({ date: dateIn.value || todayStr(), title: titleIn.value.trim(), body: bodyIn.value.trim(), tags: parseTags(tagsIn.value) });
        rerender();
      },
    }),
  ]);
}

export async function renderDreamJournal(canvas, ctx, rerender) {
  const entries = await ctx.data.DreamEntries.list();
  const sorted = [...entries].sort((a, b) => (b.date || '').localeCompare(a.date || ''));

  canvas.append(el('h1', { text: 'Dream Journal' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Log dreams, and see what keeps recurring across them.' }));

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'New entry' }));
  canvas.append(newEntryForm(ctx, rerender));

  if (entries.length >= 2) {
    const { topWords, topTags } = findRecurringPatterns(entries);
    if (topWords.length || topTags.length) {
      canvas.append(el('div', { class: 'mer-subsection-label', text: 'Recurring patterns' }));
      const row = el('div', { class: 'mer-toolbar' }, [
        ...topTags.map(([t, n]) => el('span', { class: 'mer-chip mer-chip-tag', text: `#${t} ×${n}` })),
        ...topWords.map(([w, n]) => el('span', { class: 'mer-chip', text: `${w} ×${n}` })),
      ]);
      canvas.append(row);
    }
  }

  canvas.append(el('div', { class: 'mer-subsection-label', text: `Entries (${sorted.length})` }));
  if (!sorted.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'No dreams logged yet.' }));
    return;
  }
  const list = el('div', { class: 'mer-people-list' });
  for (const entry of sorted) list.append(entryCard(entry, ctx, rerender));
  canvas.append(list);
}
