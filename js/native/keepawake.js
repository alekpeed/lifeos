// keepawake.js — keep the screen on during a task (FUTURE_FEATURES.md §13).
//
// The motivating case is cooking mode in Recipes: hold the screen awake so it
// doesn't dim/lock while your hands are busy. Native uses the KeepAwake plugin;
// the web falls back to the Screen Wake Lock API (navigator.wakeLock) where
// available. Either way it's opt-in and must be released when done.
//
// NO-BUILD NOTE: runtime Capacitor global, never a bare import.

import { hasCapability, hasWebFallback } from './capabilities.js';

let webLock = null; // held WakeLockSentinel on web

function plugin() {
  try {
    const c = typeof window !== 'undefined' ? window.Capacitor : null;
    return (c && c.Plugins && c.Plugins.KeepAwake) || null;
  } catch {
    return null;
  }
}

/** True if keeping the screen awake is possible here — native OR web wake lock. */
export function canKeepAwake() {
  return (hasCapability('keepAwake') && !!plugin()) || hasWebFallback('keepAwake');
}

export async function enableKeepAwake() {
  const p = plugin();
  if (hasCapability('keepAwake') && p) {
    try { await p.keepAwake(); return true; } catch { return false; }
  }
  try {
    if (typeof navigator !== 'undefined' && navigator.wakeLock) {
      webLock = await navigator.wakeLock.request('screen');
      // The OS drops the lock when the tab is hidden; re-acquire on return.
      if (typeof document !== 'undefined') {
        document.addEventListener('visibilitychange', reacquireOnVisible);
      }
      return true;
    }
  } catch {
    /* denied — no-op */
  }
  return false;
}

async function reacquireOnVisible() {
  try {
    if (webLock !== null && document.visibilityState === 'visible' && navigator.wakeLock) {
      webLock = await navigator.wakeLock.request('screen');
    }
  } catch {
    /* no-op */
  }
}

export async function disableKeepAwake() {
  const p = plugin();
  if (hasCapability('keepAwake') && p) {
    try { await p.allowSleep(); } catch { /* no-op */ }
    return;
  }
  try {
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', reacquireOnVisible);
    }
    if (webLock) { await webLock.release(); }
  } catch {
    /* no-op */
  } finally {
    webLock = null;
  }
}
