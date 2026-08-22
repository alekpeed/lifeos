-- Life OS -- Attachment sync storage (R-01)
-- ============================================================
-- WHAT THIS IS
--
-- The bucket behind attachment sync. Until this exists, `sync_records` moves every
-- module's text and nothing else, so a record referencing a photo, scan, PDF or ebook
-- syncs the reference and leaves the bytes on the device that made them. On the second
-- device that record renders with a missing attachment, permanently.
--
-- WHY A BUCKET AND NOT A COLUMN
--
-- Attachments could ride in sync_records.data as base64. They must not. A pull selects
-- every row the user can see, so a photo stored that way would be re-downloaded by every
-- device on every sync, forever, and a Postgres row is the wrong place for megabytes of
-- binary. Sharebox already moves its files through a bucket for the same reason; this
-- mirrors it.
--
-- LAYOUT
--
-- Objects live at "<user_id>/<blob_id>", so the FIRST PATH SEGMENT IS THE OWNER. That is
-- what the policies below check, exactly as the sharebox-files policies route through
-- their first segment. Blob ids are random and their bytes are never rewritten in place
-- (editing a photo writes a new blob and drops the old), so an object is immutable once
-- written and an upload is safely idempotent.
--
-- HOW TO APPLY: run once in the Supabase SQL Editor, same as the other files here.
-- Safe to re-run.

-- ------------------------------------------------------------
-- 1. The bucket. Private: no public URL, every read carries a JWT.
-- ------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('attachments', 'attachments', false)
on conflict (id) do nothing;

-- ------------------------------------------------------------
-- 2. Policies. Owner-only, in all four directions.
-- ------------------------------------------------------------
-- storage.foldername(name) splits the object path; [1] is "<user_id>". Comparing it to
-- auth.uid() means a signed-in user reaches their own attachments and nobody else's,
-- even though the anon key is public.
drop policy if exists "read your own attachments"   on storage.objects;
drop policy if exists "upload your own attachments" on storage.objects;
drop policy if exists "update your own attachments" on storage.objects;
drop policy if exists "delete your own attachments" on storage.objects;

create policy "read your own attachments" on storage.objects
  for select using (
    bucket_id = 'attachments'
    and ((storage.foldername(name))[1])::uuid = auth.uid()
  );

create policy "upload your own attachments" on storage.objects
  for insert with check (
    bucket_id = 'attachments'
    and ((storage.foldername(name))[1])::uuid = auth.uid()
  );

-- Update is needed as well as insert: the client uploads with x-upsert, so a retry after
-- a lost local marker overwrites rather than failing with a conflict. Without this the
-- retry path 403s and the attachment never lands.
create policy "update your own attachments" on storage.objects
  for update using (
    bucket_id = 'attachments'
    and ((storage.foldername(name))[1])::uuid = auth.uid()
  );

create policy "delete your own attachments" on storage.objects
  for delete using (
    bucket_id = 'attachments'
    and ((storage.foldername(name))[1])::uuid = auth.uid()
  );

-- ------------------------------------------------------------
-- 3. Note on cleanup
-- ------------------------------------------------------------
-- The client deliberately never deletes remote objects for attachments it no longer
-- references. Another device may still hold the record that points at them, and its copy
-- may be the older one -- deleting on one device's say-so would destroy an attachment the
-- other still expects. An unreferenced object costs storage; a deleted one is gone. If
-- the bucket needs reclaiming later, do it deliberately against a reachability check run
-- over sync_records, not automatically from a client.
