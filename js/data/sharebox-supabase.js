// Sharebox v2 data layer — Supabase-backed replacement for the Drive/Picker
// snapshot sync in sharebox-sync.js.
//
// NOT WIRED IN YET (same gate as the rest of the Supabase scaffolding): every
// function funnels through getSupabaseClient(), which throws until
// supabase-config.js has real credentials. Importing this file changes nothing
// about the running app. The existing Drive-based Sharebox keeps working until
// this path is proven live, side by side, and only then does the old code go.
//
// The model shift vs. v1: there's no snapshot-merge / tombstone / last-write-
// wins engine here at all. Postgres is the single source of truth, Row Level
// Security (sql/supabase-schema.sql) is the access control, and Realtime
// subscriptions push changes instead of a manual "Sync now". A "space" is a
// row in sharebox_spaces that both people are members of (sharebox_members) --
// membership *is* the share, replacing "pick the same Drive folder".
//
// Item shape returned to the UI is normalized to match what the existing
// Sharebox view already renders (kind / url / title / body / urgency /
// postedBy / createdAt), so switching the view over is a small change.

import { getSupabaseClient } from './supabase-client.js';

const ITEMS = 'sharebox_items';
const SPACES = 'sharebox_spaces';
const MEMBERS = 'sharebox_members';
const FILE_BUCKET = 'sharebox-files';

// --- Spaces & membership ---

// Every space the signed-in user belongs to (RLS already scopes this to them).
export async function getMySpaces() {
  const supabase = await getSupabaseClient();
  const { data, error } = await supabase
    .from(SPACES)
    .select('id, name, created_by, created_at')
    .order('created_at', { ascending: true });
  if (error) throw error;
  return data || [];
}

// Create a new space and add the creator as its first member in one go.
// `displayName` is what shows next to the items they post.
export async function createSpace(name, displayName) {
  const supabase = await getSupabaseClient();
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) throw new Error('Sign in before creating a Sharebox space.');

  const { data: space, error } = await supabase
    .from(SPACES)
    .insert({ name: name || 'Sharebox', created_by: user.id })
    .select()
    .single();
  if (error) throw error;

  await joinSpace(space.id, displayName || 'Me');
  return space;
}

// Add the signed-in user to an existing space (the friend-invite side: your
// friend runs this against the space id you share with them). Idempotent —
// re-joining just updates the display name.
export async function joinSpace(spaceId, displayName) {
  const supabase = await getSupabaseClient();
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) throw new Error('Sign in before joining a Sharebox space.');

  const { error } = await supabase
    .from(MEMBERS)
    .upsert(
      { space_id: spaceId, user_id: user.id, display_name: displayName || 'Me' },
      { onConflict: 'space_id,user_id' }
    );
  if (error) throw error;
}

// Members of a space (to show "shared with …" and resolve posted_by names).
export async function getMembers(spaceId) {
  const supabase = await getSupabaseClient();
  const { data, error } = await supabase
    .from(MEMBERS)
    .select('user_id, display_name, joined_at')
    .eq('space_id', spaceId);
  if (error) throw error;
  return data || [];
}

// --- Items ---

// Normalize a DB row + a member-name lookup into the shape the view renders.
function toItem(row, nameFor) {
  return {
    id: row.id,
    spaceId: row.space_id,
    kind: row.kind,
    url: row.url,
    title: row.title,
    body: row.body,
    urgency: row.urgency,
    storagePath: row.storage_path,
    postedBy: nameFor ? nameFor(row.posted_by) : row.posted_by,
    postedById: row.posted_by,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

export async function listItems(spaceId) {
  const supabase = await getSupabaseClient();
  const [{ data: rows, error }, members] = await Promise.all([
    supabase.from(ITEMS).select('*').eq('space_id', spaceId).order('created_at', { ascending: false }),
    getMembers(spaceId),
  ]);
  if (error) throw error;
  const names = new Map(members.map((m) => [m.user_id, m.display_name]));
  const nameFor = (id) => names.get(id) || 'Someone';
  return (rows || []).map((r) => toItem(r, nameFor));
}

// Add a link / note / file item. For files, upload the blob first (uploadFile)
// and pass its storagePath here as `storagePath`.
export async function addItem({ spaceId, kind, url, title, body, urgency, storagePath }) {
  const supabase = await getSupabaseClient();
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) throw new Error('Sign in before posting to Sharebox.');

  const { data, error } = await supabase
    .from(ITEMS)
    .insert({
      space_id: spaceId,
      posted_by: user.id,
      kind,
      url: url || null,
      title: title || null,
      body: body || null,
      urgency: urgency || 'normal',
      storage_path: storagePath || null,
    })
    .select()
    .single();
  if (error) throw error;
  return toItem(data);
}

export async function updateItem(id, patch) {
  const supabase = await getSupabaseClient();
  const allowed = {};
  for (const k of ['url', 'title', 'body', 'urgency']) {
    if (k in patch) allowed[k] = patch[k];
  }
  const { data, error } = await supabase.from(ITEMS).update(allowed).eq('id', id).select().single();
  if (error) throw error;
  return toItem(data);
}

// Delete an item, and its backing file object if it had one. (RLS only lets
// you delete your own items; the storage policy likewise scopes file removal.)
export async function removeItem(id) {
  const supabase = await getSupabaseClient();
  const { data: row } = await supabase.from(ITEMS).select('storage_path').eq('id', id).single();
  const { error } = await supabase.from(ITEMS).delete().eq('id', id);
  if (error) throw error;
  if (row?.storage_path) {
    const { error: rmErr } = await supabase.storage.from(FILE_BUCKET).remove([row.storage_path]);
    if (rmErr) console.warn('sharebox v2: file object cleanup failed', rmErr);
  }
}

// --- Files (Storage bucket) ---

// Upload a File/Blob into the space's folder. The path is prefixed with the
// space id so the Storage RLS policy (which checks the first path segment
// against your memberships) grants access. Returns the storage path to hand
// to addItem({ kind: 'file', storagePath }).
export async function uploadFile(spaceId, file) {
  const supabase = await getSupabaseClient();
  const safeName = (file.name || 'file').replace(/[^\w.\-]+/g, '_');
  const path = `${spaceId}/${Date.now()}_${safeName}`;
  const { error } = await supabase.storage.from(FILE_BUCKET).upload(path, file, {
    contentType: file.type || 'application/octet-stream',
    upsert: false,
  });
  if (error) throw error;
  return path;
}

// A temporary signed URL for a private file (bucket is not public). Default
// one hour; the UI re-requests as needed rather than caching a stale URL.
export async function getFileUrl(storagePath, expiresInSeconds = 3600) {
  const supabase = await getSupabaseClient();
  const { data, error } = await supabase.storage.from(FILE_BUCKET).createSignedUrl(storagePath, expiresInSeconds);
  if (error) throw error;
  return data?.signedUrl || null;
}

// --- Realtime ---

// Subscribe to inserts/updates/deletes on a space's items. `cb` fires on any
// change (payload has eventType + new/old rows). Returns an unsubscribe fn.
// This is what replaces v1's manual "Sync now" — the friend's post shows up
// live. RLS applies to Realtime too, so you only get events for your spaces.
export function subscribeToItems(spaceId, cb) {
  let channel = null;
  let cancelled = false;
  getSupabaseClient().then((supabase) => {
    if (cancelled) return;
    channel = supabase
      .channel(`sharebox_items:${spaceId}`)
      .on('postgres_changes',
        { event: '*', schema: 'public', table: ITEMS, filter: `space_id=eq.${spaceId}` },
        (payload) => cb(payload))
      .subscribe();
  }).catch((err) => {
    // Unconfigured (or client-load failure): nothing to subscribe to. Stay
    // quiet rather than surfacing an unhandled rejection.
    console.warn('sharebox v2: realtime subscription unavailable', err.message);
  });
  return () => {
    cancelled = true;
    if (channel) getSupabaseClient().then((s) => s.removeChannel(channel)).catch(() => {});
  };
}
