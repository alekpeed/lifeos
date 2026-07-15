// notify.js — on-device local notifications (FUTURE_FEATURES.md §13).
//
// Fires reminders (bills due, habit nudges, time-capsule unlocks) directly on
// the device, no server and no Supabase round-trip — works offline, app closed.
// This is distinct from Web Push (js/data/push.js), which needs the backend;
// local notifications are scheduled by the app itself on the phone.
//
// NO-BUILD NOTE (important): the web app loads these ES modules straight from
// js/ in the browser, with no bundler and no node_modules — so we must NOT
// `import` the bare '@capacitor/local-notifications' specifier here (the
// browser can't resolve it and the whole app would fail to load). The npm
// package exists only so `cap sync` compiles the plugin's NATIVE Android code
// into the app; on the JS side we reach it through the runtime global
// Capacitor injects: window.Capacitor.Plugins.LocalNotifications. In a plain
// browser that global is absent, so every function below is a safe no-op and
// the feature simply isn't offered — exactly the graceful degradation the
// capability layer promises.

import { hasCapability } from './capabilities.js';

function plugin() {
  try {
    const c = typeof window !== 'undefined' ? window.Capacitor : null;
    return (c && c.Plugins && c.Plugins.LocalNotifications) || null;
  } catch {
    return null;
  }
}

/** Available only in a native build with the plugin present. */
export function canNotify() {
  return hasCapability('localNotify') && !!plugin();
}

/** Ask the OS for permission to post notifications. Returns true if granted. */
export async function requestNotifyPermission() {
  const p = plugin();
  if (!canNotify() || !p) return false;
  try {
    const res = await p.requestPermissions();
    return res && res.display === 'granted';
  } catch {
    return false;
  }
}

/** Current permission state without prompting: 'granted' | 'denied' | 'prompt' | 'unavailable'. */
export async function notifyPermissionState() {
  const p = plugin();
  if (!canNotify() || !p) return 'unavailable';
  try {
    const res = await p.checkPermissions();
    return (res && res.display) || 'prompt';
  } catch {
    return 'unavailable';
  }
}

// LifeOS local-notification ids are derived from a stable string key so
// rescheduling the same reminder replaces rather than duplicates it. Capacitor
// wants a 32-bit int id, so we hash the key into one deterministically.
function idFor(key) {
  let h = 0;
  const s = String(key);
  for (let i = 0; i < s.length; i++) {
    h = (h * 31 + s.charCodeAt(i)) | 0;
  }
  // Keep it positive and within safe notification-id range.
  return Math.abs(h) % 2000000000;
}

// --- Actionable notifications (FUTURE_FEATURES.md §13) -------------------
// Buttons on the notification itself — "Mark paid", "Mark done", "Snooze" —
// so a due-item reminder can be acted on without opening the app. Each reminder
// carries an `actionTypeId` matching its module; the button ids below are what
// the localNotificationActionPerformed handler (wired in native-boot.js) reads.
export const NOTIFY_ACTION_TYPES = [
  { id: 'lifeos-bill',       actions: [{ id: 'mark-paid', title: 'Mark paid' }, { id: 'snooze', title: 'Snooze 1 day' }] },
  { id: 'lifeos-task',       actions: [{ id: 'mark-done', title: 'Mark done' }, { id: 'snooze', title: 'Snooze 1 day' }] },
  { id: 'lifeos-assignment', actions: [{ id: 'mark-done', title: 'Mark done' }] },
  { id: 'lifeos-document',   actions: [{ id: 'renew', title: 'Create renew task' }] },
];

/** Map a due-soon feed module to its notification action-type id (null if none). */
export function actionTypeForModule(module) {
  switch (module) {
    case 'bills':       return 'lifeos-bill';
    case 'tasks':       return 'lifeos-task';
    case 'assignments': return 'lifeos-assignment';
    case 'documents':   return 'lifeos-document';
    default:            return null;
  }
}

let actionsRegistered = false;
/** Register the notification action-button types once. Idempotent; no-op off-native. */
export async function registerNotificationActions() {
  const p = plugin();
  if (!canNotify() || !p || actionsRegistered) return;
  try {
    await p.registerActionTypes({ types: NOTIFY_ACTION_TYPES });
    actionsRegistered = true;
  } catch {
    /* older plugin w/o action types -- reminders still fire, just without buttons */
  }
}

/**
 * Attach a handler for notification action-button taps.
 * @param {(event:{actionId:string, notification:object}) => void} handler
 * Returns the listener handle (or null off-native / on failure).
 */
export function onNotificationAction(handler) {
  const p = plugin();
  if (!canNotify() || !p || typeof handler !== 'function') return null;
  try {
    return p.addListener('localNotificationActionPerformed', handler);
  } catch {
    return null;
  }
}

/**
 * Schedule (or reschedule) a single reminder.
 * @param {{key:string, title:string, body?:string, at:Date, actionTypeId?:string, extra?:object}} r
 * Past-dated or invalid times are skipped. Returns true if scheduled.
 */
export async function scheduleReminder(r) {
  const p = plugin();
  if (!canNotify() || !p || !r || !r.at) return false;
  const when = r.at instanceof Date ? r.at : new Date(r.at);
  if (isNaN(when.getTime()) || when.getTime() <= Date.now()) return false;
  try {
    const note = {
      id: idFor(r.key),
      title: r.title || 'LifeOS',
      body: r.body || '',
      schedule: { at: when, allowWhileIdle: true },
    };
    if (r.actionTypeId) note.actionTypeId = r.actionTypeId;
    if (r.extra) note.extra = r.extra;
    await p.schedule({ notifications: [note] });
    return true;
  } catch {
    return false;
  }
}

/** Cancel a previously-scheduled reminder by its string key. */
export async function cancelReminder(key) {
  const p = plugin();
  if (!canNotify() || !p) return;
  try {
    await p.cancel({ notifications: [{ id: idFor(key) }] });
  } catch {
    /* no-op */
  }
}

/**
 * Replace ALL currently-pending LifeOS reminders with a fresh set. Cancels
 * whatever's pending, then schedules the given list — so calling this on boot
 * with the current due-soon items keeps the phone's scheduled notifications in
 * sync with reality without piling up duplicates. Returns the count scheduled.
 */
export async function syncReminders(reminders) {
  const p = plugin();
  if (!canNotify() || !p) return 0;
  try {
    const pending = await p.getPending();
    if (pending && pending.notifications && pending.notifications.length) {
      await p.cancel({ notifications: pending.notifications.map((n) => ({ id: n.id })) });
    }
  } catch {
    /* continue -- scheduling the fresh set is what matters */
  }
  let n = 0;
  for (const r of reminders || []) {
    if (await scheduleReminder(r)) n++;
  }
  return n;
}
