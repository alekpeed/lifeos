// Supabase personal-data sync engine -- the planned replacement for the Google
// Drive snapshot sync (js/data/sync.js), built ALONGSIDE it (nothing here
// touches or disables the Drive path). Backend schema: sql/supabase-personal-
// sync-schema.sql (one `sync_records` table, JSONB per record, RLS scoped to
// the signed-in user; attachment binaries in the private `lifeos-attachments`
// Storage bucket).
//
// Model: Postgres is a relay, IndexedDB stays the source of truth (offline
// still works, unchanged). Reconciliation is the SAME last-write-wins-by-
// updatedAt rule as the Drive engine, with soft-delete tombstones (deleted_at)
// that win only if their timestamp is >= the record's -- so an edit made after
// a delete correctly resurrects. The pure reconcile() below is IO-free and
// unit-tested; everything under "--- IO ---" is the Supabase/IndexedDB plumbing
// that needs a live signed-in session to exercise.
//
// Like the Drive engine, this writes to IndexedDB through db.* DIRECTLY (never
// the api.js wrappers), preserving each record's original updatedAt so applying
// a remote change can't corrupt the merge.

import * as db from './db.js';
import { STORE_NAMES } from './schema.js';
import { events } from './events.js';
import { getSupabaseClient } from './supabase-client.js';
import { isSupabaseConfigured } from './supabase-config.js';

const TABLE = 'sync_records';
const ATTACHMENT_BUCKET = 'lifeos-attachments';
const PAGE = 1000;      // Supabase's default max rows per select
const UPSERT_CHUNK = 500;

// Same set the Drive engine syncs: everything except device-local settings, the
// tombstone log (travels via deleted_at here, not as rows), and the Sharebox
// stores (their own Supabase tables, not this generic one).
const SHAREBOX_STORES = ['shareboxItems', 'shareboxFiles', '_shareboxTombstones'];
const SYNC_STORES = STORE_NAMES.filter(
  // `embeddings` is derived, device-local data (each device rebuilds its own
  // Ask index); never sync the vectors.
  (n) => n !== 'settings' && n !== '_tombstones' && n !== 'embeddings' && !SHAREBOX_STORES.includes(n)
);

let inFlight = null;

// --- pure reconcile (no IO, unit-testable) ---
//
// localStores:     { store: [records] }   (attachment blobs already stripped)
// localTombstones: [{ key: 'store:id', store, id, deletedAt }]
// remoteRows:      [{ store, record_id, data, updated_at, deleted_at }]
// Returns:
//   localPuts:       { store: [records] }  -- write into IndexedDB (remote won)
//   localDeletes:    { store: [ids] }      -- delete from IndexedDB (tombstoned)
//   remoteUpserts:   [{ store, record_id, data, updated_at, deleted_at }] (local won)
//   mergedTombstones:[{ key, store, id, deletedAt }] -- the reconciled tombstone log
// Compare timestamps by INSTANT, not string: the app writes updatedAt as
// `...Z` ISO, but Postgres reads timestamptz back as `...+00:00`, so the same
// moment has two spellings. String comparison would spuriously re-sync (best
// case) or pick the wrong winner (worst case); epoch millis is format-agnostic.
function ms(t) {
  return t ? new Date(t).getTime() : null;
}

export function reconcile(localStores, localTombstones, remoteRows, storeNames = SYNC_STORES) {
  const inScope = new Set(storeNames);
  const localRecByKey = new Map();
  const localTombByKey = new Map();
  const remoteByKey = new Map();
  const keys = new Set();

  for (const s of storeNames) {
    for (const rec of localStores[s] || []) {
      const key = `${s}:${rec.id}`;
      localRecByKey.set(key, rec);
      keys.add(key);
    }
  }
  for (const t of localTombstones) {
    if (!inScope.has(t.store)) continue;
    localTombByKey.set(t.key, t);
    keys.add(t.key);
  }
  for (const r of remoteRows) {
    if (!inScope.has(r.store)) continue;
    remoteByKey.set(`${r.store}:${r.record_id}`, r);
    keys.add(`${r.store}:${r.record_id}`);
  }

  const localPuts = {};
  const localDeletes = {};
  for (const s of storeNames) { localPuts[s] = []; localDeletes[s] = []; }
  const remoteUpserts = [];
  const mergedTombstones = [];

  for (const key of keys) {
    const sep = key.indexOf(':');
    const store = key.slice(0, sep);
    const id = key.slice(sep + 1);
    const localRec = localRecByKey.get(key) || null;
    const localTomb = localTombByKey.get(key) || null;
    const remote = remoteByKey.get(key) || null;

    // Newest surviving record version (by updatedAt), from whichever side.
    let bestRec = null; // { data, ts }
    if (localRec) bestRec = { data: localRec, ts: localRec.updatedAt };
    if (remote && (!bestRec || ms(remote.updated_at) > ms(bestRec.ts))) bestRec = { data: remote.data, ts: remote.updated_at };

    // Newest deletion (by deletedAt), from whichever side.
    let bestDel = null;
    if (localTomb && localTomb.deletedAt) bestDel = localTomb.deletedAt;
    if (remote && remote.deleted_at && (!bestDel || ms(remote.deleted_at) > ms(bestDel))) bestDel = remote.deleted_at;

    const isDeleted = bestDel && (!bestRec || ms(bestDel) >= ms(bestRec.ts));

    if (isDeleted) {
      mergedTombstones.push({ key, store, id, deletedAt: bestDel });
      if (localRec) localDeletes[store].push(id);
      const remoteDel = remote?.deleted_at || null;
      if (!remote || ms(remoteDel) !== ms(bestDel)) {
        remoteUpserts.push({
          store, record_id: id,
          data: bestRec?.data ?? remote?.data ?? {},
          updated_at: bestRec?.ts ?? bestDel,
          deleted_at: bestDel,
        });
      }
    } else if (bestRec) {
      if (!localRec || ms(localRec.updatedAt) !== ms(bestRec.ts)) localPuts[store].push(bestRec.data);
      // Push when remote is missing, stale, or still carrying a (now-losing)
      // tombstone that this live version should clear.
      if (!remote || ms(remote.updated_at) !== ms(bestRec.ts) || remote.deleted_at) {
        remoteUpserts.push({ store, record_id: id, data: bestRec.data, updated_at: bestRec.ts, deleted_at: null });
      }
    }
  }

  return { localPuts, localDeletes, remoteUpserts, mergedTombstones };
}

// --- IO: device-local sync metadata (kept in `settings`, never synced) ---

async function getMeta(key) {
  const r = await db.get('settings', key);
  return r ? r.value : undefined;
}
async function setMeta(key, value) {
  await db.put('settings', { key, value }); // direct write, no settings event
}

async function requireUserId() {
  const supabase = await getSupabaseClient();
  const { data } = await supabase.auth.getUser();
  const user = data?.user;
  if (!user) throw new Error('Sign in (Settings > Account) before using cloud sync.');
  return { supabase, userId: user.id };
}

// --- IO: gather local / fetch remote / apply / push ---

async function gatherLocal() {
  const stores = {};
  for (const store of SYNC_STORES) {
    const all = await db.getAll(store);
    // Attachment binaries live in Storage, not in the JSONB row.
    stores[store] = store === 'attachments' ? all.map(({ blob, ...rest }) => rest) : all;
  }
  const tombstones = await db.getAll('_tombstones');
  return { stores, tombstones };
}

async function fetchRemote(supabase) {
  const rows = [];
  for (let from = 0; ; from += PAGE) {
    const { data, error } = await supabase
      .from(TABLE)
      .select('store, record_id, data, updated_at, deleted_at')
      .range(from, from + PAGE - 1);
    if (error) throw error;
    rows.push(...(data || []));
    if (!data || data.length < PAGE) break;
  }
  return rows;
}

async function applyLocal(result, supabase, userId) {
  const affected = new Set();

  for (const store of SYNC_STORES) {
    for (const rec of result.localPuts[store]) {
      if (store === 'attachments') {
        // Metadata first; fetch the binary from Storage if we don't have it.
        const existing = await db.get('attachments', rec.id);
        let blob = existing?.blob || null;
        if (!blob) {
          const { data, error } = await supabase.storage.from(ATTACHMENT_BUCKET).download(`${userId}/${rec.id}`);
          if (!error && data) blob = data;
        }
        await db.put('attachments', { ...rec, blob });
      } else {
        await db.put(store, rec);
      }
      affected.add(store);
    }
    for (const id of result.localDeletes[store]) {
      await db.remove(store, id);
      affected.add(store);
    }
  }

  // Replace the local tombstone log with the reconciled union.
  await db.clear('_tombstones');
  for (const t of result.mergedTombstones) await db.put('_tombstones', t);

  return affected;
}

async function pushRemote(result, supabase, userId) {
  // Attachment binaries: upload for any live attachment row we're about to
  // push (naturally ~once, at creation, since reconcile only emits an upsert
  // when the metadata is new/changed). Remove the Storage object for tombstones.
  for (const up of result.remoteUpserts) {
    if (up.store !== 'attachments') continue;
    const path = `${userId}/${up.record_id}`;
    if (up.deleted_at) {
      await supabase.storage.from(ATTACHMENT_BUCKET).remove([path]).catch(() => {});
    } else {
      const local = await db.get('attachments', up.record_id);
      if (local?.blob) {
        const { error } = await supabase.storage.from(ATTACHMENT_BUCKET)
          .upload(path, local.blob, { contentType: local.mimeType || 'application/octet-stream', upsert: true });
        if (error) console.warn('supabase-sync: attachment upload failed', error.message);
      }
    }
  }

  const rows = result.remoteUpserts.map((up) => ({
    user_id: userId,
    store: up.store,
    record_id: up.record_id,
    data: up.data,
    updated_at: up.updated_at,
    deleted_at: up.deleted_at,
  }));
  for (let i = 0; i < rows.length; i += UPSERT_CHUNK) {
    const chunk = rows.slice(i, i + UPSERT_CHUNK);
    const { error } = await supabase.from(TABLE).upsert(chunk, { onConflict: 'user_id,store,record_id' });
    if (error) throw error;
  }
}

async function sync() {
  if (inFlight) return inFlight;
  inFlight = (async () => {
    const { supabase, userId } = await requireUserId();

    const local = await gatherLocal();
    const remoteRows = await fetchRemote(supabase);
    const result = reconcile(local.stores, local.tombstones, remoteRows);

    const affected = await applyLocal(result, supabase, userId);
    await pushRemote(result, supabase, userId);

    await setMeta('supabaseSyncLastSyncedAt', new Date().toISOString());
    await setMeta('supabaseSyncEnabled', true);

    for (const store of affected) events.emit(store, { action: 'sync' });
    return { affected: [...affected], pushed: result.remoteUpserts.length };
  })();
  try {
    return await inFlight;
  } finally {
    inFlight = null;
  }
}

// --- public API (used by the Settings view; mirrors sync.js's shape) ---

export async function connectSupabaseSync() {
  return sync();
}

export async function syncSupabaseNow() {
  return sync();
}

export async function disconnectSupabaseSync() {
  await setMeta('supabaseSyncEnabled', false);
}

export async function getSupabaseSyncState() {
  if (!isSupabaseConfigured()) return { configured: false, enabled: false, lastSyncedAt: null };
  let signedIn = false;
  try {
    const supabase = await getSupabaseClient();
    const { data } = await supabase.auth.getUser();
    signedIn = Boolean(data?.user);
  } catch { /* client unavailable -> treat as signed-out */ }
  return {
    configured: true,
    enabled: (await getMeta('supabaseSyncEnabled')) === true,
    signedIn,
    lastSyncedAt: await getMeta('supabaseSyncLastSyncedAt'),
  };
}
