// The shell owns everything that is app-level rather than interface-level:
// routing, the active-interface lifecycle, preference application (theme /
// density / accent), and the context object interfaces receive. Interfaces
// never touch location.hash, document.documentElement, or each other — they
// render into the container they're given and talk back through ctx.

import * as data from './data/api.js';
import { events } from './data/events.js';
import { getInterface, listInterfaces } from './interfaces/registry.js';
import { MODULES, MODULE_GROUPS, DEFAULT_MODULE, isValidModule } from './modules.js';

const appEl = document.getElementById('app');
let active = null;
let switching = Promise.resolve();

// --- Routing (hash-based: works on GitHub Pages with no server config) ---

export function parseRoute() {
  const hash = location.hash.replace(/^#\/?/, '');
  const [module, ...rest] = hash.split('/').filter(Boolean);
  return {
    module: isValidModule(module) ? module : DEFAULT_MODULE,
    rest,
  };
}

export function navigate(path) {
  location.hash = '#/' + String(path).replace(/^[#/]+/, '');
}

// --- Preferences (theme/density/accent apply globally, above any interface) ---

async function applyPreferences() {
  const settings = await data.Settings.getAll();
  const root = document.documentElement;
  root.dataset.theme = settings.theme;
  root.dataset.density = settings.density;
  root.dataset.accent = settings.accent;
}

// --- Interface lifecycle ---

function setInterfaceStylesheet(href) {
  let link = document.getElementById('interface-stylesheet');
  if (!href) {
    link?.remove();
    return;
  }
  if (!link) {
    link = document.createElement('link');
    link.rel = 'stylesheet';
    link.id = 'interface-stylesheet';
    document.head.appendChild(link);
  }
  link.href = href;
}

function buildContext() {
  return {
    data,                       // the shared data API — the only data doorway
    events,                     // subscribe to data-change topics
    modules: MODULES,           // canonical module list for building navigation
    moduleGroups: MODULE_GROUPS,
    navigate,                   // request a route change
    parseRoute,                 // read the current route
    listInterfaces,             // for interface pickers
    switchInterface: (id) => switchInterface(id),
  };
}

async function doSwitch(id, { persist }) {
  const def = getInterface(id) || getInterface('default');
  if (!def) throw new Error('No interfaces registered.');
  if (active === def) return;

  if (active) {
    try {
      active.unmount?.();
    } catch (err) {
      console.error(`shell: error unmounting interface "${active.id}"`, err);
    }
  }
  appEl.innerHTML = '';
  appEl.dataset.bootState = 'ready';
  document.documentElement.dataset.interface = def.id;
  setInterfaceStylesheet(def.stylesheet || null);

  active = def;
  await def.mount(appEl, buildContext());
  await def.renderRoute?.(parseRoute());

  if (persist) await data.Settings.set('activeInterface', def.id);
}

export function switchInterface(id, { persist = true } = {}) {
  // Serialized so a rapid double-switch can't interleave mount/unmount.
  switching = switching.then(() => doSwitch(id, { persist }));
  return switching;
}

// --- Boot ---

export async function startShell() {
  await applyPreferences();
  events.on('settings', () => {
    applyPreferences().catch((err) => console.error('shell: applyPreferences failed', err));
  });

  window.addEventListener('hashchange', () => {
    Promise.resolve(active?.renderRoute?.(parseRoute())).catch((err) =>
      console.error('shell: renderRoute failed', err)
    );
  });

  const savedId = await data.Settings.get('activeInterface');
  await switchInterface(savedId, { persist: false });
}
