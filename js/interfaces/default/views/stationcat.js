// The Station Cat — a small rule-based companion whose mood reflects how
// neglected the app has been lately (same "days since last touch" idea as
// Entropy, computed independently here to keep this view self-contained).
// Purely cosmetic; no new storage.

import { el, todayStr } from '../dom.js';

function daysSince(dateStr) {
  if (!dateStr) return null;
  const ms = new Date(todayStr() + 'T00:00:00') - new Date(dateStr.slice(0, 10) + 'T00:00:00');
  return Math.max(0, Math.round(ms / 86400000));
}

function latestOf(records, field = 'updatedAt') {
  if (!records.length) return null;
  return records.reduce((latest, r) => (r[field] > latest ? r[field] : latest), records[0][field] || '');
}

const MOODS = [
  { max: 1, face: '(=^･ω･^=)', line: "Purring — you were just here." },
  { max: 3, face: '(=^-ω-^=)', line: 'Content. All is well aboard the station.' },
  { max: 7, face: '(=^･ｪ･^=)', line: "Dozing. Hasn't seen much action lately." },
  { max: 14, face: '(=￣ω￣=)', line: 'A little bored — anything due for a check-in?' },
  { max: Infinity, face: '(=;ェ;=)', line: "It's been a while. The station misses you." },
];

function moodFor(days) {
  if (days === null) return { face: '(=^･ω･^=)', line: 'Just moved in — say hello!' };
  return MOODS.find((m) => days <= m.max);
}

export async function renderStationCat(canvas, ctx) {
  const [tasks, habitLogs, healthLogs] = await Promise.all([
    ctx.data.Tasks.list(),
    ctx.data.HabitLogs.list(),
    ctx.data.HealthLogs.list(),
  ]);

  const signals = [latestOf(tasks), latestOf(habitLogs, 'date'), latestOf(healthLogs, 'date')].filter(Boolean);
  const mostRecent = signals.length ? signals.reduce((a, b) => (b > a ? b : a)) : null;
  const days = daysSince(mostRecent);
  const mood = moodFor(days);

  canvas.append(el('h1', { text: 'The Station Cat' }));
  canvas.append(el('div', { class: 'mer-stationcat' }, [
    el('div', { class: 'mer-stationcat-face', text: mood.face }),
    el('p', { class: 'mer-stationcat-line', text: mood.line }),
    el('p', { class: 'mer-muted', text: days === null ? 'No activity logged yet.' : `${days} day${days === 1 ? '' : 's'} since your last logged activity.` }),
  ]));
}
