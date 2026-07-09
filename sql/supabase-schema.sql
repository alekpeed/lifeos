-- Life OS -- Supabase schema (Sharebox v2 + future push/Telegram scaffolding)
--
-- NOT YET RUN against the live project. Paste this into the Supabase
-- dashboard's SQL Editor once supabase-config.js has real credentials.
-- Designed to work under EITHER auth strategy under discussion (Google
-- sign-in or magic-link email) -- both populate Supabase's built-in
-- auth.users table identically, so nothing here depends on that choice.
--
-- Design mirrors the existing Sharebox model (js/interfaces/default/views/
-- sharebox.js, js/data/sharebox-sync.js) but swaps "a Drive folder you both
-- picked" for "a space row you're both a member of" -- real access control
-- now lives in Postgres Row Level Security instead of Drive sharing.
--
-- Forward-looking: spaces have a members join table (not just two hardcoded
-- users), so friend-mesh Sharebox (more than one friend) needs zero schema
-- change later -- just more rows in sharebox_members.

-- ============================================================
-- SECTION 1: Sharebox (replaces the Picker/Drive sync model)
-- ============================================================

create table if not exists sharebox_spaces (
  id uuid primary key default gen_random_uuid(),
  name text not null default 'Sharebox',
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now()
);

create table if not exists sharebox_members (
  space_id uuid not null references sharebox_spaces(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  display_name text not null,           -- what shows next to their items (no separate profile table needed for v1)
  joined_at timestamptz not null default now(),
  primary key (space_id, user_id)
);

create table if not exists sharebox_items (
  id uuid primary key default gen_random_uuid(),
  space_id uuid not null references sharebox_spaces(id) on delete cascade,
  posted_by uuid not null references auth.users(id),
  kind text not null check (kind in ('link', 'note', 'file')),
  url text,
  title text,
  body text,
  urgency text not null default 'normal' check (urgency in ('normal', 'soon', 'urgent')),
  storage_path text,                    -- set for kind='file'; points into the sharebox-files Storage bucket
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Keep updated_at accurate on every edit (mirrors what api.js's entity
-- wrappers already do client-side for the rest of the app).
create or replace function set_updated_at() returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

drop trigger if exists sharebox_items_set_updated_at on sharebox_items;
create trigger sharebox_items_set_updated_at
  before update on sharebox_items
  for each row execute function set_updated_at();

-- Row Level Security: you can only see/touch a space (and its items) if
-- you're listed in sharebox_members for it. This IS the access control --
-- there's no separate "share the folder" step anymore, since adding someone
-- to a space's membership *is* granting them access.
--
-- IMPORTANT: membership checks go through the is_space_member() SECURITY
-- DEFINER helper, NOT an inline "in (select … from sharebox_members …)".
-- A policy on sharebox_members that subqueries sharebox_members recurses;
-- routing through a definer-rights function reads membership with RLS
-- bypassed, which breaks the recursion. See the full write-up in
-- sql/supabase-sharebox-rls-fix.sql (that file is the authoritative reset if
-- an already-deployed database has drifted).

alter table sharebox_spaces enable row level security;
alter table sharebox_members enable row level security;
alter table sharebox_items enable row level security;

-- Membership check that bypasses RLS on its inner read (no recursion).
create or replace function public.is_space_member(p_space_id uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
  select exists (
    select 1 from public.sharebox_members m
    where m.space_id = p_space_id and m.user_id = auth.uid()
  );
$$;
revoke all on function public.is_space_member(uuid) from public;
grant execute on function public.is_space_member(uuid) to authenticated;

-- Atomic create: space + creator membership in one txn, returns the space.
-- Sidesteps the insert-then-select trap (the spaces SELECT policy needs a
-- membership row that only exists after creation).
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

create policy "see your spaces" on sharebox_spaces
  for select using (created_by = auth.uid() or public.is_space_member(id));

create policy "see members of your spaces" on sharebox_members
  for select using (public.is_space_member(space_id));

create policy "add yourself to a space" on sharebox_members
  for insert with check (user_id = auth.uid());

create policy "update your own membership" on sharebox_members
  for update using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "leave a space" on sharebox_members
  for delete using (user_id = auth.uid());

create policy "read items in your spaces" on sharebox_items
  for select using (public.is_space_member(space_id));

create policy "post items to your spaces" on sharebox_items
  for insert with check (posted_by = auth.uid() and public.is_space_member(space_id));

create policy "edit your own items" on sharebox_items
  for update using (posted_by = auth.uid()) with check (posted_by = auth.uid());

create policy "delete your own items" on sharebox_items
  for delete using (posted_by = auth.uid());

-- Storage bucket for file-kind items (run once; Supabase also lets you
-- create this from the Storage tab in the dashboard instead if you prefer
-- clicking over SQL).
insert into storage.buckets (id, name, public)
values ('sharebox-files', 'sharebox-files', false)
on conflict (id) do nothing;

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

-- ============================================================
-- SECTION 2: FUTURE -- push notifications + Telegram
-- Harmless to create now (empty tables cost nothing on the free plan) but
-- nothing reads/writes these yet. Safe to skip this section for now and run
-- it later when that work actually starts.
-- ============================================================

create table if not exists push_subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  endpoint text not null,
  p256dh text not null,
  auth_key text not null,
  created_at timestamptz not null default now(),
  unique (user_id, endpoint)
);
alter table push_subscriptions enable row level security;
create policy "users manage their own push subscriptions" on push_subscriptions
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create table if not exists telegram_links (
  user_id uuid primary key references auth.users(id) on delete cascade,
  telegram_chat_id bigint not null unique,
  linked_at timestamptz not null default now()
);
alter table telegram_links enable row level security;
create policy "users manage their own telegram link" on telegram_links
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());
