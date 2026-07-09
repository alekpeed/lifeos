-- Life OS — Sharebox v2 RLS reset & fix
-- ============================================================
-- WHY THIS FILE EXISTS
--
-- During bring-up we hit a wall: posting an item failed with
--   403 "new row violates row-level security policy for table sharebox_items"
-- and, as a stopgap, RLS ended up disabled on sharebox_items and several
-- policies were hand-edited in the dashboard. So the LIVE database no longer
-- matches sql/supabase-schema.sql. This script is the authoritative reset:
-- run it ONCE in the Supabase SQL Editor and, no matter what patchwork is
-- currently live, the three Sharebox tables end up in a known-good state.
--
-- ROOT CAUSE (two separate bugs the one error message was hiding)
--
--   1. Self-referential members policy. The original
--        "members can see other members of their spaces" policy on
--        sharebox_members had a USING clause that itself SELECTed from
--        sharebox_members. A policy on a table that queries that same table
--        recurses; Postgres either raises 42P17 or the subquery yields no
--        rows. Every *other* policy that leaned on "…in (select space_id from
--        sharebox_members where user_id = auth.uid())" then evaluated against
--        an empty set, so the items INSERT check became `space_id in (∅)` →
--        false → the 42501 "violates row-level security" we actually saw.
--
--   2. Insert-then-select before membership exists. createSpace() inserted a
--        row into sharebox_spaces and then .select()'d it back, but the SELECT
--        policy on sharebox_spaces requires membership — which was only added
--        afterward by joinSpace(). The RETURNING read couldn't see the fresh
--        row, so .single() threw.
--
-- THE FIX
--
--   • is_space_member() — a SECURITY DEFINER function that answers "is the
--     current user a member of this space?" while BYPASSING RLS on its inner
--     read. Calling it from inside a policy can't recurse, because the inner
--     read of sharebox_members runs as the function owner (RLS off), not as
--     the querying user. This is the canonical Supabase pattern for exactly
--     this class of bug.
--
--   • create_space() — a SECURITY DEFINER RPC that inserts the space AND the
--     creator's membership in ONE transaction and returns the space row. No
--     insert-then-select trap (the function owner bypasses the SELECT policy),
--     and no half-created space if the second insert fails.
--
--   • Every read/write policy is rewritten to go through is_space_member(),
--     so none of them touch sharebox_members directly.
--
-- Safe to re-run: everything is drop-if-exists / create-or-replace.

-- ------------------------------------------------------------
-- 1. Membership check that does NOT trip RLS recursion.
-- ------------------------------------------------------------
-- SECURITY DEFINER → runs as the function owner (postgres), which bypasses
-- RLS on the inner read. STABLE because it only reads. search_path is pinned
-- and the table is schema-qualified so the definer-rights body can't be
-- hijacked by a caller-controlled search_path.
create or replace function public.is_space_member(p_space_id uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
  select exists (
    select 1
    from public.sharebox_members m
    where m.space_id = p_space_id
      and m.user_id = auth.uid()
  );
$$;

revoke all on function public.is_space_member(uuid) from public;
grant execute on function public.is_space_member(uuid) to authenticated;

-- ------------------------------------------------------------
-- 2. Atomic space creation (space + creator membership in one txn).
-- ------------------------------------------------------------
create or replace function public.create_space(p_name text, p_display_name text)
returns public.sharebox_spaces
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := auth.uid();
  v_space public.sharebox_spaces;
begin
  if v_uid is null then
    raise exception 'must be signed in to create a space';
  end if;

  insert into public.sharebox_spaces (name, created_by)
  values (coalesce(nullif(btrim(p_name), ''), 'Sharebox'), v_uid)
  returning * into v_space;

  insert into public.sharebox_members (space_id, user_id, display_name)
  values (v_space.id, v_uid, coalesce(nullif(btrim(p_display_name), ''), 'Me'));

  return v_space;
end;
$$;

revoke all on function public.create_space(text, text) from public;
grant execute on function public.create_space(text, text) to authenticated;

-- ------------------------------------------------------------
-- 3. Clean slate: drop EVERY existing policy on the three tables.
-- ------------------------------------------------------------
-- We can't drop the hand-edited policies by name (we don't know what they
-- were renamed to during debugging), so drop them all, then rebuild the
-- canonical set below. This is what makes the script authoritative.
do $$
declare r record;
begin
  for r in
    select policyname, tablename
    from pg_policies
    where schemaname = 'public'
      and tablename in ('sharebox_spaces', 'sharebox_members', 'sharebox_items')
  loop
    execute format('drop policy if exists %I on public.%I', r.policyname, r.tablename);
  end loop;
end $$;

-- Re-enable RLS everywhere (sharebox_items had it disabled as a stopgap).
alter table public.sharebox_spaces  enable row level security;
alter table public.sharebox_members enable row level security;
alter table public.sharebox_items   enable row level security;

-- ------------------------------------------------------------
-- 4. Canonical policies — all membership checks via is_space_member().
-- ------------------------------------------------------------

-- Spaces: you can see a space if you're a member OR you created it. The
-- created_by clause is belt-and-suspenders so a creator can always see their
-- own space even in the instant before/without a membership row.
create policy "see your spaces" on public.sharebox_spaces
  for select using (
    created_by = auth.uid() or public.is_space_member(id)
  );
-- No client-facing INSERT/UPDATE/DELETE policy on spaces on purpose: creation
-- goes through create_space() (SECURITY DEFINER), so direct inserts stay
-- denied and can't bypass the atomic space+membership guarantee.

-- Members: you can see member rows of any space you belong to. Non-recursive
-- because is_space_member() reads members with RLS bypassed.
create policy "see members of your spaces" on public.sharebox_members
  for select using (public.is_space_member(space_id));

-- You may add ONLY yourself to a space (knowing the space id is the invite,
-- mirroring v1's "share the folder" capability). This check never touches
-- sharebox_members, so no recursion.
create policy "add yourself to a space" on public.sharebox_members
  for insert with check (user_id = auth.uid());

-- Re-joining upserts, which takes the UPDATE path; allow updating only your
-- own membership row (e.g. changing your display name).
create policy "update your own membership" on public.sharebox_members
  for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Optional: let people leave a space (delete their own membership).
create policy "leave a space" on public.sharebox_members
  for delete using (user_id = auth.uid());

-- Items: read anything in a space you belong to.
create policy "read items in your spaces" on public.sharebox_items
  for select using (public.is_space_member(space_id));

-- Post to a space you belong to, as yourself. Both checks are cheap and
-- recursion-free.
create policy "post items to your spaces" on public.sharebox_items
  for insert with check (
    posted_by = auth.uid() and public.is_space_member(space_id)
  );

-- Edit / delete only your own items.
create policy "edit your own items" on public.sharebox_items
  for update using (posted_by = auth.uid()) with check (posted_by = auth.uid());

create policy "delete your own items" on public.sharebox_items
  for delete using (posted_by = auth.uid());

-- ------------------------------------------------------------
-- 5. Storage policies (file-kind items) — also via is_space_member().
-- ------------------------------------------------------------
-- Files live at "<space_id>/<filename>", so the first path segment is the
-- space id. Route the check through is_space_member() instead of an inline
-- subquery for the same non-recursion reason.
drop policy if exists "members can read files in their spaces"   on storage.objects;
drop policy if exists "members can upload files to their spaces" on storage.objects;
drop policy if exists "read files in your spaces"                on storage.objects;
drop policy if exists "upload files to your spaces"              on storage.objects;
drop policy if exists "delete files in your spaces"              on storage.objects;

create policy "read files in your spaces" on storage.objects
  for select using (
    bucket_id = 'sharebox-files'
    and public.is_space_member(((storage.foldername(name))[1])::uuid)
  );

create policy "upload files to your spaces" on storage.objects
  for insert with check (
    bucket_id = 'sharebox-files'
    and public.is_space_member(((storage.foldername(name))[1])::uuid)
  );

create policy "delete files in your spaces" on storage.objects
  for delete using (
    bucket_id = 'sharebox-files'
    and public.is_space_member(((storage.foldername(name))[1])::uuid)
  );
