// Sharebox sync engine — the same per-device-snapshot, last-write-wins +
// tombstone model as Drive sync (js/data/sync.js), but pointed at a folder you
// and a friend both chose via the Picker instead of your private LifeOS/
// folder. It reuses the exact merge core (mergeState) from sync.js; only the
// folder, the store list, and the tombstone store differ.
//
// Kept entirely separate from personal Drive sync: its stores are excluded
// from that engine's SYNC_STORES, and it has its OWN tombstone store because
// personal sync clears/rewrites `_tombstones` on every run. Writes go through
// db.* directly, preserving timestamps (never api.js's updatedAt-stamping
// wrappers) so applying a remote change can't corrupt the merge.

import * as db from './db.js';
import { mergeState } from './sync.js';
import { events } from './events.js';
import {
  acquireToken, hasLiveToken,
  listFiles, downloadText, downloadBlob, createFile, uploadMedia, deleteFile,
} from './gapi.js';
import {
  DRIVE_SCOPE, SHAREBOX_SNAPSHOT_PREFIX, SHAREBOX_ATTACHMENT_PREFIX, SNAPSHOT_FORMAT_VERSION,
} from './sync-config.js';
import { pickFolder } from './picker.js';

// Record stores merged in a snapshot (files carry blobs; items are metadata).
const RECORD_STORES = ['shareboxItems', 'shareboxFiles'];

let inFlight = null;

async function getMeta(key) { const r = await db.get('settings', key); return r ? r.value : undefined; }
async function setMeta(key, value) { await db.put('settings', { key, value }); }
async function getDeviceId() {
  let id = await getMeta('syncDeviceId');
  if (!id) { id = db.generateId(); await setMeta('syncDeviceId', id); }
  return id;
}

async function gatherLocal() {
  const stores = {
    shareboxItems: await db.getAll('shareboxItems'),
    // strip blobs — file binaries travel as their own Drive files, not in JSON
    shareboxFiles: (await db.getAll('shareboxFiles')).map(({ blob, ...rest }) => rest),
  };
  const tombstones = await db.getAll('_shareboxTombstones');
  return { stores, tombstones };
}

async function applyMerged(merged) {
  const affected = new Set();

  for (const rec of merged.live.shareboxItems) {
    const existing = await db.get('shareboxItems', rec.id);
    if (!existing || existing.updatedAt !== rec.updatedAt) { await db.put('shareboxItems', rec); affected.add('shareboxItems'); }
  }
  const liveItemIds = new Set(merged.live.shareboxItems.map((r) => r.id));
  for (const existing of await db.getAll('shareboxItems')) {
    if (!liveItemIds.has(existing.id)) { await db.remove('shareboxItems', existing.id); affected.add('shareboxItems'); }
  }

  const liveFiles = merged.live.shareboxFiles || [];
  const liveFileIds = new Set(liveFiles.map((f) => f.id));
  for (const f of liveFiles) {
    let blob = f.blob || null;
    if (!blob) { const local = await db.get('shareboxFiles', f.id); blob = local?.blob || null; }
    if (!blob && f.driveFileId) { const dl = await downloadBlob(f.driveFileId); blob = new Blob([dl], { type: f.mimeType || dl.type }); }
    const existing = await db.get('shareboxFiles', f.id);
    if (!existing || existing.updatedAt !== f.updatedAt || (!existing.blob && blob)) { await db.put('shareboxFiles', { ...f, blob }); affected.add('shareboxFiles'); }
  }
  for (const existing of await db.getAll('shareboxFiles')) {
    if (!liveFileIds.has(existing.id)) { await db.remove('shareboxFiles', existing.id); affected.add('shareboxFiles'); }
  }

  await db.clear('_shareboxTombstones');
  for (const t of merged.tombstones) await db.put('_shareboxTombstones', t);
  return affected;
}

async function pushSnapshot(folderId, deviceId, existingSnapshotFiles) {
  for (const f of await db.getAll('shareboxFiles')) {
    if (f.blob && !f.driveFileId) {
      const type = f.mimeType || 'application/octet-stream';
      const fid = await createFile(`${SHAREBOX_ATTACHMENT_PREFIX}${f.id}`, folderId, type);
      await uploadMedia(fid, f.blob, type);
      await db.put('shareboxFiles', { ...f, driveFileId: fid });
    }
  }
  const local = await gatherLocal();
  const snapshot = {
    app: 'sharebox-sync', version: SNAPSHOT_FORMAT_VERSION,
    deviceId, writtenAt: new Date().toISOString(),
    stores: local.stores, tombstones: local.tombstones,
  };
  const name = `${SHAREBOX_SNAPSHOT_PREFIX}${deviceId}.json`;
  const mine = existingSnapshotFiles.find((fl) => fl.name === name);
  const fileId = mine ? mine.id : await createFile(name, folderId, 'application/json');
  await uploadMedia(fileId, JSON.stringify(snapshot), 'application/json');
}

async function sync(interactive) {
  if (inFlight) return inFlight;
  inFlight = (async () => {
    await acquireToken(DRIVE_SCOPE, interactive);
    const folderId = await getMeta('shareboxFolderId');
    if (!folderId) throw new Error('No shared folder connected yet.');
    const deviceId = await getDeviceId();

    const snapshotFiles = await listFiles(folderId, SHAREBOX_SNAPSHOT_PREFIX);
    const remoteSnapshots = [];
    for (const f of snapshotFiles) {
      try {
        const parsed = JSON.parse(await downloadText(f.id));
        if (parsed && parsed.app === 'sharebox-sync') remoteSnapshots.push(parsed);
      } catch (err) { console.warn(`sharebox: skipping unreadable snapshot ${f.name}`, err); }
    }

    const local = await gatherLocal();
    const merged = mergeState(local.stores, local.tombstones, remoteSnapshots, RECORD_STORES);
    const affected = await applyMerged(merged);
    await pushSnapshot(folderId, deviceId, snapshotFiles);

    for (const fileId of merged.driveDeletes) {
      try { await deleteFile(fileId); } catch (err) { console.warn('sharebox: orphan cleanup failed', err); }
    }

    await setMeta('shareboxLastSyncedAt', new Date().toISOString());
    await setMeta('shareboxEnabled', true);
    for (const store of affected) events.emit(store, { action: 'sync' });
    return { affected: [...affected] };
  })();
  try { return await inFlight; } finally { inFlight = null; }
}

// --- public API (used by the Sharebox view via api.js re-export) ---

// Connect: grab a drive.file token, let the user pick the shared folder (which
// grants access to it), remember it, and run a first sync.
export async function connectSharebox() {
  const token = await acquireToken(DRIVE_SCOPE, true);
  const folder = await pickFolder(token);
  if (!folder) return { cancelled: true };
  await setMeta('shareboxFolderId', folder.id);
  await setMeta('shareboxFolderName', folder.name);
  const res = await sync(false);
  return { connected: true, folderName: folder.name, ...res };
}

export async function syncShareboxNow() { return sync(false); }

// Stop syncing this space. Deliberately does NOT forget the drive.file token —
// that token is shared with personal Drive sync, so dropping it would break
// that too. Just clears the folder link.
export async function disconnectSharebox() {
  await setMeta('shareboxEnabled', false);
  await setMeta('shareboxFolderId', null);
}

export async function getShareboxState() {
  return {
    enabled: (await getMeta('shareboxEnabled')) === true,
    connected: hasLiveToken(DRIVE_SCOPE),
    folderId: await getMeta('shareboxFolderId'),
    folderName: await getMeta('shareboxFolderName'),
    lastSyncedAt: await getMeta('shareboxLastSyncedAt'),
  };
}
