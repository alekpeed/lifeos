// Voicing families. A voicing is { id, name, group, description, notes },
// where notes = [{ midi, name, label }] sorted low→high. All builders place
// the chord root in the octave C3–B3 and then nudge the whole stack back
// into a playable register, so diagrams and playback share one placement.

import { toneOf } from './chords.js';

function rootMidi(chord) {
  return 48 + chord.root.pc; // C3..B3
}

function place(chord, entries) {
  // entries: [{ tone, offset }] — offset = semitones above the root position.
  let notes = entries.map(({ tone, offset }) => ({
    midi: rootMidi(chord) + offset,
    name: tone.name,
    label: tone.label,
  })).sort((a, b) => a.midi - b.midi);
  while (notes[0].midi < 36) notes = notes.map((n) => ({ ...n, midi: n.midi + 12 }));
  while (notes[notes.length - 1].midi > 86) notes = notes.map((n) => ({ ...n, midi: n.midi - 12 }));
  return notes;
}

// The 4-note "core" of a chord: its first four defined intervals (1-3-5-7,
// or 1-3-5-6 for sixth chords; altered 5ths ride along). Drop voicings are
// defined on this core; extensions live in the rootless/UST families.
function coreFour(chord) {
  const core = chord.tones.slice(0, 4);
  return core.length === 4 ? core : null;
}

function closeStack(core) {
  return [...core].sort((a, b) => a.semitones - b.semitones).map((tone) => ({ tone, offset: tone.semitones }));
}

function voicing(chord, id, name, group, description, entries) {
  return { id, name, group, description, notes: place(chord, entries) };
}

// --- Drop family: from the close stack, drop the Nth-from-top an octave ---

function dropVoicing(chord, id, name, dropIdxFromTop) {
  const core = coreFour(chord);
  if (!core) return null;
  const close = closeStack(core);
  const entries = close.map((e, i) => {
    const fromTop = close.length - 1 - i;
    return dropIdxFromTop.includes(fromTop) ? { ...e, offset: e.offset - 12 } : e;
  });
  const desc = {
    drop2: 'Close position with the 2nd note from the top dropped an octave — the workhorse of jazz guitar comping and big-band voicing.',
    drop3: '3rd from the top dropped an octave — wide, open sound; on guitar this is the classic 6-x-4-3-2 shape family.',
    drop24: '2nd and 4th from the top dropped — the widest spread; two-hand friendly on piano.',
  }[id];
  return voicing(chord, id, name, 'Drop voicings', desc, entries);
}

// --- Shell / Root-Shell-Pretty ---

function shellTones(chord) {
  const third = toneOf(chord, 3) || toneOf(chord, 4) || toneOf(chord, 2);
  const seventh = toneOf(chord, 7) || toneOf(chord, 6);
  return { third, seventh };
}

function shellVoicings(chord) {
  const { third, seventh } = shellTones(chord);
  if (!third || !seventh) return [];
  return [
    voicing(chord, 'shellA', `Shell (1–${seventh.label}–${third.label})`, 'Shells',
      'Root plus the two tones that define the chord quality — the guide tones. Seventh below, third on top.',
      [{ tone: toneOf(chord, 1), offset: 0 }, { tone: seventh, offset: seventh.semitones % 12 }, { tone: third, offset: (third.semitones % 12) + 12 }]),
    voicing(chord, 'shellB', `Shell (1–${third.label}–${seventh.label})`, 'Shells',
      'Same guide tones, stacked the other way: third below, seventh on top.',
      [{ tone: toneOf(chord, 1), offset: 0 }, { tone: third, offset: third.semitones % 12 }, { tone: seventh, offset: seventh.semitones % 12 }]),
  ];
}

// "Pretty" color tones per family, honoring alterations the quality defines.
function prettyTones(chord) {
  const cat = chord.quality.cat;
  if (cat === 'dom') return [toneOf(chord, 9, 14), toneOf(chord, 13, 21)];
  if (cat === 'maj7' || cat === '6') return [toneOf(chord, 9, 14), toneOf(chord, 6, 9)];
  if (cat === 'm7') return [toneOf(chord, 9, 14), toneOf(chord, 11, 17)];
  if (cat === 'm6' || cat === 'mMaj7') return [toneOf(chord, 9, 14)];
  return [];
}

// Root → Shell → Pretty, as three cumulative stages (Adam Maness / Open
// Studio's progressive-voicing teaching device).
export function rootShellPretty(chord) {
  const { third, seventh } = shellTones(chord);
  if (!third || !seventh) return null;
  const root = { tone: toneOf(chord, 1), offset: -12 };
  const shell = [
    { tone: third, offset: third.semitones % 12 },
    { tone: seventh, offset: seventh.semitones % 12 },
  ];
  const pretty = prettyTones(chord).filter(Boolean).map((tone) => ({
    tone, offset: (tone.semitones % 12) + 12,
  }));
  return {
    stages: [
      { name: 'Root', notes: place(chord, [root]) },
      { name: 'Root + Shell', notes: place(chord, [root, ...shell]) },
      { name: 'Root + Shell + Pretty', notes: place(chord, [root, ...shell, ...pretty]) },
    ],
  };
}

// --- Rootless (Bill Evans A / B forms) ---

function rootlessDegrees(chord, form) {
  const cat = chord.quality.cat;
  if (cat === 'dom') {
    // 3–13–♭7–9 (A) / ♭7–9–3–13 (B), with alterations riding along.
    const t3 = toneOf(chord, 3), t13 = toneOf(chord, 13, 21), t7 = toneOf(chord, 7), t9 = toneOf(chord, 9, 14);
    return form === 'A' ? [t3, t13, t7, t9] : [t7, t9, t3, t13];
  }
  if (cat === 'maj7' || cat === '6') {
    const t3 = toneOf(chord, 3), t5 = toneOf(chord, 5, 7), t7 = toneOf(chord, 7, 11), t9 = toneOf(chord, 9, 14);
    return form === 'A' ? [t3, t5, t7, t9] : [t7, t9, t3, t5];
  }
  if (cat === 'm7') {
    const t3 = toneOf(chord, 3), t5 = toneOf(chord, 5, 7), t7 = toneOf(chord, 7), t9 = toneOf(chord, 9, 14);
    return form === 'A' ? [t3, t5, t7, t9] : [t7, t9, t3, t5];
  }
  return null;
}

function rootlessVoicing(chord, form) {
  const tones = rootlessDegrees(chord, form);
  if (!tones || tones.some((t) => !t)) return null;
  // Stack ascending from the first tone; each next tone goes above the last.
  let prev = -Infinity;
  const entries = tones.map((tone) => {
    let offset = tone.semitones % 12;
    while (offset <= prev) offset += 12;
    prev = offset;
    return { tone, offset };
  });
  // B-form sits lower by convention (7th below the root's octave).
  const shift = form === 'B' ? -12 : 0;
  return voicing(chord, `rootless${form}`, `Rootless ${form} (${tones.map((t) => t.label).join('–')})`,
    'Rootless (Bill Evans)',
    form === 'A'
      ? 'Left-hand voicing built 3–5(13)–7–9: the root is left to the bass. Alternating A and B forms through a ii–V–I keeps the guide tones nearly motionless.'
      : 'The A form flipped: 7–9 on the bottom, 3 on top. Pairs with the A form for minimal-motion voice leading.',
    entries.map((e) => ({ ...e, offset: e.offset + shift })));
}

// --- Color voicings ---

function kennyBarron(chord) {
  if (chord.quality.cat !== 'm7') return null;
  const t = (d, s) => toneOf(chord, d, s);
  const entries = [
    { tone: t(1, 0), offset: -12 }, { tone: t(5, 7), offset: -5 }, { tone: t(9, 14), offset: 2 },
    { tone: t(3, 3), offset: 3 }, { tone: t(7, 10), offset: 10 }, { tone: t(11, 17), offset: 17 },
  ];
  if (entries.some((e) => !e.tone)) return null;
  return voicing(chord, 'kennyBarron', 'Kenny Barron voicing (1–5–9 / ♭3–♭7–11)', 'Color voicings',
    'Two stacks of perfect fifths a half step apart — Kenny Barron\'s signature m11 sound. Left hand 1–5–9, right hand ♭3–♭7–11. Piano-specific; too wide for one guitar shape.', entries);
}

function soWhat(chord) {
  if (chord.quality.cat !== 'm7') return null;
  const t = (d, s) => toneOf(chord, d, s);
  const entries = [
    { tone: t(1, 0), offset: 0 }, { tone: t(4, 5), offset: 5 }, { tone: t(7, 10), offset: 10 },
    { tone: t(3, 3), offset: 15 }, { tone: t(5, 7), offset: 19 },
  ];
  if (entries.some((e) => !e.tone)) return null;
  return voicing(chord, 'soWhat', '"So What" voicing (quartal)', 'Color voicings',
    'Three perfect fourths capped by a major third — Bill Evans\'s voicing from Miles Davis\'s "So What." The quartal sound of modal jazz.', entries);
}

// Each triad note: [degree, canonical semitones for labeling, octave offset].
const UPPER_STRUCTURES = [
  { id: 'ustII', numeral: 'II', triad: [[9, 14, 14], [11, 18, 18], [13, 21, 21]], sound: '13♯11 — the Lydian dominant sound' },
  { id: 'ustVI', numeral: 'VI', triad: [[13, 21, 21], [9, 13, 25], [3, 4, 28]], sound: '13♭9 — bright but altered' },
  { id: 'ustbVI', numeral: '♭VI', triad: [[13, 20, 20], [1, 0, 24], [9, 15, 27]], sound: '7♯9♭13 — the full altered color' },
];

function upperStructures(chord) {
  if (chord.quality.cat !== 'dom') return [];
  const t3 = toneOf(chord, 3), t7 = toneOf(chord, 7);
  return UPPER_STRUCTURES.map((ust) => {
    const triad = ust.triad.map(([deg, canonical, offset]) => {
      const tone = toneOf(chord, deg, canonical);
      return tone ? { tone, offset } : null;
    });
    if (triad.some((x) => !x)) return null;
    return voicing(chord, ust.id, `Upper structure ${ust.numeral}`, 'Upper structures',
      `A major triad on the ${ust.numeral} over the tritone (3 + ♭7). Yields ${ust.sound}.`,
      [{ tone: t3, offset: 4 }, { tone: t7, offset: 10 }, ...triad]);
  }).filter(Boolean);
}

// --- Public: every applicable voicing for a chord, grouped ---

export function voicingsFor(chord) {
  const list = [];
  const core = coreFour(chord);
  list.push(...shellVoicings(chord));
  if (core) {
    list.push(voicing(chord, 'close', 'Close position', 'Drop voicings',
      'All four core tones stacked within one octave — the reference position the drop voicings are derived from.', closeStack(core)));
    list.push(dropVoicing(chord, 'drop2', 'Drop 2', [1]));
    list.push(dropVoicing(chord, 'drop3', 'Drop 3', [2]));
    list.push(dropVoicing(chord, 'drop24', 'Drop 2 & 4', [1, 3]));
  }
  const rA = rootlessVoicing(chord, 'A');
  const rB = rootlessVoicing(chord, 'B');
  if (rA) list.push(rA);
  if (rB) list.push(rB);
  const kb = kennyBarron(chord);
  if (kb) list.push(kb);
  const sw = soWhat(chord);
  if (sw) list.push(sw);
  list.push(...upperStructures(chord));
  return list.filter(Boolean);
}

// --- Guitar realization ---
// Maps a voicing's pitch stack onto string sets (notes on consecutive-ish
// strings, low→high), trying octave shifts, keeping frets 0–15 and the
// span fingerable. Drop 2 / drop 3 voicings ARE guitar chord shapes, so
// this yields the idiomatic grips rather than approximations.

const TUNING = [40, 45, 50, 55, 59, 64]; // E2 A2 D3 G3 B3 E4

const STRING_SETS = {
  1: [[5], [4], [3], [2], [1], [0]],
  2: [[0, 2], [1, 3], [2, 4], [3, 5]],
  3: [[0, 2, 3], [1, 3, 4], [1, 2, 3], [2, 3, 4], [3, 4, 5], [0, 1, 2]],
  4: [[2, 3, 4, 5], [1, 2, 3, 4], [0, 1, 2, 3], [0, 2, 3, 4], [1, 3, 4, 5]],
  5: [[1, 2, 3, 4, 5], [0, 1, 2, 3, 4], [0, 2, 3, 4, 5]],
  6: [[0, 1, 2, 3, 4, 5]],
};

export function guitarShape(voicingNotes) {
  const pitches = voicingNotes.map((n) => n.midi);
  const sets = STRING_SETS[pitches.length];
  if (!sets) return null;

  let best = null;
  for (const set of sets) {
    for (let shift = -24; shift <= 24; shift += 12) {
      const frets = set.map((s, i) => pitches[i] + shift - TUNING[s]);
      if (frets.some((f) => f < 0 || f > 15)) continue;
      const fretted = frets.filter((f) => f > 0);
      const span = fretted.length ? Math.max(...fretted) - Math.min(...fretted) : 0;
      if (span > 4) continue;
      const avg = frets.reduce((a, b) => a + b, 0) / frets.length;
      const score = span * 3 + avg * 0.4 + (Math.max(...frets) > 12 ? 6 : 0);
      if (!best || score < best.score) {
        best = { score, strings: set, frets };
      }
    }
  }
  if (!best) return null;

  // Full 6-string picture: null = muted.
  const byString = Array(6).fill(null);
  best.strings.forEach((s, i) => { byString[s] = best.frets[i]; });
  const fretted = best.frets.filter((f) => f > 0);
  const baseFret = fretted.length && Math.max(...fretted) > 4 ? Math.min(...fretted) : 1;
  return { frets: byString, baseFret, labels: voicingNotes.map((n) => n.label) };
}
