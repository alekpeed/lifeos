// capabilities.js — the native capability-detection / graceful-degradation
// layer. This is the ONE place the rest of the app asks "am I running inside
// the native shell, and is this native feature actually available here?"
//
// Why it exists: LifeOS ships as the same web codebase everywhere — a plain
// browser PWA (incl. iOS), a native Android app (Capacitor), and later native
// desktop. Native-only features (share sheet, wake word, geofencing, local
// notifications…) must LIGHT UP where they're supported and QUIETLY HIDE where
// they aren't, never break. Every native feature gates on hasCapability(...)
// so the exact same file is safe to load in a browser with no Capacitor at all.
//
// How detection works: when the app runs inside a Capacitor native shell,
// Capacitor injects a global `window.Capacitor` with isNativePlatform(),
// getPlatform(), and isPluginAvailable(name). In a plain browser that global
// is simply absent — so everything below degrades to "web platform, native
// false, no native capabilities," with zero throwing and zero references to
// APIs that don't exist. No Capacitor import here on purpose: this module must
// load fine in the buildless web app, which has no node_modules.

function cap() {
  return (typeof window !== 'undefined' && window.Capacitor) || null;
}

/** True only inside a real native shell (Android/iOS app), false in any browser. */
export function isNativePlatform() {
  const c = cap();
  try {
    return !!(c && typeof c.isNativePlatform === 'function' && c.isNativePlatform());
  } catch {
    return false;
  }
}

/** 'android' | 'ios' | 'web' — 'web' for every plain browser, PWA included. */
export function getPlatform() {
  const c = cap();
  try {
    if (c && typeof c.getPlatform === 'function') return c.getPlatform();
  } catch {
    /* fall through */
  }
  return 'web';
}

export function isAndroid() { return getPlatform() === 'android'; }
export function isIOS() { return getPlatform() === 'ios'; }
export function isWeb() { return getPlatform() === 'web'; }

/** Whether a specific Capacitor plugin is present in this build. */
export function isPluginAvailable(name) {
  const c = cap();
  try {
    if (c && typeof c.isPluginAvailable === 'function') return c.isPluginAvailable(name);
  } catch {
    /* fall through */
  }
  // Fallback: some plugin registrations only surface under Capacitor.Plugins.
  try {
    return !!(c && c.Plugins && c.Plugins[name]);
  } catch {
    return false;
  }
}

// The catalog of native capabilities LifeOS builds on (FUTURE_FEATURES.md §13).
// Each entry: the Capacitor plugin it needs + which platforms can support it,
// plus a `web` fallback flag for the few that have a real browser equivalent
// (e.g. Web Share API, the Notifications API) so a feature can still offer a
// degraded version on the web instead of vanishing. `plugin: null` marks a
// capability provided by the shell/OS itself rather than a discrete plugin.
export const CAPABILITIES = {
  share:          { label: 'System share sheet',        plugin: '@capacitor/share',                platforms: ['android', 'ios'], webFallback: 'navigator.share' },
  localNotify:    { label: 'Local scheduled notifications', plugin: '@capacitor/local-notifications', platforms: ['android', 'ios'], webFallback: null },
  push:           { label: 'Native push',               plugin: '@capacitor/push-notifications',   platforms: ['android', 'ios'], webFallback: 'webpush' },
  geolocation:    { label: 'Background geolocation',     plugin: '@capacitor/geolocation',          platforms: ['android', 'ios'], webFallback: 'navigator.geolocation' },
  filesystem:     { label: 'Native filesystem',          plugin: '@capacitor/filesystem',           platforms: ['android', 'ios'], webFallback: null },
  camera:         { label: 'Native camera',              plugin: '@capacitor/camera',               platforms: ['android', 'ios'], webFallback: 'input[capture]' },
  appShortcuts:   { label: 'Home-screen shortcuts',      plugin: null,                              platforms: ['android'],        webFallback: null },
  wakeWord:       { label: 'Always-on wake word',        plugin: 'WakeWord',                        platforms: ['android'],        webFallback: 'speechrecognition' },
  nfc:            { label: 'NFC tags',                    plugin: 'Nfc',                             platforms: ['android'],        webFallback: 'webnfc' },
  ble:            { label: 'Bluetooth (BLE)',             plugin: '@capacitor-community/bluetooth-le', platforms: ['android', 'ios'], webFallback: 'webbluetooth' },
  keepAwake:      { label: 'Keep screen awake',           plugin: '@capacitor-community/keep-awake', platforms: ['android', 'ios'], webFallback: 'wakelock' },
  contacts:       { label: 'Phone contacts',              plugin: '@capacitor-community/contacts',   platforms: ['android', 'ios'], webFallback: null },
};

/**
 * The core gate every native feature calls.
 * Returns true only when: we're on a native platform that supports this
 * capability AND its backing plugin is actually present in the build.
 * Unknown ids return false (safe default).
 */
export function hasCapability(id) {
  const def = CAPABILITIES[id];
  if (!def) return false;
  if (!isNativePlatform()) return false;
  if (!def.platforms.includes(getPlatform())) return false;
  if (def.plugin === null) return true; // shell/OS-provided, no discrete plugin
  return isPluginAvailable(def.plugin);
}

/**
 * Whether a *degraded web* version of this capability exists in the current
 * (non-native) browser — used so a feature can offer a lesser experience on
 * the web instead of disappearing. Returns false when there's no web fallback
 * or when the relevant web API isn't present in this browser.
 */
export function hasWebFallback(id) {
  const def = CAPABILITIES[id];
  if (!def || !def.webFallback) return false;
  const g = typeof window !== 'undefined' ? window : {};
  const nav = (typeof navigator !== 'undefined' && navigator) || {};
  switch (def.webFallback) {
    case 'navigator.share':       return typeof nav.share === 'function';
    case 'navigator.geolocation': return !!nav.geolocation;
    case 'webpush':               return 'PushManager' in g && 'serviceWorker' in nav;
    case 'input[capture]':        return true; // file input is universal
    case 'speechrecognition':     return !!(g.SpeechRecognition || g.webkitSpeechRecognition);
    case 'webnfc':                return 'NDEFReader' in g;
    case 'webbluetooth':          return !!(nav.bluetooth);
    case 'wakelock':              return !!(nav.wakeLock);
    default:                      return false;
  }
}

/**
 * A full snapshot for a diagnostics/Settings view: platform, native flag, and
 * per-capability native/web availability. Pure data, safe to call anywhere.
 */
export function getCapabilitySnapshot() {
  const platform = getPlatform();
  const native = isNativePlatform();
  const capabilities = {};
  for (const id of Object.keys(CAPABILITIES)) {
    capabilities[id] = {
      label: CAPABILITIES[id].label,
      native: hasCapability(id),
      webFallback: hasWebFallback(id),
    };
  }
  return { platform, native, capabilities };
}

// Expose a read-only snapshot for quick console/diagnostic checks in any build.
if (typeof window !== 'undefined') {
  window.LifeOSNative = Object.freeze({
    isNativePlatform,
    getPlatform,
    hasCapability,
    hasWebFallback,
    getCapabilitySnapshot,
  });
}
