// Chord practice drills: question generation, spaced-repetition grading, and
// adaptive session building. Pure functions (no DOM, no IndexedDB) so the
// scheduling and generation logic is unit-testable in Node — the Practice tab
// in the chords view is just a renderer over this.
//
// A "concept" is the unit of skill tracking — a chord-quality family or a
// voicing type, not an individual chord (there are too many single chords to
// track meaningfully; "shaky on drop 3" is the useful signal). Each concept
// generates endless concrete questions by picking random roots.

import { parseNote } from './notes.js';
import { buildChord, getQuality } from './chords.js';
import { voicingsFor } from './voicings.js';

const ROOTS = ['C', 'D♭', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B'];

// Qualities drilled for spelling ("spell Cmaj7") and naming ("what chord is
// C–E–G–B?"). A curated working set, not all 35 — the workhorse qualities.
const DRILL_QUALITIES = ['maj7', '6', 'm7', 'm6', '7', 'm7b5', 'dim7', '9', 'maj9', '7b9', '13', 'mMaj7'];

// Voicing-recognition drills: which qualities can host each voicing type
// (mirrors voicingsFor's own applicability rules).
const CORE_QUALITIES = ['maj7', '7', 'm7', 'm6', '6', 'm7b5'];
const VOICING_DRILLS = [
  { id: 'close', qualities: CORE_QUALITIES },
  { id: 'drop2', qualities: CORE_QUALITIES },
  { id: 'drop3', qualities: CORE_QUALITIES },
  { id: 'drop24', qualities: CORE_QUALITIES },
  { id: 'shellA', qualities: ['maj7', '7', 'm7'] },
  { id: 'shellB', qualities: ['maj7', '7', 'm7'] },
  { id: 'rootlessA', qualities: ['maj7', '7', 'm7'] },
  { id: 'rootlessB', qualities: ['maj7', '7', 'm7'] },
  { id: 'kennyBarron', qualities: ['m7'] },
  { id: 'soWhat', qualities: ['m7'] },
];

// All trackable concepts, in a deliberate teaching order (new concepts are
// introduced in this order, easiest families first).
export const CONCEPTS = [
  ...DRILL_QUALITIES.map((q) => ({
    id: `spell:${q}`, kind: 'spell',
    label: `Spell ${getQuality(q).display || 'maj'} chords`,
  })),
  ...DRILL_QUALITIES.map((q) => ({
    id: `name:${q}`, kind: 'name',
    label: `Recognize ${getQuality(q).display || 'maj'} chords`,
  })),
  ...VOICING_DRILLS.map((v) => ({
    id: `voicing:${v.id}`, kind: 'voicing',
    label: `Identify: ${voicingLabel(v.id)}`,
  })),
];

const CONCEPT_BY_ID = new Map(CONCEPTS.map((c) => [c.id, c]));
export function conceptById(id) { return CONCEPT_BY_ID.get(id) || null; }

function voicingLabel(vid) {
  return {
    close: 'close position', drop2: 'Drop 2', drop3: 'Drop 3', drop24: 'Drop 2 & 4',
    shellA: 'shell (7th low)', shellB: 'shell (3rd low)',
    rootlessA: 'Rootless A', rootlessB: 'Rootless B',
    kennyBarron: 'Kenny Barron', soWhat: '"So What"',
  }[vid] || vid;
}

// Root-position stack (48 = C3 base) for spell/name questions — deliberately
// the plain chord tones, not a stylized voicing, so the question tests the
// chord itself.
function rootStack(chord) {
  return chord.tones.map((t) => ({ midi: 48 + chord.root.pc + t.semitones, name: t.name, label: t.label }));
}

// Build a concrete question for a concept. `rootName` override keeps tests
// deterministic; otherwise a random root is chosen.
export function makeQuestion(conceptId, rootName) {
  const concept = conceptById(conceptId);
  if (!concept) return null;
  const [kind, key] = conceptId.split(':');
  const root = parseNote(rootName || ROOTS[Math.floor(Math.random() * ROOTS.length)]);

  if (kind === 'spell') {
    const chord = buildChord(root, key);
    const notes = rootStack(chord);
    return {
      conceptId, kind,
      prompt: `Spell ${chord.symbol}`,
      noteNames: null, preNotes: null,
      answer: chord.tones.map((t) => t.name).join(' – '),
      answerDetail: `(${chord.tones.map((t) => t.label).join(' – ')}) — ${chord.quality.label}`,
      postNotes: notes,
      play: notes.map((n) => n.midi),
    };
  }

  if (kind === 'name') {
    const chord = buildChord(root, key);
    const notes = rootStack(chord);
    return {
      conceptId, kind,
      prompt: 'Name this chord:',
      noteNames: chord.tones.map((t) => t.name),
      preNotes: notes,
      answer: chord.symbol,
      answerDetail: chord.quality.label,
      postNotes: null,
      play: notes.map((n) => n.midi),
    };
  }

  // kind === 'voicing'
  const drill = VOICING_DRILLS.find((v) => v.id === key);
  const quality = drill.qualities[Math.floor(Math.random() * drill.qualities.length)];
  const chord = buildChord(root, quality);
  const v = voicingsFor(chord).find((x) => x.id === key)
    || voicingsFor(chord).find((x) => x.id === 'close');
  return {
    conceptId, kind,
    prompt: `Which voicing of ${chord.symbol} is this?`,
    noteNames: null,
    preNotes: v.notes, // diagram only — tone labels would give it away
    answer: v.name,
    answerDetail: v.group,
    postNotes: null,
    play: v.notes.map((n) => n.midi),
  };
}

// --- Spaced repetition (same semantics as the Languages module's cards:
// again → back to 1 day; good → interval ×2; easy → interval ×3) plus
// accuracy counters, which is what "good at / not good at" is computed from.

function addDaysTo(dateStr, days) {
  const d = new Date(dateStr + 'T00:00:00');
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

export function gradeSkill(skill, grade, today) {
  const prevInterval = skill.interval || 1;
  const interval = grade === 'again' ? 1 : grade === 'good' ? prevInterval * 2 : prevInterval * 3;
  return {
    interval,
    dueDate: addDaysTo(today, interval),
    attempts: (skill.attempts || 0) + 1,
    correct: (skill.correct || 0) + (grade === 'again' ? 0 : 1),
  };
}

export function accuracy(skill) {
  return skill && skill.attempts ? skill.correct / skill.attempts : null;
}

// --- Adaptive session building: due reviews first, then weak spots, then a
// few new concepts, topped up with strong-review so a session is never empty.

export function buildSession(skillsById, { size = 14, today, newLimit = 3 } = {}) {
  const chosen = [];
  const used = new Set();
  const take = (id) => { if (!used.has(id) && chosen.length < size) { used.add(id); chosen.push(id); } };
  const skillOf = (id) => skillsById[id];
  const acc = (id) => accuracy(skillOf(id)) ?? 1;

  // 1. Due for review (SRS), weakest first.
  CONCEPTS
    .filter((c) => skillOf(c.id) && skillOf(c.id).dueDate <= today)
    .sort((a, b) => acc(a.id) - acc(b.id) || (skillOf(a.id).dueDate < skillOf(b.id).dueDate ? -1 : 1))
    .forEach((c) => take(c.id));

  // 2. Known weak spots, even if not due yet.
  CONCEPTS
    .filter((c) => { const s = skillOf(c.id); return s && s.attempts >= 2 && accuracy(s) < 0.75; })
    .sort((a, b) => acc(a.id) - acc(b.id))
    .forEach((c) => take(c.id));

  // 3. Introduce a few unseen concepts, in teaching order.
  let introduced = 0;
  for (const c of CONCEPTS) {
    if (chosen.length >= size || introduced >= newLimit) break;
    if (!skillOf(c.id) && !used.has(c.id)) { take(c.id); introduced++; }
  }

  // 4. Fill with strong-review (best-known first — light confidence reps).
  CONCEPTS
    .filter((c) => skillOf(c.id) && !used.has(c.id))
    .sort((a, b) => acc(b.id) - acc(a.id))
    .forEach((c) => take(c.id));

  return chosen;
}

// --- "Good at / not good at" summary for the Practice tab header + the
// printable sheet. Only concepts with a real sample size (3+ attempts) count.

export function skillSummary(skillsById) {
  const rated = CONCEPTS
    .map((c) => ({ ...c, skill: skillsById[c.id] }))
    .filter((c) => c.skill && c.skill.attempts >= 3)
    .map((c) => ({ id: c.id, label: c.label, acc: accuracy(c.skill), attempts: c.skill.attempts }));
  const totals = Object.values(skillsById).reduce(
    (t, s) => ({ attempts: t.attempts + (s.attempts || 0), correct: t.correct + (s.correct || 0) }),
    { attempts: 0, correct: 0 }
  );
  return {
    weak: rated.filter((r) => r.acc < 0.75).sort((a, b) => a.acc - b.acc).slice(0, 6),
    strong: rated.filter((r) => r.acc >= 0.9).sort((a, b) => b.acc - a.acc).slice(0, 6),
    totals,
  };
}
