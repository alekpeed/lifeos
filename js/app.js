// Bootstrap: open the database, register interfaces (via the manifest's
// import side effects), then hand control to the shell.

import { openDatabase } from './data/db.js';
import { migrateLegacyPeopleToContacts, Settings, verifyAppLock, runAutomations } from './data/api.js';
import { completePendingRedirectIfAny } from './data/supabase-auth.js';
import { events } from './data/events.js';
import { startLifeMusic, stopLifeMusic } from './audio/lifemusic.js';
import './interfaces/manifest.js';
import { startShell } from './shell.js';
import { initNative } from './native/native-boot.js';

const appEl = document.getElementById('app');

// Shown before any Life OS data renders when App Lock is enabled (Settings
// > App Lock). Requires an explicit tap so the WebAuthn prompt is always
// triggered by a real user gesture, not a synthetic one.
function showLockScreen(credentialId) {
  return new Promise((resolve) => {
    appEl.dataset.bootState = 'locked';
    appEl.innerHTML = '';

    const status = document.createElement('p');
    status.className = 'applock-status';

    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'applock-btn';
    btn.textContent = 'Unlock';
    btn.onclick = async () => {
      btn.disabled = true;
      status.textContent = '';
      try {
        const ok = await verifyAppLock(credentialId);
        if (ok) { resolve(); return; }
        status.textContent = 'Unlock failed.';
      } catch (err) {
        status.textContent = err.name === 'NotAllowedError' ? 'Cancelled.' : (err.message || String(err));
      }
      btn.disabled = false;
    };

    const wrap = document.createElement('div');
    wrap.className = 'applock-screen';
    wrap.append(
      Object.assign(document.createElement('p'), { className: 'applock-title', textContent: 'Life OS is locked' }),
      btn,
      status,
    );
    appEl.append(wrap);
  });
}

async function boot() {
  try {
    // If we just landed back from a Google sign-in redirect, redeem the
    // one-time `?code=` BEFORE any view renders, so the first paint already
    // reflects the signed-in state. Awaited but self-contained: it never
    // throws (records failures on window.__shareboxAuthError), and it's a
    // no-op on every normal boot since it checks the URL first.
    await completePendingRedirectIfAny();
    await openDatabase();

    const [lockEnabled, lockCredentialId] = await Promise.all([Settings.get('appLockEnabled'), Settings.get('appLockCredentialId')]);
    if (lockEnabled && lockCredentialId) await showLockScreen(lockCredentialId);

    await migrateLegacyPeopleToContacts();
    await runAutomations();

    if (await Settings.get('ambientMusicEnabled')) startLifeMusic();
    events.on('settings', async () => {
      if (await Settings.get('ambientMusicEnabled')) startLifeMusic();
      else stopLifeMusic();
    });

    await startShell();

    // Native-only startup extras (device reminders, etc.). Non-blocking and a
    // no-op on the web — must never affect the boot outcome.
    initNative();
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
