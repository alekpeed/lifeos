// Bootstrap: open the database, register interfaces (via the manifest's
// import side effects), then hand control to the shell.

import { openDatabase } from './data/db.js';
import { migrateLegacyPeopleToContacts, migrateLegacyJapaneseToLanguagePacks, ensureLanguagePack } from './data/api.js';
import { completePendingRedirectIfAny } from './data/supabase-auth.js';
import './interfaces/manifest.js';
import { startShell } from './shell.js';

const appEl = document.getElementById('app');

async function boot() {
  try {
    // If we just landed back from a Google sign-in redirect, redeem the
    // one-time `?code=` BEFORE any view renders, so the first paint already
    // reflects the signed-in state. Awaited but self-contained: it never
    // throws (records failures on window.__shareboxAuthError), and it's a
    // no-op on every normal boot since it checks the URL first.
    await completePendingRedirectIfAny();
    await openDatabase();
    await migrateLegacyPeopleToContacts();
    await migrateLegacyJapaneseToLanguagePacks();
    await ensureLanguagePack('ja', 'Japanese', 'ja-JP');
    await startShell();
  } catch (err) {
    appEl.dataset.bootState = 'error';
    appEl.innerHTML = '';
    const msg = document.createElement('p');
    msg.className = 'boot-message';
    msg.textContent = `Failed to start Life OS: ${err.message}`;
    appEl.append(msg);
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
