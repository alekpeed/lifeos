// Google Identity Services token acquisition + minimal Google Drive REST
// helpers. Isolated here so sync.js deals in "get/put files" rather than
// OAuth and HTTP details.
//
// Network-only by design: the GIS script and all googleapis.com calls are
// cross-origin, so the service worker's fetch handler ignores them (it
// returns early for non-same-origin requests), and none of this is part of
// the offline app shell. The app runs fully without ever loading any of it;
// sync is purely additive.

import { GOOGLE_CLIENT_ID, DRIVE_SCOPE, GIS_SCRIPT_URL } from './sync-config.js';

let gisLoaded = null;      // Promise that resolves once the GIS script is ready
let tokenClient = null;    // memoized google.accounts.oauth2 token client
let token = null;          // { accessToken, expiresAt } — in-memory only
let pending = null;        // { resolve, reject } for the in-flight token request

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

async function ensureTokenClient() {
  await loadGis();
  if (tokenClient) return tokenClient;
  tokenClient = window.google.accounts.oauth2.initTokenClient({
    client_id: GOOGLE_CLIENT_ID,
    scope: DRIVE_SCOPE,
    callback: (resp) => {
      if (resp.error) {
        pending?.reject(new Error(resp.error_description || resp.error));
      } else {
        token = { accessToken: resp.access_token, expiresAt: Date.now() + (resp.expires_in - 60) * 1000 };
        pending?.resolve(token.accessToken);
      }
      pending = null;
    },
  });
  return tokenClient;
}

// Acquire an access token. `interactive` true shows Google's consent/account
// chooser (used for the explicit "Connect" button); false requests silently
// (works on later syncs once consent was granted, even in a new session).
export async function acquireToken(interactive) {
  if (token && token.expiresAt > Date.now()) return token.accessToken;
  const client = await ensureTokenClient();
  return new Promise((resolve, reject) => {
    pending = { resolve, reject };
    try {
      client.requestAccessToken({ prompt: interactive ? 'consent' : '' });
    } catch (err) {
      pending = null;
      reject(err);
    }
  });
}

export function hasLiveToken() {
  return !!(token && token.expiresAt > Date.now());
}

export function forgetToken() {
  token = null;
}

// --- Drive REST ---

const DRIVE = 'https://www.googleapis.com/drive/v3';
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3';

async function authFetch(url, options = {}) {
  const accessToken = await acquireToken(false);
  const resp = await fetch(url, {
    ...options,
    headers: { Authorization: `Bearer ${accessToken}`, ...(options.headers || {}) },
  });
  if (!resp.ok) {
    const body = await resp.text().catch(() => '');
    throw new Error(`Drive API ${resp.status}: ${body.slice(0, 200)}`);
  }
  return resp;
}

// Find the app's LifeOS folder (drive.file only sees files we created), or
// create it. Returns the folder id.
export async function ensureFolder(name) {
  const q = encodeURIComponent(
    `name='${name}' and mimeType='application/vnd.google-apps.folder' and trashed=false`
  );
  const found = await authFetch(`${DRIVE}/files?q=${q}&fields=files(id,name)&spaces=drive`);
  const { files } = await found.json();
  if (files.length) return files[0].id;

  const created = await authFetch(`${DRIVE}/files?fields=id`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, mimeType: 'application/vnd.google-apps.folder' }),
  });
  return (await created.json()).id;
}

// List files in a folder whose name starts with `prefix`.
export async function listFiles(folderId, prefix) {
  const q = encodeURIComponent(`'${folderId}' in parents and name contains '${prefix}' and trashed=false`);
  const resp = await authFetch(`${DRIVE}/files?q=${q}&fields=files(id,name,modifiedTime)&spaces=drive`);
  const { files } = await resp.json();
  // `contains` is a loose match; keep only true prefix matches.
  return files.filter((f) => f.name.startsWith(prefix));
}

export async function downloadText(fileId) {
  const resp = await authFetch(`${DRIVE}/files/${fileId}?alt=media`);
  return resp.text();
}

export async function downloadBlob(fileId) {
  const resp = await authFetch(`${DRIVE}/files/${fileId}?alt=media`);
  return resp.blob();
}

// Create an (empty) file with metadata; returns its id. Content is written
// separately via uploadMedia — a two-step create avoids hand-rolling
// multipart bodies (which is especially error-prone for binary blobs).
export async function createFile(name, folderId, mimeType) {
  const resp = await authFetch(`${DRIVE}/files?fields=id`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, parents: [folderId], mimeType }),
  });
  return (await resp.json()).id;
}

export async function uploadMedia(fileId, body, contentType) {
  await authFetch(`${UPLOAD}/files/${fileId}?uploadType=media`, {
    method: 'PATCH',
    headers: { 'Content-Type': contentType },
    body,
  });
}

export async function deleteFile(fileId) {
  await authFetch(`${DRIVE}/files/${fileId}`, { method: 'DELETE' });
}
