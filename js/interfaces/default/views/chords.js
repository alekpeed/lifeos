// Harmony study module: chord dictionary with jazz voicing families,
// Barry Harris 6th-diminished analysis, a key calculator, an explorable
// harmony map with pinnable sketches, theory lessons, and an adjustable
// synth. Study tool by design — diagrams, theory, and sound; no
// sequencing, tempo, or play-along.

import { el } from '../dom.js';
import { parseNote, noteName } from '../../../theory/notes.js';
import { QUALITIES, buildChord, parseChord, getQuality } from '../../../theory/chords.js';
import { voicingsFor, rootShellPretty, guitarShape } from '../../../theory/voicings.js';
import { diatonicChords, secondaryDominants, tritoneSub, borrowedChords, keysContaining, relatedChords } from '../../../theory/harmony.js';
import { barryAnalysis } from '../../../theory/barry.js';
import { THEORY_LESSONS } from '../../../theory/lessons.js';
import { playChord, FACTORY_PRESETS, PARAM_DEFS } from '../../../audio/synth.js';

const ROOTS = ['C', 'D♭', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B'];

let state = {
  tab: 'dictionary', // dictionary | barry | calculator | map | lessons | sound
  rootName: 'C',
  qualityId: 'maj7',
  instrument: 'piano', // piano | guitar
  calcKeyRoot: 'C',
  calcKeyMode: 'major',
  selectedLesson: null,
  sketch: [], // [{ symbol, why }]
  sketchName: '',
  synthParams: null, // loaded from settings on first render
  synthPresetName: 'Piano',
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

// --- Harmony Map tab ---

function renderMap(area, ctx, rerender) {
  area.append(chordPicker(ctx, rerender));
  const chord = currentChord();
  if (!chord) return;

  const center = el('div', { class: 'mer-map-center' }, [
    el('h2', { text: chord.symbol }),
    playBtn(ctx, (voicingsFor(chord).find((v) => v.id === 'close') || { notes: chord.tones.map((t, i) => ({ midi: 48 + chord.root.pc + t.semitones })) }).notes, '▶ hear'),
    el('button', { type: 'button', text: '📌 Pin', onclick: () => { state.sketch.push({ symbol: chord.symbol, why: 'pinned' }); rerender(); } }),
  ]);
  area.append(center);

  for (const group of relatedChords(chord)) {
    area.append(el('div', { class: 'mer-subsection-label', text: group.label }));
    const row = el('div', { class: 'mer-map-group' });
    for (const item of group.items) {
      row.append(el('div', { class: 'mer-map-item' }, [
        el('button', {
          type: 'button', class: 'mer-map-chip', text: item.symbol,
          onclick: () => { setChordFromSymbol(item.symbol); rerender(); },
        }),
        el('button', {
          type: 'button', class: 'mer-icon-btn', text: '📌', title: 'Pin to sketch',
          onclick: () => { state.sketch.push({ symbol: item.symbol, why: item.why }); rerender(); },
        }),
        el('span', { class: 'mer-muted mer-map-why', text: item.why }),
      ]));
    }
    area.append(row);
  }

  // Sketch: a saved trail of ideas — names and reasons, no tempo, no playback transport.
  area.append(el('div', { class: 'mer-subsection-label', text: 'Sketch' }));
  if (state.sketch.length) {
    area.append(el('div', { class: 'mer-place-meta' }, state.sketch.map((s, i) =>
      el('span', { class: 'mer-chip' }, [
        document.createTextNode(s.symbol + ' '),
        el('button', { type: 'button', class: 'mer-icon-btn', text: '×', onclick: () => { state.sketch.splice(i, 1); rerender(); } }),
      ]))));
    const nameInput = el('input', { type: 'text', placeholder: 'Sketch name', value: state.sketchName, onchange: (e) => { state.sketchName = e.target.value; } });
    area.append(el('div', { class: 'mer-person-form' }, [
      nameInput,
      el('button', {
        type: 'button', text: 'Save sketch',
        onclick: async () => {
          if (!nameInput.value.trim()) return;
          await ctx.data.ChordProgressions.create({ name: nameInput.value.trim(), chords: state.sketch });
          state.sketch = [];
          state.sketchName = '';
          rerender();
        },
      }),
    ]));
  } else {
    area.append(el('p', { class: 'mer-muted', text: 'Pin chords while you explore to hold onto an idea.' }));
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
        el('button', { type: 'button', text: 'Load', onclick: () => { state.sketch = [...(sk.chords || [])]; rerender(); } }),
        el('button', { type: 'button', class: 'mer-icon-btn', text: '×', onclick: async () => { await ctx.data.ChordProgressions.remove(sk.id); rerender(); } }),
      ]));
    }
    area.append(el('div', { class: 'mer-subsection-label', text: 'Saved sketches' }), list);
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

async function renderSound(area, ctx, rerender) {
  const params = await getSynthParams(ctx);
  const customPresets = (await ctx.data.Settings.get('synthPresets')) || [];

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
  area.append(el('div', { class: 'mer-subsection-label', text: 'Presets' }), presetRow);

  const testChord = buildChord(parseNote('C'), 'maj9');
  const testNotes = voicingsFor(testChord).find((v) => v.id === 'rootlessA')?.notes || [];
  area.append(el('p', {}, [el('button', {
    type: 'button', class: 'mer-play-btn', text: '▶ Test sound (Cmaj9)',
    onclick: () => play(ctx, testNotes.map((n) => n.midi)),
  })]));

  area.append(el('div', { class: 'mer-subsection-label', text: 'Shape your own' }));
  const grid = el('div', { class: 'mer-field-grid' });
  for (const def of PARAM_DEFS) {
    let input;
    if (def.type === 'select') {
      input = el('select', {
        onchange: async (e) => {
          state.synthParams[def.key] = e.target.value;
          state.synthPresetName = 'Custom';
          await ctx.data.Settings.set('synthParams', state.synthParams);
        },
      }, def.options.map((o) => el('option', { value: o, text: o, selected: o === params[def.key] })));
    } else {
      input = el('input', {
        type: 'range', min: def.min, max: def.max, step: def.step, value: params[def.key],
        oninput: async (e) => {
          state.synthParams[def.key] = Number(e.target.value);
          state.synthPresetName = 'Custom';
          await ctx.data.Settings.set('synthParams', state.synthParams);
        },
      });
    }
    grid.append(el('label', { class: 'mer-field' }, [el('span', { text: def.label }), input]));
  }
  area.append(grid);

  const nameInput = el('input', { type: 'text', placeholder: 'Preset name' });
  area.append(el('div', { class: 'mer-person-form' }, [
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

// --- Root ---

const TABS = [
  ['dictionary', 'Dictionary'],
  ['barry', 'Barry Harris'],
  ['calculator', 'Calculator'],
  ['map', 'Harmony Map'],
  ['lessons', 'Lessons'],
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
  else await renderSound(area, ctx, rerender);
}
