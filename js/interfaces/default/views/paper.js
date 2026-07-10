// The Daily Paper — a one-page brief typeset like a newspaper, built entirely
// from data the app already has: the due-soon agenda, on-this-day history,
// today's habits, a suggestion, and a small almanac. Nothing new is stored;
// this is a *view* over existing modules.
//
// The on-screen version is interactive (tick habits off, re-roll the pick).
// The "Print" button lays the same content into a `.mer-print-sheet` appended
// to <body> and hands it to the browser's print-to-PDF — reusing the exact
// @media print plumbing the Chords practice sheet already established, so the
// app chrome disappears and only the paper prints.

import { el, fmtDate, todayStr } from '../dom.js';

// undefined = not fetched yet; null = fetched, nothing to suggest. Kept at
// module scope so re-renders (ticking a habit) don't re-roll the pick.
let state = { surprise: undefined };

const MODULE_LABEL = { tasks: 'Task', bills: 'Bill', assignments: 'Assignment', documents: 'Document' };

function longDate() {
  return new Date().toLocaleDateString(undefined, {
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
  });
}

// A whimsical "issue number" — the day of the year — for masthead flavor.
function dayOfYear() {
  const now = new Date();
  const start = new Date(now.getFullYear(), 0, 0);
  return Math.floor((now - start) / 86400000);
}

function masthead() {
  return el('header', { class: 'mer-paper-masthead' }, [
    el('div', { class: 'mer-paper-title', text: 'The Daily Ledger' }),
    el('div', { class: 'mer-paper-dateline' }, [
      el('span', { text: longDate() }),
      el('span', { text: `No. ${dayOfYear()}` }),
    ]),
  ]);
}

function sectionCard(title, body) {
  return el('section', { class: 'mer-paper-section' }, [
    el('h2', { class: 'mer-paper-head', text: title }),
    body,
  ]);
}

// Reads the checklist fresh from Settings and drops it if it belongs to a
// previous local day -- this *is* the "resets at midnight" behavior, since
// todayStr() is always the device's own local date, never UTC.
async function currentChecklist(ctx) {
  const [date, checked] = await Promise.all([
    ctx.data.Settings.get('paperChecklistDate'),
    ctx.data.Settings.get('paperChecklistChecked'),
  ]);
  return new Set(date === todayStr() ? checked : []);
}

async function toggleChecklistItem(ctx, key, isChecked, rerender) {
  const set = await currentChecklist(ctx);
  if (isChecked) set.add(key); else set.delete(key);
  await Promise.all([
    ctx.data.Settings.set('paperChecklistDate', todayStr()),
    ctx.data.Settings.set('paperChecklistChecked', [...set]),
  ]);
  rerender();
}

function agendaSection(feed, checkedSet, ctx, rerender, forPrint) {
  if (!feed.length) {
    return sectionCard('On the Docket', el('p', {
      class: 'mer-paper-none',
      text: 'The docket is clear for the next seven days — a rare and beautiful thing.',
    }));
  }
  const list = el('ul', { class: 'mer-paper-list' });
  for (const item of feed) {
    const key = `${item.module}:${item.id}`;
    const isChecked = checkedSet.has(key);
    let box;
    if (forPrint) {
      box = el('span', { class: isChecked ? 'mer-paper-tick is-done' : 'mer-paper-tick' });
    } else {
      box = el('input', {
        type: 'checkbox', checked: isChecked,
        onclick: (e) => toggleChecklistItem(ctx, key, e.target.checked, rerender),
      });
    }
    list.append(el('li', { class: [item.overdue ? 'is-overdue' : '', isChecked ? 'is-checked' : ''].filter(Boolean).join(' ') }, [
      box,
      item.overdue ? el('span', { class: 'mer-paper-flag', text: 'OVERDUE' }) : null,
      el('span', { class: 'mer-paper-kicker', text: MODULE_LABEL[item.module] || item.module }),
      el('span', { class: 'mer-paper-item-title', text: item.title || '(untitled)' }),
      el('span', { class: 'mer-paper-item-date', text: fmtDate(item.dueDate) }),
    ].filter(Boolean)));
  }
  return sectionCard('On the Docket', list);
}

function onThisDaySection(items) {
  if (!items.length) return null;
  const list = el('ul', { class: 'mer-paper-list' });
  for (const it of items) {
    list.append(el('li', {}, [
      el('span', { class: 'mer-paper-kicker', text: it.kind }),
      el('span', { class: 'mer-paper-item-title', text: it.title || '(untitled)' }),
      el('span', { class: 'mer-paper-item-date', text: it.year }),
    ]));
  }
  return sectionCard('On This Day', list);
}

function habitsSection(habits, logsByHabit, ctx, rerender, forPrint) {
  if (!habits.length) return null;
  const list = el('ul', { class: 'mer-paper-habits' });
  for (const h of habits) {
    const logs = logsByHabit.get(h.id) || [];
    const doneToday = logs.some((l) => l.date === todayStr());
    let box;
    if (forPrint) {
      // A static square that prints; filled if already done today.
      box = el('span', { class: doneToday ? 'mer-paper-tick is-done' : 'mer-paper-tick' });
    } else {
      box = el('input', {
        type: 'checkbox', checked: doneToday,
        onclick: async (e) => {
          if (e.target.checked) {
            await ctx.data.HabitLogs.create({ habitId: h.id, date: todayStr() });
          } else {
            const t = logs.find((l) => l.date === todayStr());
            if (t) await ctx.data.HabitLogs.remove(t.id);
          }
          rerender();
        },
      });
    }
    list.append(el('li', {}, [box, el('span', { class: 'mer-paper-item-title', text: h.name || '(untitled)' })]));
  }
  return sectionCard('Habit Roll Call', list);
}

function pickSection(ctx, rerender, forPrint) {
  const s = state.surprise;
  let body;
  if (s === undefined) {
    body = el('p', { class: 'mer-paper-none', text: '…' });
  } else if (!s) {
    body = el('p', { class: 'mer-paper-none', text: 'Nothing in the queue — add a want-to-go place or an unread book to get a pick.' });
  } else {
    body = el('p', { class: 'mer-paper-pick' }, [
      el('span', { class: 'mer-paper-kicker', text: s.kind }),
      el('span', { class: 'mer-paper-item-title', text: s.title }),
    ]);
  }
  const wrap = el('div', {}, [body]);
  if (!forPrint) {
    wrap.append(el('button', {
      type: 'button', class: 'mer-reader-btn', text: 'Another →',
      onclick: async () => { state.surprise = await ctx.data.getSurpriseMe(); rerender(); },
    }));
  }
  return sectionCard("Editor's Pick", wrap);
}

function weatherSection(weather, ctx) {
  if (!weather) return null;
  const { icon, label } = ctx.data.describeWeatherCode(weather.code);
  const body = el('p', { class: 'mer-paper-pick' }, [
    el('span', { class: 'mer-paper-kicker', text: `${icon} ${label}` }),
    el('span', { class: 'mer-paper-item-title', text: `${Math.round(weather.tempF)}°F` }),
    weather.highF != null ? el('span', { class: 'mer-paper-item-date', text: `H:${Math.round(weather.highF)}° L:${Math.round(weather.lowF)}°` }) : null,
  ].filter(Boolean));
  return sectionCard('Weather', body);
}

function almanacSection({ feed, habits, logsByHabit, sleep }) {
  const overdue = feed.filter((f) => f.overdue).length;
  const doneToday = habits.filter((h) => (logsByHabit.get(h.id) || []).some((l) => l.date === todayStr())).length;
  const rows = [
    ['Items due (7 days)', String(feed.length)],
    ['Overdue', String(overdue)],
    habits.length ? ['Habits kept today', `${doneToday} / ${habits.length}`] : null,
    sleep != null ? ['Last night', `${sleep} h sleep`] : null,
  ].filter(Boolean);
  const dl = el('dl', { class: 'mer-paper-almanac' });
  for (const [k, v] of rows) { dl.append(el('dt', { text: k }), el('dd', { text: v })); }
  return sectionCard('The Almanac', dl);
}

function buildPaper(data, forPrint, ctx, rerender) {
  const { feed, onThisDay, habits, logsByHabit, sleep, weather, checkedSet } = data;
  const sections = [
    weatherSection(weather, ctx),
    agendaSection(feed, checkedSet, ctx, rerender, forPrint),
    onThisDaySection(onThisDay),
    habitsSection(habits, logsByHabit, ctx, rerender, forPrint),
    pickSection(ctx, rerender, forPrint),
    almanacSection({ feed, habits, logsByHabit, sleep }),
  ].filter(Boolean);
  const body = el('div', { class: 'mer-paper-body' }, sections);
  const cls = forPrint ? 'mer-print-sheet mer-paper mer-paper--print' : 'mer-paper';
  return el('div', { class: cls }, [masthead(), body]);
}

// Plain-text version of the paper's core content, for the "Send to Telegram"
// button. Deliberately a summary, not a full transcription of buildPaper().
function buildDigestText(data) {
  const { feed, habits, logsByHabit, weather } = data;
  const lines = [`Life OS — ${longDate()}`];
  if (weather) lines.push(`${weather.tempF != null ? Math.round(weather.tempF) + '°F' : ''}`.trim());
  if (feed.length) {
    lines.push('', 'On the docket:');
    for (const item of feed.slice(0, 10)) {
      lines.push(`${item.overdue ? '⚠️ ' : ''}${item.title || '(untitled)'} — ${fmtDate(item.dueDate)}`);
    }
  } else {
    lines.push('', 'Nothing due in the next 7 days.');
  }
  if (habits.length) {
    const doneToday = habits.filter((h) => (logsByHabit.get(h.id) || []).some((l) => l.date === todayStr())).length;
    lines.push('', `Habits: ${doneToday}/${habits.length} done today`);
  }
  return lines.join('\n');
}

function printPaper(data, ctx) {
  const sheet = buildPaper(data, true, ctx, () => {});
  document.body.append(sheet);
  const cleanup = () => sheet.remove();
  window.addEventListener('afterprint', cleanup, { once: true });
  window.print();
  setTimeout(cleanup, 2000); // fallback if afterprint never fires
}

export async function renderPaper(canvas, ctx, rerender) {
  const [billDays, docDays] = await Promise.all([
    ctx.data.Settings.get('billDueSoonDays'),
    ctx.data.Settings.get('documentExpiryDays'),
  ]);
  const [feed, onThisDay, habits, allLogs, healthLogs, weather, checkedSet] = await Promise.all([
    ctx.data.getDueSoonFeed(7, billDays, docDays),
    ctx.data.getOnThisDay(),
    ctx.data.Habits.list(),
    ctx.data.HabitLogs.list(),
    ctx.data.HealthLogs.list(),
    ctx.data.getWeather(),
    currentChecklist(ctx),
  ]);
  if (state.surprise === undefined) state.surprise = await ctx.data.getSurpriseMe();

  const logsByHabit = new Map();
  for (const log of allLogs) {
    if (!logsByHabit.has(log.habitId)) logsByHabit.set(log.habitId, []);
    logsByHabit.get(log.habitId).push(log);
  }
  const latest = healthLogs
    .filter((l) => l.sleepHours != null && l.sleepHours !== '')
    .sort((a, b) => (a.date < b.date ? 1 : -1))[0];
  const sleep = latest ? latest.sleepHours : null;

  const data = { feed, onThisDay, habits, logsByHabit, sleep, weather, checkedSet };

  canvas.append(el('h1', { text: 'Daily Paper' }));
  const telegramStatus = el('span', { class: 'mer-muted' });
  canvas.append(el('div', { class: 'mer-toolbar' }, [
    el('button', {
      type: 'button', text: '🖨️ Print / Save as PDF',
      title: 'Print or save as PDF via your browser',
      onclick: () => printPaper(data, ctx),
    }),
    el('button', {
      type: 'button', text: '📤 Send to Telegram',
      onclick: async () => {
        telegramStatus.textContent = 'Sending…';
        telegramStatus.classList.remove('mer-sync-error');
        try {
          await ctx.data.sendDigestToTelegram(buildDigestText(data));
          telegramStatus.textContent = 'Sent!';
        } catch (err) {
          telegramStatus.textContent = err.message || String(err);
          telegramStatus.classList.add('mer-sync-error');
        }
      },
    }),
    telegramStatus,
  ]));
  canvas.append(buildPaper(data, false, ctx, rerender));
}
