// Voice-leading analysis: WHY a move from chord A to chord B sounds the way
// it does. Everything here is computed from the actual notes — common tones,
// semitone pulls, tritone resolutions, bass-root motion — and then translated
// into prose by mapping those measured facts onto their standard, textbook
// perceptual descriptions. No claim is generated that isn't backed by a
// computed fact about the two chords.

import { noteName } from './notes.js';

// Signed shortest pitch-class distance from a to b, in -6..+6 semitones.
function pcDist(a, b) {
  let d = ((b - a) % 12 + 12) % 12;
  if (d > 6) d -= 12;
  return d;
}

const tName = (tone) => tone.name || noteName(tone.note);

// --- The measured facts ---------------------------------------------------
// from/to are chord objects from buildChord()/parseChord().
// Returns:
// {
//   commonTones: [{ name, fromLabel, toLabel }],
//   moves:       [{ fromName, fromLabel, toName, toLabel, semis }],  // nearest-tone, non-common
//   bass:        { semisUp (0..11), type, text },
//   tritone:     null | { aName, bName, aToName, bToName, direction }, // both resolve by half step
//   guide:       [ strings ],   // guide-tone continuity facts
//   smoothness:  { total, movedVoices, verdict },
// }
export function voiceLeading(from, to) {
  const fromTones = from.tones;
  const toTones = to.tones;
  const toPcs = new Set(toTones.map((t) => t.note.pc));

  // Common tones: pitch classes present in both chords.
  const commonTones = [];
  for (const ft of fromTones) {
    if (!toPcs.has(ft.note.pc)) continue;
    const tt = toTones.find((t) => t.note.pc === ft.note.pc);
    const name = tName(ft) === tName(tt) ? tName(ft) : `${tName(ft)}/${tName(tt)}`;
    commonTones.push({ name, fromLabel: ft.label, toLabel: tt.label });
  }

  // Moving voices: each non-common source tone maps to its nearest target tone.
  const moves = [];
  for (const ft of fromTones) {
    if (toPcs.has(ft.note.pc)) continue;
    let best = null;
    for (const tt of toTones) {
      const d = pcDist(ft.note.pc, tt.note.pc);
      if (!best || Math.abs(d) < Math.abs(best.semis)) {
        best = { fromName: tName(ft), fromLabel: ft.label, toName: tName(tt), toLabel: tt.label, semis: d };
      }
    }
    if (best) moves.push(best);
  }
  moves.sort((a, b) => Math.abs(a.semis) - Math.abs(b.semis));

  // Smoothness: how far does each TARGET tone sit from the nearest source
  // tone? (Target-side, so added color tones count as motion to reach.)
  const fromPcs = fromTones.map((t) => t.note.pc);
  let total = 0;
  for (const tt of toTones) {
    total += Math.min(...fromPcs.map((pc) => Math.abs(pcDist(pc, tt.note.pc))));
  }
  const movedVoices = moves.length;
  const verdict =
    total <= 2 ? 'velvet — barely any travel' :
    total <= 4 ? 'smooth — mostly stepwise' :
    total <= 7 ? 'moderate — a real change of place' :
    'a leap — the ear lands somewhere new';

  // Tritone resolution: a pair of source tones 6 semitones apart where both
  // resolve by half step. The engine of dominant tension→release.
  let tritone = null;
  for (let i = 0; i < fromTones.length && !tritone; i++) {
    for (let j = i + 1; j < fromTones.length && !tritone; j++) {
      const a = fromTones[i], b = fromTones[j];
      if (Math.abs(pcDist(a.note.pc, b.note.pc)) !== 6) continue;
      const resolve = (t) => {
        if (toPcs.has(t.note.pc)) return { to: toTones.find((x) => x.note.pc === t.note.pc), semis: 0 };
        let best = null;
        for (const tt of toTones) {
          const d = pcDist(t.note.pc, tt.note.pc);
          if (Math.abs(d) === 1 && (!best || Math.abs(d) < Math.abs(best.semis))) best = { to: tt, semis: d };
        }
        return best;
      };
      const ra = resolve(a), rb = resolve(b);
      if (ra && rb && (ra.semis !== 0 || rb.semis !== 0) && Math.abs(ra.semis) <= 1 && Math.abs(rb.semis) <= 1) {
        tritone = {
          aName: tName(a), bName: tName(b),
          aToName: tName(ra.to), bToName: tName(rb.to),
          aSemis: ra.semis, bSemis: rb.semis,
        };
      }
    }
  }

  // Guide-tone continuity: the 3rd/7th threads that stitch progressions together.
  const guide = [];
  const deg = (chord, d) => chord.tones.find((t) => t.degree === d);
  const f3 = deg(from, 3), f7 = deg(from, 7), t3 = deg(to, 3), t7 = deg(to, 7);
  if (f7 && t3 && Math.abs(pcDist(f7.note.pc, t3.note.pc)) === 1) {
    guide.push(`${tName(f7)} (the 7th) falls a half step to ${tName(t3)} (the new 3rd) — the classic guide-tone resolution.`);
  }
  if (f3 && t7 && f3.note.pc === t7.note.pc) {
    guide.push(`${tName(f3)} stays put, changing hats: 3rd of ${from.symbol} becomes the 7th of ${to.symbol} — the thread that stitches ii–V motion together.`);
  }
  if (f7 && t7 && Math.abs(pcDist(f7.note.pc, t7.note.pc)) === 1) {
    guide.push(`the 7ths walk chromatically: ${tName(f7)} → ${tName(t7)}.`);
  }

  // Bass (root) motion classification.
  const semisUp = ((to.root.pc - from.root.pc) % 12 + 12) % 12;
  const bass = { semisUp, ...BASS_CLASSES[semisUp] };

  return { commonTones, moves, bass, tritone, guide, smoothness: { total, movedVoices, verdict } };
}

const BASS_CLASSES = {
  0:  { type: 'static',       text: 'The bass holds still — this is transformation, not travel. The ear hears one place changing color.' },
  5:  { type: 'down-fifth',   text: 'The root falls a fifth — the strongest gravitational move in tonal music. The second chord lands as an arrival.' },
  7:  { type: 'up-fifth',     text: 'The root rises a fifth — away from gravity. This opens tension rather than releasing it (the I→V feeling).' },
  1:  { type: 'up-half',      text: 'The bass pushes up a half step — leading-tone energy in the bass itself; the ear is pulled bodily upward.' },
  11: { type: 'down-half',    text: 'The bass slides down a half step — the chromatic glide that makes tritone-sub motion feel like a velvet ramp instead of a jump.' },
  2:  { type: 'up-whole',     text: 'The bass steps up a whole tone — a stepwise arrival without a leading tone. Softer, rounder landing (the backdoor stride).' },
  10: { type: 'down-whole',   text: 'The bass settles down a whole step — an easy, unhurried descent.' },
  4:  { type: 'up-maj3',      text: 'Roots a major third apart — mediant territory: chords that share a tone but no function, so the move reads as color, not logic.' },
  3:  { type: 'up-min3',      text: 'Roots a minor third apart — close cousins (they often share two tones), so the ear hears kinship with a changed mood.' },
  8:  { type: 'down-maj3',    text: 'The root drops a major third — a mediant fall; shared tones keep it coherent while the ground shifts underneath.' },
  9:  { type: 'down-min3',    text: 'The root falls a minor third — the gentle, nostalgic drop (the sound of falling-thirds cycles).' },
  6:  { type: 'tritone',      text: 'The roots are a tritone apart — the most distant bass move possible. Maximum surprise; it works when the upper voices barely move.' },
};

// --- Prose: measured facts → why it sounds that way -----------------------

export function explainMove(from, to) {
  const vl = voiceLeading(from, to);
  const prose = [];

  prose.push(vl.bass.text);

  if (vl.tritone) {
    const t = vl.tritone;
    const intro = `${from.symbol} carries a tritone (${t.aName}–${t.bName}) — the most unstable interval there is —`;
    if (t.aSemis !== 0 && t.bSemis !== 0) {
      const contrary = (t.aSemis > 0) !== (t.bSemis > 0);
      prose.push(
        `${intro} and here it resolves by ${contrary ? 'contrary' : 'parallel'} half-step motion onto ${t.aToName}–${t.bToName}. That double half-step release is the physical mechanism of dominant tension and resolution.`
      );
    } else {
      const [stay, move, moveTo] = t.aSemis === 0
        ? [t.aName, t.bName, t.bToName] : [t.bName, t.aName, t.aToName];
      prose.push(
        `${intro} and here ${move} resolves by half step to ${moveTo} while ${stay} holds as an anchor. Releasing just one side of the tritone is enough to discharge its tension.`
      );
    }
  }

  for (const g of vl.guide) prose.push(g.charAt(0).toUpperCase() + g.slice(1));

  if (vl.commonTones.length >= 2) {
    prose.push(
      `${vl.commonTones.map((c) => c.name).join(' and ')} are shared by both chords — anchors the ear holds onto while the rest moves. High common-tone counts are why a change can feel like a shading rather than a departure.`
    );
  } else if (vl.commonTones.length === 1) {
    prose.push(
      `One tone (${vl.commonTones[0].name}) carries over — a single thread of continuity. Everything else moves around it, which is what gives common-tone progressions their pivoting, kaleidoscope quality.`
    );
  } else {
    prose.push('No tones are shared at all — the ear gets no anchor, so the second chord registers as a genuinely new place. That clean break is a color of its own.');
  }

  const halfSteps = vl.moves.filter((m) => Math.abs(m.semis) === 1);
  if (halfSteps.length) {
    const list = halfSteps.map((m) => `${m.fromName} ${m.semis > 0 ? 'rises' : 'falls'} to ${m.toName}`).join('; ');
    prose.push(
      `Half-step pulls: ${list}. The half step is the most magnetic move a voice can make — the ear hears these resolutions as inevitable, and they do most of the emotional work of the change.`
    );
  }

  prose.push(
    `Total voice travel: ${vl.smoothness.total} semitone${vl.smoothness.total === 1 ? '' : 's'} across ${vl.smoothness.movedVoices} moving voice${vl.smoothness.movedVoices === 1 ? '' : 's'} — ${vl.smoothness.verdict}.`
  );

  return { facts: vl, prose };
}

// --- Minimal-motion playback voicing ---------------------------------------
// Given the actual MIDI notes just played for the source chord, choose MIDI
// notes for the target chord where each target tone sits as close as possible
// to the sounding voices. This is what lets "hear the move" demonstrate the
// smooth voice-leading instead of jumping to a root-position block chord.

export function voiceLeadMidis(sourceMidis, toChord) {
  const out = [];
  for (const tt of toChord.tones) {
    let best = null;
    for (const src of sourceMidis) {
      // candidate octave placements of this tone around each source voice
      const base = src + pcDist(src % 12, tt.note.pc);
      for (const cand of [base, base - 12, base + 12]) {
        if (cand < 34 || cand > 88) continue;
        const cost = Math.min(...sourceMidis.map((s) => Math.abs(s - cand)));
        if (!best || cost < best.cost) best = { midi: cand, cost };
      }
    }
    if (best && !out.includes(best.midi)) out.push(best.midi);
  }
  out.sort((a, b) => a - b);
  return out;
}
