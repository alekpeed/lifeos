// native-boot.js — native-only wiring that runs once at app startup.
//
// Kept separate from app.js so the boot path stays readable and so all of this
// is trivially a no-op on the web: every branch gates on the capability layer.
// app.js calls initNative() after the shell is up; it never blocks boot and
// never throws into it.

import { isNativePlatform, isPluginAvailable, hasCapability } from './capabilities.js';
import { speak, canSpeak } from './speak.js';
import {
  canNotify, notifyPermissionState, syncReminders, scheduleReminder,
  registerNotificationActions, onNotificationAction, actionTypeForModule,
  showNextUp, clearNextUp,
} from './notify.js';
import { getDueSoonFeed, getBriefing, Settings, Bills, Tasks, Assignments, Links, Ideas } from '../data/api.js';

function tomorrowStr() {
  const t = new Date();
  t.setDate(t.getDate() + 1);
  return new Date(t.getFullYear(), t.getMonth(), t.getDate()).toISOString().slice(0, 10);
}

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
      // Action buttons on the notification (Mark paid / Mark done / Snooze /
      // Renew). extra travels with the notification so the tap handler knows
      // which record to act on without opening the app.
      actionTypeId: actionTypeForModule(it.module),
      extra: { module: it.module, id: it.id, title: it.title || '', dueDate: it.dueDate },
    });
  }
  return out;
}

// Apply a notification action-button tap without opening the app UI. Button ids
// match NOTIFY_ACTION_TYPES in notify.js; notification.extra carries the record
// the reminder was scheduled for. Best-effort — any failure is swallowed so a
// stray tap never crashes the boot path.
async function applyNotificationAction(actionId, notification) {
  const extra = (notification && notification.extra) || {};
  const { module, id, title, dueDate } = extra;
  try {
    if (actionId === 'mark-paid' && module === 'bills' && id) {
      await Bills.update(id, { paid: true });
    } else if (actionId === 'mark-done' && module === 'tasks' && id) {
      await Tasks.update(id, { status: 'done' });
    } else if (actionId === 'mark-done' && module === 'assignments' && id) {
      await Assignments.update(id, { status: 'done' });
    } else if (actionId === 'renew' && module === 'documents') {
      await Tasks.create({ title: `Renew: ${title || 'document'}`, status: 'not_started', priority: 'low', dueDate: dueDate || null });
    } else if (actionId === 'snooze' && id) {
      // Re-fire this same reminder in 24h. For a task we also push its snooze
      // date so the in-app Briefing agrees. We deliberately DON'T resync after
      // a snooze (a resync would cancel the reminder we just re-armed); the
      // next app open re-syncs from the live feed anyway.
      if (module === 'tasks') { try { await Tasks.update(id, { snoozedUntil: tomorrowStr() }); } catch { /* ignore */ } }
      await scheduleReminder({
        key: `due:${module}:${id}`,
        title: (notification && notification.title) || 'LifeOS',
        body: (notification && notification.body) || '',
        at: new Date(Date.now() + 24 * 60 * 60 * 1000),
        actionTypeId: actionTypeForModule(module),
        extra,
      });
      return;
    } else {
      return;
    }
    // The acted item has left the due-soon feed; resync so its (already-fired)
    // reminder is cleared and the on-device schedule reflects reality.
    await refreshDeviceReminders();
  } catch {
    /* best-effort: never throw out of a notification tap */
  }
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
    await registerNotificationActions();
    const feed = await getDueSoonFeed(14, 30, 30);
    return await syncReminders(remindersFromFeed(feed));
  } catch {
    return 0;
  }
}

// --- Inbound system share sheet (FUTURE_FEATURES.md §13) -----------------
// When another app shares text/a link to LifeOS, MainActivity.java captures the
// ACTION_SEND intent and hands us the payload by setting window.__lifeosSharedIntent
// and dispatching a 'lifeosshared' event. We file a URL into Links and plain
// text into Ideas — the same stores the in-app quick-add uses.

function firstUrl(s) {
  const m = /(https?:\/\/[^\s]+)/i.exec(String(s || ''));
  return m ? m[1].replace(/[)\]}.,;'"]+$/, '') : null;
}

async function routeSharedContent(payload) {
  const text = String((payload && payload.text) || '').trim();
  const subject = String((payload && payload.subject) || '').trim();
  if (!text && !subject) return;
  const url = firstUrl(text) || firstUrl(subject);
  try {
    if (url) {
      const isYouTube = /(?:youtube\.com|youtu\.be)/i.test(url);
      await Links.create({
        type: isYouTube ? 'video' : 'article',
        url,
        title: subject || '',
        tags: [],
        status: 'unread',
        shareWith: '',
        thumbnailUrl: null,
      });
      window.location.hash = '#/links';
    } else {
      const body = subject && subject !== text ? `${subject}\n${text}`.trim() : text;
      await Ideas.create({ text: body, archived: false });
      window.location.hash = '#/ideas';
    }
  } catch {
    /* best-effort capture: never throw out of a share */
  }
}

// Attach the inbound-share handler. Uses the 'lifeosshared' event for the warm
// path, plus a few delayed re-checks of the global for the cold-start path,
// where native may set it around the time the web app is still booting.
function initShareReceiver() {
  const consume = () => {
    const payload = window.__lifeosSharedIntent;
    if (!payload) return;
    window.__lifeosSharedIntent = null;
    routeSharedContent(payload);
  };
  try {
    window.addEventListener('lifeosshared', consume);
    consume();
    setTimeout(consume, 400);
    setTimeout(consume, 1200);
  } catch {
    /* no-op */
  }
}

// --- Clipboard catcher (FUTURE_FEATURES.md §13) --------------------------
// Copy a link or a note anywhere on the phone, then return to LifeOS — it
// offers to file what's on your clipboard (URL → Links, text → Ideas), reusing
// the same routing as the share sheet. Runs when the app comes to foreground
// (Android only allows a foreground, focused app to read the clipboard). Purely
// opt-in per prompt; nothing is filed without a tap, and we never re-offer the
// same clipboard contents twice.

let lastClipboardSeen = '';

// A small, self-contained confirm banner (no app toast helper exists). Native
// capture UI only, so it's inline-styled rather than themed to an interface.
function showCaptureBanner(message, onConfirm, confirmLabel = 'File it') {
  try {
    const existing = document.getElementById('lifeos-capture-banner');
    if (existing) existing.remove();
    const bar = document.createElement('div');
    bar.id = 'lifeos-capture-banner';
    bar.setAttribute('role', 'dialog');
    bar.style.cssText = [
      'position:fixed', 'left:12px', 'right:12px', 'bottom:16px', 'z-index:2147483000',
      'background:#1b1f2a', 'color:#f4f6fb', 'border:1px solid #38405a', 'border-radius:12px',
      'padding:12px 14px', 'box-shadow:0 8px 28px rgba(0,0,0,.45)', 'font:14px/1.4 system-ui,sans-serif',
      'display:flex', 'gap:10px', 'align-items:center', 'justify-content:space-between',
    ].join(';');
    const label = document.createElement('span');
    label.textContent = message;
    label.style.cssText = 'flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap';
    const dismiss = document.createElement('button');
    dismiss.textContent = 'Dismiss';
    dismiss.style.cssText = 'background:transparent;color:#aab3c8;border:0;padding:6px 8px;font:inherit;cursor:pointer';
    dismiss.onclick = () => bar.remove();
    const file = document.createElement('button');
    file.textContent = confirmLabel;
    file.style.cssText = 'background:#4f7cff;color:#fff;border:0;border-radius:8px;padding:8px 14px;font:inherit;font-weight:600;cursor:pointer';
    file.onclick = async () => { bar.remove(); try { await onConfirm(); } catch { /* ignore */ } };
    bar.append(label, dismiss, file);
    document.body.appendChild(bar);
    // Auto-dismiss if ignored, so it never lingers.
    setTimeout(() => { if (bar.isConnected) bar.remove(); }, 12000);
  } catch {
    /* never let capture UI break the app */
  }
}

async function checkClipboard() {
  try {
    if (!navigator.clipboard || typeof navigator.clipboard.readText !== 'function') return;
    const text = (await navigator.clipboard.readText() || '').trim();
    if (!text || text === lastClipboardSeen) return;      // nothing new
    if (text.length > 4000) return;                       // too big to be a capture
    const url = firstUrl(text);
    // Only offer for things that look fileable: a URL, or a short-ish note.
    if (!url && text.length < 3) return;
    lastClipboardSeen = text;
    const preview = text.length > 60 ? `${text.slice(0, 57)}…` : text;
    const dest = url ? 'Links' : 'Ideas';
    showCaptureBanner(`File clipboard to ${dest}? “${preview}”`, () => routeSharedContent({ text }));
  } catch {
    // readText rejects without focus/permission — just skip this pass.
  }
}

// Offer to file the clipboard whenever the app returns to the foreground.
function initClipboardCatcher() {
  try {
    const c = typeof window !== 'undefined' ? window.Capacitor : null;
    const app = c && c.Plugins && c.Plugins.App;
    if (!app || !isPluginAvailable('@capacitor/app')) return;
    app.addListener('resume', () => { setTimeout(checkClipboard, 400); });
    // Also treat a foregrounding appStateChange as a resume (covers cold-ish
    // returns some launchers deliver as state changes rather than resume).
    if (typeof app.addListener === 'function') {
      app.addListener('appStateChange', (state) => { if (state && state.isActive) setTimeout(checkClipboard, 400); });
    }
  } catch {
    /* no-op */
  }
}

/**
 * Sync the persistent "next up" ticker notification with the top Briefing item.
 * Opt-in via the `nextUpTickerEnabled` setting; when off (or nothing needs
 * attention) the ticker is cleared. No-op on web / without permission. Returns
 * true if a ticker is currently shown.
 */
export async function refreshNextUp() {
  if (!canNotify()) return false;
  if ((await notifyPermissionState()) !== 'granted') return false;
  try {
    const enabled = await Settings.get('nextUpTickerEnabled');
    if (!enabled) { await clearNextUp(); return false; }
    const items = await getBriefing();
    const top = items && items[0];
    if (!top) { await clearNextUp(); return false; }
    const detail = top.detail ? `${top.detail}` : '';
    return await showNextUp({ title: `Next up: ${top.title}`, body: detail });
  } catch {
    return false;
  }
}

// --- Charging-cable evening ritual (FUTURE_FEATURES.md §13) --------------
// Plugging in at night is a natural end-of-day trigger. When the app is in the
// foreground and charging in the evening, offer to read your evening Briefing
// aloud. Opt-in (chargingRitualEnabled, off by default) and once per day.

function localDateStr(d = new Date()) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).toISOString().slice(0, 10);
}

async function isCharging() {
  try {
    const c = typeof window !== 'undefined' ? window.Capacitor : null;
    const dev = c && c.Plugins && c.Plugins.Device;
    if (!dev || typeof dev.getBatteryInfo !== 'function') return false;
    const info = await dev.getBatteryInfo();
    return !!(info && info.isCharging);
  } catch {
    return false;
  }
}

async function maybeRunChargingRitual() {
  try {
    if (!hasCapability('battery')) return;
    if (!(await Settings.get('chargingRitualEnabled'))) return;
    const hour = new Date().getHours();
    if (hour < 18 && hour > 3) return;                 // evening / late night only
    const today = localDateStr();
    if ((await Settings.get('chargingRitualLastFired')) === today) return; // once/day
    if (!(await isCharging())) return;

    const items = await getBriefing();
    await Settings.set('chargingRitualLastFired', today);
    if (!items || !items.length) return;               // nothing worth reading

    const top = items.slice(0, 5);
    const spoken = `Good evening. You have ${items.length} thing${items.length === 1 ? '' : 's'} that need${items.length === 1 ? 's' : ''} attention. `
      + top.map((it, i) => `${i + 1}. ${it.title}. ${it.detail || ''}`).join(' ');
    const canRead = canSpeak();
    showCaptureBanner(
      'Plugged in for the night — hear your evening briefing?',
      () => (canRead ? speak(spoken) : (window.location.hash = '#/briefing')),
      canRead ? 'Read it' : 'Open',
    );
  } catch {
    /* never let the ritual break a resume */
  }
}

// Fire the ritual check when the app returns to the foreground.
function initChargingRitual() {
  try {
    const c = typeof window !== 'undefined' ? window.Capacitor : null;
    const app = c && c.Plugins && c.Plugins.App;
    if (!app || !isPluginAvailable('@capacitor/app') || !hasCapability('battery')) return;
    app.addListener('resume', () => { setTimeout(maybeRunChargingRitual, 600); });
    app.addListener('appStateChange', (state) => { if (state && state.isActive) setTimeout(maybeRunChargingRitual, 600); });
  } catch {
    /* no-op */
  }
}

/** One-shot native startup hook. Called by app.js boot; never throws. */
export async function initNative() {
  if (!isNativePlatform()) return;
  try {
    initDeepLinks();
    // Handle notification action-button taps (works even if the tap cold-starts
    // the app: the listener is attached before we do any async awaiting).
    onNotificationAction((event) => {
      applyNotificationAction(event && event.actionId, event && event.notification);
    });
    initShareReceiver();
    initClipboardCatcher();
    initChargingRitual();
    await refreshDeviceReminders();
    await refreshNextUp();
    await maybeRunChargingRitual();
  } catch {
    /* boot must never fail because of a native extra */
  }
}
