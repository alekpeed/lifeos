// Time Machine — scrub back through time and see the app as it looked on a
// past day. This is the agreed LIGHTWEIGHT approximation: it reads the
// createdAt timestamps every record already carries, plus the log stores
// whose records are genuinely dated (habit check-ins, health logs, cook
// logs, reading logs). It does NOT pretend to more history than the data
// actually holds, and says so on screen:
//   • "existed then" = created on or before that day (createdAt cutoff)
//   • field values shown are CURRENT values — an edited title reads as it
//     does today, not as it did then
//   • records deleted since then are absent from the picture entirely
// A true any-point-in-time reconstruction needs the event-sourced core from
// the moonshot tier; when that lands, this view upgrades in place.

import { el, fmtDate, todayStr } from '../dom.js';

let state = { date: null }; // null = today (the "present" position)

// Content stores that make sense to count "existence over time" for, with
// how to title a record when listing the ones born on the chosen day.
const STORES = [
  { entity: 'Tasks', label: 'Tasks', titleOf: (r) => r.title },
  { entity: 'Places', label: 'Places', titleOf: (r) => r.name },
  { entity: 'Links', label: 'Links', titleOf: (r) => r.title || r.url },
  { entity: 'Books', label: 'Books', titleOf: (r) => r.title },
  { entity: 'Recipes', label: 'Recipes', titleOf: (r) => r.title },
  { entity: 'Bills', label: 'Bills', titleOf: (r) => r.name },
  { entity: 'Subscriptions', label: 'Subscriptions', titleOf: (r) => r.name },
  { entity: 'Documents', label: 'Documents', titleOf: (r) => r.title },
  { entity: 'Contacts', label: 'Contacts', titleOf: (r) => r.name },
  { entity: 'Milestones', label: 'Milestones', titleOf: (r) => r.title },
  { entity: 'Habits', label: 'Habits', titleOf: (r) => r.name },
  { entity: 'Collections', label: 'Collections', titleOf: (r) => r.name },
  { entity: 'RabbitHoles', label: 'Rabbit Holes', titleOf: (r) => r.topic },
  { entity: 'TimeCapsules', label: 'Time Capsules', titleOf: (r) => r.title },
  { entity: 'InventoryItems', label: 'Inventory', titleOf: (r) => r.name },
];

function createdDay(record) {
  return (record.createdAt || '').slice(0, 10);
}

function daysBetween(a, b) {
  return Math.round((new Date(b + 'T00:00:00') - new Date(a + 'T00:00:00')) / 86400000);
}

function isoDaysAfter(dateStr, n) {
  const d = new Date(dateStr + 'T00:00:00');
  d.setDate(d.getDate() + n);
  return d.toISOString().slice(0, 10);
}

export async function renderTimeMachine(canvas, ctx, rerender) {
  const today = todayStr();
  const all = await Promise.all(STORES.map(async (s) => ({ ...s, records: await ctx.data[s.entity].list() })));
  const [habitLogs, healthLogs, cookLogs, readingLogs, habits, recipes] = await Promise.all([
    ctx.data.HabitLogs.list(), ctx.data.HealthLogs.list(), ctx.data.CookLogs.list(),
    ctx.data.ReadingLogs.list(), ctx.data.Habits.list(), ctx.data.Recipes.list(),
  ]);

  // The timeline starts at the oldest thing the app has ever seen (records
  // or dated logs), ends today.
  const allDates = [
    ...all.flatMap((s) => s.records.map(createdDay)),
    ...[...habitLogs, ...healthLogs, ...cookLogs, ...readingLogs].map((l) => (l.date || '').slice(0, 10)),
  ].filter(Boolean);
  const earliest = allDates.length ? allDates.reduce((a, b) => (b < a ? b : a)) : today;
  const span = Math.max(1, daysBetween(earliest, today));

  if (!state.date || state.date > today || state.date < earliest) state.date = today;
  const date = state.date;
  const atPresent = date === today;

  canvas.append(el('h1', { text: 'Time Machine' }));
  canvas.append(el('p', { class: 'mer-muted', text: `Scrub back through ${span} day${span === 1 ? '' : 's'} of recorded life and see what the app knew on any given day.` }));

  // --- The scrubber: a slider and a date input, kept in sync ---
  const slider = el('input', {
    type: 'range', min: '0', max: String(span), value: String(daysBetween(earliest, date)),
    class: 'mer-tm-slider',
    onchange: (e) => { state.date = isoDaysAfter(earliest, Number(e.target.value)); rerender(); },
  });
  const dateInput = el('input', {
    type: 'date', value: date, min: earliest, max: today,
    onchange: (e) => { if (e.target.value) { state.date = e.target.value; rerender(); } },
  });
  canvas.append(el('div', { class: 'mer-tm-controls' }, [
    el('span', { class: 'mer-tm-endlabel', text: fmtDate(earliest) }),
    slider,
    el('span', { class: 'mer-tm-endlabel', text: 'today' }),
  ]));
  canvas.append(el('div', { class: 'mer-person-form' }, [
    dateInput,
    atPresent ? null : el('button', { type: 'button', text: 'Return to today', onclick: () => { state.date = null; rerender(); } }),
  ].filter(Boolean)));

  // --- The snapshot ---
  const cutoff = date; // records created on or before this day "existed"
  const rows = all.map((s) => {
    const then = s.records.filter((r) => createdDay(r) && createdDay(r) <= cutoff).length;
    return { ...s, then, now: s.records.length };
  }).filter((r) => r.now > 0);
  const totalThen = rows.reduce((sum, r) => sum + r.then, 0);
  const totalNow = rows.reduce((sum, r) => sum + r.now, 0);

  canvas.append(el('div', { class: 'mer-subsection-label', text: atPresent ? 'The present' : `Life OS as of ${fmtDate(date)}` }));
  canvas.append(el('div', { class: 'mer-tm-headline' }, [
    el('span', { class: 'mer-tm-big', text: String(totalThen) }),
    el('span', { class: 'mer-muted', text: atPresent ? ' records live in the app today.' : ` of today's ${totalNow} records already existed.` }),
  ]));

  const grid = el('div', { class: 'mer-tm-grid' });
  for (const r of rows) {
    grid.append(el('div', { class: 'mer-tm-cell' }, [
      el('div', { class: 'mer-tm-label', text: r.label }),
      el('div', { class: 'mer-tm-count' }, [
        el('span', { text: String(r.then) }),
        atPresent || r.then === r.now ? null : el('span', { class: 'mer-tm-now', text: ` → ${r.now}` }),
      ].filter(Boolean)),
    ]));
  }
  canvas.append(grid);

  // --- Born that day ---
  if (!atPresent) {
    const born = all.flatMap((s) =>
      s.records.filter((r) => createdDay(r) === date)
        .map((r) => ({ label: s.label, title: s.titleOf(r) || '(untitled)' })));
    canvas.append(el('div', { class: 'mer-subsection-label', text: `Added that day (${born.length})` }));
    if (!born.length) {
      canvas.append(el('p', { class: 'mer-muted', text: 'Nothing new entered the record that day.' }));
    } else {
      const area = el('div', { class: 'mer-task-list-area' });
      for (const b of born.slice(0, 25)) {
        area.append(el('div', { class: 'mer-task-row' }, [
          el('span', { class: 'mer-task-title', text: b.title }),
          el('span', { class: 'mer-chip', text: b.label }),
        ]));
      }
      if (born.length > 25) area.append(el('p', { class: 'mer-muted', text: `…and ${born.length - 25} more.` }));
      canvas.append(area);
    }

    // --- Genuinely historical: the dated logs for that exact day ---
    const habitName = new Map(habits.map((h) => [h.id, h.name || '(untitled)']));
    const recipeName = new Map(recipes.map((r) => [r.id, r.title || '(untitled)']));
    const dayHabits = habitLogs.filter((l) => l.date === date);
    const dayHealth = healthLogs.filter((l) => l.date === date);
    const dayCooks = cookLogs.filter((l) => l.date === date);
    const dayReads = readingLogs.filter((l) => l.date === date);
    const lines = [
      ...dayHabits.map((l) => `✅ Checked in: ${habitName.get(l.habitId) || 'a habit'}`),
      ...dayHealth.map((l) => `❤️ ${[l.sleepHours != null ? `slept ${l.sleepHours}h` : null, l.workoutType ? `${l.workoutType}${l.workoutMinutes ? ` (${l.workoutMinutes}m)` : ''}` : null, l.waterOz ? `${l.waterOz}oz water` : null].filter(Boolean).join(' · ') || 'health log'}`),
      ...dayCooks.map((l) => `🍳 Cooked: ${recipeName.get(l.recipeId) || 'a recipe'}`),
      ...dayReads.map((l) => `📖 Reading session${l.pages ? ` (${l.pages} pages)` : ''}`),
    ];
    canvas.append(el('div', { class: 'mer-subsection-label', text: 'Lived that day' }));
    if (!lines.length) {
      canvas.append(el('p', { class: 'mer-muted', text: 'No dated activity logged for that day.' }));
    } else {
      const ul = el('ul', { class: 'mer-starter-list' });
      for (const line of lines) ul.append(el('li', { text: line }));
      canvas.append(ul);
    }

    // --- Honesty box: what this view can and cannot know ---
    canvas.append(el('p', { class: 'mer-tm-honesty', text: 'What this can’t show: records deleted since then are missing from this picture, and titles/fields read as they do today, not as they did then. Full time travel needs the event-sourced core on the moonshot list — when that lands, this view gets truthful about edits too.' }));
  }
}
