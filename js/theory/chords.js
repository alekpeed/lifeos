// Chord construction: quality formulas as (degree, semitones) pairs, a
// symbol parser, and degree-aware tone spelling built on notes.js.

import { parseNote, spellInterval, noteName, degreeLabel } from './notes.js';

// `cat` buckets qualities into families the voicing/Barry engines key off:
// triad-maj, triad-min, dim, aug, sus, 6, m6, maj7, dom, m7, mMaj7, m7b5, dim7.
export const QUALITIES = [
  { id: 'maj',     display: '',        label: 'Major',            cat: 'triad-maj', intervals: [[1, 0], [3, 4], [5, 7]], aliases: ['', 'maj', 'M'] },
  { id: 'm',       display: 'm',       label: 'Minor',            cat: 'triad-min', intervals: [[1, 0], [3, 3], [5, 7]], aliases: ['m', 'min', '-'] },
  { id: 'dim',     display: '°',       label: 'Diminished',       cat: 'dim',       intervals: [[1, 0], [3, 3], [5, 6]], aliases: ['dim', '°', 'o'] },
  { id: 'aug',     display: '+',       label: 'Augmented',        cat: 'aug',       intervals: [[1, 0], [3, 4], [5, 8]], aliases: ['aug', '+', '#5'] },
  { id: 'sus4',    display: 'sus4',    label: 'Suspended 4th',    cat: 'sus',       intervals: [[1, 0], [4, 5], [5, 7]], aliases: ['sus4', 'sus'] },
  { id: 'sus2',    display: 'sus2',    label: 'Suspended 2nd',    cat: 'sus',       intervals: [[1, 0], [2, 2], [5, 7]], aliases: ['sus2'] },
  { id: 'add9',    display: 'add9',    label: 'Added 9th',        cat: 'triad-maj', intervals: [[1, 0], [3, 4], [5, 7], [9, 14]], aliases: ['add9'] },
  { id: 'madd9',   display: 'm(add9)', label: 'Minor added 9th',  cat: 'triad-min', intervals: [[1, 0], [3, 3], [5, 7], [9, 14]], aliases: ['madd9', 'm(add9)'] },

  { id: '6',       display: '6',       label: 'Major 6th',        cat: '6',   intervals: [[1, 0], [3, 4], [5, 7], [6, 9]], aliases: ['6', 'maj6', 'M6'] },
  { id: 'm6',      display: 'm6',      label: 'Minor 6th',        cat: 'm6',  intervals: [[1, 0], [3, 3], [5, 7], [6, 9]], aliases: ['m6', 'min6', '-6'] },
  { id: '69',      display: '6/9',     label: 'Six-nine',         cat: '6',   intervals: [[1, 0], [3, 4], [5, 7], [6, 9], [9, 14]], aliases: ['69', '6/9', '6add9'] },
  { id: 'm69',     display: 'm6/9',    label: 'Minor six-nine',   cat: 'm6',  intervals: [[1, 0], [3, 3], [5, 7], [6, 9], [9, 14]], aliases: ['m69', 'm6/9'] },

  { id: 'maj7',    display: 'maj7',    label: 'Major 7th',        cat: 'maj7', intervals: [[1, 0], [3, 4], [5, 7], [7, 11]], aliases: ['maj7', 'ma7', 'M7', 'Δ', 'Δ7'] },
  { id: 'maj9',    display: 'maj9',    label: 'Major 9th',        cat: 'maj7', intervals: [[1, 0], [3, 4], [5, 7], [7, 11], [9, 14]], aliases: ['maj9', 'M9'] },
  { id: 'maj7s11', display: 'maj7♯11', label: 'Major 7th ♯11',    cat: 'maj7', intervals: [[1, 0], [3, 4], [5, 7], [7, 11], [9, 14], [11, 18]], aliases: ['maj7#11', 'maj7♯11', 'M7#11'] },
  { id: 'maj13',   display: 'maj13',   label: 'Major 13th',       cat: 'maj7', intervals: [[1, 0], [3, 4], [5, 7], [7, 11], [9, 14], [13, 21]], aliases: ['maj13', 'M13'] },

  { id: '7',       display: '7',       label: 'Dominant 7th',     cat: 'dom', intervals: [[1, 0], [3, 4], [5, 7], [7, 10]], aliases: ['7', 'dom7'] },
  { id: '9',       display: '9',       label: 'Dominant 9th',     cat: 'dom', intervals: [[1, 0], [3, 4], [5, 7], [7, 10], [9, 14]], aliases: ['9'] },
  { id: '13',      display: '13',      label: 'Dominant 13th',    cat: 'dom', intervals: [[1, 0], [3, 4], [5, 7], [7, 10], [9, 14], [13, 21]], aliases: ['13'] },
  { id: '7sus4',   display: '7sus4',   label: '7 suspended 4th',  cat: 'sus', intervals: [[1, 0], [4, 5], [5, 7], [7, 10]], aliases: ['7sus4', '7sus'] },
  { id: '7b9',     display: '7♭9',     label: 'Dominant 7 ♭9',    cat: 'dom', intervals: [[1, 0], [3, 4], [5, 7], [7, 10], [9, 13]], aliases: ['7b9', '7♭9'] },
  { id: '7s9',     display: '7♯9',     label: 'Dominant 7 ♯9',    cat: 'dom', intervals: [[1, 0], [3, 4], [5, 7], [7, 10], [9, 15]], aliases: ['7#9', '7♯9'] },
  { id: '7s11',    display: '7♯11',    label: 'Dominant 7 ♯11',   cat: 'dom', intervals: [[1, 0], [3, 4], [7, 10], [9, 14], [11, 18]], aliases: ['7#11', '7♯11'] },
  { id: '7b13',    display: '7♭13',    label: 'Dominant 7 ♭13',   cat: 'dom', intervals: [[1, 0], [3, 4], [7, 10], [9, 14], [13, 20]], aliases: ['7b13', '7♭13'] },
  { id: '7s5',     display: '7♯5',     label: 'Dominant 7 ♯5',    cat: 'dom', intervals: [[1, 0], [3, 4], [5, 8], [7, 10]], aliases: ['7#5', '7♯5', 'aug7', '+7'] },
  { id: '7alt',    display: '7alt',    label: 'Altered dominant', cat: 'dom', intervals: [[1, 0], [3, 4], [7, 10], [9, 13], [13, 20]], aliases: ['7alt', 'alt'] },

  { id: 'm7',      display: 'm7',      label: 'Minor 7th',        cat: 'm7', intervals: [[1, 0], [3, 3], [5, 7], [7, 10]], aliases: ['m7', 'min7', '-7'] },
  { id: 'm9',      display: 'm9',      label: 'Minor 9th',        cat: 'm7', intervals: [[1, 0], [3, 3], [5, 7], [7, 10], [9, 14]], aliases: ['m9', 'min9', '-9'] },
  { id: 'm11',     display: 'm11',     label: 'Minor 11th',       cat: 'm7', intervals: [[1, 0], [3, 3], [5, 7], [7, 10], [9, 14], [11, 17]], aliases: ['m11', 'min11', '-11'] },
  { id: 'm13',     display: 'm13',     label: 'Minor 13th',       cat: 'm7', intervals: [[1, 0], [3, 3], [5, 7], [7, 10], [9, 14], [13, 21]], aliases: ['m13', 'min13'] },
  { id: 'mMaj7',   display: 'm(maj7)', label: 'Minor-major 7th',  cat: 'mMaj7', intervals: [[1, 0], [3, 3], [5, 7], [7, 11]], aliases: ['mmaj7', 'm(maj7)', 'minmaj7', '-Δ', 'mM7'] },
  { id: 'm7b5',    display: 'm7♭5',    label: 'Half-diminished',  cat: 'm7b5', intervals: [[1, 0], [3, 3], [5, 6], [7, 10]], aliases: ['m7b5', 'm7♭5', 'ø', 'ø7', 'min7b5', '-7b5'] },
  { id: 'dim7',    display: '°7',      label: 'Diminished 7th',   cat: 'dim7', intervals: [[1, 0], [3, 3], [5, 6], [7, 9]], aliases: ['dim7', '°7', 'o7'] },
];

const QUALITY_BY_ID = new Map(QUALITIES.map((q) => [q.id, q]));

// Case matters in chord symbols — "M7" is major 7, "m7" is minor 7 — so
// matching is exact-case first. A case-insensitive fallback (for typed
// input like "cMAJ7") only covers aliases whose lowercase form is
// unambiguous across qualities.
const ALIAS_EXACT = new Map();
const lowerCounts = new Map();
for (const q of QUALITIES) {
  for (const a of q.aliases) {
    ALIAS_EXACT.set(a, q.id);
    lowerCounts.set(a.toLowerCase(), (lowerCounts.get(a.toLowerCase()) || new Set()).add(q.id));
  }
}
const ALIAS_LOWER = new Map();
for (const q of QUALITIES) {
  for (const a of q.aliases) {
    if (lowerCounts.get(a.toLowerCase()).size === 1) ALIAS_LOWER.set(a.toLowerCase(), q.id);
  }
}

export function getQuality(id) {
  return QUALITY_BY_ID.get(id) || null;
}

// Parse a chord symbol like "Cmaj7", "F#m7b5", "Bb13", "AΔ", "Eø".
export function parseChord(text) {
  const m = /^([A-Ga-g])(𝄫|bb|♭♭|𝄪|##|♯♯|b|♭|#|♯)?(.*)$/.exec(String(text).trim());
  if (!m) return null;
  const root = parseNote(m[1] + (m[2] || ''));
  if (!root) return null;
  const rest = m[3].trim().replace(/\s+/g, '');
  const qualityId = ALIAS_EXACT.get(rest) ?? ALIAS_LOWER.get(rest.toLowerCase());
  if (qualityId === undefined) return null;
  return buildChord(root, qualityId);
}

export function buildChord(root, qualityId) {
  const quality = QUALITY_BY_ID.get(qualityId);
  if (!quality || !root) return null;
  const tones = quality.intervals.map(([degree, semitones]) => {
    const note = spellInterval(root, ((degree - 1) % 7) + 1, semitones % 12);
    return { degree, semitones, note, name: noteName(note), label: degreeLabel(degree, semitones) };
  });
  return {
    root,
    quality,
    tones,
    symbol: noteName(root) + quality.display,
  };
}

// Look up a tone by degree, with a fallback (degree, semitones) synthesized
// if the quality doesn't define it — voicings use this to add e.g. a 9th to
// a plain 7 chord while respecting an altered 9 when the quality has one.
export function toneOf(chord, degree, fallbackSemis) {
  const existing = chord.tones.find((t) => t.degree === degree);
  if (existing) return existing;
  if (fallbackSemis === undefined) return null;
  const note = spellInterval(chord.root, ((degree - 1) % 7) + 1, fallbackSemis % 12);
  return { degree, semitones: fallbackSemis, note, name: noteName(note), label: degreeLabel(degree, fallbackSemis) };
}
