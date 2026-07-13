// "Test Mode" — the default interface (registry id 'default'). Originally
// built and named "Equator" as a baseline for testing; renamed 2026-07-13
// when Alek confirmed it isn't the model going forward, just the baseline
// rail-and-canvas daily driver everything else was validated against. The
// folder/id/CSS scope (default/mer-) were already generic and didn't need
// to change -- this interface never rendered its own name anywhere on
// screen, so this is a metadata-only rename, not a UI change.
//
// Interface-contract plumbing (mount/renderRoute/unmount, ctx-only data
// access, reactive re-render via ctx.events) lives here; each module's
// actual view lives in views/*.js so this file stays a router, not a
// growing pile of unrelated view code.

import { registerInterface } from '../registry.js';
import { VIEWS } from '../view-library.js';
import { el } from './dom.js';
import { moduleIconMarkup } from './icons.js';

let ctx = null;
let els = null; // { nav, canvas }
let unsubscribe = null;
let currentRoute = null;
let renderPending = false;
let renderChain = Promise.resolve(); // serializes renders so concurrent triggers can't interleave DOM writes

function buildNav() {
  const nav = el('nav', { class: 'mer-nav', 'aria-label': 'Modules' });
  // The bar (brand + current-module label + hamburger toggle) is always
  // visible. On desktop the toggle/label are simply hidden by CSS and the
  // group list below renders inline via `display: contents`, so desktop's
  // layout is byte-for-byte the same as before this was added. On mobile the
  // group list collapses behind the toggle instead of the old horizontal
  // scroll-strip of all ~19 modules.
  const bar = el('div', { class: 'mer-nav-bar' }, [
    el('div', { class: 'mer-brand', text: 'Life OS' }),
    el('span', { class: 'mer-nav-current' }),
    el('button', {
      type: 'button', class: 'mer-nav-toggle', 'aria-label': 'Open menu', text: '☰',
      onclick: () => nav.classList.toggle('is-expanded'),
    }),
  ]);
  nav.append(bar);

  const groups = el('div', { class: 'mer-nav-groups' });
  for (const group of ctx.moduleGroups) {
    const groupModules = ctx.modules.filter((m) => m.group === group.id);
    if (!groupModules.length) continue;
    groups.append(el('div', { class: 'mer-nav-group', text: group.label }));
    for (const mod of groupModules) {
      groups.append(
        el('a', {
          class: 'mer-nav-item',
          href: '#/' + mod.id,
          'data-module': mod.id,
        }, [
          el('span', { class: 'mer-nav-icon', html: moduleIconMarkup(mod.id) }),
          el('span', { class: 'mer-nav-label', text: mod.label }),
        ])
      );
    }
  }
  nav.append(groups);
  return nav;
}

function markActiveNav(moduleId) {
  for (const item of els.nav.querySelectorAll('.mer-nav-item')) {
    item.classList.toggle('is-active', item.dataset.module === moduleId);
  }
  const mod = ctx.modules.find((m) => m.id === moduleId);
  const currentLabel = els.nav.querySelector('.mer-nav-current');
  if (currentLabel) currentLabel.textContent = mod?.label || '';
  // Collapse the mobile dropdown on every navigation (harmless on desktop,
  // where the class is never added in the first place).
  els.nav.classList.remove('is-expanded');
}

function renderPlaceholder(canvas, moduleId) {
  const mod = ctx.modules.find((m) => m.id === moduleId);
  canvas.append(
    el('h1', { text: mod?.label || moduleId }),
    el('p', { class: 'mer-muted', text: 'This module is not built yet.' })
  );
}

// VIEWS (module id → render function) comes from the shared view library —
// see js/interfaces/view-library.js for why it's shared across interfaces.

// --- Interface contract implementation ---

async function doRender(route) {
  markActiveNav(route.module);
  els.canvas.innerHTML = '';
  const view = VIEWS[route.module] || ((c) => renderPlaceholder(c, route.module));
  // Views receive `requestRerender` instead of calling their own render
  // function directly — that's what keeps every re-render (data-driven or
  // view-triggered) funneled through the same serialized chain below.
  await view(els.canvas, ctx, () => requestRender(route));
}

// Every render request — whether from the shell's renderRoute contract call,
// a data-change event, or a view's own "please refresh me" callback — is
// appended to this single chain. Without it, two renders (e.g. a field edit's
// own refresh racing the data-event listener's refresh) can both start before
// either finishes clearing/rebuilding the canvas, producing duplicated DOM.
function requestRender(route) {
  currentRoute = route;
  renderChain = renderChain.then(() => doRender(route)).catch((err) => {
    console.error('meridian: render failed', err);
  });
  return renderChain;
}

async function renderRoute(route) {
  // Only called on an actual navigation (hash change), never for a same-page
  // re-render (habit ticks, etc. go through requestRender/scheduleRerender
  // directly) -- so it's the right and only place to reset scroll position.
  await requestRender(route);
  window.scrollTo({ top: 0 });
}

function scheduleRerender() {
  // Coalesce bursts of data changes into a single re-render of the open view.
  if (renderPending || !currentRoute) return;
  renderPending = true;
  queueMicrotask(() => {
    renderPending = false;
    requestRender(currentRoute);
  });
}

registerInterface({
  id: 'default',
  name: 'Test Mode',
  description: 'Calm rail-and-canvas layout — the reliable daily driver.',
  stylesheet: 'js/interfaces/default/style.css',

  async mount(container, context) {
    ctx = context;
    const nav = buildNav();
    const canvas = el('main', { class: 'mer-canvas' });
    const root = el('div', { class: 'mer-root' }, [nav, canvas]);
    container.append(root);
    els = { nav, canvas };
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
  },
});
