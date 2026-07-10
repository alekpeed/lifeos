// Ghost Days — an ambient, full-page "on this day across the years" view.
// A superset of the Dashboard/Daily Paper's small on-this-day snippet
// (Milestones, Places visited, Books started/finished), adding contact
// birthdays, recipes cooked, and tasks/assignments completed on this date in
// past years. Deliberately its own computation rather than reusing
// getOnThisDay() in api.js -- that function feeds two already-tested views
// (Dashboard, Daily Paper) and this is a strict superset with a different,
// more ambient presentation, so keeping it separate avoids any risk of
// changing behavior those views already rely on.

import { el, fmtDate } from '../dom.js';

function monthDay(dateStr) {
  return dateStr ? dateStr.slice(5, 10) : null;
}

function yearsAgo(year) {
  const n = new Date().getFullYear() - Number(year);
  return n <= 0 ? 'this year' : `${n} year${n === 1 ? '' : 's'} ago`;
}

export async function renderGhostDays(canvas, ctx) {
  const now = new Date();
  const todayMD = `${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const thisYear = String(now.getFullYear());

  const [milestones, places, books, contacts, recipes, cookLogs, tasks, assignments] = await Promise.all([
    ctx.data.Milestones.list(),
    ctx.data.Places.list(),
    ctx.data.Books.list(),
    ctx.data.Contacts.list(),
    ctx.data.Recipes.list(),
    ctx.data.CookLogs.list(),
    ctx.data.Tasks.list(),
    ctx.data.Assignments.list(),
  ]);

  const items = [];

  for (const m of milestones) {
    if (monthDay(m.date) === todayMD && m.date.slice(0, 4) !== thisYear) {
      items.push({ year: m.date.slice(0, 4), kind: 'Milestone', text: m.title || '(untitled)' });
    }
  }
  for (const p of places) {
    for (const d of p.visitDates || []) {
      if (monthDay(d) === todayMD && d.slice(0, 4) !== thisYear) {
        items.push({ year: d.slice(0, 4), kind: 'Visited', text: p.name || '(untitled)' });
      }
    }
  }
  for (const b of books) {
    for (const [field, kind] of [['finishedDate', 'Finished reading'], ['startedDate', 'Started reading']]) {
      const d = b[field];
      if (d && monthDay(d) === todayMD && d.slice(0, 4) !== thisYear) {
        items.push({ year: d.slice(0, 4), kind, text: b.title || '(untitled)' });
      }
    }
  }
  for (const c of contacts) {
    if (c.birthday && monthDay(c.birthday) === todayMD) {
      items.push({ year: null, kind: 'Birthday', text: c.name || '(untitled)' });
    }
  }
  const recipeById = new Map(recipes.map((r) => [r.id, r]));
  for (const log of cookLogs) {
    if (log.date && monthDay(log.date) === todayMD && log.date.slice(0, 4) !== thisYear) {
      const recipe = recipeById.get(log.recipeId);
      items.push({ year: log.date.slice(0, 4), kind: 'Cooked', text: recipe?.title || '(untitled recipe)' });
    }
  }
  for (const t of [...tasks, ...assignments]) {
    if (t.status === 'done' && t.updatedAt && monthDay(t.updatedAt) === todayMD && t.updatedAt.slice(0, 4) !== thisYear) {
      items.push({ year: t.updatedAt.slice(0, 4), kind: 'Completed', text: t.title || '(untitled)' });
    }
  }

  // Birthdays (year: null) float to the top as "today"; the rest sort most-recent-year first.
  items.sort((a, b) => {
    if (a.year === null && b.year === null) return 0;
    if (a.year === null) return -1;
    if (b.year === null) return 1;
    return b.year.localeCompare(a.year);
  });

  canvas.append(el('h1', { text: 'Ghost Days' }));
  canvas.append(el('p', { class: 'mer-muted', text: `Everything that happened on ${fmtDate(now.toISOString().slice(0, 10))} across the years.` }));

  if (!items.length) {
    canvas.append(el('p', { class: 'mer-ghostday-empty', text: 'No ghosts today — a quiet page in the record.' }));
    return;
  }

  const list = el('div', { class: 'mer-ghostday-list' });
  for (const item of items) {
    list.append(el('div', { class: 'mer-ghostday-row' }, [
      el('span', { class: 'mer-ghostday-kind', text: item.kind }),
      el('span', { class: 'mer-ghostday-text', text: item.text }),
      el('span', { class: 'mer-ghostday-when', text: item.year === null ? 'today' : yearsAgo(item.year) }),
    ]));
  }
  canvas.append(list);
}
