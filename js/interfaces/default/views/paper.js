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
// editorialGenerating/editorialError track the AI editorial's in-flight
// state across re-renders (see ensureEditorial below).
let state = { surprise: undefined, editorialGenerating: false, editorialError: null };

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

function editorialSection(editorial, forPrint, onRegenerate) {
  if (!editorial) return null;
  // Printed issues contain finished editorial prose only—not setup guidance,
  // transient loading copy, or provider errors.
  if (forPrint && (!editorial.text || editorial.loading || editorial.error || editorial.unavailable)) return null;
  let body;
  if (editorial.unavailable) {
    body = el('p', {
      class: 'mer-paper-none',
      text: 'Add your Anthropic API key in Settings to have Claude write a private, data-grounded editorial for today.',
    });
  } else if (editorial.loading) {
    body = el('p', { class: 'mer-paper-none', text: 'Writing…' });
  } else if (editorial.error) {
    body = el('p', { class: 'mer-paper-none mer-sync-error', text: editorial.error });
  } else if (editorial.text) {
    body = el('p', { class: 'mer-paper-pick' }, [document.createTextNode(editorial.text)]);
  } else {
    return null;
  }
  const wrap = el('div', {}, [body]);
  if (!forPrint && !editorial.loading && !editorial.unavailable) {
    wrap.append(el('button', { type: 'button', class: 'mer-reader-btn', text: editorial.error ? 'Retry' : '🔄 Regenerate', onclick: onRegenerate }));
  }
  return sectionCard('The Editorial', wrap);
}

// Kicks off one Claude call (fire-and-forget from the caller's perspective)
// and caches the result under today's local date -- same reset-by-date
// pattern as the checklist. Guarded by state.editorialGenerating so a
// re-render mid-generation (e.g. ticking a habit while it's writing)
// doesn't fire a second concurrent call.
async function ensureEditorial(ctx, data, rerender) {
  state.editorialGenerating = true;
  state.editorialError = null;
  try {
    const text = await ctx.data.generateDailyEditorial(buildEditorialContext(data));
    if (!text) throw new Error('Claude returned an empty editorial. Please try again.');
    await Promise.all([
      ctx.data.Settings.set('paperEditorialDate', todayStr()),
      ctx.data.Settings.set('paperEditorialText', text),
      ctx.data.Settings.set('paperEditorialOwner', data.editorialOwner),
    ]);
  } catch (err) {
    state.editorialError = err.message || String(err);
  }
  state.editorialGenerating = false;
  rerender();
}

async function regenerateEditorial(ctx, rerender) {
  await Promise.all([
    ctx.data.Settings.set('paperEditorialDate', ''),
    ctx.data.Settings.set('paperEditorialText', ''),
  ]);
  state.editorialError = null;
  rerender();
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
  const { feed, onThisDay, habits, logsByHabit, sleep, weather, checkedSet, editorial } = data;
  const sections = [
    editorialSection(editorial, forPrint, () => regenerateEditorial(ctx, rerender)),
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
  const { feed, habits, logsByHabit, weather, editorial } = data;
  const lines = [`Life OS — ${longDate()}`];
  if (editorial?.text) lines.push('', editorial.text);
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

// A factual, bounded source packet for the AI. This is intentionally separate
// from the Telegram digest: it includes enough context to write something
// useful while making missing data explicit and never feeding an older
// editorial back into the model.
export function buildEditorialContext(data) {
  const { feed, onThisDay, habits, logsByHabit, weather, weatherDescription, sleep } = data;
  const lines = [`Date: ${longDate()}`];

  if (weather) {
    const temperatures = [
      weather.tempF != null ? `current ${Math.round(weather.tempF)}°F` : null,
      weather.highF != null ? `high ${Math.round(weather.highF)}°F` : null,
      weather.lowF != null ? `low ${Math.round(weather.lowF)}°F` : null,
    ].filter(Boolean).join(', ');
    lines.push(`Weather: ${weatherDescription || 'condition unavailable'}${temperatures ? `; ${temperatures}` : ''}`);
  } else {
    lines.push('Weather: unavailable');
  }

  if (feed.length) {
    lines.push('Due and overdue items:');
    for (const item of feed.slice(0, 15)) {
      const status = item.overdue ? 'overdue' : 'due soon';
      lines.push(`- [${MODULE_LABEL[item.module] || item.module}; ${status}; ${fmtDate(item.dueDate)}] ${item.title || '(untitled)'}`);
    }
  } else {
    lines.push('Due and overdue items: none in the next seven days');
  }

  if (habits.length) {
    const done = habits.filter((habit) =>
      (logsByHabit.get(habit.id) || []).some((log) => log.date === todayStr()));
    lines.push(`Habits completed today (${done.length}/${habits.length}): ${done.map((habit) => habit.name || '(untitled)').join(', ') || 'none yet'}`);
    const remaining = habits.filter((habit) => !done.includes(habit));
    lines.push(`Habits remaining today: ${remaining.map((habit) => habit.name || '(untitled)').join(', ') || 'none'}`);
  } else {
    lines.push('Habits: none configured');
  }

  lines.push(`Most recent logged sleep: ${sleep != null ? `${sleep} hours` : 'unavailable'}`);
  if (onThisDay.length) {
    lines.push(`On this day: ${onThisDay.slice(0, 5).map((item) => `${item.title || '(untitled)'} (${item.year})`).join('; ')}`);
  } else {
    lines.push('On this day: no entries');
  }
  if (state.surprise) lines.push(`Editor's pick: ${state.surprise.kind} — ${state.surprise.title}`);

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

  const accountUser = await ctx.data.Account.getCurrentUser().catch(() => null);
  const editorialOwner = accountUser?.id || 'local-anonymous';
  const weatherDescription = weather ? ctx.data.describeWeatherCode(weather.code).label : null;
  const data = { feed, onThisDay, habits, logsByHabit, sleep, weather, weatherDescription, checkedSet, editorialOwner };

  // AI editorial: visible as a setup state without a key, and generated with
  // the user's own key when configured. Cached by local date and account; a
  // cache miss kicks off one Claude call and shows
  // "Writing…" until it resolves, rather than blocking the rest of the page.
  const apiKey = await ctx.data.Settings.get('anthropicApiKey');
  if (apiKey) {
    const [edDate, edText, edOwner] = await Promise.all([
      ctx.data.Settings.get('paperEditorialDate'),
      ctx.data.Settings.get('paperEditorialText'),
      ctx.data.Settings.get('paperEditorialOwner'),
    ]);
    if (edDate === todayStr() && edText && edOwner === editorialOwner) {
      data.editorial = { text: edText, loading: false, error: null };
    } else if (state.editorialGenerating) {
      data.editorial = { text: '', loading: true, error: null };
    } else if (state.editorialError) {
      data.editorial = { text: '', loading: false, error: state.editorialError };
    } else {
      data.editorial = { text: '', loading: true, error: null };
      ensureEditorial(ctx, data, rerender);
    }
  } else {
    data.editorial = { unavailable: true, text: '', loading: false, error: null };
  }

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
