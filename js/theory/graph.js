// The walkable harmony graph: for any center chord, every worthwhile place to
// go, every common way in, and every stand-in — each edge carrying a short
// reason (the long "why" comes from voicelead.js when an edge is inspected).
//
// Curation IS the feature. In tonal music almost anything can follow anything,
// so a naive graph is a hairball. Edges here are hand-curated per chord family
// and weighted: strength 3 = strong functional pull, 2 = natural continuation,
// 1 = color move (hidden unless "adventurous" is on).
//
// dir: 'out' = where this chord goes · 'in' = what leads here · 'sub' = swaps.

import { spellInterval, noteName, parseNote } from './notes.js';
import { buildChord, parseChord, QUALITIES } from './chords.js';
import { diatonicChords } from './harmony.js';

const MAJ_FAMILY = new Set(['maj7', '6', 'triad-maj']);
const MIN_FAMILY = new Set(['m7', 'triad-min', 'm6', 'mMaj7']);

export const EDGE_CATEGORIES = [
  { id: 'resolution', label: 'Resolves to',   dir: 'out' },
  { id: 'motion',     label: 'Moves on to',   dir: 'out' },
  { id: 'color',      label: 'Color moves',   dir: 'out' },
  { id: 'approach',   label: 'Approached by', dir: 'in'  },
  { id: 'sub',        label: 'Stands in for', dir: 'sub' },
];

export function harmonyEdges(chord, { adventurous = false, keyCtx = null } = {}) {
  const cat = chord.quality.cat;
  const s = (deg, semis) => spellInterval(chord.root, deg, semis);
  const edges = [];
  const seen = new Set([chord.symbol]);
  const add = (category, c, reason, strength = 2) => {
    if (!c || seen.has(c.symbol)) return;
    seen.add(c.symbol);
    edges.push({ category, symbol: c.symbol, reason, strength });
  };

  if (cat === 'dom' || cat === 'aug') {
    const target = s(4, 5);
    add('resolution', buildChord(target, 'maj7'), 'V7→I — the fundamental cadence', 3);
    add('resolution', buildChord(target, 'm7'), 'V7→i — resolving into minor', 3);
    add('resolution', buildChord(target, '6'), 'V7→I6 — the Barry Harris tonic', 2);
    add('resolution', buildChord(spellInterval(target, 6, 9), 'm7'), 'deceptive — vi arrives where I was promised', 2);
    add('motion', buildChord(target, '7'), 'dominant chain — each V7 becomes ii of the next', 2);
    add('color', buildChord(spellInterval(target, 6, 8), 'maj7'), '♭VI landing — the parallel-minor deceptive cadence', 1);
    add('color', buildChord(s(2, 1), 'maj7'), 'common-tone surprise — up a half step on the ♭9 axis', 1);
    add('approach', buildChord(s(5, 7), 'm7'), 'the ii that makes this a V', 3);
    add('approach', buildChord(s(5, 7), '7'), 'its own dominant (V of V)', 2);
    add('approach', buildChord(s(2, 1), '7'), 'chromatic dominant from a half step above', 2);
    add('approach', buildChord(chord.root, '7sus4'), 'the suspension that releases into it', 2);
    add('sub', buildChord(s(5, 6), '7'), 'tritone sub — same tritone, chromatic bass', 3);
    add('sub', buildChord(s(5, 7), 'm6'), 'Barry Harris: the m6 on the 5th', 2);
    add('sub', buildChord(s(2, 1), 'dim7'), 'dim7 on the ♭9 — its own upper structure', 2);
    for (const semis of [3, 6, 9]) {
      const deg = { 3: 3, 6: 5, 9: 6 }[semis];
      add('sub', buildChord(s(deg, semis), '7'), 'shares the same dim7 core — minor-3rd family', semis === 6 ? 1 : 2);
    }
  }

  if (MAJ_FAMILY.has(cat)) {
    add('motion', buildChord(s(2, 2), 'm7'), 'to ii — set out on a ii–V journey', 3);
    add('motion', buildChord(s(4, 5), 'maj7'), 'to IV — the plagal neighbor', 2);
    add('motion', buildChord(s(6, 9), '7'), 'V7/ii — a secondary dominant departure', 2);
    add('motion', buildChord(s(3, 4), '7'), 'V7/vi — darkening toward the relative minor', 2);
    add('color', buildChord(s(6, 8), 'maj7'), '♭VI — chromatic mediant shimmer (shared tone, shifted world)', 1);
    add('color', buildChord(s(3, 3), 'maj7'), '♭III — borrowed mediant', 1);
    add('color', buildChord(s(3, 4), 'maj7'), 'III — brightened mediant', 1);
    add('color', buildChord(chord.root, 'm7'), 'parallel minor — same root, darkened world', 1);
    add('color', buildChord(s(4, 6), 'm7b5'), '♯ivø — the key\'s far corner, the Lydian pull', 1);
    add('approach', buildChord(s(5, 7), '7'), 'its V7', 3);
    add('approach', buildChord(s(2, 1), '7'), 'subV — sliding down onto it', 2);
    add('approach', buildChord(s(7, 10), '7'), 'backdoor ♭VII7 — arriving up a whole step', 2);
    add('approach', buildChord(s(7, 11), 'dim7'), 'leading-tone dim7 from below', 2);
    add('sub', buildChord(chord.root, cat === '6' ? 'maj7' : '6'), 'maj7 and 6 — two faces of one tonic', 2);
    add('sub', buildChord(s(6, 9), 'm7'), 'relative minor — the same notes as the 6th chord', 2);
    add('sub', buildChord(s(3, 4), 'm7'), 'iii for I — tonic substitute', 2);
  }

  if (MIN_FAMILY.has(cat)) {
    add('motion', buildChord(s(4, 5), '7'), 'as ii → its V7: the engine of jazz motion', 3);
    add('motion', buildChord(s(3, 3), 'maj7'), 'lift to the relative major', 2);
    add('motion', buildChord(s(4, 5), 'm7'), 'to iv — deeper into minor', 2);
    add('motion', buildChord(s(6, 8), 'maj7'), 'to ♭VI — the minor key\'s warm plateau', 2);
    add('color', buildChord(chord.root, 'maj7'), 'parallel major — the Picardy lift', 1);
    add('color', buildChord(s(7, 10), '7'), '♭VII7 — modal, backdoor color', 1);
    add('approach', buildChord(s(5, 7), '7b9'), 'its V7♭9 — the minor cadence', 3);
    add('approach', buildChord(s(2, 2), 'm7b5'), 'iiø — the minor ii–V', 2);
    add('approach', buildChord(s(2, 1), '7'), 'subV from above', 1);
    add('sub', buildChord(s(3, 3), '6'), 'relative major 6 — literally the same four notes', 2);
    add('sub', buildChord(chord.root, cat === 'm6' ? 'm7' : 'm6'), 'the minor-tonic alter ego', 2);
    add('sub', buildChord(chord.root, 'mMaj7'), 'melodic-minor tonic color', cat === 'mMaj7' ? 0 : 1);
  }

  if (cat === 'm7b5') {
    add('motion', buildChord(s(4, 5), '7b9'), 'iiø → V7♭9 — the minor-key cadence engine', 3);
    add('approach', buildChord(s(5, 7), '7'), 'a dominant a fifth up sets it in motion', 1);
    add('sub', buildChord(s(3, 3), 'm6'), 'the same four notes as the m6 a minor 3rd up', 2);
    add('sub', buildChord(s(6, 8), '9'), 'rootless dominant 9 a major 3rd below', 2);
    add('sub', buildChord(chord.root, 'm7'), 'lift the ♭5 — the softer plain minor', 1);
  }

  if (cat === 'dim7' || cat === 'dim') {
    add('resolution', buildChord(s(2, 1), 'maj7'), 'resolves up a half step — leading-tone diminished', 3);
    add('resolution', buildChord(s(2, 1), 'm7'), 'up a half step into minor', 3);
    if (cat === 'dim7') {
      add('sub', buildChord(s(3, 3), 'dim7'), 'the same four notes, renamed (dim7 symmetry)', 2);
      add('sub', buildChord(s(5, 6), 'dim7'), 'the same four notes, renamed (dim7 symmetry)', 2);
      for (const [deg, semis] of [[7, 11], [2, 2], [4, 5], [6, 8]]) {
        add('approach', buildChord(s(deg, semis), '7b9'), 'this dim7 lives inside it as the 7♭9', 2);
      }
    }
  }

  if (cat === 'sus') {
    add('resolution', buildChord(chord.root, chord.quality.id === '7sus4' ? '7' : 'maj'), 'the suspension releases — 4 falls to 3', 3);
    add('resolution', buildChord(s(4, 5), 'maj7'), 'or skip the release and resolve home directly', 2);
    add('sub', buildChord(s(5, 7), 'm7'), 'the ii it contains (a m7 over the 5th)', 2);
  }

  // Universal color: negative-harmony mirror, when a key context exists.
  if (keyCtx) {
    const neg = negativeHarmony(chord, keyCtx);
    if (neg) add('color', neg, `negative harmony mirror in ${noteName(keyCtx.root)} — every interval reflected around the key's axis`, 1);
  }

  const kept = edges.filter((e) => e.strength > 0 && (adventurous || e.strength >= 2));
  return EDGE_CATEGORIES
    .map((c) => ({ ...c, edges: kept.filter((e) => e.category === c.id) }))
    .filter((g) => g.edges.length);
}

// --- Negative harmony -------------------------------------------------------
// Reflect every pitch class around the key's tonic–dominant axis
// (pc → 2·tonic + 7 − pc). If the mirrored set spells a nameable chord,
// return it. Rooted search prefers plainer qualities.

const NEG_PREF = ['maj', 'm', '7', 'm7', 'maj7', 'm7b5', 'dim7', '6', 'm6', 'dim', 'aug'];

export function negativeHarmony(chord, keyCtx) {
  const t = keyCtx.root.pc;
  const mirrored = new Set(chord.tones.map((tone) => ((2 * t + 7 - tone.note.pc) % 12 + 12) % 12));
  const roots = ['C', 'D♭', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B'];
  for (const qid of NEG_PREF) {
    const q = QUALITIES.find((x) => x.id === qid);
    if (q.intervals.length !== mirrored.size) continue;
    for (const rName of roots) {
      const root = parseNote(rName);
      const pcs = new Set(q.intervals.map(([, semis]) => (root.pc + semis) % 12));
      if (pcs.size === mirrored.size && [...pcs].every((pc) => mirrored.has(pc))) {
        return buildChord(root, qid);
      }
    }
  }
  return null;
}

// --- Roman numeral of a chord relative to a key ------------------------------

const OFFSET_NUMERAL = ['I', '♭II', 'II', '♭III', 'III', 'IV', '♯IV', 'V', '♭VI', 'VI', '♭VII', 'VII'];
const LOWER_CATS = new Set(['triad-min', 'm7', 'm6', 'mMaj7', 'm7b5', 'dim', 'dim7']);

export function romanNumeral(chord, keyCtx) {
  if (!keyCtx) return null;
  // Exact diatonic match first (correct numeral including ° and ø forms).
  for (const row of diatonicChords(keyCtx.root, keyCtx.mode)) {
    if (row.chord.root.pc === chord.root.pc && row.quality === chord.quality.id) return row.numeral;
  }
  const offset = ((chord.root.pc - keyCtx.root.pc) % 12 + 12) % 12;
  let base = OFFSET_NUMERAL[offset];
  if (LOWER_CATS.has(chord.quality.cat)) base = base.replace(/[IV]+/, (m) => m.toLowerCase());
  return base + (chord.quality.display || '');
}
