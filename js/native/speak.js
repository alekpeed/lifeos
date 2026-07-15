// speak.js — read text aloud (FUTURE_FEATURES.md §13).
//
// The motivating case is a hands-free Briefing ("read me my morning"). Native
// uses the TextToSpeech plugin; the web falls back to the browser's built-in
// speechSynthesis, which is essentially universal — so read-aloud works in the
// PWA too, keyless and offline.
//
// NO-BUILD NOTE: runtime Capacitor global, never a bare import.

import { hasCapability, hasWebFallback } from './capabilities.js';

function plugin() {
  try {
    const c = typeof window !== 'undefined' ? window.Capacitor : null;
    return (c && c.Plugins && c.Plugins.TextToSpeech) || null;
  } catch {
    return null;
  }
}

/** True if we can speak here — native TTS plugin OR web speechSynthesis. */
export function canSpeak() {
  return (hasCapability('tts') && !!plugin()) || hasWebFallback('tts');
}

/** Speak the given text. Cancels anything already speaking first. */
export async function speak(text) {
  const clean = (text || '').toString().trim();
  if (!clean) return false;
  const p = plugin();
  if (hasCapability('tts') && p) {
    try {
      await p.stop();
      await p.speak({ text: clean, lang: 'en-US', rate: 1.0 });
      return true;
    } catch {
      return false;
    }
  }
  try {
    const g = typeof window !== 'undefined' ? window : null;
    if (g && 'speechSynthesis' in g) {
      g.speechSynthesis.cancel();
      const u = new g.SpeechSynthesisUtterance(clean);
      u.lang = 'en-US';
      g.speechSynthesis.speak(u);
      return true;
    }
  } catch {
    /* no-op */
  }
  return false;
}

/** Stop any in-progress speech. */
export async function stopSpeaking() {
  const p = plugin();
  if (hasCapability('tts') && p) {
    try { await p.stop(); } catch { /* no-op */ }
    return;
  }
  try {
    const g = typeof window !== 'undefined' ? window : null;
    if (g && 'speechSynthesis' in g) g.speechSynthesis.cancel();
  } catch {
    /* no-op */
  }
}
