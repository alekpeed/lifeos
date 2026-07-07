// Pitch-spelling primitives for the harmony engine. A "note" is
// { letter, acc, pc } — letter A–G, acc = accidental offset (-2..+2),
// pc = pitch class 0–11. Spelling is degree-aware: a chord tone is named
// by its interval degree first and accidental second, so the ♯9 of C is
// always D♯ (a ninth = some kind of D), never E♭.

export const LETTERS = ['C', 'D', 'E', 'F', 'G', 'A', 'B'];
export const NATURAL_PC = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };

const ACC_GLYPH = { '-2': '𝄫', '-1': '♭', 0: '', 1: '♯', 2: '𝄪' };

export function parseNote(input) {
  const m = /^([A-Ga-g])(𝄫|bb|♭♭|𝄪|##|♯♯|b|♭|#|♯)?$/.exec(String(input).trim());
  if (!m) return null;
  const letter = m[1].toUpperCase();
  const accStr = m[2] || '';
  const acc =
    accStr === '' ? 0
    : /^(bb|♭♭|𝄫)$/.test(accStr) ? -2
    : /^(##|♯♯|𝄪)$/.test(accStr) ? 2
    : /^(b|♭)$/.test(accStr) ? -1
    : 1;
  return { letter, acc, pc: (NATURAL_PC[letter] + acc + 12) % 12 };
}

export function noteName(note) {
  return note.letter + ACC_GLYPH[note.acc];
}

// Spell the note `semitones` above `root` that functions as scale degree
// `degree` (1-based; 9/11/13 work — letters wrap every 7 degrees).
export function spellInterval(root, degree, semitones) {
  const letter = LETTERS[(LETTERS.indexOf(root.letter) + degree - 1) % 7];
  let acc = ((root.pc + semitones) % 12) - NATURAL_PC[letter];
  if (acc > 6) acc -= 12;
  if (acc < -6) acc += 12;
  const pc = (NATURAL_PC[letter] + acc + 12) % 12;
  return { letter, acc, pc };
}

// What each degree is "naturally" in semitones, so alterations label
// correctly: 14 semitones over degree 9 is a plain 9, 13 is a ♭9.
const DEGREE_NATURAL = { 1: 0, 2: 2, 3: 4, 4: 5, 5: 7, 6: 9, 7: 11, 9: 14, 11: 17, 13: 21 };

export function degreeLabel(degree, semitones) {
  const diff = semitones - DEGREE_NATURAL[degree];
  const glyph = diff === 0 ? '' : diff === -1 ? '♭' : diff === 1 ? '♯' : diff === -2 ? '𝄫' : '𝄪';
  return glyph + degree;
}
