// Life as Music — a short ambient pad progression generated from your own
// data. Each chord represents one area of your life (tasks, habits, books,
// recipes, places, contacts); its root note and quality are derived
// deterministically from that area's numbers, so the "soundtrack" actually
// reflects your data rather than being random. Reuses the existing
// synth engine (js/audio/synth.js) and its 'pad' preset -- no new audio code,
// just a data-to-chord mapping and playSequence().

import { el } from '../dom.js';
import { playSequence, FACTORY_PRESETS } from '../../../audio/synth.js';

const NOTE_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
const BASE_MIDI = 48; // C3 -- low enough for a pad to sit warmly under a slow progression

// A small, self-contained quality palette (intervals in semitones from the
// root). Deliberately not importing js/theory/*'s full harmonic-analysis
// machinery -- this only needs "build a triad/seventh from a root," not key
// context or roman numerals.
const QUALITIES = [
  { name: 'maj', intervals: [0, 4, 7] },
  { name: 'min', intervals: [0, 3, 7] },
  { name: 'maj7', intervals: [0, 4, 7, 11] },
  { name: 'min7', intervals: [0, 3, 7, 10] },
  { name: 'sus2', intervals: [0, 2, 7] },
  { name: 'sus4', intervals: [0, 5, 7] },
];

function chordFor(label, count) {
  const root = count % 12;
  const quality = QUALITIES[count % QUALITIES.length];
  const midiNotes = quality.intervals.map((iv) => BASE_MIDI + root + iv);
  return { label, count, symbol: `${NOTE_NAMES[root]}${quality.name}`, midiNotes };
}

function chordCard(chord) {
  return el('div', { class: 'mer-lifemusic-chord' }, [
    el('div', { class: 'mer-lifemusic-symbol', text: chord.symbol },),
    el('div', { class: 'mer-lifemusic-label', text: chord.label }),
    el('div', { class: 'mer-muted', text: `${chord.count}` }),
  ]);
}

export async function renderLifeAsMusic(canvas, ctx) {
  const [tasks, assignments, habitLogs, books, cookLogs, places, contacts] = await Promise.all([
    ctx.data.Tasks.list(),
    ctx.data.Assignments.list(),
    ctx.data.HabitLogs.list(),
    ctx.data.Books.list(),
    ctx.data.CookLogs.list(),
    ctx.data.Places.list(),
    ctx.data.Contacts.list(),
  ]);

  const doneCount = tasks.filter((t) => t.status === 'done').length + assignments.filter((a) => a.status === 'done').length;
  const booksFinished = books.filter((b) => b.status === 'finished').length;
  const visitCount = places.reduce((sum, p) => sum + (p.visitDates || []).length, 0);

  const chords = [
    chordFor('Tasks done', doneCount),
    chordFor('Habit check-ins', habitLogs.length),
    chordFor('Books finished', booksFinished),
    chordFor('Recipes cooked', cookLogs.length),
    chordFor('Places visited', visitCount),
    chordFor('Contacts', contacts.length),
  ];

  canvas.append(el('h1', { text: 'Life as Music' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'A short ambient progression, generated from your own numbers — one chord per area of your life.' }));

  const grid = el('div', { class: 'mer-lifemusic-grid' });
  for (const chord of chords) grid.append(chordCard(chord));
  canvas.append(grid);

  const playBtn = el('button', {
    type: 'button', text: '▶ Play',
    onclick: () => {
      playBtn.disabled = true;
      playSequence(chords.map((c) => c.midiNotes), FACTORY_PRESETS.pad, 2.2);
      setTimeout(() => { playBtn.disabled = false; }, chords.length * 2200);
    },
  });
  canvas.append(el('div', { class: 'mer-toolbar' }, [playBtn]));
}
