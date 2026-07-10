// The Museum of Finished Things — a trophy-case view over completions that
// already live scattered across other modules (done tasks, finished books,
// milestones, cooked recipes, closed-out projects, habit streaks). Nothing
// new is stored; this is a *view*, same spirit as the Daily Paper.
//
// "Finished" per module, matched to how each module already marks it:
//   - Tasks / Assignments: status === 'done'
//   - Books: status === 'finished' (finishedDate if set, else updatedAt)
//   - Milestones: every milestone IS an achieved thing by definition
//   - Recipes: any recipe with at least one cook log, ranked by times cooked
//   - Projects: archived === true
//   - Habits: the longest unbroken streak ever logged (not just current)

import { el, fmtDate } from '../dom.js';

const isCover = (a) => (a.mimeType || '').startsWith('image/');

function wing(title, count, body) {
  return el('section', { class: 'mer-museum-wing' }, [
    el('div', { class: 'mer-museum-wing-head' }, [
      el('h2', { class: 'mer-museum-wing-title', text: title }),
      el('span', { class: 'mer-museum-wing-count', text: String(count) }),
    ]),
    body,
  ]);
}

function empty(text) {
  return el('p', { class: 'mer-muted mer-museum-empty', text });
}

// --- Longest-ever streak (distinct from habits.js's "current streak",
// which only counts backward from today). Walks the sorted unique dates and
// finds the longest run of consecutive days anywhere in the history. ---
function longestStreak(dates) {
  const days = [...new Set(dates)].sort();
  if (!days.length) return 0;
  let best = 1;
  let run = 1;
  for (let i = 1; i < days.length; i++) {
    const prev = new Date(days[i - 1] + 'T00:00:00');
    const cur = new Date(days[i] + 'T00:00:00');
    const diff = Math.round((cur - prev) / 86400000);
    run = diff === 1 ? run + 1 : 1;
    if (run > best) best = run;
  }
  return best;
}

function plaque(title, meta) {
  return el('div', { class: 'mer-museum-plaque' }, [
    el('div', { class: 'mer-museum-plaque-title', text: title || '(untitled)' }),
    meta ? el('div', { class: 'mer-museum-plaque-meta', text: meta }) : null,
  ].filter(Boolean));
}

function booksWing(books, coversByBookId) {
  const finished = books
    .filter((b) => b.status === 'finished')
    .sort((a, b) => (b.finishedDate || b.updatedAt || '').localeCompare(a.finishedDate || a.updatedAt || ''));
  if (!finished.length) return wing('Books Read', 0, empty('No finished books yet — mark one "Finished" in Books to see it here.'));

  const grid = el('div', { class: 'mer-museum-book-grid' });
  for (const b of finished) {
    const cover = coversByBookId.get(b.id);
    grid.append(el('div', { class: 'mer-museum-book' }, [
      cover
        ? el('img', { class: 'mer-museum-cover', src: cover, alt: b.title })
        : el('div', { class: 'mer-museum-cover mer-museum-cover-empty', text: '📖' }),
      el('div', { class: 'mer-museum-book-title', text: b.title || '(untitled)' }),
      el('div', { class: 'mer-museum-book-meta', text: b.author || '' }),
      el('div', { class: 'mer-museum-book-meta', text: fmtDate(b.finishedDate || b.updatedAt) }),
    ]));
  }
  return wing('Books Read', finished.length, grid);
}

function tasksWing(tasks, assignments) {
  const doneTasks = tasks.filter((t) => t.status === 'done');
  const doneAssignments = assignments.filter((a) => a.status === 'done');
  const total = doneTasks.length + doneAssignments.length;
  if (!total) return wing('Tasks & Assignments Completed', 0, empty('Nothing checked off yet.'));

  const recent = [...doneTasks, ...doneAssignments]
    .sort((a, b) => (b.updatedAt || '').localeCompare(a.updatedAt || ''))
    .slice(0, 10);
  const list = el('ul', { class: 'mer-museum-list' });
  for (const item of recent) {
    list.append(el('li', {}, [plaque(item.title, fmtDate(item.updatedAt))]));
  }
  const body = el('div', {}, [
    el('div', { class: 'mer-museum-tally', text: `${total} thing${total === 1 ? '' : 's'} done` }),
    list,
  ]);
  return wing('Tasks & Assignments Completed', total, body);
}

function milestonesWing(milestones) {
  const sorted = [...milestones].sort((a, b) => (b.date || '').localeCompare(a.date || ''));
  if (!sorted.length) return wing('Milestones Achieved', 0, empty('No milestones logged yet.'));
  const list = el('ul', { class: 'mer-museum-list' });
  for (const m of sorted) {
    const meta = [m.category, fmtDate(m.date)].filter(Boolean).join(' · ');
    list.append(el('li', {}, [plaque(m.title, meta)]));
  }
  return wing('Milestones Achieved', sorted.length, list);
}

function recipesWing(recipes, cookLogs) {
  const countByRecipe = new Map();
  for (const log of cookLogs) {
    countByRecipe.set(log.recipeId, (countByRecipe.get(log.recipeId) || 0) + 1);
  }
  const cooked = recipes
    .filter((r) => countByRecipe.has(r.id))
    .sort((a, b) => (countByRecipe.get(b.id) || 0) - (countByRecipe.get(a.id) || 0));
  if (!cooked.length) return wing('Recipes Mastered', 0, empty('Log a cook on a recipe to see it here.'));
  const list = el('ul', { class: 'mer-museum-list' });
  for (const r of cooked) {
    const n = countByRecipe.get(r.id);
    list.append(el('li', {}, [plaque(r.title, `Cooked ${n} time${n === 1 ? '' : 's'}`)]));
  }
  return wing('Recipes Mastered', cooked.length, list);
}

function projectsWing(projects) {
  const done = projects.filter((p) => p.archived);
  if (!done.length) return wing('Projects Completed', 0, empty('No archived projects yet.'));
  const list = el('ul', { class: 'mer-museum-list' });
  for (const p of done) {
    list.append(el('li', {}, [plaque(p.name, fmtDate(p.updatedAt))]));
  }
  return wing('Projects Completed', done.length, list);
}

function habitsWing(habits, logsByHabit) {
  const withStreaks = habits
    .map((h) => ({ habit: h, best: longestStreak((logsByHabit.get(h.id) || []).map((l) => l.date)) }))
    .filter((x) => x.best > 0)
    .sort((a, b) => b.best - a.best);
  if (!withStreaks.length) return wing('Best Habit Streaks', 0, empty('No streaks logged yet.'));
  const list = el('ul', { class: 'mer-museum-list' });
  for (const { habit, best } of withStreaks) {
    list.append(el('li', {}, [plaque(habit.name, `🔥 ${best}-day streak`)]));
  }
  return wing('Best Habit Streaks', withStreaks.length, list);
}

export async function renderMuseum(canvas, ctx) {
  const [tasks, assignments, books, milestones, recipes, cookLogs, projects, habits, habitLogs] = await Promise.all([
    ctx.data.Tasks.list(),
    ctx.data.Assignments.list(),
    ctx.data.Books.list(),
    ctx.data.Milestones.list(),
    ctx.data.Recipes.list(),
    ctx.data.CookLogs.list(),
    ctx.data.Projects.list(),
    ctx.data.Habits.list(),
    ctx.data.HabitLogs.list(),
  ]);

  const finishedBooks = books.filter((b) => b.status === 'finished');
  const coverLists = await Promise.all(finishedBooks.map((b) => ctx.data.getAttachmentsFor('books', b.id)));
  const coversByBookId = new Map();
  finishedBooks.forEach((b, i) => {
    const cover = coverLists[i].find(isCover);
    if (cover) coversByBookId.set(b.id, ctx.data.attachmentUrl(cover));
  });

  const logsByHabit = new Map();
  for (const log of habitLogs) {
    if (!logsByHabit.has(log.habitId)) logsByHabit.set(log.habitId, []);
    logsByHabit.get(log.habitId).push(log);
  }

  const doneTasks = tasks.filter((t) => t.status === 'done').length;
  const doneAssignments = assignments.filter((a) => a.status === 'done').length;
  const totalFinished = doneTasks + doneAssignments + finishedBooks.length + milestones.length
    + recipes.filter((r) => cookLogs.some((l) => l.recipeId === r.id)).length
    + projects.filter((p) => p.archived).length;

  canvas.append(el('h1', { text: 'Museum of Finished Things' }));
  canvas.append(el('p', { class: 'mer-muted', text: `${totalFinished} things finished, and counting.` }));

  const hall = el('div', { class: 'mer-museum-hall' }, [
    booksWing(books, coversByBookId),
    tasksWing(tasks, assignments),
    milestonesWing(milestones),
    recipesWing(recipes, cookLogs),
    habitsWing(habits, logsByHabit),
    projectsWing(projects),
  ]);
  canvas.append(hall);
}
