-- Life OS -- Personal-data sync schema (Supabase migration off Google Drive)
-- ============================================================
-- WHAT THIS IS
--
-- The backend for moving the rest of the app's data (Tasks, Places, Finance,
-- Books, everything except Sharebox, which already lives on Supabase) off the
-- Drive snapshot-file sync and onto Postgres. Built ALONGSIDE Drive sync, not
-- replacing it in place: nothing here touches the existing Drive path, and the
-- app keeps working exactly as-is until this is applied AND the client engine
-- (js/data/supabase-sync.js) is wired in and proven live.
--
-- DESIGN: one generic table, not ~57 per-module tables.
--
-- The app already treats every record as an opaque { id, updatedAt, ... }
-- object in an IndexedDB object store. This mirrors that directly: one row per
-- record, keyed by (owner, store name, record id), with the whole record as
-- JSONB. It's a near-mechanical port of the Drive model (which likewise moves
-- whole record objects around), the lowest-surface-area option, and still
-- fully queryable server-side later via Postgres JSONB operators + a GIN index
-- where a specific push/webhook feature needs one. Per-module tables would be
-- ~57 hand-modeled schemas for records whose nested shapes (recurring bills,
-- visit-date arrays, custom-accent objects) end up as JSONB columns anyway.
--
-- CONFLICT MODEL: last-write-wins, identical to the current Drive sync.
--
-- `updated_at` mirrors each record's OWN updatedAt (set by the client), and
-- reconciliation keeps the max-updatedAt version. Deletes are soft: a row with
-- deleted_at set is a tombstone that propagates instead of the record
-- resurrecting from another device. A tombstone wins only if its deleted_at is
-- >= the record's updated_at, so an edit made after a delete correctly
-- resurrects -- exactly the rule mergeState() in js/data/sync.js already uses.
--
-- IMPORTANT: there is deliberately NO set_updated_at() trigger on this table.
-- updated_at must stay the record's app-level timestamp, NOT the row's DB
-- write time -- otherwise every push would look "newest" and last-write-wins
-- would break. The client always sets updated_at explicitly.
--
-- HOW TO APPLY: run once in the Supabase SQL Editor (or via the Management
-- API's POST /v1/projects/{ref}/database/query with a personal access token),
-- same as supabase-schema.sql / supabase-accounts-schema.sql were run.
-- ============================================================

-- One row per record, per owner. `store` is the IndexedDB store name
-- ('tasks', 'bills', ...), `record_id` is the record's own app-generated id.
create table if not exists sync_records (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  store text not null,
  record_id text not null,
  data jsonb not null,
  updated_at timestamptz not null,
  deleted_at timestamptz,
  primary key (user_id, store, record_id)
);

-- The "pull everything that changed since my last sync" query hits
-- (user_id, updated_at); the primary key covers point lookups by
-- (user_id, store, record_id). A GIN index on `data` is deliberately NOT
-- created yet -- it costs write amplification on every sync, so add it only
-- when a concrete server-side JSONB query (a push job, a webhook) needs it.
create index if not exists sync_records_user_updated
  on sync_records (user_id, updated_at);

alter table sync_records enable row level security;

-- One policy per operation, all the same rule: you only ever touch your own
-- rows. auth.uid() is the signed-in user; an unauthenticated request matches
-- nothing. (No cross-user sharing here at all -- that's Sharebox's job, a
-- separate set of tables.)
drop policy if exists sync_records_select on sync_records;
create policy sync_records_select on sync_records
  for select using (user_id = auth.uid());

drop policy if exists sync_records_insert on sync_records;
create policy sync_records_insert on sync_records
  for insert with check (user_id = auth.uid());

drop policy if exists sync_records_update on sync_records;
create policy sync_records_update on sync_records
  for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Deletes are soft (set deleted_at) in normal operation, so this policy only
-- matters if you ever hard-prune old tombstones; scoped to your own rows
-- either way.
drop policy if exists sync_records_delete on sync_records;
create policy sync_records_delete on sync_records
  for delete using (user_id = auth.uid());

-- ============================================================
-- Attachment binaries -> private Storage bucket, one folder per user
-- ============================================================
-- Photos, PDFs, book covers etc. don't belong in JSONB. The metadata record
-- (the `attachments` store row, minus its blob) still syncs through
-- sync_records like everything else; only the binary lives here, at the path
-- `<user_id>/<record_id>`. Same shape as the sharebox-files bucket, but the
-- RLS check is "the first path segment is YOUR user id" instead of space
-- membership.
insert into storage.buckets (id, name, public)
values ('lifeos-attachments', 'lifeos-attachments', false)
on conflict (id) do nothing;

drop policy if exists lifeos_attachments_select on storage.objects;
create policy lifeos_attachments_select on storage.objects
  for select using (
    bucket_id = 'lifeos-attachments'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists lifeos_attachments_insert on storage.objects;
create policy lifeos_attachments_insert on storage.objects
  for insert with check (
    bucket_id = 'lifeos-attachments'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists lifeos_attachments_update on storage.objects;
create policy lifeos_attachments_update on storage.objects
  for update using (
    bucket_id = 'lifeos-attachments'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists lifeos_attachments_delete on storage.objects;
create policy lifeos_attachments_delete on storage.objects
  for delete using (
    bucket_id = 'lifeos-attachments'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
