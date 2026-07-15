// native-boot.js — native-only wiring that runs once at app startup.
//
// Kept separate from app.js so the boot path stays readable and so all of this
// is trivially a no-op on the web: every branch gates on the capability layer.
// app.js calls initNative() after the shell is up; it never blocks boot and
// never throws into it.

import { isNativePlatform, isPluginAvailable } from './capabilities.js';
import { canNotify, notifyPermissionState, syncReminders } from './notify.js';
import { getDueSoonFeed } from '../data/api.js';

// Deep-link routing for home-screen shortcuts (and any future lifeos:// link).
// A shortcut fires lifeos://open/<route>; the App plugin surfaces it here and
// we navigate by setting the hash the app's router already understands.
function routeFromDeepLink(url) {
  try {
    // Accept lifeos://open/tasks and lifeos://open?route=tasks forms.
    const m = /lifeos:\/\/open\/?\??(?:route=)?([a-z0-9-]+)?/i.exec(String(url || ''));
    const route = m && m[1];
    if (route) window.location.hash = `#/${route}`;
  } catch {
    /* ignore malformed links */
  }
}

function initDeepLinks() {
  try {
    const c = typeof window !== 'undefined' ? window.Capacitor : null;
    const app = c && c.Plugins && c.Plugins.App;
    if (!app || !isPluginAvailable('@capacitor/app')) return;
    // Cold start: the app may have been launched by a shortcut.
    if (typeof app.getLaunchUrl === 'function') {
      app.getLaunchUrl().then((res) => { if (res && res.url) routeFromDeepLink(res.url); }).catch(() => {});
    }
    // Warm: a shortcut tapped while the app is already running.
    app.addListener('appUrlOpen', (data) => { if (data && data.url) routeFromDeepLink(data.url); });
  } catch {
    /* no-op */
  }
}

// Build a local reminder per upcoming due item: a 9am nudge on the due date.
// getDueSoonFeed already filters to the near horizon; scheduleReminder drops
// anything in the past, so overdue items simply don't schedule (the app's
// Dashboard/Briefing already surface those when you open it).
function remindersFromFeed(items) {
  const out = [];
  for (const it of items || []) {
    if (!it.dueDate) continue;
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(it.dueDate);
    if (!m) continue;
    const at = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 9, 0, 0, 0);
    out.push({
      key: `due:${it.module}:${it.id}`,
      title: it.module === 'bills' ? 'Bill due' : 'Due soon',
      body: it.title || '',
      at,
    });
  }
  return out;
}

/**
 * Sync the phone's scheduled local notifications with the current due-soon
 * feed. Safe to call anytime; on web or without notification permission it's a
 * no-op. Returns the number of reminders scheduled.
 */
export async function refreshDeviceReminders() {
  if (!canNotify()) return 0;
  if ((await notifyPermissionState()) !== 'granted') return 0;
  try {
    const feed = await getDueSoonFeed(14, 30, 30);
    return await syncReminders(remindersFromFeed(feed));
  } catch {
    return 0;
  }
}

/** One-shot native startup hook. Called by app.js boot; never throws. */
export async function initNative() {
  if (!isNativePlatform()) return;
  try {
    initDeepLinks();
    await refreshDeviceReminders();
  } catch {
    /* boot must never fail because of a native extra */
  }
}
