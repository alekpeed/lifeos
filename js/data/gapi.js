// Google Identity Services token acquisition + minimal Google REST helpers
// (Drive and Calendar). Isolated here so sync.js / calendar.js deal in
// "get/put files" and "insert/patch events" rather than OAuth and HTTP details.
//
// Network-only by design: the GIS script and all googleapis.com calls are
// cross-origin, so the service worker's fetch handler ignores them (it
// returns early for non-same-origin requests), and none of this is part of
// the offline app shell. The app runs fully without ever loading any of it;
// sync (Drive or Calendar) is purely additive.
//
// Tokens are managed per-scope: Drive and Calendar each get their own token
// client and their own in-memory access token, so the two features are
// independent — granting one never implies the other, matching least-privilege.

import { GOOGLE_CLIENT_ID, DRIVE_SCOPE, CALENDAR_SCOPE, GIS_SCRIPT_URL } from './sync-config.js';

let gisLoaded = null; // Promise that resolves once the GIS script is ready

// scope -> { client, token: { accessToken, expiresAt } | null, pending }
const clients = new Map();

function loadGis() {
  if (gisLoaded) return gisLoaded;
  gisLoaded = new Promise((resolve, reject) => {
    if (window.google?.accounts?.oauth2) return resolve();
    const script = document.createElement('script');
    script.src = GIS_SCRIPT_URL;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Could not load Google sign-in (offline?).'));
    document.head.appendChild(script);
  });
  return gisLoaded;
}

async function ensureClient(scope) {
  await loadGis();
  let entry = clients.get(scope);
  if (entry) return entry;
  entry = { client: null, token: null, pending: null };
  entry.client = window.google.accounts.oauth2.initTokenClient({
    client_id: GOOGLE_CLIENT_ID,
    scope,
    callback: (resp) => {
      if (resp.error) {
        entry.pending?.reject(new Error(resp.error_description || resp.error));
      } else {
        entry.token = { accessToken: resp.access_token, expiresAt: Date.now() + (resp.expires_in - 60) * 1000 };
        entry.pending?.resolve(entry.token.accessToken);
      }
      entry.pending = null;
    },
  });
  clients.set(scope, entry);
  return entry;
}

// Acquire an access token for `scope`. `interactive` true shows Google's
// consent/account chooser (used for an explicit "Connect" button); false
// requests silently (works on later syncs once consent was granted).
export async function acquireToken(scope, interactive) {
  const existing = clients.get(scope);
  if (existing?.token && existing.token.expiresAt > Date.now()) return existing.token.accessToken;
  const entry = await ensureClient(scope);
  return new Promise((resolve, reject) => {
    entry.pending = { resolve, reject };
    try {
      entry.client.requestAccessToken({ prompt: interactive ? 'consent' : '' });
    } catch (err) {
      entry.pending = null;
      reject(err);
    }
  });
}

export function hasLiveToken(scope) {
  const entry = clients.get(scope);
  return !!(entry?.token && entry.token.expiresAt > Date.now());
}

export function forgetToken(scope) {
  const entry = clients.get(scope);
  if (entry) entry.token = null;
}

async function authFetch(scope, url, options = {}) {
  const accessToken = await acquireToken(scope, false);
  const resp = await fetch(url, {
    ...options,
    headers: { Authorization: `Bearer ${accessToken}`, ...(options.headers || {}) },
  });
  if (!resp.ok) {
    const body = await resp.text().catch(() => '');
    throw new Error(`Google API ${resp.status}: ${body.slice(0, 200)}`);
  }
  return resp;
}

// --- Drive REST (scope: drive.file) ---

const DRIVE = 'https://www.googleapis.com/drive/v3';
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3';

// Find the app's LifeOS folder (drive.file only sees files we created), or
// create it. Returns the folder id.
export async function ensureFolder(name) {
  const q = encodeURIComponent(
    `name='${name}' and mimeType='application/vnd.google-apps.folder' and trashed=false`
  );
  const found = await authFetch(DRIVE_SCOPE, `${DRIVE}/files?q=${q}&fields=files(id,name)&spaces=drive`);
  const { files } = await found.json();
  if (files.length) return files[0].id;

  const created = await authFetch(DRIVE_SCOPE, `${DRIVE}/files?fields=id`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, mimeType: 'application/vnd.google-apps.folder' }),
  });
  return (await created.json()).id;
}

// List files in a folder whose name starts with `prefix`.
export async function listFiles(folderId, prefix) {
  const q = encodeURIComponent(`'${folderId}' in parents and name contains '${prefix}' and trashed=false`);
  const resp = await authFetch(DRIVE_SCOPE, `${DRIVE}/files?q=${q}&fields=files(id,name,modifiedTime)&spaces=drive`);
  const { files } = await resp.json();
  // `contains` is a loose match; keep only true prefix matches.
  return files.filter((f) => f.name.startsWith(prefix));
}

export async function downloadText(fileId) {
  const resp = await authFetch(DRIVE_SCOPE, `${DRIVE}/files/${fileId}?alt=media`);
  return resp.text();
}

export async function downloadBlob(fileId) {
  const resp = await authFetch(DRIVE_SCOPE, `${DRIVE}/files/${fileId}?alt=media`);
  return resp.blob();
}

// Create an (empty) file with metadata; returns its id. Content is written
// separately via uploadMedia — a two-step create avoids hand-rolling
// multipart bodies (which is especially error-prone for binary blobs).
export async function createFile(name, folderId, mimeType) {
  const resp = await authFetch(DRIVE_SCOPE, `${DRIVE}/files?fields=id`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, parents: [folderId], mimeType }),
  });
  return (await resp.json()).id;
}

export async function uploadMedia(fileId, body, contentType) {
  await authFetch(DRIVE_SCOPE, `${UPLOAD}/files/${fileId}?uploadType=media`, {
    method: 'PATCH',
    headers: { 'Content-Type': contentType },
    body,
  });
}

export async function deleteFile(fileId) {
  await authFetch(DRIVE_SCOPE, `${DRIVE}/files/${fileId}`, { method: 'DELETE' });
}

// --- Calendar REST (scope: calendar.app.created) ---

const CAL = 'https://www.googleapis.com/calendar/v3';

// Every calendar this OAuth client created (app.created scope returns only
// those). Returned as [{ id, summary }] so calendar.js can find-or-create the
// shared "Life OS" calendar by name.
export async function listAppCalendars() {
  const resp = await authFetch(CALENDAR_SCOPE, `${CAL}/users/me/calendarList?minAccessRole=owner&maxResults=250`);
  const { items } = await resp.json();
  return (items || []).map((c) => ({ id: c.id, summary: c.summary }));
}

export async function createCalendar(summary) {
  const resp = await authFetch(CALENDAR_SCOPE, `${CAL}/calendars`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ summary }),
  });
  return (await resp.json()).id;
}

// List only the events Life OS owns on this calendar (filtered by our private
// extended property), so a resync never sees or disturbs events the user added
// by hand. One page is plenty for a personal due-soon feed.
export async function listEvents(calendarId, appTag) {
  const q = `privateExtendedProperty=${encodeURIComponent(`${appTag}=1`)}&maxResults=2500&showDeleted=false`;
  const resp = await authFetch(CALENDAR_SCOPE, `${CAL}/calendars/${encodeURIComponent(calendarId)}/events?${q}`);
  const { items } = await resp.json();
  return items || [];
}

export async function insertEvent(calendarId, event) {
  const resp = await authFetch(CALENDAR_SCOPE, `${CAL}/calendars/${encodeURIComponent(calendarId)}/events`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(event),
  });
  return (await resp.json()).id;
}

export async function patchEvent(calendarId, eventId, patch) {
  await authFetch(CALENDAR_SCOPE, `${CAL}/calendars/${encodeURIComponent(calendarId)}/events/${encodeURIComponent(eventId)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(patch),
  });
}

export async function deleteEvent(calendarId, eventId) {
  await authFetch(CALENDAR_SCOPE, `${CAL}/calendars/${encodeURIComponent(calendarId)}/events/${encodeURIComponent(eventId)}`, {
    method: 'DELETE',
  });
}
