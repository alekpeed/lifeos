// "Meridian" — the default interface. A calm rail-and-canvas daily driver.
// This is deliberately minimal right now: it proves the interface contract
// (mount/renderRoute/unmount, ctx-only data access, reactive re-render via
// ctx.events) so the per-module views can be built out as Tier 1 work.

import { registerInterface } from '../registry.js';

let ctx = null;
let els = null; // { nav, canvas }
let unsubscribe = null;
let currentRoute = null;
let renderPending = false;

function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'text') node.textContent = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v);
  }
  for (const child of children) node.append(child);
  return node;
}

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

// --- Module views (placeholders except dashboard + settings for now) ---

async function renderDashboard(canvas) {
  const feed = await ctx.data.getDueSoonFeed(7);
  canvas.append(el('h1', { text: 'Today' }));
  if (!feed.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing due in the next 7 days.' }));
    return;
  }
  const list = el('ul', { class: 'mer-feed' });
  for (const item of feed) {
    list.append(
      el('li', { class: item.overdue ? 'mer-feed-item is-overdue' : 'mer-feed-item' }, [
        el('span', { class: 'mer-feed-module', text: item.module }),
        el('span', { class: 'mer-feed-title', text: item.title || '(untitled)' }),
        el('span', { class: 'mer-feed-date', text: item.dueDate || '' }),
      ])
    );
  }
  canvas.append(list);
}

async function renderSettings(canvas) {
  canvas.append(el('h1', { text: 'Settings' }));
  const settings = await ctx.data.Settings.getAll();

  const select = (label, key, options, current, onChange) => {
    const sel = el('select', { onchange: (e) => onChange(e.target.value) });
    for (const opt of options) {
      const o = el('option', { value: opt.value, text: opt.label });
      if (opt.value === current) o.selected = true;
      sel.append(o);
    }
    return el('label', { class: 'mer-setting' }, [el('span', { text: label }), sel]);
  };

  canvas.append(
    select('Theme', 'theme', [
      { value: 'dark', label: 'Dark' },
      { value: 'light', label: 'Light' },
    ], settings.theme, (v) => ctx.data.Settings.set('theme', v)),

    select('Accent', 'accent', [
      { value: 'brass', label: 'Brass' },
      { value: 'teal', label: 'Teal' },
      { value: 'garnet', label: 'Garnet' },
    ], settings.accent, (v) => ctx.data.Settings.set('accent', v)),

    select('Density', 'density', [
      { value: 'comfortable', label: 'Comfortable' },
      { value: 'compact', label: 'Compact' },
    ], settings.density, (v) => ctx.data.Settings.set('density', v)),

    select('Interface', 'activeInterface',
      ctx.listInterfaces().map((i) => ({ value: i.id, label: i.name })),
      settings.activeInterface,
      (v) => ctx.switchInterface(v)),
  );
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
};

// --- Interface contract implementation ---

async function renderRoute(route) {
  currentRoute = route;
  markActiveNav(route.module);
  els.canvas.innerHTML = '';
  const view = VIEWS[route.module] || ((c) => renderPlaceholder(c, route.module));
  await view(els.canvas, route);
}

function scheduleRerender() {
  // Coalesce bursts of data changes into a single re-render of the open view.
  if (renderPending || !currentRoute) return;
  renderPending = true;
  queueMicrotask(() => {
    renderPending = false;
    renderRoute(currentRoute).catch((err) => console.error('meridian: re-render failed', err));
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
