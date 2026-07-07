// Barry Harris's 6th-diminished system. The core object: an 8-note "scale
// of chords" interleaving a 6th chord with the diminished 7th built on its
// major 7th degree, so harmonizing every scale step alternates
// 6th-chord inversion / dim7 inversion — motion over a static tonic.

import { spellInterval, noteName } from './notes.js';
import { buildChord } from './chords.js';

// [degree, semitones] — major: 1 2 3 4 5 ♭6 6 7; minor: 1 2 ♭3 4 5 ♭6 6 7.
const MAJOR_6DIM = [[1, 0], [2, 2], [3, 4], [4, 5], [5, 7], [6, 8], [6, 9], [7, 11]];
const MINOR_6DIM = [[1, 0], [2, 2], [3, 3], [4, 5], [5, 7], [6, 8], [6, 9], [7, 11]];

function spellScale(root, steps) {
  // The ♭6/6 pair shares a letter degree; spell the ♭6 as ♯5 when the 6 is
  // natural so both appear (G♯ vs A in C major 6-dim) — Barry's own habit.
  return steps.map(([deg, semis], i) => {
    if (semis === 8 && steps[i + 1]?.[1] === 9) {
      const note = spellInterval(root, 5, 8); // ♯5 spelling
      return { note, name: noteName(note), label: '♯5/♭6' };
    }
    const note = spellInterval(root, deg, semis);
    return { note, name: noteName(note), label: (semis === 3 ? '♭3' : String(deg)) };
  });
}

export function sixthDimScale(root, mode) {
  const steps = mode === 'minor' ? MINOR_6DIM : MAJOR_6DIM;
  const scale = spellScale(root, steps);
  const semis = steps.map(([, s]) => s);

  // Harmonize: chord on step i = scale indices i, i+2, i+4, i+6 (mod 8).
  // Odd steps land on the 6th chord's tones, even on the dim7's.
  const sixthChord = buildChord(root, mode === 'minor' ? 'm6' : '6');
  const dimRoot = spellInterval(root, 7, 11);
  const dimChord = buildChord(dimRoot, 'dim7');

  const positions = semis.map((_, i) => {
    const idxs = [i, (i + 2) % 8, (i + 4) % 8, (i + 6) % 8];
    let prev = -Infinity;
    const notes = idxs.map((idx) => {
      let midi = 48 + root.pc + semis[idx];
      while (midi <= prev) midi += 12;
      prev = midi;
      return { midi, name: scale[idx].name, label: scale[idx].label };
    });
    const isSixth = i % 2 === 0;
    return {
      step: i + 1,
      chordName: isSixth ? `${sixthChord.symbol}${i === 0 ? '' : ' (inv)'}` : `${dimChord.symbol} (inv)`,
      family: isSixth ? 'sixth' : 'dim',
      notes,
    };
  });

  return { root, mode, scale, sixthChord, dimChord, positions };
}

// A dim7 is symmetrical: the same four notes read from any of its tones.
// Those four notes are also the ♭9-upper-structure of four dominants a
// minor 3rd apart — Barry's bridge from diminished to dominant harmony.
export function dominantFamily(domRoot) {
  const members = [[1, 0], [3, 3], [5, 6], [6, 9]].map(([deg, semis]) => {
    const r = spellInterval(domRoot, deg, semis);
    return buildChord(r, '7');
  });
  const dimRoot = spellInterval(domRoot, 3, 4); // dim7 on the dominant's 3rd
  return { members, dim: buildChord(dimRoot, 'dim7') };
}

// How Barry hears each chord family — which 6-dim scale to reach for.
export function barryAnalysis(chord) {
  const cat = chord.quality.cat;
  const s = (deg, semis) => spellInterval(chord.root, deg, semis);

  if (cat === '6' || cat === 'maj7' || cat === 'triad-maj') {
    return {
      scale: sixthDimScale(chord.root, 'major'),
      headline: `${chord.symbol} → ${noteName(chord.root)} major 6th-diminished scale`,
      explanation: cat === 'maj7'
        ? 'Barry treats maj7 as a 6 chord — the 6th replaces the 7th as the resting tone, and the major 7th becomes a scale tone you pass through. Harmonize the scale below and every other step is your tonic chord.'
        : 'This is the home sound: the 6th chord and its leading-tone dim7 woven into one scale. Moving stepwise through the positions creates motion while the harmony stands still.',
    };
  }
  if (cat === 'm6' || cat === 'mMaj7' || cat === 'triad-min') {
    return {
      scale: sixthDimScale(chord.root, 'minor'),
      headline: `${chord.symbol} → ${noteName(chord.root)} minor 6th-diminished scale`,
      explanation: 'The minor tonic in Barry\'s world is the m6 chord. Same construction as the major version with a ♭3 — the dim7 on the major 7th supplies the motion.',
    };
  }
  if (cat === 'm7') {
    const rel = s(3, 3);
    return {
      scale: sixthDimScale(rel, 'major'),
      headline: `${chord.symbol} = ${noteName(rel)}6 → ${noteName(rel)} major 6th-diminished scale`,
      explanation: `A m7 chord is a major 6 chord starting from its 6th: ${chord.symbol} and ${noteName(rel)}6 are the same four notes. Barry runs the ${noteName(rel)} major 6th-diminished scale over both.`,
    };
  }
  if (cat === 'm7b5') {
    const rel = s(3, 3);
    return {
      scale: sixthDimScale(rel, 'minor'),
      headline: `${chord.symbol} = ${noteName(rel)}m6 → ${noteName(rel)} minor 6th-diminished scale`,
      explanation: `Half-diminished is a minor 6 chord in disguise: ${chord.symbol} inverts to ${noteName(rel)}m6. Think from ${noteName(rel)} and the ø chord voices itself.`,
    };
  }
  if (cat === 'dom') {
    const fifth = s(5, 7);
    return {
      scale: sixthDimScale(fifth, 'minor'),
      family: dominantFamily(chord.root),
      headline: `${chord.symbol} → ${noteName(fifth)}m6 (the m6 on the 5th) + the ♭9 diminished family`,
      explanation: `Two Barry moves: (1) ${noteName(fifth)}m6 over a ${noteName(chord.root)} bass IS ${noteName(chord.root)}9 — so the ${noteName(fifth)} minor 6th-diminished scale harmonizes this dominant. (2) Add the ♭9 and the top of the chord is a dim7, shared with three sibling dominants a minor 3rd apart — each can substitute for the others.`,
    };
  }
  if (cat === 'dim7') {
    return {
      family: { members: [11, 2, 5, 8].map((semis, i) => buildChord(spellInterval(chord.root, [7, 2, 4, 6][i], semis), '7')), dim: chord },
      headline: `${chord.symbol} → four dominants, four resolutions`,
      explanation: 'A dim7 is completely symmetrical — the same notes from any of its four tones. It lives inside four different 7♭9 chords (roots a major 3rd below each tone), so it can resolve four ways. Barry: "the diminished is the mother of the dominants."',
    };
  }
  return null;
}
