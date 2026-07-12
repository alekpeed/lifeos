// The Almanac — long-horizon correlations between a few curated pairs of
// personal stats (sleep vs habits kept, sleep vs tasks completed, workout
// minutes vs sleep), a Forecasts section (bounded stats work -- linear
// regression, weekday buckets, pace extrapolation -- over real logged
// history), and a What If section that forks a forecast: adjust an input,
// see a projected outcome computed live from that same real data. Never an
// AI guess anywhere on this page. No external stats library, just the
// formulas.

import { el, todayStr } from '../dom.js';

const MIN_SAMPLE = 5; // below this, a correlation number is more noise than signal

function pearson(xs, ys) {
  const n = xs.length;
  const meanX = xs.reduce((a, b) => a + b, 0) / n;
  const meanY = ys.reduce((a, b) => a + b, 0) / n;
  let num = 0, denomX = 0, denomY = 0;
  for (let i = 0; i < n; i++) {
    const dx = xs[i] - meanX;
    const dy = ys[i] - meanY;
    num += dx * dy;
    denomX += dx * dx;
    denomY += dy * dy;
  }
  const denom = Math.sqrt(denomX * denomY);
  return denom === 0 ? 0 : num / denom;
}

function describe(r) {
  const abs = Math.abs(r);
  const strength = abs >= 0.5 ? 'strong' : abs >= 0.3 ? 'moderate' : abs >= 0.1 ? 'weak' : 'no real';
  const direction = r > 0 ? 'positive' : r < 0 ? 'negative' : '';
  return abs < 0.1 ? 'No real correlation found.' : `A ${strength} ${direction} correlation.`;
}

// Build a date -> value map from records, using `field` on each, keyed by
// `dateOf(record)`. Multiple records on the same date are summed (fits
// counts like habit check-ins) or the last one wins for `mode: 'last'` (fits
// single daily readings like sleep hours).
function dailyMap(records, dateOf, valueOf, mode = 'sum') {
  const map = new Map();
  for (const r of records) {
    const d = dateOf(r);
    const v = valueOf(r);
    if (!d || v == null || Number.isNaN(v)) continue;
    if (mode === 'sum') map.set(d, (map.get(d) || 0) + v);
    else map.set(d, v);
  }
  return map;
}

function pairedSeries(mapA, mapB) {
  const xs = [], ys = [];
  for (const [date, a] of mapA) {
    if (mapB.has(date)) { xs.push(a); ys.push(mapB.get(date)); }
  }
  return { xs, ys };
}

function correlationCard(label, xs, ys) {
  if (xs.length < MIN_SAMPLE) {
    return el('div', { class: 'mer-almanac-card' }, [
      el('div', { class: 'mer-almanac-label', text: label }),
      el('p', { class: 'mer-muted', text: `Not enough overlapping days yet (${xs.length}/${MIN_SAMPLE} needed).` }),
    ]);
  }
  const r = pearson(xs, ys);
  return el('div', { class: 'mer-almanac-card' }, [
    el('div', { class: 'mer-almanac-label', text: label }),
    el('div', { class: 'mer-almanac-value', text: `r = ${r.toFixed(2)}` }),
    el('p', { class: 'mer-muted', text: `${describe(r)} (${xs.length} days compared)` }),
  ]);
}

// --- Forecasts: real trend modeling over your own logged history. Each one
// requires a minimum sample before showing anything, same "not enough data
// yet" honesty as the correlation cards above. ---

function money(n) {
  return `$${Number(n || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function forecastCard(label, body) {
  return el('div', { class: 'mer-almanac-card' }, [
    el('div', { class: 'mer-almanac-label', text: label }),
    body,
  ]);
}

function notEnoughData(text) {
  return el('p', { class: 'mer-muted', text });
}

// Ordinary least squares over (index, value) pairs -- xs are just 0..n-1
// (one point per month/period), so this reduces to slope + intercept.
function linearRegression(ys) {
  const n = ys.length;
  const meanX = (n - 1) / 2;
  const meanY = ys.reduce((a, b) => a + b, 0) / n;
  let num = 0, denom = 0;
  for (let i = 0; i < n; i++) {
    num += (i - meanX) * (ys[i] - meanY);
    denom += (i - meanX) * (i - meanX);
  }
  const slope = denom === 0 ? 0 : num / denom;
  const intercept = meanY - slope * meanX;
  return { slope, intercept };
}

const MIN_SPEND_MONTHS = 3;

function spendTrendForecast(payments) {
  const byMonth = new Map();
  for (const p of payments) {
    if (!p.datePaid || p.amountPaid == null) continue;
    const month = p.datePaid.slice(0, 7); // YYYY-MM
    byMonth.set(month, (byMonth.get(month) || 0) + Number(p.amountPaid));
  }
  const months = [...byMonth.keys()].sort();
  if (months.length < MIN_SPEND_MONTHS) {
    return notEnoughData(`Not enough months of logged bill payments yet (${months.length}/${MIN_SPEND_MONTHS} needed).`);
  }
  const totals = months.map((m) => byMonth.get(m));
  const { slope, intercept } = linearRegression(totals);
  const avg = totals.reduce((a, b) => a + b, 0) / totals.length;
  const projectedNext = Math.max(0, intercept + slope * totals.length);
  const direction = slope > 0.5 ? 'rising' : slope < -0.5 ? 'falling' : 'holding steady';
  return el('div', {}, [
    el('div', { class: 'mer-almanac-value', text: money(projectedNext) }),
    el('p', { class: 'mer-muted', text: `Bill spend is ${direction}, about ${money(Math.abs(slope))}/month, over the last ${months.length} months (avg ${money(avg)}/month). Projected next month, if the trend holds.` }),
  ]);
}

const MIN_HABIT_LOGS = 14; // ~2 weeks, enough for a weekday signal to mean something

const WEEKDAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

function habitBreakpointForecast(habits, habitLogs) {
  const cards = [];
  for (const habit of habits) {
    const created = (habit.createdAt || '').slice(0, 10);
    if (!created) continue;
    const logDates = new Set(habitLogs.filter((l) => l.habitId === habit.id).map((l) => l.date));
    if (logDates.size < MIN_HABIT_LOGS) continue;

    const byWeekday = Array.from({ length: 7 }, () => ({ total: 0, done: 0 }));
    const cursor = new Date(created + 'T00:00:00');
    const end = new Date(todayStr() + 'T00:00:00');
    while (cursor <= end) {
      const iso = cursor.toISOString().slice(0, 10);
      const wd = byWeekday[cursor.getDay()];
      wd.total++;
      if (logDates.has(iso)) wd.done++;
      cursor.setDate(cursor.getDate() + 1);
    }

    const rates = byWeekday.map((wd, i) => ({ day: i, rate: wd.total ? wd.done / wd.total : null, total: wd.total }));
    const overall = logDates.size / rates.reduce((a, r) => a + r.total, 0);
    const eligible = rates.filter((r) => r.total >= 3 && r.rate != null);
    if (!eligible.length) continue;
    const weakest = eligible.reduce((a, b) => (b.rate < a.rate ? b : a));
    if (weakest.rate > overall - 0.15) continue; // not a real enough dip to call out

    cards.push(forecastCard(habit.name || '(untitled habit)', el('p', { class: 'mer-muted' }, [
      `You're most likely to skip on ${WEEKDAY_NAMES[weakest.day]}s — a ${Math.round(weakest.rate * 100)}% keep rate there vs. ${Math.round(overall * 100)}% overall.`,
    ])));
  }
  return cards;
}

const MIN_READING_LOGS = 2;

function readingPaceForecast(books, readingLogs) {
  const cards = [];
  for (const book of books) {
    if (book.status !== 'reading' || !book.totalPages || book.currentPage == null) continue;
    const logs = readingLogs.filter((l) => l.bookId === book.id).sort((a, b) => a.date.localeCompare(b.date));
    if (logs.length < MIN_READING_LOGS) continue;

    const days = new Set(logs.map((l) => l.date)).size;
    if (days < 2) continue; // a single-day binge isn't a pace
    const totalPagesLogged = logs.reduce((sum, l) => sum + (l.pagesRead || 0), 0);
    const spanDays = Math.max(1, Math.round((new Date(logs[logs.length - 1].date) - new Date(logs[0].date)) / 86400000) + 1);
    const pace = totalPagesLogged / spanDays;
    if (pace <= 0) continue;

    const remaining = book.totalPages - book.currentPage;
    if (remaining <= 0) continue;
    const daysToFinish = Math.ceil(remaining / pace);
    const finishDate = new Date();
    finishDate.setDate(finishDate.getDate() + daysToFinish);

    cards.push(forecastCard(book.title || '(untitled book)', el('p', { class: 'mer-muted' }, [
      `At your recent pace (~${pace.toFixed(1)} pages/day), you'll likely finish around ${finishDate.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}.`,
    ])));
  }
  return cards;
}

// --- What If: forks a forecast instead of just stating it -- adjust an
// input, see a projected outcome, computed live from the same real data.
// Companion to Forecasts above, not a replacement: forecasting predicts the
// default; this lets you fork it. ---

// Ordinary least squares over real (x, y) pairs, not just index/value like
// linearRegression() above -- used to fit an actual sleep-hours-vs-habits
// line rather than assume one.
function regressXY(xs, ys) {
  const n = xs.length;
  const meanX = xs.reduce((a, b) => a + b, 0) / n;
  const meanY = ys.reduce((a, b) => a + b, 0) / n;
  let num = 0, denom = 0;
  for (let i = 0; i < n; i++) {
    num += (xs[i] - meanX) * (ys[i] - meanY);
    denom += (xs[i] - meanX) * (xs[i] - meanX);
  }
  const slope = denom === 0 ? 0 : num / denom;
  return { slope, meanX, meanY };
}

function sleepWhatIfCard(sleepByDate, habitsByDate, totalHabits) {
  const { xs, ys } = pairedSeries(sleepByDate, habitsByDate);
  if (xs.length < MIN_SAMPLE) {
    return forecastCard('Sleep +/- vs. habits kept', notEnoughData(`Not enough overlapping days yet (${xs.length}/${MIN_SAMPLE} needed).`));
  }
  const { slope, meanX, meanY } = regressXY(xs, ys);
  const result = el('p', { class: 'mer-muted' });
  const cap = totalHabits || Math.max(...ys, meanY);
  const update = (deltaMinutes) => {
    const projected = Math.max(0, Math.min(cap, meanY + slope * (deltaMinutes / 60)));
    const sign = deltaMinutes > 0 ? '+' : '';
    result.textContent = `Right now you average ${meanY.toFixed(1)} habits/day on ${meanX.toFixed(1)}h of sleep. At ${sign}${deltaMinutes} min sleep, the trend across your own ${xs.length} logged days projects ~${projected.toFixed(1)} habits/day.`;
  };
  const deltaInput = el('input', {
    type: 'range', min: '-120', max: '120', step: '15', value: '0',
    oninput: (e) => update(Number(e.target.value)),
  });
  update(0);
  return forecastCard('Sleep +/- vs. habits kept', el('div', {}, [deltaInput, result]));
}

function subMonthlyEquivalent(amount, freq) {
  const a = Number(amount) || 0;
  if (freq === 'yearly') return a / 12;
  if (freq === 'weekly') return (a * 52) / 12;
  return a;
}

function subscriptionWhatIfCard(subs) {
  const active = subs.filter((s) => s.stillInUse !== false);
  if (!active.length) {
    return forecastCard('Cancel subscriptions', notEnoughData('No active subscriptions logged in Finance yet.'));
  }
  const selected = new Set();
  const result = el('p', { class: 'mer-muted', text: 'Pick any to see the projected yearly savings.' });
  const update = () => {
    const yearly = [...selected].reduce((sum, id) => {
      const s = active.find((x) => x.id === id);
      return sum + (s ? subMonthlyEquivalent(s.amount, s.billingFreq) * 12 : 0);
    }, 0);
    result.textContent = selected.size
      ? `Cancel these ${selected.size}: save ~${money(yearly)}/year.`
      : 'Pick any to see the projected yearly savings.';
  };
  const list = el('div', { class: 'mer-task-list-area' }, active.map((s) => el('div', { class: 'mer-task-row' }, [
    el('input', {
      type: 'checkbox',
      onchange: (e) => { if (e.target.checked) selected.add(s.id); else selected.delete(s.id); update(); },
    }),
    el('span', { class: 'mer-task-title', text: `${s.name || '(untitled)'} — ${money(subMonthlyEquivalent(s.amount, s.billingFreq))}/mo` }),
  ])));
  return forecastCard('Cancel subscriptions', el('div', {}, [list, result]));
}

async function whatIfSection(ctx, sleepByDate, habitsByDate) {
  const [subs, habits] = await Promise.all([ctx.data.Subscriptions.list(), ctx.data.Habits.list()]);
  return el('div', {}, [
    el('div', { class: 'mer-subsection-label', text: 'What If' }),
    el('p', { class: 'mer-muted', text: 'Fork a forecast: adjust an input and see the projected outcome, computed live from the same real data above -- not a guess.' }),
    el('div', { class: 'mer-almanac-grid' }, [
      sleepWhatIfCard(sleepByDate, habitsByDate, habits.length),
      subscriptionWhatIfCard(subs),
    ]),
  ]);
}

async function forecastsSection(ctx) {
  const [payments, habits, habitLogs, books, readingLogs] = await Promise.all([
    ctx.data.BillPayments.list(),
    ctx.data.Habits.list(),
    ctx.data.HabitLogs.list(),
    ctx.data.Books.list(),
    ctx.data.ReadingLogs.list(),
  ]);

  const cards = [
    forecastCard('Bill spend trend', spendTrendForecast(payments)),
    ...habitBreakpointForecast(habits, habitLogs),
    ...readingPaceForecast(books, readingLogs),
  ];

  return el('div', {}, [
    el('div', { class: 'mer-subsection-label', text: 'Forecasts' }),
    el('p', { class: 'mer-muted', text: 'Real trend modeling over your own logged history — a projection, weekday pattern, or pace estimate, never a guess. Each needs enough history before it shows anything.' }),
    el('div', { class: 'mer-almanac-grid' }, cards),
  ]);
}

export async function renderAlmanac(canvas, ctx) {
  const [healthLogs, habitLogs, tasks, assignments] = await Promise.all([
    ctx.data.HealthLogs.list(),
    ctx.data.HabitLogs.list(),
    ctx.data.Tasks.list(),
    ctx.data.Assignments.list(),
  ]);

  const sleepByDate = dailyMap(healthLogs, (l) => l.date, (l) => l.sleepHours, 'last');
  const workoutByDate = dailyMap(healthLogs, (l) => l.date, (l) => l.workoutMinutes, 'last');
  const habitsByDate = dailyMap(habitLogs, (l) => l.date, () => 1, 'sum');
  const doneByDate = dailyMap(
    [...tasks, ...assignments].filter((t) => t.status === 'done'),
    (t) => (t.updatedAt || '').slice(0, 10), () => 1, 'sum'
  );

  canvas.append(el('h1', { text: 'The Almanac' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Long-horizon correlations across your own logged stats.' }));

  const grid = el('div', { class: 'mer-almanac-grid' });
  const pairs = [
    ['Sleep vs. habits kept that day', sleepByDate, habitsByDate],
    ['Sleep vs. tasks completed that day', sleepByDate, doneByDate],
    ['Workout minutes vs. sleep that day', workoutByDate, sleepByDate],
  ];
  for (const [label, mapA, mapB] of pairs) {
    const { xs, ys } = pairedSeries(mapA, mapB);
    grid.append(correlationCard(label, xs, ys));
  }
  canvas.append(grid);

  canvas.append(await forecastsSection(ctx));
  canvas.append(await whatIfSection(ctx, sleepByDate, habitsByDate));
}
