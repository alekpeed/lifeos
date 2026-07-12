// Recall — the Languages module's SRS engine generalized to resurface any
// record in the app: a book highlight, a contact you haven't reached out to
// in months, a place you meant to revisit. Deliberately reuses the
// Knowledge Graph's foundations rather than duplicating them: "what's
// schedulable" is "what globalSearch can find" (same picker grammar as the
// graph's "add a connection"), and titles resolve live via
// resolveGraphNode instead of a second title-lookup table, so a renamed or
// deleted record never leaves a stale label here either.

import { el, fmtDate, todayStr } from '../dom.js';

let state = { query: '' };

function moduleLabel(ctx, moduleId) {
  return ctx.modules.find((m) => m.id === moduleId)?.label || moduleId || '?';
}

function computeStreak(dates) {
  const days = new Set(dates);
  let streak = 0;
  const cursor = new Date(todayStr() + 'T00:00:00');
  if (!days.has(cursor.toISOString().slice(0, 10))) cursor.setDate(cursor.getDate() - 1);
  while (days.has(cursor.toISOString().slice(0, 10))) {
    streak++;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

function searchForm(placeholder, initial, onSearch) {
  const input = el('input', {
    type: 'search', placeholder, value: initial,
    onkeydown: (e) => { if (e.key === 'Enter') onSearch(e.target.value); },
  });
  return el('div', { class: 'mer-person-form' }, [
    input,
    el('button', { type: 'button', text: 'Search', onclick: () => onSearch(input.value) }),
  ]);
}

async function renderScheduler(canvas, ctx, rerender, scheduledKeys) {
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Schedule something for recall' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Anything Search can find — a task, a book, a contact, a place — can be scheduled here. It resurfaces on its own interval, exactly like a language flashcard, but there\'s no answer to grade: just "have you looked at this again?"' }));
  canvas.append(searchForm('Search everything…', state.query, (q) => { state.query = q; rerender(); }));

  if (!state.query.trim()) return;
  const results = (await ctx.data.globalSearch(state.query))
    .filter((r) => !scheduledKeys.has(ctx.data.graphKey(r.store, r.id)));
  if (!results.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'No schedulable matches (already-scheduled records are hidden).' }));
    return;
  }
  const area = el('div', { class: 'mer-task-list-area' });
  for (const r of results.slice(0, 20)) {
    area.append(el('div', { class: 'mer-task-row' }, [
      el('span', { class: 'mer-task-title', text: r.title }),
      el('span', { class: 'mer-chip', text: moduleLabel(ctx, r.module) }),
      el('button', {
        type: 'button', class: 'mer-reader-btn', text: '+ Schedule',
        onclick: async () => {
          await ctx.data.addResurfaceItem(r.store, r.id);
          state.query = '';
          rerender();
        },
      }),
    ]));
  }
  canvas.append(area);
}

async function renderReviewSession(canvas, ctx, rerender, dueNodes) {
  canvas.append(el('div', { class: 'mer-subsection-label', text: `Review (${dueNodes.length} due)` }));
  const area = el('div', { class: 'mer-task-list-area' });
  for (const n of dueNodes) {
    const row = el('div', { class: 'mer-task-detail' }, [
      el('h2', { text: n.title }),
      el('span', { class: 'mer-chip', text: moduleLabel(ctx, n.module) }),
      el('div', { class: 'mer-toolbar' }, [
        n.module ? el('button', { type: 'button', class: 'mer-reader-btn', text: 'Open →', onclick: () => ctx.navigate(n.module) }) : null,
        el('button', { type: 'button', class: 'mer-reader-btn', text: 'Remove from Recall', onclick: async () => { await ctx.data.ResurfaceItems.remove(n.itemId); rerender(); } }),
      ].filter(Boolean)),
      el('div', { class: 'mer-toggle-group' }, [
        el('button', { type: 'button', text: 'Again', onclick: async () => { await ctx.data.gradeResurfaceItem(n.itemId, 'again'); rerender(); } }),
        el('button', { type: 'button', text: 'Good', onclick: async () => { await ctx.data.gradeResurfaceItem(n.itemId, 'good'); rerender(); } }),
        el('button', { type: 'button', text: 'Easy', onclick: async () => { await ctx.data.gradeResurfaceItem(n.itemId, 'easy'); rerender(); } }),
      ]),
    ]);
    area.append(row);
  }
  canvas.append(area);
}

export async function renderRecall(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Recall' }));

  const [items, logs] = await Promise.all([
    ctx.data.ResurfaceItems.list(),
    ctx.data.ResurfaceReviewLogs.list(),
  ]);
  const streak = computeStreak(logs.map((l) => l.date));
  canvas.append(el('p', {}, [el('strong', { text: `🔁 ${streak}-day recall streak` })]));

  const nodes = await Promise.all(items.map(async (item) => {
    const node = await ctx.data.resolveGraphNode(item.key);
    return { ...node, itemId: item.id, dueDate: item.srs?.dueDate || todayStr(), interval: item.srs?.interval || 1 };
  }));

  // A scheduled record that was since deleted has nothing left to resurface --
  // drop it silently rather than showing a tombstone review prompt.
  const live = nodes.filter((n) => n.exists);
  for (const gone of nodes.filter((n) => !n.exists)) {
    await ctx.data.ResurfaceItems.remove(gone.itemId);
  }

  const due = live.filter((n) => n.dueDate <= todayStr());
  const upcoming = live.filter((n) => n.dueDate > todayStr()).sort((a, b) => a.dueDate.localeCompare(b.dueDate));

  if (due.length) await renderReviewSession(canvas, ctx, rerender, due);

  canvas.append(el('div', { class: 'mer-subsection-label', text: `Upcoming (${upcoming.length})` }));
  if (!upcoming.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing scheduled ahead — everything due is in Review above, or nothing\'s scheduled yet.' }));
  } else {
    const area = el('div', { class: 'mer-task-list-area' });
    for (const n of upcoming) {
      area.append(el('div', { class: 'mer-task-row' }, [
        el('span', { class: 'mer-task-title', text: n.title }),
        el('div', { class: 'mer-task-meta' }, [
          el('span', { class: 'mer-chip', text: moduleLabel(ctx, n.module) }),
          el('span', { class: 'mer-chip', text: `due ${fmtDate(n.dueDate)}` }),
        ]),
        el('button', {
          type: 'button', class: 'mer-icon-btn', text: '×', title: 'Remove from Recall',
          onclick: async () => { await ctx.data.ResurfaceItems.remove(n.itemId); rerender(); },
        }),
      ]));
    }
    canvas.append(area);
  }

  await renderScheduler(canvas, ctx, rerender, new Set(live.map((n) => n.key)));
}
