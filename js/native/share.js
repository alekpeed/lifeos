// share.js — share LifeOS content OUT to any other app (FUTURE_FEATURES.md §13).
//
// Native: Capacitor's Share plugin opens the real Android/iOS share sheet.
// Web: falls back to the browser's Web Share API (navigator.share) where it
// exists (most mobile browsers, some desktop), so this works in the PWA too.
// Where neither exists, canShare() is false and callers just don't offer it.
//
// NO-BUILD NOTE: reach the plugin via the runtime Capacitor global, never a
// bare import — see js/native/notify.js for the full rationale.

import { hasCapability, hasWebFallback } from './capabilities.js';

function plugin() {
  try {
    const c = typeof window !== 'undefined' ? window.Capacitor : null;
    return (c && c.Plugins && c.Plugins.Share) || null;
  } catch {
    return null;
  }
}

/** True if we can share here at all — native plugin OR Web Share API. */
export function canShare() {
  return (hasCapability('share') && !!plugin()) || hasWebFallback('share');
}

/**
 * Share content out. @param {{title?:string, text?:string, url?:string}} data
 * Returns true if the share sheet was invoked, false if unavailable or the user
 * dismissed it in a way that threw (treated as a no-op, never rejects upward).
 */
export async function shareContent(data) {
  const payload = {
    title: data && data.title ? String(data.title) : undefined,
    text: data && data.text ? String(data.text) : undefined,
    url: data && data.url ? String(data.url) : undefined,
  };
  // Prefer the native sheet when present.
  const p = plugin();
  if (hasCapability('share') && p) {
    try {
      await p.share(payload);
      return true;
    } catch {
      return false;
    }
  }
  // Web Share API fallback.
  try {
    if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
      await navigator.share({ title: payload.title, text: payload.text, url: payload.url });
      return true;
    }
  } catch {
    /* user dismissed / not allowed — no-op */
  }
  return false;
}
