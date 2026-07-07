// Key-level harmony: diatonic chords, secondary dominants, tritone subs,
// modal interchange, key membership, and the relationship groups behind
// the Harmony Map. Everything is derived from spelled scale degrees so
// chord symbols come out with correct enharmonics (A♭7, not G♯7).

import { spellInterval, noteName } from './notes.js';
import { buildChord, getQuality } from './chords.js';

const MAJOR_STEPS = [[1, 0], [2, 2], [3, 4], [4, 5], [5, 7], [6, 9], [7, 11]];
const MINOR_STEPS = [[1, 0], [2, 2], [3, 3], [4, 5], [5, 7], [6, 8], [7, 10]]; // natural minor

export function majorScale(root) {
  return MAJOR_STEPS.map(([deg, semis]) => spellInterval(root, deg, semis));
}

export function minorScale(root) {
  return MINOR_STEPS.map(([deg, semis]) => spellInterval(root, deg, semis));
}

const MAJOR_DIATONIC = [
  { numeral: 'Imaj7', triadNumeral: 'I', quality: 'maj7', triad: 'maj' },
  { numeral: 'iim7', triadNumeral: 'ii', quality: 'm7', triad: 'm' },
  { numeral: 'iiim7', triadNumeral: 'iii', quality: 'm7', triad: 'm' },
  { numeral: 'IVmaj7', triadNumeral: 'IV', quality: 'maj7', triad: 'maj' },
  { numeral: 'V7', triadNumeral: 'V', quality: '7', triad: 'maj' },
  { numeral: 'vim7', triadNumeral: 'vi', quality: 'm7', triad: 'm' },
  { numeral: 'viim7♭5', triadNumeral: 'vii°', quality: 'm7b5', triad: 'dim' },
];

// Jazz-practice minor: natural-minor sevenths with the harmonic-minor V7
// and vii°7 noted alongside (the V is almost always played dominant).
const MINOR_DIATONIC = [
  { numeral: 'im7', triadNumeral: 'i', quality: 'm7', triad: 'm', note: 'often played im6 / im(maj7) as a tonic' },
  { numeral: 'iim7♭5', triadNumeral: 'ii°', quality: 'm7b5', triad: 'dim' },
  { numeral: '♭IIImaj7', triadNumeral: '♭III', quality: 'maj7', triad: 'maj' },
  { numeral: 'ivm7', triadNumeral: 'iv', quality: 'm7', triad: 'm' },
  { numeral: 'vm7', triadNumeral: 'v', quality: 'm7', triad: 'm', note: 'raised to V7 via harmonic minor at cadences' },
  { numeral: '♭VImaj7', triadNumeral: '♭VI', quality: 'maj7', triad: 'maj' },
  { numeral: '♭VII7', triadNumeral: '♭VII', quality: '7', triad: 'maj' },
];

export function diatonicChords(root, mode) {
  const scale = mode === 'minor' ? minorScale(root) : majorScale(root);
  const table = mode === 'minor' ? MINOR_DIATONIC : MAJOR_DIATONIC;
  return table.map((row, i) => ({
    ...row,
    root: scale[i],
    chord: buildChord(scale[i], row.quality),
    triadChord: buildChord(scale[i], row.triad),
  }));
}

export function secondaryDominants(root) {
  const scale = majorScale(root);
  // V7 of ii, iii, IV, V, vi — a dominant a perfect 5th above each target.
  return [1, 2, 3, 4, 5].map((idx) => {
    const target = scale[idx];
    const domRoot = spellInterval(target, 5, 7);
    return {
      label: `V7/${MAJOR_DIATONIC[idx].triadNumeral}`,
      chord: buildChord(domRoot, '7'),
      resolvesTo: buildChord(target, MAJOR_DIATONIC[idx].quality),
    };
  });
}

// Tritone substitute: the dominant whose root is a tritone away shares the
// same guide tones (3↔♭7 swap). Spelled as ♭2 of the resolution target.
export function tritoneSub(domChord) {
  const target = spellInterval(domChord.root, 4, 5); // where the dominant resolves
  const subRoot = spellInterval(target, 2, 1); // ♭2 of the target
  return buildChord(subRoot, '7');
}

export function borrowedChords(root) {
  const s = (deg, semis) => spellInterval(root, deg, semis);
  return [
    { label: 'ivm7', chord: buildChord(s(4, 5), 'm7'), from: 'parallel minor — the classic "backdoor" setup' },
    { label: '♭VImaj7', chord: buildChord(s(6, 8), 'maj7'), from: 'parallel minor' },
    { label: '♭VII7', chord: buildChord(s(7, 10), '7'), from: 'parallel minor — the backdoor dominant, resolving up a whole step to I' },
    { label: '♭IIImaj7', chord: buildChord(s(3, 3), 'maj7'), from: 'parallel minor' },
    { label: 'iim7♭5', chord: buildChord(s(2, 2), 'm7b5'), from: 'parallel minor — darkens the ii–V' },
    { label: '♭IImaj7', chord: buildChord(s(2, 1), 'maj7'), from: 'Neapolitan — chromatic color above the tonic' },
  ];
}

// Which keys contain this chord diatonically, and as what.
export function keysContaining(chord) {
  const results = [];
  const pcs = new Set(chord.tones.map((t) => t.note.pc));
  for (const [mode] of [['major'], ['minor']]) {
    for (const tonicName of ['C', 'D♭', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B']) {
      const tonic = { letter: tonicName[0], acc: tonicName.length > 1 ? (tonicName[1] === '♭' ? -1 : 1) : 0, pc: 0 };
      tonic.pc = (({ C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 })[tonic.letter] + tonic.acc + 12) % 12;
      for (const row of diatonicChords(tonic, mode)) {
        if (row.chord.root.pc !== chord.root.pc) continue;
        const rowPcs = new Set(row.chord.tones.map((t) => t.note.pc));
        if ([...pcs].every((pc) => rowPcs.has(pc)) || [...rowPcs].every((pc) => pcs.has(pc))) {
          results.push({ key: `${tonicName} ${mode}`, numeral: row.numeral });
        }
      }
    }
  }
  return results;
}

// --- Harmony Map: related-chord groups for a center chord, each with a
// stated reason. Purely tonal-theory relations; no key context required. ---

export function relatedChords(chord) {
  const groups = [];
  const cat = chord.quality.cat;
  const s = (deg, semis) => spellInterval(chord.root, deg, semis);
  const item = (c, why) => (c ? { symbol: c.symbol, why } : null);

  if (cat === 'dom') {
    const target = s(4, 5);
    groups.push({
      label: 'Resolves to',
      items: [
        item(buildChord(target, 'maj7'), 'down a fifth — the V7→I resolution'),
        item(buildChord(target, 'm7'), 'down a fifth to minor — V7→i'),
        item(buildChord(target, '6'), 'Barry Harris tonic: the 6th chord as home'),
        item(buildChord(spellInterval(target, 6, 9), 'm7'), 'deceptive resolution — vi instead of I'),
      ],
    });
    groups.push({
      label: 'Substitutes',
      items: [
        item(tritoneSub(chord), 'tritone sub — same guide tones, chromatic bass'),
        item(buildChord(s(5, 7), 'm6'), 'Barry Harris: the m6 on the 5th — same sound as this 9/13 chord'),
        item(buildChord(s(2, 1), 'dim7'), 'dim7 on the ♭9 — the 7♭9 upper structure'),
      ],
    });
    groups.push({
      label: 'Barry Harris family (shared dim7)',
      items: [3, 6, 9].map((semis) => {
        const deg = { 3: 3, 6: 5, 9: 6 }[semis];
        return item(buildChord(s(deg, semis), '7'), 'shares this chord\'s 7♭9 diminished core — a minor 3rd apart');
      }),
    });
  }

  if (cat === 'maj7' || cat === '6' || cat === 'triad-maj') {
    groups.push({
      label: 'Substitutes',
      items: [
        item(buildChord(s(6, 9), 'm7'), 'relative minor — the same notes as this chord\'s 6th voicing'),
        item(buildChord(s(3, 4), 'm7'), 'iii for I — shares three tones'),
        item(buildChord(chord.root, cat === '6' ? 'maj7' : '6'), 'Barry Harris: maj7 and 6 are two faces of the same tonic'),
      ],
    });
    groups.push({
      label: 'Approached by',
      items: [
        item(buildChord(s(5, 7), '7'), 'its V7'),
        item(buildChord(s(2, 2), 'm7'), 'its ii — start of the ii–V'),
        item(buildChord(s(2, 1), '7'), 'sub-V — tritone sub resolving down a half step'),
        item(buildChord(s(7, 10), '7'), 'backdoor dominant (♭VII7), borrowed from the parallel minor'),
      ],
    });
  }

  if (cat === 'm7' || cat === 'triad-min' || cat === 'm6' || cat === 'mMaj7') {
    groups.push({
      label: 'Moves to',
      items: [
        item(buildChord(s(4, 5), '7'), 'as ii: on to its V7'),
        item(buildChord(s(7, 10), 'maj7'), 'as ii: through V to the I a whole step below', ),
      ].filter(Boolean),
    });
    groups.push({
      label: 'Substitutes',
      items: [
        item(buildChord(s(3, 3), '6'), 'relative major 6 — literally the same four notes (Barry Harris)'),
        item(buildChord(chord.root, cat === 'm6' ? 'm7' : 'm6'), 'the minor-tonic alter ego'),
        item(buildChord(chord.root, 'mMaj7'), 'minor-major 7 — the melodic-minor tonic color'),
      ],
    });
    groups.push({
      label: 'Approached by',
      items: [
        item(buildChord(s(5, 7), '7'), 'its V7 (usually with a ♭9)'),
        item(buildChord(s(2, 2), 'm7b5'), 'its iiø — the minor ii–V'),
      ],
    });
  }

  if (cat === 'm7b5') {
    groups.push({
      label: 'Moves to',
      items: [item(buildChord(s(4, 5), '7b9'), 'iiø–V7♭9: the minor cadence engine')],
    });
    groups.push({
      label: 'Substitutes',
      items: [
        item(buildChord(s(3, 3), 'm6'), 'the same four notes as the m6 a minor 3rd up (Barry Harris)'),
        item(buildChord(s(6, 8), '9'), 'rootless dominant 9 a major 3rd below'),
      ],
    });
  }

  if (cat === 'dim7') {
    const resolutions = [1].map(() => {
      const up = spellInterval(chord.root, 2, 1);
      return item(buildChord(up, 'maj7'), 'resolves up a half step — leading-tone diminished');
    });
    groups.push({ label: 'Resolves to', items: resolutions });
    groups.push({
      label: 'The four dominants that contain it (7♭9)',
      items: [11, 2, 5, 8].map((semis) => {
        const domRoot = spellInterval(chord.root, semis === 11 ? 7 : semis === 2 ? 2 : semis === 5 ? 4 : 6, semis);
        return item(buildChord(domRoot, '7b9'), 'this dim7 sits on its ♭9');
      }),
    });
  }

  return groups
    .map((g) => ({ ...g, items: g.items.filter(Boolean) }))
    .filter((g) => g.items.length);
}
