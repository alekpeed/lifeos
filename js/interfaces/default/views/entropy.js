// Entropy — one neglect score per module (and an overall one), based on how
// long it's been since each module's data was last touched. Pure computed
// view over existing records' updatedAt/date fields; nothing new is stored.

import { el, fmtDate, todayStr } from '../dom.js';

function daysSince(dateStr) {
  if (!dateStr) return null;
  const ms = new Date(todayStr() + 'T00:00:00') - new Date(dateStr.slice(0, 10) + 'T00:00:00');
  return Math.max(0, Math.round(ms / 86400000));
}

function latestOf(records, field = 'updatedAt') {
  if (!records.length) return null;
  return records.reduce((latest, r) => (r[field] > latest ? r[field] : latest), records[0][field] || '');
}

// A simple, legible scale: 0-6 days = fresh, 7-29 = a little stale, 30+ = neglected.
function severity(days) {
  if (days === null) return { label: 'No data yet', cls: 'is-unknown' };
  if (days <= 6) return { label: `${days}d ago`, cls: 'is-fresh' };
  if (days <= 29) return { label: `${days}d ago`, cls: 'is-stale' };
  return { label: `${days}d ago`, cls: 'is-neglected' };
}

export async function renderEntropy(canvas, ctx) {
  const [tasks, places, links, books, recipes, contacts, milestones, habitLogs, healthLogs, documents] = await Promise.all([
    ctx.data.Tasks.list(),
    ctx.data.Places.list(),
    ctx.data.Links.list(),
    ctx.data.Books.list(),
    ctx.data.Recipes.list(),
    ctx.data.Contacts.list(),
    ctx.data.Milestones.list(),
    ctx.data.HabitLogs.list(),
    ctx.data.HealthLogs.list(),
    ctx.data.Documents.list(),
  ]);

  const areas = [
    { module: 'tasks', label: 'Tasks', last: latestOf(tasks) },
    { module: 'places', label: 'Places', last: latestOf(places) },
    { module: 'links', label: 'Links', last: latestOf(links) },
    { module: 'books', label: 'Books', last: latestOf(books) },
    { module: 'recipes', label: 'Recipes', last: latestOf(recipes) },
    { module: 'contacts', label: 'Contacts', last: latestOf(contacts) },
    { module: 'milestones', label: 'Milestones', last: latestOf(milestones) },
    { module: 'habits', label: 'Habits', last: latestOf(habitLogs, 'date') },
    { module: 'health', label: 'Health', last: latestOf(healthLogs, 'date') },
    { module: 'documents', label: 'Documents', last: latestOf(documents) },
  ].map((a) => ({ ...a, days: daysSince(a.last) }));

  const known = areas.filter((a) => a.days !== null);
  const overallDays = known.length ? Math.round(known.reduce((sum, a) => sum + a.days, 0) / known.length) : null;
  const overall = severity(overallDays);

  const sorted = [...areas].sort((a, b) => (b.days ?? -1) - (a.days ?? -1));

  canvas.append(el('h1', { text: 'Entropy' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'How long since each area of the app was last touched — most neglected first.' }));

  canvas.append(el('div', { class: `mer-entropy-overall ${overall.cls}` }, [
    el('span', { class: 'mer-entropy-overall-label', text: 'Overall' }),
    el('span', { class: 'mer-entropy-overall-value', text: overallDays === null ? '—' : `${overallDays} days avg.` }),
  ]));

  const list = el('div', { class: 'mer-entropy-list' });
  for (const a of sorted) {
    const s = severity(a.days);
    list.append(el('div', { class: `mer-entropy-row ${s.cls}` }, [
      el('span', { class: 'mer-entropy-label', text: a.label }),
      el('span', { class: 'mer-entropy-value', text: s.label }),
      a.last ? el('span', { class: 'mer-muted', text: fmtDate(a.last) }) : null,
    ].filter(Boolean)));
  }
  canvas.append(list);
}
