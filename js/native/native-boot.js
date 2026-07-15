// native-boot.js — native-only wiring that runs once at app startup.
//
// Kept separate from app.js so the boot path stays readable and so all of this
// is trivially a no-op on the web: every branch gates on the capability layer.
// app.js calls initNative() after the shell is up; it never blocks boot and
// never throws into it.

import { isNativePlatform } from './capabilities.js';
import { canNotify, notifyPermissionState, syncReminders } from './notify.js';
import { getDueSoonFeed } from '../data/api.js';

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
    await refreshDeviceReminders();
  } catch {
    /* boot must never fail because of a native extra */
  }
}
