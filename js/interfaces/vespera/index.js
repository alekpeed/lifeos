// "Vespera" — the spatial interface. Life OS as an orbital station you
// navigate, not a dashboard (see VESPERA_SPEC.md). Navigation shape:
//
//   Hub (Grand Concourse) → District → Space (a module) → Content
//
// Hash routing underneath is unchanged: a Space is just a route (#/tasks)
// with a spatial skin and a travel animation instead of an instant cut.
// The hub replaces #/dashboard as home; districts are interface-internal
// state (no hash change until you actually enter a Space). Module content
// comes from the shared view library — the same canonical views Equator
// renders, hosted inside station chrome (see view-library.js for why
// that's sanctioned).
//
// Room art: the hub expects its generated concourse image at
// js/interfaces/vespera/img/hub.png. Until that file exists the CSS paints
// a pure-gradient starfield fallback, so the interface works (and every
// door still functions) with no image at all — the art is atmosphere,
// never load-bearing.

import { registerInterface } from '../registry.js';
import { VIEWS } from '../view-library.js';

// Local minimal element helper. Deliberately not imported from Equator's
// dom.js: chrome-level code stays self-contained per the registry rule;
// only the view library is shared.
function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value == null) continue;
    if (key === 'text') node.textContent = value;
    else if (key === 'class') node.className = value;
    else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value);
  }
  for (const child of [].concat(children)) {
    if (child) node.append(child);
  }
  return node;
}

// The eight districts, plus Station News (center floor). Taglines describe
// what the doors actually open -- where the image's painted label (if any)
// drifts from built reality (e.g. "Japanese" before the Languages module
// generalized, "ChatGPT, Gemini" before they exist), the code label tells
// the truth.
//
// hotspot.clip is a clip-path polygon (percentages relative to that
// district's own bounding box, not the full image) tracing the actual
// walk-through opening of each destination -- its sign rail and surrounding
// wall are deliberately excluded. These measurements are tied to
// img/hub.png: replacing the art means remeasuring every plaque.
const DISTRICTS = [
  { id: 'ops', name: 'Operations Deck', tagline: 'Tasks & Projects', icon: '📋', modules: ['tasks', 'ideas', 'habits', 'museum', 'skilltree', 'health'],
    hotspot: { cx: 13.61, cy: 16.9, w: 27.21, h: 12.54, clip: 'polygon(3.52% 0%, 89.89% 19.83%, 98.9% 42.15%, 100% 80.99%, 92.53% 100%, 6.15% 80.17%, 0% 64.46%, 0% 15.7%)' } },
  { id: 'navbay', name: 'Navigation Bay', tagline: 'Places & Maps', icon: '🧭', modules: ['places', 'packing'],
    hotspot: { cx: 14.83, cy: 37.94, w: 25.84, h: 14.03, clip: 'polygon(0.69% 0%, 91.2% 0%, 98.84% 21.97%, 100% 78.03%, 90.28% 100%, 9.49% 100%, 0% 81.82%)' } },
  { id: 'archive', name: 'The Archive', tagline: 'Links, Books & Education', icon: '📚', modules: ['links', 'books', 'education', 'knowledge', 'rabbitholes', 'collections'],
    hotspot: { cx: 13.67, cy: 60.63, w: 27.33, h: 13.07, clip: 'polygon(3.94% 0%, 94.97% 0%, 100% 17.07%, 100% 81.3%, 94.09% 100%, 6.13% 100%, 0% 79.67%, 0% 18.7%)' } },
  { id: 'ledger', name: 'The Ledger', tagline: 'Bills, Finance & Documents', icon: '🧾', modules: ['finance', 'documents', 'quartermaster'],
    hotspot: { cx: 13.94, cy: 81.67, w: 27.87, h: 13.07, clip: 'polygon(5.15% 0%, 92.7% 0%, 100% 18.7%, 99.36% 80.49%, 92.7% 100%, 6.01% 100%, 0% 82.93%, 0% 18.7%)' } },
  { id: 'quarters', name: 'Personal Quarters', tagline: 'Contacts, Milestones & Recipes', icon: '👤', modules: ['contacts', 'milestones', 'recipes', 'photos', 'sharebox', 'timecapsules', 'starters', 'dreamjournal'],
    hotspot: { cx: 86.16, cy: 16.79, w: 27.33, h: 10.41, clip: 'polygon(2.19% 21.4%, 95.6% 0%, 100% 15.3%, 100% 77.55%, 94.08% 100%, 3.29% 100%, 0% 82.65%, 0% 41.84%)' } },
  { id: 'conservatory', name: 'The Conservatory', tagline: 'Languages & Music', icon: '🎵', modules: ['languages', 'chords', 'lifeasmusic'],
    hotspot: { cx: 85.74, cy: 38, w: 28.53, h: 11.9, clip: 'polygon(2.93% 17.86%, 92.27% 0%, 100% 12.5%, 100% 77.68%, 93.73% 100%, 7.31% 100%, 0% 78.57%, 0% 34.82%)' },
    // Immersive entry room (see renderRoom). `image` is a plain
    // establishing shot (no text, no markers) rendered on an aspect-locked
    // stage (same technique as the hub). `quad` is the wall plane the
    // signage is projected onto: four corners (TL,TR,BR,BL as % of the
    // image), measured -- not eyeballed -- from a GPT-placed 4-marker
    // reference image (Alek iterated the marker placement twice; these are
    // the corners that used the most of the wall while staying clear of
    // the floor trim and the architecture on the right, verified by pixel-
    // detecting the actual marker centers before the markers were inpainted
    // out for the shipped image). Real DOM text (title/subtitle/links,
    // rendered in renderRoom) is mapped onto this quad via a 4-point
    // perspective homography (matrix3d) -- this superseded a prior CSS
    // attempt that used an eyeballed quad and looked "not glued to the
    // wall"; the theory is that was a bad-data problem, not a bad-math one,
    // since a true homography against accurate corners has no freedom to
    // look wrong. Verify that theory with a screenshot before trusting it
    // twice.
    room: {
      image: 'img/conservatory.png',
      ratio: '1672 / 941',
      designW: 460, designH: 348,
      quad: [[4.52, 20.72], [32.18, 29.49], [33.43, 62.11], [4.25, 64.45]],
    } },
  { id: 'core', name: 'Systems Core', tagline: 'Tools & Settings', icon: '🛠️', modules: ['tools', 'settings', 'search', 'qrsync', 'timemachine', 'entropy', 'almanac', 'themefromphoto'],
    hotspot: { cx: 85.55, cy: 60.89, w: 27.93, h: 12.11, clip: 'polygon(3.43% 0%, 100% 0%, 100% 78.95%, 93.15% 100%, 3.21% 100%, 0% 79.82%, 0% 24.56%)' } },
  { id: 'relay', name: 'AI Relay', tagline: 'AI Assistant — Claude', icon: '🤖', modules: ['assistant', 'stationcat'],
    hotspot: { cx: 85.55, cy: 81.51, w: 27.93, h: 13.39, clip: 'polygon(3.21% 0%, 100% 0%, 100% 80.95%, 93.15% 100%, 3.21% 100%, 0% 81.75%, 0% 24.6%)' } },
  // Added for Dashboard's due-soon feed to have a hub-level home. This
  // art does paint a real Station News sign bottom-center, same as the
  // other 8 -- measured the same way, not a special case.
  { id: 'news', name: 'Station News', tagline: 'The Daily Paper', icon: '📰', modules: ['paper', 'orrery', 'ghostdays'],
    hotspot: { cx: 50, cy: 91.07, w: 32.18, h: 14.24, clip: 'polygon(12.64% 0%, 86.8% 0%, 100% 64.18%, 87.18% 99.25%, 13.75% 99.25%, 0% 64.18%)' } },
];

let ctx = null;
let els = null; // { root }
let unsubscribe = null;
let currentRoute = null;
let renderPending = false;
let renderChain = Promise.resolve();
// Interface-internal navigation layer: which district's doors are open on
// the hub. Not a route — entering an actual Space is what changes the hash.
let state = { district: null };

function reducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function moduleLabel(id) {
  return ctx.modules.find((m) => m.id === id)?.label || id;
}

// Short room-link descriptors. Only needed for modules that appear as
// links inside an immersive room; anything without an entry falls back to
// the module's own label so a new room never renders a blank sub-line.
const MODULE_TAGLINES = {
  languages: 'Study packs & drills',
  chords: 'Harmony & voicings',
  lifeasmusic: 'Your life, played back',
};
function moduleTagline(id) {
  return MODULE_TAGLINES[id] || moduleLabel(id);
}

function districtOf(moduleId) {
  return DISTRICTS.find((d) => d.modules.includes(moduleId)) || null;
}

// Perspective homography: the matrix3d that maps a flat wDesign x hDesign
// box onto an arbitrary destination quadrilateral (TL,TR,BR,BL in px).
// Standard 4-point projective solve (8x8 Gaussian elimination). Used to
// project real DOM signage onto a wall plane measured from reference
// markers in the room art (see the `quad` comment on DISTRICTS.room).
function quadTransform(wDesign, hDesign, dst) {
  const src = [[0, 0], [wDesign, 0], [wDesign, hDesign], [0, hDesign]];
  const A = [];
  for (let i = 0; i < 4; i++) {
    const [x, y] = src[i];
    const [X, Y] = dst[i];
    A.push([x, y, 1, 0, 0, 0, -x * X, -y * X, X]);
    A.push([0, 0, 0, x, y, 1, -x * Y, -y * Y, Y]);
  }
  for (let col = 0; col < 8; col++) {
    let piv = col;
    for (let r = col + 1; r < 8; r++) if (Math.abs(A[r][col]) > Math.abs(A[piv][col])) piv = r;
    [A[col], A[piv]] = [A[piv], A[col]];
    if (Math.abs(A[col][col]) < 1e-12) return ''; // degenerate quad
    for (let r = 0; r < 8; r++) {
      if (r === col) continue;
      const f = A[r][col] / A[col][col];
      for (let c = col; c < 9; c++) A[r][c] -= f * A[col][c];
    }
  }
  const h = A.map((row, i) => row[8] / A[i][i]); // [a,b,c,d,e,f,g,h]
  return `matrix3d(${h[0]},${h[3]},0,${h[6]},${h[1]},${h[4]},0,${h[7]},0,0,1,0,${h[2]},${h[5]},0,1)`;
}

// Shared zoom-travel: aim the transform-origin at the clicked target, add
// the depart class (CSS scales+fades), then run `done` after the animation.
// Used by both the hub's district plaques and immersive-room links so every
// transition is the same motion -- and the single place future room-to-room
// animation hooks in.
function travel(zoomEl, targetEl, done) {
  if (reducedMotion() || !zoomEl) { done(); return; }
  const box = zoomEl.getBoundingClientRect();
  const r = targetEl.getBoundingClientRect();
  const ox = ((r.left + r.width / 2 - box.left) / box.width) * 100;
  const oy = ((r.top + r.height / 2 - box.top) / box.height) * 100;
  zoomEl.style.transformOrigin = `${ox}% ${oy}%`;
  zoomEl.classList.add('is-depart');
  setTimeout(done, 420);
}

// --- Hub (Grand Concourse) ---

// Each plaque is an invisible click target clipped to the real painted
// sign's shape (see the DISTRICTS comment above) -- at rest it shows
// nothing but a faint outline, brightening to a glow on hover/focus so the
// actual station art reads as the label.
function plaque(district, hub) {
  const h = district.hotspot;
  const posStyle = h
    ? `left:${h.cx}%; top:${h.cy}%; width:${h.w}%; height:${h.h}%; transform: translate(-50%, -50%); clip-path: ${h.clip};`
    : '';
  const btn = el('button', {
    type: 'button',
    class: `vsp-plaque vsp-plaque--${district.id}`,
    style: posStyle,
    title: `${district.name} — ${district.tagline}`,
    'aria-label': `${district.name}. ${district.tagline}.`,
    onclick: () => depart(district, btn, hub),
  }, [
    el('span', { class: 'vsp-plaque-chrome' }, [
      el('span', { class: 'vsp-plaque-icon', text: district.icon }),
      el('span', { class: 'vsp-plaque-text' }, [
        el('span', { class: 'vsp-plaque-name', text: district.name }),
        el('span', { class: 'vsp-plaque-tag', text: district.tagline }),
      ]),
    ]),
  ]);
  return btn;
}

// Travel: zoom the whole concourse toward the chosen doorway, then arrive.
// A district with a single space skips its doors screen and goes straight
// in (Station News is just the Daily Paper; no hallway needed).
function depart(district, plaqueEl, hub) {
  const arrive = () => {
    if (district.modules.length === 1) {
      ctx.navigate(district.modules[0]);
    } else {
      state.district = district;
      requestRender(currentRoute);
    }
  };
  travel(hub, plaqueEl, arrive);
}

function renderHub(root) {
  const hub = el('div', { class: 'vsp-hub' });
  // The stage is what the art and the plaques share: a box locked to the
  // image's exact aspect ratio, letterboxed inside the hub. Hotspot
  // percentages only mean anything measured against the image itself --
  // putting them on a container that cover-crops the art (the previous
  // structure) silently breaks alignment at any viewport that isn't
  // exactly 16:9, which is most real browser windows.
  const stage = el('div', { class: 'vsp-stage' });
  const layer = el('div', { class: 'vsp-plaque-layer' });
  for (const d of DISTRICTS) layer.append(plaque(d, hub));
  stage.append(
    el('header', { class: 'vsp-masthead' }, [
      el('h1', { class: 'vsp-title', text: 'VESPERA' }),
      el('p', { class: 'vsp-subtitle', text: 'Grand Concourse' }),
    ]),
    layer,
  );
  hub.append(stage);
  root.append(hub);

  // Central Directory: the flat every-module list, so nothing is
  // unreachable while most districts only surface their headline spaces.
  const dir = el('div', { class: 'vsp-directory' });
  const toggle = el('button', {
    type: 'button', class: 'vsp-dir-toggle', text: '◈ Central Directory',
    onclick: () => dir.classList.toggle('is-open'),
  });
  const list = el('div', { class: 'vsp-dir-list' });
  for (const mod of ctx.modules) {
    list.append(el('a', { class: 'vsp-dir-item', href: '#/' + mod.id, text: mod.label }));
  }
  dir.append(toggle, list);
  root.append(dir);
}

// --- District screen ---

function renderDistrict(root, district) {
  if (district.room) { renderRoom(root, district); return; }

  const screen = el('div', { class: 'vsp-screen vsp-district' });
  screen.append(el('div', { class: 'vsp-bar' }, [
    el('button', {
      type: 'button', class: 'vsp-back', text: '◂ Concourse',
      onclick: () => { state.district = null; requestRender(currentRoute); },
    }),
    el('span', { class: 'vsp-bar-title', text: `${district.icon} ${district.name}` }),
    el('span', { class: 'vsp-bar-tag', text: district.tagline }),
  ]));
  const doors = el('div', { class: 'vsp-doors' });
  for (const id of district.modules) {
    doors.append(el('button', {
      type: 'button', class: 'vsp-door', onclick: () => ctx.navigate(id),
    }, [
      el('span', { class: 'vsp-door-name', text: moduleLabel(id) }),
      el('span', { class: 'vsp-door-enter', text: 'Enter ▸' }),
    ]));
  }
  screen.append(doors);
  root.append(screen);
}

// --- Immersive district room (a full-bleed establishing shot with a
// perspective-mounted link placard on a blank wall in the art). Shares the
// aspect-locked-stage technique with the hub so the placard stays glued to
// its wall at every viewport, and reuses travel() so clicking a link zooms
// the room toward it before navigating -- the same motion the hub uses,
// and the hook for future room-to-room animation.
function renderRoom(root, district) {
  const { room } = district;
  const screen = el('div', { class: 'vsp-screen vsp-district' });
  screen.append(el('div', { class: 'vsp-bar' }, [
    el('button', {
      type: 'button', class: 'vsp-back', text: '◂ Concourse',
      onclick: () => { state.district = null; requestRender(currentRoute); },
    }),
    el('span', { class: 'vsp-bar-title', text: `${district.icon} ${district.name}` }),
    el('span', { class: 'vsp-bar-tag', text: district.tagline }),
  ]));

  const zoom = el('div', { class: 'vsp-hub vsp-room' });
  // Resolve the art path against THIS module's URL, not the document base:
  // an inline background-image url() would otherwise resolve relative to
  // index.html (wrong folder, and wrong again under GitHub Pages' subpath).
  const imgUrl = new URL(room.image, import.meta.url).href;
  const stage = el('div', {
    class: 'vsp-stage vsp-room-stage',
    style: `aspect-ratio: ${room.ratio}; background-image: url('${imgUrl}');`,
  });

  // The signage plane: real DOM text laid out flat in a designW x designH
  // box, then projected onto the measured wall quad via a matrix3d
  // homography (computed below, kept in sync with the stage's rendered
  // size). position/left/top/transform-origin ship INLINE (not in the
  // stylesheet) so a stale cached stylesheet can never detach them from
  // the matrix -- that exact mismatch shipped once already.
  const panel = el('div', {
    class: 'vsp-room-quad',
    style: `position:absolute; left:0; top:0; transform-origin:0 0;`
      + ` width:${room.designW}px; height:${room.designH}px;`,
  }, [
    el('div', { class: 'vsp-room-title', text: district.name }),
    el('div', { class: 'vsp-room-sub', text: district.tagline }),
  ]);
  const links = el('div', { class: 'vsp-room-links' });
  for (const id of district.modules) {
    const link = el('button', {
      type: 'button', class: 'vsp-room-link',
      onclick: () => travel(zoom, link, () => ctx.navigate(id)),
    }, [
      el('span', { class: 'vsp-room-link-name', text: moduleLabel(id) }),
      el('span', { class: 'vsp-room-link-sub', text: moduleTagline(id) }),
    ]);
    links.append(link);
  }
  panel.append(links);
  stage.append(panel);
  zoom.append(stage);
  screen.append(zoom);
  root.append(screen);

  // Project the text plane onto the wall, and keep it projected: the quad
  // is stored as % of the image, so the matrix depends on the stage's
  // rendered size and must be recomputed whenever that changes. (Mobile
  // overrides the inline transform with !important and falls back to a
  // flat stacked list -- recomputing here is a harmless no-op there.)
  const apply = () => {
    // clientWidth/Height, not getBoundingClientRect: layout size is immune
    // to ancestor transforms (the arrive/depart scale animations would
    // otherwise bake a stale scale into the matrix).
    const w = stage.clientWidth;
    const h = stage.clientHeight;
    if (!w || !h) { requestAnimationFrame(apply); return; }
    const dst = room.quad.map(([qx, qy]) => [(qx / 100) * w, (qy / 100) * h]);
    panel.style.transform = quadTransform(room.designW, room.designH, dst);
  };
  new ResizeObserver(apply).observe(stage);
  requestAnimationFrame(apply);
}

// --- Space (module content inside station chrome) ---

async function renderSpace(root, route) {
  const district = districtOf(route.module);
  const screen = el('div', { class: 'vsp-screen vsp-space' });
  screen.append(el('div', { class: 'vsp-bar' }, [
    el('button', {
      type: 'button', class: 'vsp-back', text: '◂ Concourse',
      onclick: () => ctx.navigate('dashboard'),
    }),
    district
      ? el('button', {
        type: 'button', class: 'vsp-back', text: `${district.icon} ${district.name}`,
        onclick: () => { state.district = district; ctx.navigate('dashboard'); },
      })
      : el('span', { class: 'vsp-bar-tag', text: '◈ Directory' }),
    el('span', { class: 'vsp-bar-title', text: moduleLabel(route.module) }),
  ]));

  // The content well opts back into Equator's view styling: mer-* rules are
  // scoped under [data-interface='default'], and attribute selectors match
  // any ancestor, so this wrapper turns them on for the hosted view only —
  // Vespera's own chrome stays outside it.
  const well = el('div', { class: 'vsp-content', 'data-interface': 'default' });
  const canvas = el('main', { class: 'mer-canvas' });
  well.append(canvas);
  screen.append(well);
  root.append(screen);

  const view = VIEWS[route.module];
  if (!view) {
    canvas.append(el('h1', { text: moduleLabel(route.module) }), el('p', { class: 'mer-muted', text: 'This space is still under construction.' }));
    return;
  }
  await view(canvas, ctx, () => requestRender(route));
}

// --- Interface contract implementation ---

async function doRender(route) {
  els.root.innerHTML = '';
  if (route.module === 'dashboard') {
    if (state.district) renderDistrict(els.root, state.district);
    else renderHub(els.root);
  } else {
    await renderSpace(els.root, route);
  }
}

// Same serialized render chain as Equator, for the same reason: concurrent
// triggers (a view's own refresh racing a data-event refresh) must never
// interleave DOM writes.
function requestRender(route) {
  currentRoute = route;
  renderChain = renderChain.then(() => doRender(route)).catch((err) => {
    console.error('vespera: render failed', err);
  });
  return renderChain;
}

async function renderRoute(route) {
  // A real navigation (vs. an in-place re-render): leaving the hub clears
  // the open-district state, and every arrival starts at the top.
  if (route.module !== 'dashboard') state.district = null;
  await requestRender(route);
  window.scrollTo({ top: 0 });
}

function scheduleRerender() {
  if (renderPending || !currentRoute) return;
  renderPending = true;
  queueMicrotask(() => {
    renderPending = false;
    requestRender(currentRoute);
  });
}

registerInterface({
  id: 'vespera',
  name: 'Vespera',
  description: 'The orbital station — Life OS as a place you navigate.',
  stylesheet: 'js/interfaces/vespera/style.css',

  async mount(container, context) {
    ctx = context;
    const root = el('div', { class: 'vsp-root' });
    container.append(root);
    els = { root };
    state = { district: null };
    unsubscribe = ctx.events.on('*', ({ topic }) => {
      if (topic !== 'settings') scheduleRerender();
    });
  },

  renderRoute,

  unmount() {
    unsubscribe?.();
    unsubscribe = null;
    ctx = null;
    els = null;
    currentRoute = null;
    state = { district: null };
  },
});
