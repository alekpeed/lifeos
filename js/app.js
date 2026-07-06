// Bootstrap. This intentionally does not build navigation or mount any
// interface yet — the interface-registry pattern (Section 1a of the brief)
// that the rest of the UI depends on is a separate, deliberate design pass.
// For now this just proves the data layer and PWA shell are wired up.

import { openDatabase } from './data/db.js';
import { Settings } from './data/api.js';

const appEl = document.getElementById('app');

function applyPreferences(settings) {
  const root = document.documentElement;
  root.dataset.theme = settings.theme;
  root.dataset.density = settings.density;
  root.dataset.accent = settings.accent;
}

async function boot() {
  try {
    await openDatabase();
    const settings = await Settings.getAll();
    applyPreferences(settings);

    appEl.dataset.bootState = 'ready';
    appEl.innerHTML = '<p class="boot-message">Life OS foundation ready — no interface mounted yet.</p>';
  } catch (err) {
    appEl.dataset.bootState = 'error';
    appEl.innerHTML = `<p class="boot-message">Failed to start Life OS: ${err.message}</p>`;
    console.error(err);
  }
}

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('service-worker.js').catch((err) => {
      console.warn('Service worker registration failed:', err);
    });
  });
}

boot();
