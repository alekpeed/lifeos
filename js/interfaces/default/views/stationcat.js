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
  { max: 1, key: 'purring', line: "Purring — you were just here." },
  { max: 3, key: 'content', line: 'Content. All is well aboard the station.' },
  { max: 7, key: 'dozing', line: "Dozing. Hasn't seen much action lately." },
  { max: 14, key: 'bored', line: 'A little bored — anything due for a check-in?' },
  { max: Infinity, key: 'missing', line: "Hissing at the doorway. It's been gone so long it doesn't recognize you." },
];

function moodFor(days) {
  if (days === null) return { key: 'purring', line: 'Just moved in — say hello!' };
  return MOODS.find((m) => days <= m.max);
}

// --- Sound ---
//
// Synthesized with the Web Audio API rather than shipped as audio files: the
// app works fully offline (see tokens.css's system-font-only rule for the
// same principle applied to type), so no asset to vendor or fail to load.
// One shared AudioContext, created lazily on first click -- browsers refuse
// to start audio without a user gesture, so this can only ever fire from the
// "Listen" button's onclick, never on render.
let audioCtx = null;
function ensureAudioCtx() {
  if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
  if (audioCtx.state === 'suspended') audioCtx.resume();
  return audioCtx;
}

// A low pulsing rumble: a triangle oscillator whose gain is driven by a
// precomputed envelope (attack/release wrapped around a ~24Hz tremolo, the
// rate that reads as "purr" rather than a plain buzzing tone).
function playPurr(ctx) {
  const dur = 1.7;
  const now = ctx.currentTime;
  const osc = ctx.createOscillator();
  osc.type = 'triangle';
  osc.frequency.value = 48;
  const gain = ctx.createGain();
  const steps = 300;
  const curve = new Float32Array(steps);
  for (let i = 0; i < steps; i++) {
    const t = (i / steps) * dur;
    const envelope = Math.min(1, t / 0.15) * Math.min(1, (dur - t) / 0.4);
    const tremolo = 0.55 + 0.45 * Math.sin(2 * Math.PI * 24 * t);
    curve[i] = Math.max(0, envelope * tremolo * 0.2);
  }
  gain.gain.setValueCurveAtTime(curve, now, dur);
  osc.connect(gain).connect(ctx.destination);
  osc.start(now);
  osc.stop(now + dur + 0.05);
}

// A short, gentle rising-then-falling "mrow" -- the neutral/sleepy sound,
// nowhere near as committed as a purr or a hiss.
function playMew(ctx) {
  const now = ctx.currentTime;
  const dur = 0.45;
  const osc = ctx.createOscillator();
  osc.type = 'sine';
  osc.frequency.setValueAtTime(340, now);
  osc.frequency.exponentialRampToValueAtTime(520, now + 0.15);
  osc.frequency.exponentialRampToValueAtTime(300, now + dur);
  const gain = ctx.createGain();
  gain.gain.setValueAtTime(0.0001, now);
  gain.gain.exponentialRampToValueAtTime(0.18, now + 0.08);
  gain.gain.exponentialRampToValueAtTime(0.0001, now + dur);
  osc.connect(gain).connect(ctx.destination);
  osc.start(now);
  osc.stop(now + dur + 0.02);
}

// A sharp hiss: filtered white noise, fast attack, quick decay. Plays once
// the cat's gone long enough it "doesn't recognize you" (the 15+ day tier).
function playHiss(ctx) {
  const now = ctx.currentTime;
  const dur = 0.9;
  const bufferSize = Math.floor(ctx.sampleRate * dur);
  const buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < bufferSize; i++) data[i] = Math.random() * 2 - 1;
  const noise = ctx.createBufferSource();
  noise.buffer = buffer;
  const filter = ctx.createBiquadFilter();
  filter.type = 'highpass';
  filter.frequency.value = 2500;
  filter.Q.value = 0.7;
  const gain = ctx.createGain();
  gain.gain.setValueAtTime(0, now);
  gain.gain.linearRampToValueAtTime(0.3, now + 0.04);
  gain.gain.linearRampToValueAtTime(0.14, now + dur * 0.55);
  gain.gain.linearRampToValueAtTime(0, now + dur);
  noise.connect(filter).connect(gain).connect(ctx.destination);
  noise.start(now);
  noise.stop(now + dur);
}

const MOOD_SOUND = {
  purring: playPurr,
  content: playPurr,
  dozing: playMew,
  bored: playMew,
  missing: playHiss,
};

function soundButton(moodKey) {
  const btn = el('button', {
    type: 'button', class: 'mer-reader-btn mer-cat-sound-btn', text: '🔊 Listen',
    onclick: () => {
      const play = MOOD_SOUND[moodKey];
      if (!play) return;
      btn.disabled = true;
      try {
        play(ensureAudioCtx());
      } catch {
        // Web Audio unavailable (e.g. blocked by browser policy) -- the
        // face and text already carry the mood, so fail quiet.
      }
      setTimeout(() => { btn.disabled = false; }, 900);
    },
  });
  return btn;
}

// A small CSS-only cat face (no image/emoji) whose mood is expressed purely
// through shape modifier classes -- see the "Station Cat" rules in
// default/style.css for what each mer-cat--<key> class actually draws.
function catFace(moodKey) {
  return el('div', { class: `mer-cat mer-cat--${moodKey}` }, [
    el('div', { class: 'mer-cat-ear mer-cat-ear--l' }),
    el('div', { class: 'mer-cat-ear mer-cat-ear--r' }),
    el('div', { class: 'mer-cat-head' }, [
      el('div', { class: 'mer-cat-eye mer-cat-eye--l' }),
      el('div', { class: 'mer-cat-eye mer-cat-eye--r' }),
      el('div', { class: 'mer-cat-nose' }),
      el('div', { class: 'mer-cat-mouth' }),
      el('div', { class: 'mer-cat-tear' }),
      el('div', { class: 'mer-cat-whisker mer-cat-whisker--l1' }),
      el('div', { class: 'mer-cat-whisker mer-cat-whisker--l2' }),
      el('div', { class: 'mer-cat-whisker mer-cat-whisker--r1' }),
      el('div', { class: 'mer-cat-whisker mer-cat-whisker--r2' }),
    ]),
  ]);
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
    catFace(mood.key),
    el('p', { class: 'mer-stationcat-line', text: mood.line }),
    el('p', { class: 'mer-muted', text: days === null ? 'No activity logged yet.' : `${days} day${days === 1 ? '' : 's'} since your last logged activity.` }),
    soundButton(mood.key),
  ]));
}
