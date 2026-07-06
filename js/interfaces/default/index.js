// "Meridian" — the default interface. A calm rail-and-canvas daily driver.
// Interface-contract plumbing (mount/renderRoute/unmount, ctx-only data
// access, reactive re-render via ctx.events) lives here; each module's
// actual view lives in views/*.js so this file stays a router, not a
// growing pile of unrelated view code.

import { registerInterface } from '../registry.js';
import { el } from './dom.js';
import { renderDashboard } from './views/dashboard.js';
import { renderSettings } from './views/settings.js';
import { renderTasks } from './views/tasks.js';
import { renderPlaces } from './views/places.js';

let ctx = null;
let els = null; // { nav, canvas }
let unsubscribe = null;
let currentRoute = null;
let renderPending = false;
let renderChain = Promise.resolve(); // serializes renders so concurrent triggers can't interleave DOM writes

function buildNav() {
  const nav = el('nav', { class: 'mer-nav', 'aria-label': 'Modules' });
  nav.append(el('div', { class: 'mer-brand', text: 'Life OS' }));
  for (const group of ctx.moduleGroups) {
    const groupModules = ctx.modules.filter((m) => m.group === group.id);
    if (!groupModules.length) continue;
    nav.append(el('div', { class: 'mer-nav-group', text: group.label }));
    for (const mod of groupModules) {
      nav.append(
        el('a', {
          class: 'mer-nav-item',
          href: '#/' + mod.id,
          'data-module': mod.id,
          text: mod.label,
        })
      );
    }
  }
  return nav;
}

function markActiveNav(moduleId) {
  for (const item of els.nav.querySelectorAll('.mer-nav-item')) {
    item.classList.toggle('is-active', item.dataset.module === moduleId);
  }
}

function renderPlaceholder(canvas, moduleId) {
  const mod = ctx.modules.find((m) => m.id === moduleId);
  canvas.append(
    el('h1', { text: mod?.label || moduleId }),
    el('p', { class: 'mer-muted', text: 'This module is not built yet.' })
  );
}

const VIEWS = {
  dashboard: renderDashboard,
  settings: renderSettings,
  tasks: renderTasks,
  places: renderPlaces,
};

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
  return requestRender(route);
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
  name: 'Meridian',
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
