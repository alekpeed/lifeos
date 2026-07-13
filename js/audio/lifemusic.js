// Life as Music, autonomous background mode. Was a standalone page you
// visited and pressed play on; as of 2026-07-13 it's an ambient background
// loop instead -- turn it on in Settings and it quietly plays your life back
// to you while you use the app, no screen of its own. Each pass re-reads the
// same live-data numbers the old page used (tasks done, habit check-ins,
// books finished, recipes cooked, places visited, contacts) and regenerates
// the chord sequence from them, so it stays "autonomous" and keeps
// reflecting your actual data as it changes. Reuses the shared synth engine
// (./synth.js) and its 'pad' preset, just quieter -- this is atmosphere, not
// a focus activity.

import * as data from '../data/api.js';
import { playSequence, FACTORY_PRESETS } from './synth.js';

const BASE_MIDI = 48; // C3 -- low enough for a pad to sit warmly under a slow progression

const QUALITIES = [
  { intervals: [0, 4, 7] },   // maj
  { intervals: [0, 3, 7] },   // min
  { intervals: [0, 4, 7, 11] }, // maj7
  { intervals: [0, 3, 7, 10] }, // min7
  { intervals: [0, 2, 7] },   // sus2
  { intervals: [0, 5, 7] },   // sus4
];

const AMBIENT_PARAMS = { ...FACTORY_PRESETS.pad, volume: FACTORY_PRESETS.pad.volume * 0.3 };
const CHORD_GAP_S = 2.6;
const LOOP_GAP_MS = 25000; // quiet pause between passes -- ambient, not a wall of sound

function chordFor(count) {
  const root = count % 12;
  const quality = QUALITIES[count % QUALITIES.length];
  return quality.intervals.map((iv) => BASE_MIDI + root + iv);
}

async function computeChords() {
  const [tasks, assignments, habitLogs, books, cookLogs, places, contacts] = await Promise.all([
    data.Tasks.list(),
    data.Assignments.list(),
    data.HabitLogs.list(),
    data.Books.list(),
    data.CookLogs.list(),
    data.Places.list(),
    data.Contacts.list(),
  ]);

  const doneCount = tasks.filter((t) => t.status === 'done').length + assignments.filter((a) => a.status === 'done').length;
  const booksFinished = books.filter((b) => b.status === 'finished').length;
  const visitCount = places.reduce((sum, p) => sum + (p.visitDates || []).length, 0);

  return [
    chordFor(doneCount),
    chordFor(habitLogs.length),
    chordFor(booksFinished),
    chordFor(cookLogs.length),
    chordFor(visitCount),
    chordFor(contacts.length),
  ];
}

let running = false;
let timer = null;

async function playOnce() {
  if (!running) return;
  const chords = await computeChords();
  if (!running) return; // stopped while the fetch above was in flight
  playSequence(chords, AMBIENT_PARAMS, CHORD_GAP_S);
  const cycleMs = chords.length * CHORD_GAP_S * 1000 + LOOP_GAP_MS;
  timer = setTimeout(playOnce, cycleMs);
}

export function startLifeMusic() {
  if (running) return;
  running = true;
  playOnce();
}

export function stopLifeMusic() {
  running = false;
  if (timer) clearTimeout(timer);
  timer = null;
}
