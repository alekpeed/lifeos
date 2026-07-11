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
// district's own bounding box, not the full image) tracing the real
// painted sign's four corners -- both cx/cy/w/h and clip were measured
// directly from img/hub.png via a red-corner-marker commissioning process
// (see img/README.txt): the art is generated with small solid #FF0000
// squares at each sign's real corners, which get detected, clustered, and
// converted to these coordinates, then inpainted out of the shipped image.
// This is inherently tied to the current hub.png -- a new image needs the
// same process re-run, not a hand-tweak of these numbers.
const DISTRICTS = [
  { id: 'ops', name: 'Operations Deck', tagline: 'Tasks & Projects', icon: '📋', modules: ['tasks', 'ideas', 'habits'],
    hotspot: { cx: 16.22, cy: 13.34, w: 13.67, h: 14.88, clip: 'polygon(0.4% 0.0%, 100.0% 59.6%, 99.3% 100.0%, 0.0% 55.4%)' } },
  { id: 'navbay', name: 'Navigation Bay', tagline: 'Places & Maps', icon: '🧭', modules: ['places', 'packing'],
    hotspot: { cx: 16.85, cy: 36.88, w: 12.95, h: 8.29, clip: 'polygon(0.0% 0.0%, 99.8% 32.7%, 100.0% 100.0%, 0.5% 86.5%)' } },
  { id: 'archive', name: 'The Archive', tagline: 'Links, Books & Education', icon: '📚', modules: ['links', 'books', 'education', 'knowledge', 'rabbitholes'],
    hotspot: { cx: 18.39, cy: 59.62, w: 11.78, h: 8.29, clip: 'polygon(0.5% 20.5%, 99.7% 0.0%, 100.0% 69.9%, 0.0% 100.0%)' } },
  { id: 'ledger', name: 'The Ledger', tagline: 'Bills, Finance & Documents', icon: '🧾', modules: ['finance', 'documents'],
    hotspot: { cx: 16.39, cy: 78.91, w: 12.38, h: 10.95, clip: 'polygon(0.0% 39.3%, 99.3% 0.0%, 100.0% 51.9%, 0.0% 100.0%)' } },
  { id: 'quarters', name: 'Personal Quarters', tagline: 'Contacts, Milestones & Recipes', icon: '👤', modules: ['contacts', 'milestones', 'recipes', 'photos'],
    hotspot: { cx: 86.99, cy: 13.02, w: 12.62, h: 14.35, clip: 'polygon(0.0% 58.1%, 100.0% 0.0%, 100.0% 58.5%, 0.2% 100.0%)' } },
  { id: 'conservatory', name: 'The Conservatory', tagline: 'Languages & Music', icon: '🎵', modules: ['languages', 'chords', 'lifeasmusic'],
    hotspot: { cx: 85.93, cy: 36.56, w: 13.43, h: 8.5, clip: 'polygon(0.0% 31.2%, 100.0% 0.0%, 99.1% 90.0%, 0.2% 100.0%)' },
    // Immersive entry room (see renderRoom). `image` is the establishing
    // shot rendered on an aspect-locked stage (same technique as the hub).
    //
    // Superseded the CSS-projected signage (homography onto a measured
    // wall quad, see git history) with baked-in art: Alek had the image
    // generator itself render the real title/link text directly on the
    // wall in solid red (#FF0000-ish, with some anti-aliased/darker-red
    // variance), at whatever perspective and lighting it chose -- solving
    // the "glued to the wall" problem at the only layer that actually can
    // (the render), not at the CSS layer. img/conservatory.png is that art
    // with every detected red pixel recolored teal in place (brightness
    // preserved, soft bloom added) -- that's the always-visible rest
    // state, no code-drawn box or text at all.
    //
    // `links[].overlay` is a small pre-cropped RGBA cutout of just that
    // link's pixels (icon+name+description together), recolored hot pink
    // with a stronger fuzzy bloom and transparent everywhere else --
    // revealed on hover by absolute-positioning it (left/top/width/height
    // as % of the image, from the same crop) over the teal base. No
    // rotation or perspective math anywhere: the overlay is pixel-for-
    // pixel from the same photo, so it can't help lining up.
    //
    // Regenerating this art requires re-running the red-text detection +
    // recolor + crop pipeline in img/README.txt against the new image --
    // these numbers are tied to the current source (img/consredout.png,
    // the outline-signage version).
    room: {
      image: 'img/conservatory.png',
      ratio: '1672 / 941',
      links: {
        languages: { overlay: 'img/ops_row_languages.png', left: 3.83, top: 38.04, width: 17.46, height: 10.41 },
        chords: { overlay: 'img/ops_row_chords.png', left: 3.77, top: 47.93, width: 18.06, height: 10.41 },
        lifeasmusic: { overlay: 'img/ops_row_life.png', left: 3.47, top: 57.81, width: 20.16, height: 10.84 },
      },
    } },
  { id: 'core', name: 'Systems Core', tagline: 'Tools & Settings', icon: '🛠️', modules: ['tools', 'settings', 'search', 'qrsync'],
    hotspot: { cx: 85.68, cy: 60.31, w: 12.8, h: 9.14, clip: 'polygon(0.2% 0.0%, 100.0% 22.7%, 100.0% 100.0%, 0.0% 64.0%)' } },
  { id: 'relay', name: 'AI Relay', tagline: 'AI Assistant — Claude', icon: '🤖', modules: ['assistant'],
    hotspot: { cx: 86.66, cy: 80.74, w: 12.92, h: 11.96, clip: 'polygon(0.7% 0.0%, 100.0% 42.2%, 99.8% 100.0%, 0.0% 49.3%)' } },
  // Added for Dashboard's due-soon feed to have a hub-level home. This
  // art does paint a real Station News sign bottom-center, same as the
  // other 8 -- measured the same way, not a special case.
  { id: 'news', name: 'Station News', tagline: 'The Daily Paper', icon: '📰', modules: ['paper'],
    hotspot: { cx: 51.11, cy: 75.56, w: 15.67, h: 6.7, clip: 'polygon(0.6% 0.0%, 99.4% 0.8%, 100.0% 100.0%, 0.0% 100.0%)' } },
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
  // Resolve art paths against THIS module's URL, not the document base: an
  // inline background-image/img url() would otherwise resolve relative to
  // index.html (wrong folder, and wrong again under GitHub Pages' subpath).
  const resolve = (rel) => new URL(rel, import.meta.url).href;
  const stage = el('div', {
    class: 'vsp-stage vsp-room-stage',
    style: `aspect-ratio: ${room.ratio}; background-image: url('${resolve(room.image)}');`,
  });

  // Each link is an invisible click target the size of its signage row,
  // holding an absolutely-positioned pink-recolored cutout of that exact
  // row (see the room config comment) that's transparent until hover. No
  // rotation or perspective math: it's a pixel-for-pixel crop of the same
  // photo, so it is the correct shape and position by construction.
  for (const id of district.modules) {
    const spec = room.links[id];
    const link = el('button', {
      type: 'button', class: 'vsp-room-link',
      style: `left:${spec.left}%; top:${spec.top}%; width:${spec.width}%; height:${spec.height}%;`,
      'aria-label': `${moduleLabel(id)} — ${moduleTagline(id)}`,
      onclick: () => travel(zoom, link, () => ctx.navigate(id)),
    }, [
      el('img', {
        class: 'vsp-room-link-overlay', src: resolve(spec.overlay), alt: '', draggable: 'false',
      }),
      // Mobile drops the background art (see .vsp-stage's mobile override),
      // so the baked-in signage isn't visible there either -- this text is
      // visually hidden on desktop (the art already says it) and shown as
      // a plain stacked label on mobile so the room stays usable without
      // the photo.
      el('span', { class: 'vsp-room-link-fallback' }, [
        el('span', { class: 'vsp-room-link-fallback-name', text: moduleLabel(id) }),
        el('span', { class: 'vsp-room-link-fallback-sub', text: moduleTagline(id) }),
      ]),
    ]);
    stage.append(link);
  }
  zoom.append(stage);
  screen.append(zoom);
  root.append(screen);
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
