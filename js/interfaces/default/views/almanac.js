// The Almanac — long-horizon correlations between a few curated pairs of
// personal stats (sleep vs habits kept, sleep vs tasks completed, workout
// minutes vs sleep). Plain Pearson correlation over whatever days have both
// values logged; no external stats library, just the formula.

import { el } from '../dom.js';

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
}
