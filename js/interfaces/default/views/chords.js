// Harmony study module: chord dictionary with jazz voicing families,
// Barry Harris 6th-diminished analysis, a key calculator, a walkable
// harmony graph (radial map of where a chord can go, with voice-leading
// "why" explanations), theory lessons, and an adjustable synth. Study tool
// by design — diagrams, theory, and sound; no sequencing, tempo, or
// play-along.

import { el, todayStr, fmtDate } from '../dom.js';
import { makeKnob, niceStep } from '../knob.js';
import { parseNote, noteName, spellInterval } from '../../../theory/notes.js';
import { QUALITIES, buildChord, parseChord, getQuality } from '../../../theory/chords.js';
import { voicingsFor, rootShellPretty, guitarShape } from '../../../theory/voicings.js';
import { diatonicChords, secondaryDominants, tritoneSub, borrowedChords, keysContaining } from '../../../theory/harmony.js';
import { barryAnalysis } from '../../../theory/barry.js';
import { THEORY_LESSONS } from '../../../theory/lessons.js';
import { harmonyEdges, romanNumeral, qualityFamily } from '../../../theory/graph.js';
import { explainMove, voiceLeadMidis } from '../../../theory/voicelead.js';
import { conceptById, makeQuestion, gradeSkill, buildSession, skillSummary } from '../../../theory/drills.js';
import { playChord, playSequence, FACTORY_PRESETS, PARAM_DEFS } from '../../../audio/synth.js';

const ROOTS = ['C', 'D♭', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B'];

let state = {
  tab: 'dictionary', // dictionary | barry | calculator | map | lessons | sound
  rootName: 'C',
  qualityId: 'maj7',
  instrument: 'piano', // piano | guitar
  calcKeyRoot: 'C',
  calcKeyMode: 'major',
  selectedLesson: null,
  sketch: [], // [{ symbol, why }] — the walked trail; saved as a progression
  sketchName: '',
  mapAdventurous: false,
  mapKeyRoot: 'C',
  mapKeyMode: 'none', // 'none' | 'major' | 'minor'
  mapView: 'walk', // 'walk' (hub graph) | 'atlas' (circle of fifths + dim engines)
  mapDetail: null, // { symbol, reason, catLabel, dir } — the inspected edge
  synthParams: null, // loaded from settings on first render
  synthPresetName: 'Piano',
  drill: null, // active session: { questions, index, showAnswer, goodCount }
};

function currentChord() {
  return buildChord(parseNote(state.rootName), state.qualityId);
}

function setChordFromSymbol(symbol) {
  const chord = parseChord(symbol);
  if (!chord) return false;
  state.rootName = noteName(chord.root);
  state.qualityId = chord.quality.id;
  return true;
}

async function getSynthParams(ctx) {
  if (!state.synthParams) {
    const saved = await ctx.data.Settings.get('synthParams');
    state.synthParams = saved || { ...FACTORY_PRESETS.piano };
  }
  return state.synthParams;
}

async function play(ctx, midiNotes) {
  const params = await getSynthParams(ctx);
  try {
    playChord(midiNotes, params);
  } catch (err) {
    console.warn('synth unavailable:', err.message);
  }
}

// --- SVG helpers (el() is HTML-only; SVG needs the namespace) ---

function svgEl(tag, attrs = {}, children = []) {
  const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
  for (const child of children) node.append(child);
  return node;
}

// Piano keyboard with chord tones highlighted.
function keyboardSVG(notes) {
  const midis = notes.map((n) => n.midi);
  const lo = Math.floor(Math.min(...midis) / 12) * 12;
  const hi = Math.ceil((Math.max(...midis) + 1) / 12) * 12;
  const isBlack = (m) => [1, 3, 6, 8, 10].includes(m % 12);
  const whites = [];
  for (let m = lo; m < hi; m++) if (!isBlack(m)) whites.push(m);
  const W = 13, H = 52, BW = 8, BH = 32;
  const xOfWhite = new Map(whites.map((m, i) => [m, i * W]));
  const highlight = new Set(midis);

  const svg = svgEl('svg', { viewBox: `0 0 ${whites.length * W} ${H + 4}`, class: 'mer-kbd' });
  for (const m of whites) {
    svg.append(svgEl('rect', {
      x: xOfWhite.get(m), y: 0, width: W - 1, height: H, rx: 1.5,
      class: highlight.has(m) ? 'mer-kbd-white is-on' : 'mer-kbd-white',
    }));
  }
  for (let m = lo; m < hi; m++) {
    if (!isBlack(m)) continue;
    const x = xOfWhite.get(m - 1) + W - BW / 2 - 0.5;
    svg.append(svgEl('rect', {
      x, y: 0, width: BW, height: BH, rx: 1.5,
      class: highlight.has(m) ? 'mer-kbd-black is-on' : 'mer-kbd-black',
    }));
  }
  return svg;
}

// Standard vertical chord grid for guitar.
function fretboardSVG(shape) {
  const SW = 15, FH = 19, TOP = 15, LEFT = 12;
  const width = LEFT * 2 + SW * 5, height = TOP + FH * 5 + 6;
  const svg = svgEl('svg', { viewBox: `0 0 ${width + 22} ${height}`, class: 'mer-fret' });

  for (let s = 0; s < 6; s++) {
    svg.append(svgEl('line', { x1: LEFT + s * SW, y1: TOP, x2: LEFT + s * SW, y2: TOP + FH * 5, class: 'mer-fret-line' }));
  }
  for (let f = 0; f <= 5; f++) {
    svg.append(svgEl('line', {
      x1: LEFT, y1: TOP + f * FH, x2: LEFT + SW * 5, y2: TOP + f * FH,
      class: shape.baseFret === 1 && f === 0 ? 'mer-fret-nut' : 'mer-fret-line',
    }));
  }
  if (shape.baseFret > 1) {
    svg.append(svgEl('text', { x: width + 4, y: TOP + FH * 0.65, class: 'mer-fret-label' }, [`${shape.baseFret}fr`]));
  }
  shape.frets.forEach((fret, s) => {
    const x = LEFT + s * SW;
    if (fret === null) {
      svg.append(svgEl('text', { x, y: TOP - 4, class: 'mer-fret-mark', 'text-anchor': 'middle' }, ['×']));
    } else if (fret === 0) {
      svg.append(svgEl('circle', { cx: x, cy: TOP - 7, r: 3.4, class: 'mer-fret-open' }));
    } else {
      const rel = fret - shape.baseFret + 1;
      svg.append(svgEl('circle', { cx: x, cy: TOP + (rel - 0.5) * FH, r: 5.2, class: 'mer-fret-dot' }));
    }
  });
  return svg;
}

function diagramFor(notes) {
  if (state.instrument === 'guitar') {
    const shape = guitarShape(notes);
    return shape
      ? fretboardSVG(shape)
      : el('p', { class: 'mer-muted mer-fret-none', text: 'No practical guitar grip — piano-specific spread.' });
  }
  return keyboardSVG(notes);
}

function noteChips(notes) {
  return el('div', { class: 'mer-place-meta' },
    notes.map((n) => el('span', { class: 'mer-chip', text: `${n.name} (${n.label})` })));
}

function playBtn(ctx, notes, label = '▶') {
  return el('button', { type: 'button', class: 'mer-play-btn', text: label, onclick: () => play(ctx, notes.map((n) => n.midi)) });
}

// --- Shared pickers ---

function chordPicker(ctx, rerender) {
  const rootSelect = el('select', { onchange: (e) => { state.rootName = e.target.value; rerender(); } },
    ROOTS.map((r) => el('option', { value: r, text: r, selected: r === state.rootName })));
  const qualitySelect = el('select', { onchange: (e) => { state.qualityId = e.target.value; rerender(); } },
    QUALITIES.map((q) => el('option', { value: q.id, text: `${q.display || 'maj'} — ${q.label}`, selected: q.id === state.qualityId })));
  const typeIn = el('input', {
    type: 'text', placeholder: 'or type: F#m7b5, BbΔ, Ealt…',
    onkeydown: (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      if (setChordFromSymbol(e.target.value)) rerender();
      else e.target.classList.add('is-invalid');
    },
  });
  const instrumentToggle = el('div', { class: 'mer-toggle-group' }, [
    el('button', { type: 'button', class: state.instrument === 'piano' ? 'is-active' : '', text: '🎹 Piano', onclick: () => { state.instrument = 'piano'; rerender(); } }),
    el('button', { type: 'button', class: state.instrument === 'guitar' ? 'is-active' : '', text: '🎸 Guitar', onclick: () => { state.instrument = 'guitar'; rerender(); } }),
  ]);
  return el('div', { class: 'mer-toolbar' }, [rootSelect, qualitySelect, typeIn, instrumentToggle]);
}

// --- Dictionary tab ---

function renderDictionary(area, ctx, rerender) {
  area.append(chordPicker(ctx, rerender));
  const chord = currentChord();
  if (!chord) { area.append(el('p', { class: 'mer-muted', text: 'Pick a chord.' })); return; }

  const header = el('div', { class: 'mer-detail-header' }, [
    el('h2', { text: chord.symbol }),
    el('span', { class: 'mer-muted', text: chord.quality.label }),
  ]);
  area.append(header, noteChips(chord.tones.map((t) => ({ name: t.name, label: t.label }))));

  // Root – Shell – Pretty: staged, cumulative.
  const rsp = rootShellPretty(chord);
  if (rsp) {
    area.append(el('div', { class: 'mer-subsection-label', text: 'Root – Shell – Pretty' }));
    const row = el('div', { class: 'mer-voicing-grid' });
    for (const stage of rsp.stages) {
      row.append(el('div', { class: 'mer-voicing-card' }, [
        el('div', { class: 'mer-voicing-name' }, [
          el('span', { text: stage.name }),
          playBtn(ctx, stage.notes),
        ]),
        diagramFor(stage.notes),
        noteChips(stage.notes),
      ]));
    }
    area.append(row);
  }

  // Voicing families, grouped.
  const grouped = new Map();
  for (const v of voicingsFor(chord)) {
    if (!grouped.has(v.group)) grouped.set(v.group, []);
    grouped.get(v.group).push(v);
  }
  for (const [group, list] of grouped) {
    area.append(el('div', { class: 'mer-subsection-label', text: group }));
    const row = el('div', { class: 'mer-voicing-grid' });
    for (const v of list) {
      row.append(el('div', { class: 'mer-voicing-card' }, [
        el('div', { class: 'mer-voicing-name' }, [el('span', { text: v.name }), playBtn(ctx, v.notes)]),
        diagramFor(v.notes),
        noteChips(v.notes),
        el('p', { class: 'mer-muted mer-voicing-desc', text: v.description }),
      ]));
    }
    area.append(row);
  }

  area.append(el('button', {
    type: 'button', text: '→ How Barry Harris hears this chord',
    onclick: () => { state.tab = 'barry'; rerender(); },
  }));
}

// --- Barry Harris tab ---

function renderBarry(area, ctx, rerender) {
  area.append(chordPicker(ctx, rerender));
  const chord = currentChord();
  const analysis = chord && barryAnalysis(chord);
  if (!analysis) {
    area.append(el('p', { class: 'mer-muted', text: 'No 6th-diminished mapping for this quality — try a major, minor, 6th, m7, ø, dominant, or dim7 chord.' }));
    return;
  }

  area.append(el('h2', { text: analysis.headline }));
  area.append(el('p', {}, [el('span', { text: analysis.explanation })]));

  if (analysis.scale) {
    const { scale } = analysis;
    area.append(el('div', { class: 'mer-subsection-label', text: `The scale (${scale.sixthChord.symbol} + ${scale.dimChord.symbol})` }));
    area.append(el('div', { class: 'mer-place-meta' },
      scale.scale.map((s) => el('span', { class: 'mer-chip', text: `${s.name} (${s.label})` }))));

    area.append(el('div', { class: 'mer-subsection-label', text: 'Harmonized: the scale of chords' }));
    area.append(el('p', { class: 'mer-muted', text: 'Step through the positions in order — every other step is the tonic chord, the rest are the diminished. Motion inside stillness.' }));
    const grid = el('div', { class: 'mer-voicing-grid' });
    for (const pos of scale.positions) {
      grid.append(el('div', { class: `mer-voicing-card ${pos.family === 'dim' ? 'is-dim' : ''}` }, [
        el('div', { class: 'mer-voicing-name' }, [
          el('span', { text: `${pos.step}. ${pos.chordName}` }),
          playBtn(ctx, pos.notes),
        ]),
        diagramFor(pos.notes),
        noteChips(pos.notes),
      ]));
    }
    area.append(grid);
  }

  if (analysis.family) {
    area.append(el('div', { class: 'mer-subsection-label', text: `The ♭9 diminished family — shared core: ${analysis.family.dim.symbol}` }));
    area.append(el('p', { class: 'mer-muted', text: 'Four dominants a minor 3rd apart share one dim7 as their 7♭9 upper structure. Each can substitute for the others.' }));
    const famRow = el('div', { class: 'mer-place-meta' },
      analysis.family.members.map((m) => el('button', {
        type: 'button', class: 'mer-map-chip', text: m.symbol,
        onclick: () => { setChordFromSymbol(m.symbol); rerender(); },
      })));
    area.append(famRow);
  }
}

// --- Calculator tab ---

function renderCalculator(area, ctx, rerender) {
  const rootSelect = el('select', { onchange: (e) => { state.calcKeyRoot = e.target.value; rerender(); } },
    ROOTS.map((r) => el('option', { value: r, text: r, selected: r === state.calcKeyRoot })));
  const modeSelect = el('select', { onchange: (e) => { state.calcKeyMode = e.target.value; rerender(); } }, [
    el('option', { value: 'major', text: 'major', selected: state.calcKeyMode === 'major' }),
    el('option', { value: 'minor', text: 'minor', selected: state.calcKeyMode === 'minor' }),
  ]);
  area.append(el('div', { class: 'mer-toolbar' }, [el('span', { text: 'Key:' }), rootSelect, modeSelect]));

  const keyRoot = parseNote(state.calcKeyRoot);
  const table = (rows) => el('table', { class: 'mer-table' }, [
    el('tbody', {}, rows),
  ]);
  const chordCell = (chord) => el('td', {}, [el('button', {
    type: 'button', class: 'mer-map-chip', text: chord.symbol,
    onclick: () => { setChordFromSymbol(chord.symbol); state.tab = 'dictionary'; rerender(); },
  })]);

  area.append(el('div', { class: 'mer-subsection-label', text: 'Diatonic chords' }));
  area.append(table(diatonicChords(keyRoot, state.calcKeyMode).map((row) => el('tr', {}, [
    el('td', { text: row.numeral }),
    chordCell(row.chord),
    el('td', { class: 'mer-muted', text: row.note || '' }),
  ]))));

  if (state.calcKeyMode === 'major') {
    area.append(el('div', { class: 'mer-subsection-label', text: 'Secondary dominants' }));
    area.append(table(secondaryDominants(keyRoot).map((sd) => el('tr', {}, [
      el('td', { text: sd.label }),
      chordCell(sd.chord),
      el('td', {}, [el('span', { class: 'mer-muted', text: 'resolves to ' }), chordCell(sd.resolvesTo).firstChild]),
      el('td', {}, [el('span', { class: 'mer-muted', text: 'tritone sub: ' }), chordCell(tritoneSub(sd.chord)).firstChild]),
    ]))));

    area.append(el('div', { class: 'mer-subsection-label', text: 'Borrowed from the parallel minor' }));
    area.append(table(borrowedChords(keyRoot).map((b) => el('tr', {}, [
      el('td', { text: b.label }),
      chordCell(b.chord),
      el('td', { class: 'mer-muted', text: b.from }),
    ]))));
  }

  const chord = currentChord();
  if (chord) {
    area.append(el('div', { class: 'mer-subsection-label', text: `Where does ${chord.symbol} live?` }));
    const homes = keysContaining(chord);
    area.append(homes.length
      ? el('div', { class: 'mer-place-meta' }, homes.map((h) => el('span', { class: 'mer-chip', text: `${h.key}: ${h.numeral}` })))
      : el('p', { class: 'mer-muted', text: 'Not diatonic to any major or natural-minor key — a chromatic color chord.' }));
  }
}

// --- Harmony Map tab: the walkable graph ---

function closeMidis(chord) {
  const v = voicingsFor(chord).find((x) => x.id === 'close');
  if (v) return v.notes.map((n) => n.midi);
  return chord.tones.map((t) => 48 + chord.root.pc + t.semitones);
}

// Audition a move with minimal-motion voice-leading: the second chord lands
// on the nearest available tones instead of jumping to root position — so
// what you HEAR is the same smoothness the explanation describes.
async function playMove(ctx, fromChord, toChord) {
  const params = await getSynthParams(ctx);
  const src = closeMidis(fromChord);
  const tgt = voiceLeadMidis(src, toChord);
  try { playSequence([src, tgt], params, 0.95); } catch (err) { console.warn('synth unavailable:', err.message); }
}

async function playTrail(ctx, symbols) {
  const chords = symbols.map((sym) => parseChord(sym)).filter(Boolean);
  if (!chords.length) return;
  const params = await getSynthParams(ctx);
  const seq = [closeMidis(chords[0])];
  for (let i = 1; i < chords.length; i++) seq.push(voiceLeadMidis(seq[i - 1], chords[i]));
  try { playSequence(seq, params, 0.85); } catch (err) { console.warn('synth unavailable:', err.message); }
}

function mapKeyCtx() {
  if (state.mapKeyMode === 'none') return null;
  return { root: parseNote(state.mapKeyRoot), mode: state.mapKeyMode };
}

// Angular sectors for each edge category (degrees; 0° = east, +90° = south).
const MAP_SECTORS = {
  resolution: { a0: -82, a1: -30 },
  motion:     { a0: -18, a1: 40 },
  color:      { a0: 48, a1: 84 },
  approach:   { a0: 110, a1: 172 },
  sub:        { a0: 186, a1: 250 },
};

function graphSVG(chord, groups, keyCtx, onInspect) {
  const CX = 390, CY = 300;
  const svg = svgEl('svg', { viewBox: '0 0 780 600', class: 'mer-hg' });
  const rad = (deg) => (deg * Math.PI) / 180;

  // Sector headers.
  for (const g of groups) {
    const sec = MAP_SECTORS[g.id];
    const mid = rad((sec.a0 + sec.a1) / 2);
    svg.append(svgEl('text', {
      x: CX + 278 * Math.cos(mid), y: CY + 278 * Math.sin(mid),
      class: 'mer-hg-sector', 'text-anchor': 'middle',
    }, [g.label.toUpperCase()]));
  }

  const nodes = [];
  for (const g of groups) {
    const sec = MAP_SECTORS[g.id];
    g.edges.forEach((edge, i) => {
      const n = g.edges.length;
      const t = n === 1 ? 0.5 : i / (n - 1);
      const angle = rad(sec.a0 + t * (sec.a1 - sec.a0));
      const r = 170 + (i % 2) * 58;
      nodes.push({ edge, g, x: CX + r * Math.cos(angle), y: CY + r * Math.sin(angle) });
    });
  }

  // Edges first (under the nodes).
  for (const { edge, g, x, y } of nodes) {
    svg.append(svgEl('line', {
      x1: CX, y1: CY, x2: x, y2: y,
      class: `mer-hg-edge mer-hg-${g.id}${g.id === 'sub' ? ' is-dashed' : ''}`,
      'stroke-width': edge.strength === 3 ? 3.2 : edge.strength === 2 ? 1.9 : 1.1,
    }));
  }

  // Nodes.
  for (const { edge, g, x, y } of nodes) {
    const edgeChord = parseChord(edge.symbol);
    const numeral = keyCtx ? romanNumeral(edgeChord, keyCtx) : null;
    const w = Math.max(edge.symbol.length, numeral ? numeral.length : 0) * 8.5 + 20;
    const h = numeral ? 40 : 27;
    const node = svgEl('g', { class: `mer-hg-node mer-hg-${g.id} mer-hq-${qualityFamily(edgeChord)}`, tabindex: '0', role: 'button' });
    node.append(svgEl('rect', { x: x - w / 2, y: y - h / 2, width: w, height: h, rx: 13 }));
    node.append(svgEl('text', { x, y: numeral ? y - 3 : y + 4.5, 'text-anchor': 'middle', class: 'mer-hg-symbol' }, [edge.symbol]));
    if (numeral) node.append(svgEl('text', { x, y: y + 13, 'text-anchor': 'middle', class: 'mer-hg-numeral' }, [numeral]));
    const inspect = () => onInspect(edge, g);
    node.addEventListener('click', inspect);
    node.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); inspect(); } });
    svg.append(node);
  }

  // Center last, on top.
  const cw = chord.symbol.length * 11 + 30;
  const numeral = keyCtx ? romanNumeral(chord, keyCtx) : null;
  const center = svgEl('g', { class: `mer-hg-node mer-hg-center mer-hq-${qualityFamily(chord)}` });
  center.append(svgEl('rect', { x: CX - cw / 2, y: CY - (numeral ? 26 : 20), width: cw, height: numeral ? 52 : 40, rx: 18 }));
  center.append(svgEl('text', { x: CX, y: numeral ? CY - 2 : CY + 6, 'text-anchor': 'middle', class: 'mer-hg-symbol is-center' }, [chord.symbol]));
  if (numeral) center.append(svgEl('text', { x: CX, y: CY + 16, 'text-anchor': 'middle', class: 'mer-hg-numeral' }, [numeral]));
  svg.append(center);

  return svg;
}

// Chord-quality color legend (Illustrated-Harmony-style: fill = what kind of
// chord; line color = its relationship to the center; thickness = pull).
function qualityLegend() {
  const items = [['major', 'Major'], ['minor', 'Minor'], ['dominant', 'Dominant / sus'], ['diminished', 'Dim / ø'], ['augmented', 'Augmented']];
  return el('div', { class: 'mer-place-meta mer-hg-legend' },
    items.map(([fam, label]) => el('span', { class: 'mer-hg-legend-item' }, [
      el('span', { class: `mer-hq-dot mer-hq-${fam}` }),
      el('span', { class: 'mer-muted', text: label }),
    ])));
}

// --- Atlas view: the whole territory at once (vs. the walkable hub) ---

const FIFTHS = ['C', 'G', 'D', 'A', 'E', 'B', 'F♯', 'D♭', 'A♭', 'E♭', 'B♭', 'F'];

// The three diminished "engines": every dominant 7th in existence belongs to
// exactly one of three dim7 cores (its 7♭9 upper structure). Four dominants
// per core, a minor third apart — Barry Harris's substitution wheels.
const DIM_ENGINES = [
  { core: 'C°7', doms: ['B7', 'D7', 'F7', 'A♭7'] },
  { core: 'C♯°7', doms: ['C7', 'E♭7', 'F♯7', 'A7'] },
  { core: 'D°7', doms: ['D♭7', 'E7', 'G7', 'B♭7'] },
];

function atlasSVG(chord, keyCtx, onPick) {
  const CX = 390, CY = 262, OUTER = 200, INNER = 130;
  const svg = svgEl('svg', { viewBox: '0 0 780 530', class: 'mer-hg mer-atlas' });
  const rad = (deg) => (deg * Math.PI) / 180;

  // Which wedge is diatonic? The key's six wheel-triads sit at tonic ± one
  // slot (IV I V outside, ii vi iii inside). Minor keys share their relative
  // major's wedge.
  let diatonicIdx = null;
  if (keyCtx) {
    const majPc = keyCtx.mode === 'major' ? keyCtx.root.pc : (keyCtx.root.pc + 3) % 12;
    const idx = FIFTHS.findIndex((n) => parseNote(n).pc === majPc);
    diatonicIdx = new Set([(idx + 11) % 12, idx, (idx + 1) % 12]);
  }
  const fam = qualityFamily(chord);

  svg.append(svgEl('text', { x: CX, y: CY - 8, 'text-anchor': 'middle', class: 'mer-hg-sector' }, ['CIRCLE OF']));
  svg.append(svgEl('text', { x: CX, y: CY + 10, 'text-anchor': 'middle', class: 'mer-hg-sector' }, ['FIFTHS']));

  FIFTHS.forEach((name, i) => {
    const angle = rad(-90 + i * 30);
    const root = parseNote(name);
    const relMinor = noteName(spellInterval(root, 6, 9)); // relative minor: spelled 6th degree

    for (const ring of [
      { symbol: name, r: OUTER, size: 24, family: 'major', pc: root.pc, isMinor: false },
      { symbol: relMinor + 'm', r: INNER, size: 19, family: 'minor', pc: (root.pc + 9) % 12, isMinor: true },
    ]) {
      const x = CX + ring.r * Math.cos(angle), y = CY + ring.r * Math.sin(angle);
      const classes = ['mer-atlas-node', `mer-hq-${ring.family}`];
      if (diatonicIdx && diatonicIdx.has(i)) classes.push('is-diatonic');
      const here = (ring.isMinor ? fam === 'minor' : fam === 'major') && chord.root.pc === ring.pc;
      if (here) classes.push('is-here');
      const node = svgEl('g', { class: classes.join(' '), tabindex: '0', role: 'button' });
      node.append(svgEl('circle', { cx: x, cy: y, r: ring.size }));
      node.append(svgEl('text', { x, y: y + 4.5, 'text-anchor': 'middle', class: 'mer-hg-symbol' }, [ring.symbol]));
      const pick = () => onPick(ring.symbol);
      node.addEventListener('click', pick);
      node.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick(); } });
      svg.append(node);
    }
  });

  return svg;
}

function dimEnginesSVG(chord, onPick) {
  const svg = svgEl('svg', { viewBox: '0 0 780 210', class: 'mer-hg mer-atlas' });
  const centers = [140, 390, 640];
  DIM_ENGINES.forEach((engine, e) => {
    const cx = centers[e], cy = 102;
    // spokes + four dominants at the diagonals
    engine.doms.forEach((dom, i) => {
      const angle = ((i * 90 + 45) * Math.PI) / 180;
      const x = cx + 68 * Math.cos(angle), y = cy + 68 * Math.sin(angle);
      svg.append(svgEl('line', { x1: cx, y1: cy, x2: x, y2: y, class: 'mer-hg-edge mer-hg-sub is-dashed', 'stroke-width': 1.4 }));
      const isHere = chord.quality.cat === 'dom' && parseChord(dom)?.root.pc === chord.root.pc;
      const node = svgEl('g', { class: `mer-atlas-node mer-hq-dominant${isHere ? ' is-here' : ''}`, tabindex: '0', role: 'button' });
      node.append(svgEl('circle', { cx: x, cy: y, r: 21 }));
      node.append(svgEl('text', { x, y: y + 4.5, 'text-anchor': 'middle', class: 'mer-hg-symbol' }, [dom]));
      node.addEventListener('click', () => onPick(dom));
      svg.append(node);
    });
    const core = svgEl('g', { class: 'mer-atlas-node mer-hq-diminished', tabindex: '0', role: 'button' });
    core.append(svgEl('circle', { cx, cy, r: 25 }));
    core.append(svgEl('text', { x: cx, y: cy + 4.5, 'text-anchor': 'middle', class: 'mer-hg-symbol' }, [engine.core]));
    core.addEventListener('click', () => onPick(engine.core));
    svg.append(core);
  });
  return svg;
}

function renderAtlas(area, ctx, rerender, chord, keyCtx) {
  const pick = (symbol) => {
    if (!setChordFromSymbol(symbol)) return;
    state.mapView = 'walk';
    state.mapDetail = null;
    rerender();
  };

  area.append(el('p', { class: 'mer-muted mer-hg-hint', text: 'The whole territory at once. Neighbors on the wheel share six of seven notes — the closer, the more related. Inner ring: relative minors (same notes, darker home). Click any chord to zoom into its walkable map.' + (keyCtx ? ' Highlighted wedge: the six wheel-chords of your key.' : '') }));
  area.append(atlasSVG(chord, keyCtx, pick));

  area.append(el('div', { class: 'mer-subsection-label', text: 'The three diminished engines' }));
  area.append(el('p', { class: 'mer-muted mer-hg-hint', text: 'Every dominant 7th in existence belongs to exactly one of these three dim7 cores (its 7♭9 upper structure). The four dominants around each core are a minor third apart and can substitute for each other — Barry Harris\'s substitution wheels.' }));
  area.append(dimEnginesSVG(chord, pick));

  area.append(qualityLegend());
}

function mapDetailPanel(area, ctx, chord, rerender) {
  const d = state.mapDetail;
  const other = parseChord(d.symbol);
  if (!other) { state.mapDetail = null; return; }
  const [from, to] = d.dir === 'in' ? [other, chord] : [chord, other];
  const { facts, prose } = explainMove(from, to);

  const walk = () => {
    if (!state.sketch.length) state.sketch.push({ symbol: chord.symbol, why: 'start' });
    state.sketch.push({ symbol: d.symbol, why: d.reason });
    setChordFromSymbol(d.symbol);
    state.mapDetail = null;
    rerender();
  };

  const panel = el('div', { class: 'mer-hg-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: d.dir === 'in' ? `${other.symbol} → ${chord.symbol}` : `${chord.symbol} ${d.dir === 'sub' ? '↔' : '→'} ${other.symbol}` }),
      el('span', { class: 'mer-chip', text: d.catLabel }),
      el('span', { class: 'mer-muted', text: d.reason }),
    ]),
    el('div', { class: 'mer-place-meta' }, [
      el('span', { class: 'mer-chip', text: `${facts.commonTones.length} common tone${facts.commonTones.length === 1 ? '' : 's'}` }),
      el('span', { class: 'mer-chip', text: `${facts.smoothness.total} semitones of travel` }),
      el('span', { class: 'mer-chip', text: facts.bass.type.replace(/-/g, ' ') }),
    ]),
    ...prose.map((p) => el('p', { class: 'mer-hg-why', text: p })),
    ...(facts.moves.length ? [el('div', { class: 'mer-place-meta' },
      facts.moves.map((m) => el('span', {
        class: 'mer-chip',
        text: `${m.fromName} (${m.fromLabel}) → ${m.toName} (${m.toLabel}) · ${Math.abs(m.semis)} semi${Math.abs(m.semis) === 1 ? '' : 's'} ${m.semis > 0 ? '↑' : '↓'}`,
      })))] : []),
    el('div', { class: 'mer-toolbar' }, [
      el('button', { type: 'button', class: 'mer-play-btn', text: '▶ Hear the move', onclick: () => playMove(ctx, from, to) }),
      el('button', { type: 'button', text: `Walk to ${d.symbol} →`, onclick: walk }),
      el('button', {
        type: 'button', text: '📌 Pin without walking',
        onclick: () => { state.sketch.push({ symbol: d.symbol, why: d.reason }); rerender(); },
      }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '×', title: 'Close', onclick: () => { state.mapDetail = null; rerender(); } }),
    ]),
  ]);
  area.append(panel);
}

function renderMap(area, ctx, rerender) {
  area.append(chordPicker(ctx, rerender));

  // Key context + adventurous controls.
  const keySelect = el('select', { onchange: (e) => { state.mapKeyRoot = e.target.value; rerender(); } },
    ROOTS.map((r) => el('option', { value: r, text: r, selected: r === state.mapKeyRoot })));
  const modeSelect = el('select', { onchange: (e) => { state.mapKeyMode = e.target.value; rerender(); } }, [
    el('option', { value: 'none', text: 'no key context', selected: state.mapKeyMode === 'none' }),
    el('option', { value: 'major', text: 'major', selected: state.mapKeyMode === 'major' }),
    el('option', { value: 'minor', text: 'minor', selected: state.mapKeyMode === 'minor' }),
  ]);
  const advBtn = el('button', {
    type: 'button', class: state.mapAdventurous ? 'is-active' : '', text: '🧪 Adventurous',
    title: 'Reveal color moves: chromatic mediants, modal borrowings, negative harmony',
    onclick: () => { state.mapAdventurous = !state.mapAdventurous; rerender(); },
  });
  const viewToggle = el('div', { class: 'mer-toggle-group' }, [
    el('button', { type: 'button', class: state.mapView === 'walk' ? 'is-active' : '', text: '🧭 Walk', onclick: () => { state.mapView = 'walk'; rerender(); } }),
    el('button', { type: 'button', class: state.mapView === 'atlas' ? 'is-active' : '', text: '🗺️ Atlas', onclick: () => { state.mapView = 'atlas'; state.mapDetail = null; rerender(); } }),
  ]);
  area.append(el('div', { class: 'mer-toolbar' }, [
    viewToggle,
    el('span', { class: 'mer-muted', text: 'Analyze in:' }), modeSelect,
    ...(state.mapKeyMode !== 'none' ? [keySelect] : []),
    ...(state.mapView === 'walk' ? [el('div', { class: 'mer-toggle-group' }, [advBtn])] : []),
  ]));

  const chord = currentChord();
  if (!chord) return;
  const keyCtx = mapKeyCtx();

  if (state.mapView === 'atlas') {
    renderAtlas(area, ctx, rerender, chord, keyCtx);
  } else {
    const groups = harmonyEdges(chord, { adventurous: state.mapAdventurous, keyCtx });

    area.append(el('p', { class: 'mer-muted mer-hg-hint', text: 'Click any chord to see WHY the move sounds the way it does — then walk there and keep going. Fill color = kind of chord; line color = its relationship to the center; thick lines are strong pulls; dashed lines are stand-ins, not motion.' }));
    area.append(graphSVG(chord, groups, keyCtx, (edge, g) => {
      state.mapDetail = { symbol: edge.symbol, reason: edge.reason, catLabel: g.label, dir: g.dir };
      rerender();
    }));
    area.append(qualityLegend());

    if (state.mapDetail) mapDetailPanel(area, ctx, chord, rerender);

    // Diatonic quick-jumps when a key context is set.
    if (keyCtx) {
      area.append(el('div', { class: 'mer-subsection-label', text: `Diatonic in ${state.mapKeyRoot} ${state.mapKeyMode}` }));
      area.append(el('div', { class: 'mer-place-meta' }, diatonicChords(keyCtx.root, keyCtx.mode).map((row) =>
        el('button', {
          type: 'button', class: 'mer-map-chip', text: `${row.numeral} · ${row.chord.symbol}`,
          onclick: () => { setChordFromSymbol(row.chord.symbol); state.mapDetail = null; rerender(); },
        }))));
    }
  }

  // Trail: the walked path — playable with voice-led motion, savable.
  area.append(el('div', { class: 'mer-subsection-label', text: 'Trail' }));
  if (state.sketch.length) {
    area.append(el('div', { class: 'mer-place-meta mer-hg-trail' }, state.sketch.flatMap((s, i) => [
      ...(i ? [el('span', { class: 'mer-muted', text: '→' })] : []),
      el('span', { class: 'mer-chip', title: s.why }, [
        document.createTextNode(s.symbol + ' '),
        el('button', { type: 'button', class: 'mer-icon-btn', text: '×', onclick: () => { state.sketch.splice(i, 1); rerender(); } }),
      ]),
    ])));
    const nameInput = el('input', { type: 'text', placeholder: 'Trail name', value: state.sketchName, onchange: (e) => { state.sketchName = e.target.value; } });
    area.append(el('div', { class: 'mer-person-form' }, [
      el('button', {
        type: 'button', class: 'mer-play-btn', text: '▶ Hear the trail',
        title: 'Plays the walked path with minimal-motion voice leading',
        onclick: () => playTrail(ctx, state.sketch.map((s) => s.symbol)),
      }),
      nameInput,
      el('button', {
        type: 'button', text: 'Save trail',
        onclick: async () => {
          if (!nameInput.value.trim()) return;
          await ctx.data.ChordProgressions.create({ name: nameInput.value.trim(), chords: state.sketch });
          state.sketch = [];
          state.sketchName = '';
          rerender();
        },
      }),
      el('button', { type: 'button', text: 'Clear', onclick: () => { state.sketch = []; rerender(); } }),
    ]));
  } else {
    area.append(el('p', { class: 'mer-muted', text: 'Walk the graph (or pin from the detail panel) and your path collects here — then hear it back with smooth voice leading, or save it.' }));
  }

  ctx.data.ChordProgressions.list().then((saved) => {
    if (!saved.length) return;
    const list = el('div', { class: 'mer-people-list' });
    for (const sk of saved) {
      list.append(el('div', { class: 'mer-person-card' }, [
        el('div', { class: 'mer-person-info' }, [
          el('div', { class: 'mer-person-name', text: sk.name }),
          el('div', { class: 'mer-person-meta', text: (sk.chords || []).map((c) => c.symbol).join(' → ') }),
        ]),
        el('button', { type: 'button', class: 'mer-play-btn', text: '▶', title: 'Hear it (voice-led)', onclick: () => playTrail(ctx, (sk.chords || []).map((c) => c.symbol)) }),
        el('button', { type: 'button', text: 'Load', onclick: () => { state.sketch = [...(sk.chords || [])]; rerender(); } }),
        el('button', { type: 'button', class: 'mer-icon-btn', text: '×', onclick: async () => { await ctx.data.ChordProgressions.remove(sk.id); rerender(); } }),
      ]));
    }
    area.append(el('div', { class: 'mer-subsection-label', text: 'Saved trails' }), list);
  });
}

// --- Lessons tab ---

function renderLessons(area, ctx, rerender) {
  const byTopic = new Map();
  THEORY_LESSONS.forEach((lesson, i) => {
    if (!byTopic.has(lesson.topic)) byTopic.set(lesson.topic, []);
    byTopic.get(lesson.topic).push({ ...lesson, idx: i });
  });

  for (const [topic, lessons] of byTopic) {
    area.append(el('div', { class: 'mer-group-label', text: topic }));
    for (const lesson of lessons) {
      const row = el('div', { class: 'mer-task-row' }, [el('span', { class: 'mer-task-title', text: lesson.title })]);
      row.addEventListener('click', () => {
        state.selectedLesson = state.selectedLesson === lesson.idx ? null : lesson.idx;
        rerender();
      });
      area.append(row);
      if (state.selectedLesson === lesson.idx) {
        area.append(el('div', { class: 'mer-task-detail' }, [
          ...lesson.body.split('\n\n').map((p) => el('p', { text: p })),
          el('div', { class: 'mer-subsection-label', text: 'Explore these in the Dictionary' }),
          el('div', { class: 'mer-place-meta' }, lesson.examples.map((sym) => el('button', {
            type: 'button', class: 'mer-map-chip', text: sym,
            onclick: () => { if (setChordFromSymbol(sym)) { state.tab = 'dictionary'; rerender(); } },
          }))),
        ]));
      }
    }
  }
}

// --- Sound tab ---

const SYNTH_SKINS = [
  { id: 'meridian', label: 'Meridian' },
  { id: 'rhodes', label: 'Rhodes' },
  { id: 'wurlitzer', label: 'Wurlitzer' },
  { id: 'nord', label: 'Nord' },
  { id: 'moog', label: 'Moog' },
];
const CONTROL_STYLES = [
  { id: 'auto', label: 'Auto' },
  { id: 'knob', label: 'Knobs' },
  { id: 'slider', label: 'Sliders' },
];

function resolveControlStyle(setting) {
  if (setting === 'knob' || setting === 'slider') return setting;
  // Auto: knobs where there's a precise pointer (mouse/trackpad), detented
  // sliders on touch (phone/tablet) where a knob-drag is fiddlier.
  return window.matchMedia && window.matchMedia('(pointer: fine)').matches ? 'knob' : 'slider';
}

// One numeric synth parameter as either a knob or a detented, numerically
// labeled slider. Both drive the same setParam callback.
function paramControl(def, value, style, defaultValue, setParam) {
  if (style === 'knob') {
    return makeKnob({
      min: def.min, max: def.max, step: def.step, value, defaultValue,
      label: def.label, onInput: (v) => setParam(v),
    });
  }
  // Slider: coarsen to a "nice" detent step so it clicks through distinct
  // stops instead of feeling glassy-smooth; native range already commits
  // discrete values and never drifts after release.
  const step = niceStep(def.max - def.min, def.step);
  const readout = el('span', { class: 'mer-slider-readout' });
  const decimals = String(step).includes('.') ? String(step).split('.')[1].length : 0;
  const show = (v) => { readout.textContent = decimals ? Number(v).toFixed(decimals) : String(v); };
  show(value);
  const input = el('input', {
    type: 'range', class: 'mer-detent-slider', min: def.min, max: def.max, step, value,
    oninput: (e) => { const v = Number(e.target.value); show(v); setParam(v); },
    ondblclick: (e) => { if (defaultValue != null) { e.target.value = defaultValue; show(defaultValue); setParam(defaultValue); } },
  });
  return el('div', { class: 'mer-slider-field' }, [
    el('div', { class: 'mer-slider-top' }, [el('span', { class: 'mer-knob-label', text: def.label }), readout]),
    input,
    el('div', { class: 'mer-slider-ends' }, [
      el('span', { text: String(def.min) }), el('span', { text: String(def.max) }),
    ]),
  ]);
}

async function renderSound(area, ctx, rerender) {
  const params = await getSynthParams(ctx);
  const customPresets = (await ctx.data.Settings.get('synthPresets')) || [];
  const skin = await ctx.data.Settings.get('synthPanelSkin');
  const controlSetting = await ctx.data.Settings.get('synthControlStyle');
  const style = resolveControlStyle(controlSetting);

  const panel = el('div', { class: `mer-synth-panel skin-${skin}` });
  area.append(panel);

  // Look (skin) + Controls (knob/slider) selectors.
  const skinSelect = el('select', {
    onchange: async (e) => { await ctx.data.Settings.set('synthPanelSkin', e.target.value); rerender(); },
  }, SYNTH_SKINS.map((s) => el('option', { value: s.id, text: s.label, selected: s.id === skin })));
  const styleSelect = el('select', {
    onchange: async (e) => { await ctx.data.Settings.set('synthControlStyle', e.target.value); rerender(); },
  }, CONTROL_STYLES.map((s) => el('option', { value: s.id, text: s.label, selected: s.id === controlSetting })));
  panel.append(el('div', { class: 'mer-synth-bar' }, [
    el('label', { class: 'mer-setting' }, [el('span', { text: 'Look' }), skinSelect]),
    el('label', { class: 'mer-setting' }, [el('span', { text: 'Controls' }), styleSelect]),
  ]));

  const applyPreset = async (name, preset) => {
    state.synthParams = { ...preset, name };
    state.synthPresetName = name;
    await ctx.data.Settings.set('synthParams', state.synthParams);
    rerender();
  };

  const presetRow = el('div', { class: 'mer-toggle-group' }, [
    ...Object.values(FACTORY_PRESETS).map((p) => el('button', {
      type: 'button', class: state.synthPresetName === p.name ? 'is-active' : '', text: p.name,
      onclick: () => applyPreset(p.name, p),
    })),
    ...customPresets.map((p) => el('button', {
      type: 'button', class: state.synthPresetName === p.name ? 'is-active' : '', text: `★ ${p.name}`,
      onclick: () => applyPreset(p.name, p.params),
    })),
  ]);
  panel.append(el('div', { class: 'mer-subsection-label', text: 'Presets' }), presetRow);

  const testChord = buildChord(parseNote('C'), 'maj9');
  const testNotes = voicingsFor(testChord).find((v) => v.id === 'rootlessA')?.notes || [];
  panel.append(el('p', {}, [el('button', {
    type: 'button', class: 'mer-play-btn', text: '▶ Test sound (Cmaj9)',
    onclick: () => play(ctx, testNotes.map((n) => n.midi)),
  })]));

  panel.append(el('div', { class: 'mer-subsection-label', text: 'Shape your own' }));
  // Double-click a control to snap it back toward the Piano baseline.
  const grid = el('div', { class: style === 'knob' ? 'mer-knob-grid' : 'mer-field-grid' });
  for (const def of PARAM_DEFS) {
    const setParam = async (v) => {
      state.synthParams[def.key] = v;
      state.synthPresetName = 'Custom';
      await ctx.data.Settings.set('synthParams', state.synthParams);
    };
    if (def.type === 'select') {
      const input = el('select', {
        onchange: (e) => setParam(e.target.value),
      }, def.options.map((o) => el('option', { value: o, text: o, selected: o === params[def.key] })));
      grid.append(el('label', { class: 'mer-field' }, [el('span', { text: def.label }), input]));
    } else {
      grid.append(paramControl(def, params[def.key], style, FACTORY_PRESETS.piano[def.key], setParam));
    }
  }
  panel.append(grid);

  const nameInput = el('input', { type: 'text', placeholder: 'Preset name' });
  panel.append(el('div', { class: 'mer-person-form' }, [
    nameInput,
    el('button', {
      type: 'button', text: 'Save as preset',
      onclick: async () => {
        if (!nameInput.value.trim()) return;
        const next = [...customPresets.filter((p) => p.name !== nameInput.value.trim()),
          { name: nameInput.value.trim(), params: { ...state.synthParams } }];
        await ctx.data.Settings.set('synthPresets', next);
        state.synthPresetName = nameInput.value.trim();
        rerender();
      },
    }),
    ...(customPresets.length ? [el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete custom presets',
      onclick: async () => {
        if (!confirm('Remove all custom synth presets?')) return;
        await ctx.data.Settings.set('synthPresets', []);
        rerender();
      },
    })] : []),
  ]));
}

// --- Practice tab: adaptive drills with spaced repetition ---

function computeDrillStreak(dates) {
  const days = new Set(dates);
  let streak = 0;
  const cursor = new Date(todayStr() + 'T00:00:00');
  if (!days.has(cursor.toISOString().slice(0, 10))) cursor.setDate(cursor.getDate() - 1);
  while (days.has(cursor.toISOString().slice(0, 10))) {
    streak++;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

async function loadSkillsById(ctx) {
  const all = await ctx.data.ChordSkills.list();
  return Object.fromEntries(all.map((s) => [s.id, s]));
}

function todaysSessionIds(skillsById) {
  // A brand-new user gets a fuller introductory session; after that, new
  // concepts trickle in 3 per day alongside reviews.
  const newLimit = Object.keys(skillsById).length ? 3 : 8;
  return buildSession(skillsById, { size: 14, today: todayStr(), newLimit });
}

// The printable sheet: prompts up top (with space to work), answer key at the
// bottom. Printed via the browser's own print-to-PDF — @media print CSS hides
// the app and shows only this.
function printPracticeSheet(sessionIds) {
  const questions = sessionIds.map((cid) => makeQuestion(cid)).filter(Boolean);
  const kindLabel = { spell: 'Spell these chords', name: 'Name these chords', voicing: 'Identify these voicings' };
  const byKind = new Map();
  questions.forEach((q, i) => {
    if (!byKind.has(q.kind)) byKind.set(q.kind, []);
    byKind.get(q.kind).push({ ...q, n: i + 1 });
  });

  const promptText = (q) => {
    if (q.kind === 'spell') return q.prompt;
    if (q.kind === 'name') return `${q.noteNames.join(' – ')}`;
    // voicing: notes low→high, labels withheld (they'd give it away)
    return `${q.prompt.replace('Which voicing of ', '').replace(' is this?', '')}, played as ${q.preNotes.map((n) => n.name).join(' – ')} (low to high)`;
  };

  const sheet = el('div', { class: 'mer-print-sheet' }, [
    el('h1', { text: `Chord practice — ${fmtDate(todayStr())}` }),
    ...[...byKind.entries()].flatMap(([kind, qs]) => [
      el('h2', { text: kindLabel[kind] }),
      el('ol', {}, qs.map((q) => el('li', {}, [
        el('span', { text: promptText(q) }),
        el('span', { class: 'mer-print-blank' }),
      ]))),
    ]),
    el('h2', { class: 'mer-print-key-title', text: 'Answer key' }),
    el('ol', { class: 'mer-print-key' }, questions.map((q) => el('li', { text: q.answer }))),
  ]);
  document.body.append(sheet);
  const cleanup = () => sheet.remove();
  window.addEventListener('afterprint', cleanup, { once: true });
  window.print();
  setTimeout(cleanup, 2000); // fallback if afterprint never fires
}

function renderDrillCard(area, ctx, rerender) {
  const drill = state.drill;
  const q = drill.questions[drill.index];

  if (!q) {
    area.append(el('div', { class: 'mer-task-detail' }, [
      el('h2', { text: 'Session complete!' }),
      el('p', {}, [el('strong', { text: `${drill.goodCount} of ${drill.questions.length} solid.` })]),
      el('p', { class: 'mer-muted', text: 'Misses come back tomorrow; solid answers wait longer. Come back daily and the routine reshapes itself around what needs work.' }),
      el('button', { type: 'button', text: '← Back to practice', onclick: () => { state.drill = null; rerender(); } }),
    ]));
    return;
  }

  const grade = async (g) => {
    const existing = await ctx.data.ChordSkills.get(q.conceptId);
    const next = gradeSkill(existing || {}, g, todayStr());
    if (existing) await ctx.data.ChordSkills.update(q.conceptId, next);
    else await ctx.data.ChordSkills.create({ id: q.conceptId, ...next });
    await ctx.data.ChordDrillLogs.create({ conceptId: q.conceptId, date: todayStr(), grade: g });
    if (g !== 'again') drill.goodCount++;
    drill.index++;
    drill.showAnswer = false;
    rerender();
  };

  const card = el('div', { class: 'mer-task-detail' }, [
    el('p', { class: 'mer-muted', text: `Question ${drill.index + 1} of ${drill.questions.length} · ${conceptById(q.conceptId)?.label || ''}` }),
    el('h2', { text: q.prompt }),
    q.noteNames ? el('div', { class: 'mer-place-meta' }, q.noteNames.map((n) => el('span', { class: 'mer-chip', text: n }))) : null,
    q.preNotes ? diagramFor(q.preNotes) : null,
    el('p', {}, [el('button', { type: 'button', class: 'mer-play-btn', text: '▶ Hear it', onclick: () => play(ctx, q.play) })]),
    state.drill.showAnswer
      ? el('div', {}, [
        el('p', {}, [el('strong', { text: q.answer }), el('span', { class: 'mer-muted', text: q.answerDetail ? `  ${q.answerDetail}` : '' })]),
        q.postNotes ? diagramFor(q.postNotes) : null,
        el('p', { class: 'mer-muted', text: 'How did you do? (Be honest — the routine adapts to this.)' }),
        el('div', { class: 'mer-toggle-group' }, [
          el('button', { type: 'button', text: 'Missed it', onclick: () => grade('again') }),
          el('button', { type: 'button', text: 'Got it', onclick: () => grade('good') }),
          el('button', { type: 'button', text: 'Instant', onclick: () => grade('easy') }),
        ]),
      ])
      : el('button', { type: 'button', text: 'Show answer', onclick: () => { state.drill.showAnswer = true; rerender(); } }),
  ]);
  area.append(card);
}

async function renderPractice(area, ctx, rerender) {
  if (state.drill) { renderDrillCard(area, ctx, rerender); return; }

  const [skillsById, logs] = await Promise.all([
    loadSkillsById(ctx),
    ctx.data.ChordDrillLogs.list(),
  ]);
  const summary = skillSummary(skillsById);
  const streak = computeDrillStreak(logs.map((l) => l.date));
  const sessionIds = todaysSessionIds(skillsById);

  if (summary.totals.attempts) {
    area.append(el('p', {}, [el('strong', { text: `🔥 ${streak}-day practice streak · ${summary.totals.attempts} drills · ${Math.round((summary.totals.correct / summary.totals.attempts) * 100)}% overall` })]));
  } else {
    area.append(el('p', { class: 'mer-muted', text: 'Quick self-graded drills — spell chords, recognize them by ear and eye, identify voicings. The app tracks what you miss and rebuilds tomorrow\'s routine around it, spaced-repetition style. First session introduces the basics.' }));
  }

  area.append(el('div', { class: 'mer-subsection-label', text: `Today's routine (${sessionIds.length})` }));
  area.append(el('div', { class: 'mer-place-meta' }, sessionIds.map((cid) =>
    el('span', { class: 'mer-chip', text: conceptById(cid)?.label || cid }))));
  area.append(el('div', { class: 'mer-toolbar' }, [
    el('button', {
      type: 'button', class: 'mer-play-btn', text: `▶ Start session (${sessionIds.length})`,
      onclick: () => {
        state.drill = { questions: sessionIds.map((cid) => makeQuestion(cid)), index: 0, showAnswer: false, goodCount: 0 };
        rerender();
      },
    }),
    el('button', { type: 'button', text: '🖨️ Print practice sheet', title: 'Print or save as PDF via your browser', onclick: () => printPracticeSheet(sessionIds) }),
  ]));

  if (summary.weak.length) {
    area.append(el('div', { class: 'mer-subsection-label', text: 'Needs work' }));
    area.append(el('div', { class: 'mer-place-meta' }, summary.weak.map((w) =>
      el('span', { class: 'mer-chip is-overdue', text: `${w.label} · ${Math.round(w.acc * 100)}%` }))));
  }
  if (summary.strong.length) {
    area.append(el('div', { class: 'mer-subsection-label', text: 'Solid' }));
    area.append(el('div', { class: 'mer-place-meta' }, summary.strong.map((s) =>
      el('span', { class: 'mer-chip', text: `${s.label} · ${Math.round(s.acc * 100)}%` }))));
  }
}

// --- Root ---

const TABS = [
  ['dictionary', 'Dictionary'],
  ['barry', 'Barry Harris'],
  ['calculator', 'Calculator'],
  ['map', 'Harmony Map'],
  ['lessons', 'Lessons'],
  ['practice', 'Practice'],
  ['sound', 'Sound'],
];

export async function renderChords(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Chords' }));
  canvas.append(el('div', { class: 'mer-toolbar' }, [
    el('div', { class: 'mer-toggle-group' }, TABS.map(([id, label]) => el('button', {
      type: 'button', class: state.tab === id ? 'is-active' : '', text: label,
      onclick: () => { state.tab = id; rerender(); },
    }))),
  ]));

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  if (state.tab === 'dictionary') renderDictionary(area, ctx, rerender);
  else if (state.tab === 'barry') renderBarry(area, ctx, rerender);
  else if (state.tab === 'calculator') renderCalculator(area, ctx, rerender);
  else if (state.tab === 'map') renderMap(area, ctx, rerender);
  else if (state.tab === 'lessons') renderLessons(area, ctx, rerender);
  else if (state.tab === 'practice') await renderPractice(area, ctx, rerender);
  else await renderSound(area, ctx, rerender);
}
