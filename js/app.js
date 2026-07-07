// Bootstrap: open the database, register interfaces (via the manifest's
// import side effects), then hand control to the shell.

import { openDatabase } from './data/db.js';
import { migrateLegacyPeopleToContacts, migrateLegacyJapaneseToLanguagePacks, ensureLanguagePack } from './data/api.js';
import './interfaces/manifest.js';
import { startShell } from './shell.js';

const appEl = document.getElementById('app');

async function boot() {
  try {
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
