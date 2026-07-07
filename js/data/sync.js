// Google Drive sync engine.
//
// Model: each device owns ONE snapshot file in the Drive LifeOS/ folder
// (lifeos-snapshot-<deviceId>.json). A device only ever writes its own file
// and reads every device's — so two devices can never clobber the same file
// (no read-modify-write race). Reconciliation is last-write-wins by each
// record's `updatedAt`, with a tombstone log so deletions propagate instead
// of resurrecting. Attachment binaries live as their own Drive files
// (driveFileId); only their metadata travels in the snapshot JSON.
//
// This is part of the data layer, so it writes through db.* directly,
// preserving each record's original timestamps — NEVER through the api.js
// wrappers that would stamp a fresh updatedAt and corrupt the merge.

import * as db from './db.js';
import { STORE_NAMES } from './schema.js';
import { events } from './events.js';
import {
  acquireToken, hasLiveToken, forgetToken,
  ensureFolder, listFiles, downloadText, downloadBlob,
  createFile, uploadMedia, deleteFile,
} from './gapi.js';
import {
  DRIVE_SCOPE, DRIVE_FOLDER_NAME, SNAPSHOT_PREFIX, ATTACHMENT_PREFIX, SNAPSHOT_FORMAT_VERSION,
} from './sync-config.js';

// Everything except plain key-value settings (device-local preferences with
// no per-key timestamps to merge) and the tombstone log (travels separately).
const SYNC_STORES = STORE_NAMES.filter((n) => n !== 'settings' && n !== '_tombstones');

let inFlight = null; // serializes concurrent sync() calls

// --- device-local sync metadata (kept in `settings`, which is NOT synced) ---

async function getMeta(key) {
  const r = await db.get('settings', key);
  return r ? r.value : undefined;
}
async function setMeta(key, value) {
  await db.put('settings', { key, value }); // direct write: no settings event
}
async function getDeviceId() {
  let id = await getMeta('syncDeviceId');
  if (!id) { id = db.generateId(); await setMeta('syncDeviceId', id); }
  return id;
}

// --- pure merge (no IO, unit-testable) ---
// localStores: { store: [records] }; localTombstones: [tomb];
// remoteSnapshots: [{ stores: {store:[records]}, tombstones:[tomb] }]
// Returns { live: {store: [records]}, tombstones: [tomb], driveDeletes: [id] }
export function mergeState(localStores, localTombstones, remoteSnapshots) {
  // 1. Fold all tombstones: keep the latest deletedAt per key, and remember
  //    any driveFileId ever seen for it.
  const tombMap = new Map(); // key -> { key, store, id, deletedAt, driveFileId }
  const foldTomb = (t) => {
    const prev = tombMap.get(t.key);
    if (!prev) {
      tombMap.set(t.key, { ...t });
    } else {
      if (t.deletedAt > prev.deletedAt) prev.deletedAt = t.deletedAt;
      if (!prev.driveFileId && t.driveFileId) prev.driveFileId = t.driveFileId;
    }
  };
  localTombstones.forEach(foldTomb);
  remoteSnapshots.forEach((s) => (s.tombstones || []).forEach(foldTomb));

  // 2. Fold all record versions: keep the max-updatedAt version per id per
  //    store. Tie on equal updatedAt: prefer the version carrying a
  //    driveFileId (a more-complete attachment record).
  const better = (a, b) => {
    if (b.updatedAt > a.updatedAt) return b;
    if (b.updatedAt < a.updatedAt) return a;
    if (!a.driveFileId && b.driveFileId) return b;
    return a;
  };
  const recMaps = {}; // store -> Map(id -> record)
  for (const store of SYNC_STORES) recMaps[store] = new Map();
  const foldRecords = (stores) => {
    for (const store of SYNC_STORES) {
      for (const rec of stores[store] || []) {
        const m = recMaps[store];
        const prev = m.get(rec.id);
        m.set(rec.id, prev ? better(prev, rec) : rec);
      }
    }
  };
  foldRecords(localStores);
  remoteSnapshots.forEach((s) => foldRecords(s.stores || {}));

  // 3. Resolve record-vs-tombstone per id. A tombstone wins only if its
  //    deletedAt is >= the surviving record's updatedAt — so an edit made
  //    AFTER a delete (edit-wins-if-later) correctly resurrects the record.
  const live = {};
  const driveDeletes = [];
  for (const store of SYNC_STORES) {
    live[store] = [];
    for (const rec of recMaps[store].values()) {
      const tomb = tombMap.get(`${store}:${rec.id}`);
      if (tomb && tomb.deletedAt >= rec.updatedAt) {
        if (tomb.driveFileId) driveDeletes.push(tomb.driveFileId);
      } else {
        live[store].push(rec);
      }
    }
  }

  return { live, tombstones: [...tombMap.values()], driveDeletes };
}

// --- IO: gather / apply / push ---

async function gatherLocal() {
  const stores = {};
  for (const store of SYNC_STORES) {
    const all = await db.getAll(store);
    // Strip attachment blobs from the snapshot view (binaries sync separately).
    stores[store] = store === 'attachments'
      ? all.map(({ blob, ...rest }) => rest)
      : all;
  }
  const tombstones = await db.getAll('_tombstones');
  return { stores, tombstones };
}

async function applyMerged(merged) {
  const affected = new Set();

  for (const store of SYNC_STORES) {
    if (store === 'attachments') continue; // handled below (needs blobs)
    for (const rec of merged.live[store]) {
      const existing = await db.get(store, rec.id);
      if (!existing || existing.updatedAt !== rec.updatedAt) {
        await db.put(store, rec);
        affected.add(store);
      }
    }
    // Delete anything tombstoned that's still present locally.
    const liveIds = new Set(merged.live[store].map((r) => r.id));
    for (const existing of await db.getAll(store)) {
      if (!liveIds.has(existing.id)) {
        await db.remove(store, existing.id);
        affected.add(store);
      }
    }
  }

  // Attachments: upsert metadata, and make sure each live one has its binary
  // (kept locally if we have it, else downloaded via driveFileId).
  const liveAtt = merged.live.attachments || [];
  const liveAttIds = new Set(liveAtt.map((a) => a.id));
  for (const a of liveAtt) {
    let blob = a.blob || null;
    if (!blob) { const local = await db.get('attachments', a.id); blob = local?.blob || null; }
    if (!blob && a.driveFileId) {
      const dl = await downloadBlob(a.driveFileId);
      blob = new Blob([dl], { type: a.mimeType || dl.type });
    }
    const existing = await db.get('attachments', a.id);
    if (!existing || existing.updatedAt !== a.updatedAt || (!existing.blob && blob)) {
      await db.put('attachments', { ...a, blob });
      affected.add('attachments');
    }
  }
  for (const existing of await db.getAll('attachments')) {
    if (!liveAttIds.has(existing.id)) {
      await db.remove('attachments', existing.id);
      affected.add('attachments');
    }
  }

  // Replace the local tombstone log with the merged union.
  await db.clear('_tombstones');
  for (const t of merged.tombstones) await db.put('_tombstones', t);

  return affected;
}

async function pushSnapshot(folderId, deviceId, existingSnapshotFiles) {
  // Upload any local attachment binaries that aren't on Drive yet, recording
  // their driveFileId (WITHOUT bumping updatedAt — a storage detail, not an
  // edit) before the snapshot is built so the id travels with the metadata.
  for (const a of await db.getAll('attachments')) {
    if (a.blob && !a.driveFileId) {
      const type = a.mimeType || 'application/octet-stream';
      const fid = await createFile(`${ATTACHMENT_PREFIX}${a.id}`, folderId, type);
      await uploadMedia(fid, a.blob, type);
      await db.put('attachments', { ...a, driveFileId: fid });
    }
  }

  const local = await gatherLocal();
  const snapshot = {
    app: 'lifeos-sync',
    version: SNAPSHOT_FORMAT_VERSION,
    deviceId,
    writtenAt: new Date().toISOString(),
    stores: local.stores,
    tombstones: local.tombstones,
  };
  const name = `${SNAPSHOT_PREFIX}${deviceId}.json`;
  const mine = existingSnapshotFiles.find((f) => f.name === name);
  const fileId = mine ? mine.id : await createFile(name, folderId, 'application/json');
  await uploadMedia(fileId, JSON.stringify(snapshot), 'application/json');
}

// The one entry point. `interactive` controls whether token acquisition may
// show Google's consent/account UI.
async function sync(interactive) {
  if (inFlight) return inFlight;
  inFlight = (async () => {
    await acquireToken(DRIVE_SCOPE, interactive); // throws if not granted / offline
    const deviceId = await getDeviceId();

    let folderId = await getMeta('syncFolderId');
    if (!folderId) { folderId = await ensureFolder(DRIVE_FOLDER_NAME); await setMeta('syncFolderId', folderId); }

    const snapshotFiles = await listFiles(folderId, SNAPSHOT_PREFIX);
    const remoteSnapshots = [];
    for (const f of snapshotFiles) {
      try {
        const parsed = JSON.parse(await downloadText(f.id));
        if (parsed && parsed.app === 'lifeos-sync') remoteSnapshots.push(parsed);
      } catch (err) {
        console.warn(`sync: skipping unreadable snapshot ${f.name}`, err);
      }
    }

    const local = await gatherLocal();
    const merged = mergeState(local.stores, local.tombstones, remoteSnapshots);

    const affected = await applyMerged(merged);
    await pushSnapshot(folderId, deviceId, snapshotFiles);

    // Best-effort cleanup of orphaned attachment binaries for deletes that
    // carried a driveFileId. Failure here never affects data integrity.
    for (const fileId of merged.driveDeletes) {
      try { await deleteFile(fileId); } catch (err) { console.warn('sync: orphan cleanup failed', err); }
    }

    await setMeta('syncLastSyncedAt', new Date().toISOString());
    await setMeta('syncEnabled', true);

    for (const store of affected) events.emit(store, { action: 'sync' });
    return { affected: [...affected] };
  })();
  try {
    return await inFlight;
  } finally {
    inFlight = null;
  }
}

// --- public API (used by the Settings view) ---

export async function connectDrive() {
  return sync(true); // interactive: first-ever run shows Google consent
}

export async function syncNow() {
  return sync(false); // silent token; throws if never connected / offline
}

export async function disconnectDrive() {
  forgetToken(DRIVE_SCOPE);
  await setMeta('syncEnabled', false);
}

export async function getSyncState() {
  return {
    enabled: (await getMeta('syncEnabled')) === true,
    connected: hasLiveToken(DRIVE_SCOPE),
    lastSyncedAt: await getMeta('syncLastSyncedAt'),
    deviceId: await getMeta('syncDeviceId'),
  };
}
