// Google Calendar sync engine.
//
// Model: a ONE-WAY push. Life OS is the source of truth; it mirrors its
// due-soon items (open tasks, unpaid bills, open assignments, document
// expiries) into a dedicated "Life OS" calendar so they show up natively on
// your phone/desktop calendar with reminders. It is NOT bidirectional — the
// app never reads your primary calendar and never imports events back.
//
// Every device pushes the SAME desired event set into the SAME shared calendar
// (found-or-created by name). That's safe because reconciliation is keyed by
// each event's source record (`lifeosKey` extended property), so a second
// device's push simply sees the events already there and makes no change. Only
// events Life OS itself created are ever listed or touched — anything you add
// to the calendar by hand is invisible to this code.
//
// This is part of the data layer, so it reads through db.* directly rather than
// through api.js (avoiding a circular import and keeping the boundary clean).

import * as db from './db.js';
import {
  acquireToken, hasLiveToken, forgetToken,
  listAppCalendars, createCalendar, listEvents, insertEvent, patchEvent, deleteEvent,
} from './gapi.js';
import {
  CALENDAR_SCOPE, CALENDAR_NAME, CALENDAR_HORIZON_DEFAULT,
  CALENDAR_APP_TAG, CALENDAR_KEY_PROP,
} from './sync-config.js';

let inFlight = null; // serializes concurrent push() calls

// --- device-local metadata (kept in `settings`, which is NOT synced) ---

async function getMeta(key) {
  const r = await db.get('settings', key);
  return r ? r.value : undefined;
}
async function setMeta(key, value) {
  await db.put('settings', { key, value }); // direct write: no settings event
}

// --- pure: which events SHOULD exist (no IO, unit-testable) ---
// Mirrors the Dashboard's due-soon sources, but with a calendar-appropriate
// horizon (default 90 days out). Overdue-but-still-open items are always
// included regardless of horizon — the upper bound is all that's checked, so a
// still-unpaid bill from last month stays on the calendar on its real date.
// Returns [{ key, summary, date }] with `date` a plain YYYY-MM-DD (all-day).

function within(dateStr, now, horizonDays) {
  if (!dateStr) return false;
  const cutoff = new Date(now);
  cutoff.setDate(cutoff.getDate() + horizonDays);
  return new Date(dateStr) <= cutoff;
}

export function desiredEventsFrom(stores, horizonDays, now = new Date()) {
  const { tasks = [], bills = [], assignments = [], documents = [] } = stores;
  const out = [];
  const add = (store, id, summary, date) => {
    if (!within(date, now, horizonDays)) return;
    out.push({ key: `${store}:${id}`, summary, date: String(date).slice(0, 10) });
  };

  for (const t of tasks) {
    if (t.status !== 'done') add('tasks', t.id, `Task: ${t.title || '(untitled)'}`, t.dueDate);
  }
  for (const b of bills) {
    if (!b.paid) add('bills', b.id, `Bill due: ${b.name || '(untitled)'}`, b.dueDate);
  }
  for (const a of assignments) {
    if (a.status !== 'done') add('assignments', a.id, `Assignment: ${a.title || '(untitled)'}`, a.dueDate);
  }
  for (const d of documents) {
    add('documents', d.id, `Expires: ${d.title || '(untitled)'}`, d.expiryDate);
  }
  return out;
}

// --- pure: diff desired vs. what's already on the calendar (no IO) ---
// desired:  [{ key, summary, date }]
// existing: [{ id, key, summary, date }]   (id + key from the Google event)
// Returns { inserts: [item], patches: [{ id, item }], deletes: [id] }.
// Idempotent: reconcile(desired, <events already matching desired>) is empty.
// Duplicate existing events sharing a key (e.g. from an interrupted run) keep
// one canonical event and delete the rest, self-healing over time.

export function reconcileEvents(desired, existing) {
  const existingByKey = new Map();
  for (const ev of existing) {
    if (!ev.key) continue; // ignore anything without our key (shouldn't happen)
    if (!existingByKey.has(ev.key)) existingByKey.set(ev.key, []);
    existingByKey.get(ev.key).push(ev);
  }

  const inserts = [];
  const patches = [];
  const deletes = [];
  const desiredKeys = new Set();

  for (const item of desired) {
    desiredKeys.add(item.key);
    const matches = existingByKey.get(item.key);
    if (!matches || !matches.length) {
      inserts.push(item);
      continue;
    }
    const [canonical, ...extras] = matches;
    if (canonical.summary !== item.summary || canonical.date !== item.date) {
      patches.push({ id: canonical.id, item });
    }
    for (const dup of extras) deletes.push(dup.id); // drop accidental duplicates
  }

  for (const [key, matches] of existingByKey) {
    if (desiredKeys.has(key)) continue; // handled above
    for (const ev of matches) deletes.push(ev.id); // source gone → remove event
  }

  return { inserts, patches, deletes };
}

// --- event body construction ---

function nextDay(dateStr) {
  const d = new Date(`${dateStr}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}

function eventBody(item) {
  return {
    summary: item.summary,
    start: { date: item.date },
    end: { date: nextDay(item.date) }, // all-day end is exclusive (next day)
    transparency: 'transparent',        // a reminder, not a "busy" block
    reminders: { useDefault: true },    // respect the user's own calendar defaults
    extendedProperties: { private: { [CALENDAR_APP_TAG]: '1', [CALENDAR_KEY_PROP]: item.key } },
  };
}

function patchBody(item) {
  // Only the fields that can change; the extended properties (identity) stay.
  return { summary: item.summary, start: { date: item.date }, end: { date: nextDay(item.date) } };
}

// --- IO orchestration ---

// The shared calendar: cached locally once known, else found-or-created by
// name among the calendars this app created (mirrors Drive's ensureFolder).
async function ensureCalendar() {
  let calId = await getMeta('calendarId');
  if (calId) return calId;
  const cals = await listAppCalendars();
  const found = cals.find((c) => c.summary === CALENDAR_NAME);
  calId = found ? found.id : await createCalendar(CALENDAR_NAME);
  await setMeta('calendarId', calId);
  return calId;
}

async function push(interactive) {
  if (inFlight) return inFlight;
  inFlight = (async () => {
    await acquireToken(CALENDAR_SCOPE, interactive); // throws if not granted / offline
    const horizon = (await getMeta('calendarHorizonDays')) || CALENDAR_HORIZON_DEFAULT;
    const calId = await ensureCalendar();

    const [tasks, bills, assignments, documents] = await Promise.all([
      db.getAll('tasks'), db.getAll('bills'), db.getAll('assignments'), db.getAll('documents'),
    ]);
    const desired = desiredEventsFrom({ tasks, bills, assignments, documents }, horizon);

    const existing = (await listEvents(calId, CALENDAR_APP_TAG)).map((e) => ({
      id: e.id,
      key: e.extendedProperties?.private?.[CALENDAR_KEY_PROP],
      summary: e.summary,
      date: e.start?.date,
    }));

    const { inserts, patches, deletes } = reconcileEvents(desired, existing);
    for (const item of inserts) await insertEvent(calId, eventBody(item));
    for (const { id, item } of patches) await patchEvent(calId, id, patchBody(item));
    for (const id of deletes) {
      try { await deleteEvent(calId, id); } catch (err) { console.warn('calendar: delete failed', err); }
    }

    await setMeta('calendarLastSyncedAt', new Date().toISOString());
    await setMeta('calendarEnabled', true);
    return { added: inserts.length, updated: patches.length, removed: deletes.length };
  })();
  try {
    return await inFlight;
  } finally {
    inFlight = null;
  }
}

// --- public API (used by the Settings view via api.js re-export) ---

export async function connectCalendar() {
  return push(true); // interactive: first-ever run shows Google consent
}

export async function syncCalendarNow() {
  return push(false); // silent token; throws if never connected / offline
}

// Stops pushing; leaves the calendar and its events in place (delete the
// "Life OS" calendar from Google Calendar directly if you want them gone).
export async function disconnectCalendar() {
  forgetToken(CALENDAR_SCOPE);
  await setMeta('calendarEnabled', false);
}

export async function getCalendarState() {
  return {
    enabled: (await getMeta('calendarEnabled')) === true,
    connected: hasLiveToken(CALENDAR_SCOPE),
    lastSyncedAt: await getMeta('calendarLastSyncedAt'),
    calendarId: await getMeta('calendarId'),
  };
}
