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
    hotspot: { cx: 85.93, cy: 36.56, w: 13.43, h: 8.5, clip: 'polygon(0.0% 31.2%, 100.0% 0.0%, 99.1% 90.0%, 0.2% 100.0%)' } },
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

function districtOf(moduleId) {
  return DISTRICTS.find((d) => d.modules.includes(moduleId)) || null;
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
  if (reducedMotion() || !hub) { arrive(); return; }
  const hubRect = hub.getBoundingClientRect();
  const r = plaqueEl.getBoundingClientRect();
  const ox = ((r.left + r.width / 2 - hubRect.left) / hubRect.width) * 100;
  const oy = ((r.top + r.height / 2 - hubRect.top) / hubRect.height) * 100;
  hub.style.transformOrigin = `${ox}% ${oy}%`;
  hub.classList.add('is-depart');
  setTimeout(arrive, 420);
}

function renderHub(root) {
  const hub = el('div', { class: 'vsp-hub' });
  const layer = el('div', { class: 'vsp-plaque-layer' });
  for (const d of DISTRICTS) layer.append(plaque(d, hub));
  hub.append(
    el('header', { class: 'vsp-masthead' }, [
      el('h1', { class: 'vsp-title', text: 'VESPERA' }),
      el('p', { class: 'vsp-subtitle', text: 'Grand Concourse' }),
    ]),
    layer,
  );
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

// --- District doors screen ---

function renderDistrict(root, district) {
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
