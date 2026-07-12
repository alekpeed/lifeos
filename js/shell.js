// The shell owns everything that is app-level rather than interface-level:
// routing, the active-interface lifecycle, preference application (theme /
// density / accent), and the context object interfaces receive. Interfaces
// never touch location.hash, document.documentElement, or each other — they
// render into the container they're given and talk back through ctx.

import * as data from './data/api.js';
import { events } from './data/events.js';
import { getInterface, listInterfaces } from './interfaces/registry.js';
import { MODULES, MODULE_GROUPS, DEFAULT_MODULE, isValidModule, getRemoteModules, isRemoteModule } from './modules.js';
import { isMobileRemoteContext, isTouchPrimaryDevice } from './data/device-context.js';

const appEl = document.getElementById('app');
let active = null;
let switching = Promise.resolve();

// --- Routing (hash-based: works on GitHub Pages with no server config) ---

export function parseRoute() {
  const hash = location.hash.replace(/^#\/?/, '');
  const [module, ...rest] = hash.split('/').filter(Boolean);
  const eligible = isValidModule(module) && (!isMobileRemoteContext() || isRemoteModule(module));
  return {
    module: eligible ? module : DEFAULT_MODULE,
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
  // A photo-derived accent (Theme-from-Photo) is stored as raw hex colors
  // rather than a CSS-selector preset name, so it's applied as an inline
  // override on top of whichever [data-accent] preset matched above --
  // present only when accent === 'custom'; cleared otherwise so switching
  // back to a preset doesn't leave a stale inline color behind.
  if (settings.accent === 'custom' && settings.customAccent) {
    root.style.setProperty('--accent', settings.customAccent.accent);
    root.style.setProperty('--accent-strong', settings.customAccent.accentStrong);
  } else {
    root.style.removeProperty('--accent');
    root.style.removeProperty('--accent-strong');
  }
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
  // On the installed mobile remote, nav is curated to the `remote: true`
  // subset (see modules.js) -- everywhere else (desktop, a plain browser
  // tab) sees the full canonical list. This is a UI-surface distinction
  // only; the underlying data isn't segregated, so a module left off the
  // remote's nav is still reachable data-wise (e.g. via Search/Recall),
  // just not offered as a destination in its own right.
  const remote = isMobileRemoteContext();
  return {
    data,                       // the shared data API — the only data doorway
    events,                     // subscribe to data-change topics
    modules: remote ? getRemoteModules() : MODULES,  // curated for building navigation
    moduleGroups: MODULE_GROUPS,
    isMobileRemote: remote,     // interfaces can adapt further (density, copy, etc.) if needed
    navigate,                   // request a route change
    parseRoute,                 // read the current route
    // On any touch-primary device (installed remote or a plain phone
    // browser tab), touch-unsafe interfaces (e.g. Vespera) aren't offered
    // as a switch target in the first place -- not just rejected on pick.
    listInterfaces: () => {
      const all = listInterfaces();
      if (!isTouchPrimaryDevice()) return all;
      return all.filter((i) => getInterface(i.id)?.touchSafe !== false);
    },
    switchInterface: (id) => switchInterface(id),
  };
}

async function doSwitch(id, { persist }) {
  let def = getInterface(id) || getInterface('default');
  if (!def) throw new Error('No interfaces registered.');
  // A touch-primary fallback is a display-only override, not a real
  // preference change -- activeInterface is a synced setting, so persisting
  // the fallback here would silently overwrite the user's real (desktop)
  // choice the next time it syncs back down.
  let forcedFallback = false;
  if (def.touchSafe === false && isTouchPrimaryDevice()) {
    def = getInterface('default');
    forcedFallback = true;
  }
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

  if (persist && !forcedFallback) await data.Settings.set('activeInterface', def.id);
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
